package com.example.vocabhelp.data.repo

import com.example.vocabhelp.data.remote.DatamuseApi
import com.example.vocabhelp.data.remote.DatamuseWordDto
import com.example.vocabhelp.data.remote.WiktionaryApi
import com.example.vocabhelp.data.remote.WiktionaryDefinitionDto
import com.example.vocabhelp.data.remote.WiktionaryUsageDto
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class DictionaryLookupTest {

    // --- parsing (fixtures copied from real API responses) ---

    @Test
    fun `wiktionary definitions are stripped of HTML and lowercased POS`() {
        val options = parseWiktionary(
            mapOf(
                "en" to listOf(
                    WiktionaryUsageDto(
                        partOfSpeech = "Verb",
                        definitions = listOf(
                            WiktionaryDefinitionDto(
                                definition = "<span class=\"usage-label-sense\" about=\"#mwt29\" typeof=\"mw:Transclusion\"></span> " +
                                    "To give <a rel=\"mw:WikiLink\" href=\"/wiki/birth\" title=\"birth\">birth</a> to an animal prematurely.",
                                examples = listOf("a cow that <b>slinks</b> her calf"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(1, options.size)
        assertEquals("verb", options[0].partOfSpeech)
        assertEquals("To give birth to an animal prematurely.", options[0].definition)
        assertEquals("a cow that slinks her calf", options[0].example)
    }

    @Test
    fun `wiktionary nested sub-sense lists are dropped and empty definitions skipped`() {
        val options = parseWiktionary(
            mapOf(
                "en" to listOf(
                    WiktionaryUsageDto(
                        partOfSpeech = "Verb",
                        definitions = listOf(
                            WiktionaryDefinitionDto("To move swiftly.\n<ol><li><span></span> To <a href=\"/wiki/go\">go</a> fast.</li></ol>"),
                            WiktionaryDefinitionDto(""),
                            WiktionaryDefinitionDto("<span class=\"usage-label-sense\"></span>"),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(listOf("To move swiftly."), options.map { it.definition })
        assertNull(options[0].example)
    }

    @Test
    fun `wiktionary non-English sections are ignored`() {
        val usage = WiktionaryUsageDto("Noun", listOf(WiktionaryDefinitionDto("A thing.")))
        assertTrue(parseWiktionary(mapOf("nl" to listOf(usage), "sv" to listOf(usage))).isEmpty())
    }

    @Test
    fun `datamuse tab-separated defs map parts of speech`() {
        val options = parseDatamuse(
            listOf(
                "v\t(intransitive) To sneak about furtively. ",
                "adj\t(Scotland) Thin; lean ",
                "u\tSomething unknown",
                "n\t ",
            ),
        )
        assertEquals(listOf("verb", "adjective", null), options.map { it.partOfSpeech })
        assertEquals("(intransitive) To sneak about furtively.", options[0].definition)
    }

    @Test
    fun `stripHtml decodes entities and collapses whitespace`() {
        assertEquals("a & b < c \"d\" é", stripHtml("a &amp; b&nbsp;&lt; c  &quot;d&quot;\n&#233;"))
        assertEquals("&lt;", stripHtml("&amp;lt;"))
    }

    // --- fallback behaviour ---

    @Test
    fun `wiktionary hit is used without calling datamuse`() = runTest {
        val datamuse = FakeDatamuse(define = { error("should not be called") })
        val lookup = DictionaryLookup(FakeWiktionary { WIKI_HIT }, datamuse)
        val outcome = lookup.lookup("  Lugubrious ") as LookupOutcome.Success
        assertEquals("Gloomy, mournful or dismal.", outcome.options.single().definition)
    }

    @Test
    fun `wiktionary 404 falls back to datamuse`() = runTest {
        val lookup = DictionaryLookup(
            FakeWiktionary { throw http(404) },
            FakeDatamuse(define = { listOf(DatamuseWordDto("slink", listOf("n\tA furtive sneaking motion."))) }),
        )
        val outcome = lookup.lookup("slink") as LookupOutcome.Success
        assertEquals("noun", outcome.options.single().partOfSpeech)
    }

    @Test
    fun `wiktionary timeout falls back to datamuse`() = runTest {
        val lookup = DictionaryLookup(
            FakeWiktionary { throw SocketTimeoutException("timeout") },
            FakeDatamuse(define = { listOf(DatamuseWordDto("slink", listOf("n\tA furtive sneaking motion."))) }),
        )
        assertTrue(lookup.lookup("slink") is LookupOutcome.Success)
    }

    @Test
    fun `datamuse result for a different spelling is not accepted`() = runTest {
        val lookup = DictionaryLookup(
            FakeWiktionary { throw http(404) },
            FakeDatamuse(define = { listOf(DatamuseWordDto("slinky", listOf("adj\tSinuous."))) }),
        )
        assertEquals(LookupOutcome.NotFound, lookup.lookup("slink"))
    }

    @Test
    fun `both sources answering empty is NotFound`() = runTest {
        val lookup = DictionaryLookup(FakeWiktionary { throw http(404) }, FakeDatamuse(define = { emptyList() }))
        assertEquals(LookupOutcome.NotFound, lookup.lookup("zzqqxxw"))
    }

    @Test
    fun `any source failing without a hit is NetworkError`() = runTest {
        val wikiDown = DictionaryLookup(FakeWiktionary { throw http(503) }, FakeDatamuse(define = { emptyList() }))
        assertEquals(LookupOutcome.NetworkError, wikiDown.lookup("slink"))

        val offline = DictionaryLookup(
            FakeWiktionary { throw IOException("offline") },
            FakeDatamuse(define = { throw IOException("offline") }),
        )
        assertEquals(LookupOutcome.NetworkError, offline.lookup("slink"))
    }

    @Test
    fun `suggest swallows failures`() = runTest {
        val ok = DictionaryLookup(
            FakeWiktionary { WIKI_HIT },
            FakeDatamuse(suggest = { listOf(DatamuseWordDto("lugubrious"), DatamuseWordDto("lugubriously")) }),
        )
        assertEquals(listOf("lugubrious", "lugubriously"), ok.suggest("lugub"))

        val offline = DictionaryLookup(FakeWiktionary { WIKI_HIT }, FakeDatamuse(suggest = { throw IOException() }))
        assertEquals(emptyList<String>(), offline.suggest("lugub"))
        assertEquals(emptyList<String>(), offline.suggest("  "))
    }

    private class FakeWiktionary(
        private val respond: (String) -> Map<String, List<WiktionaryUsageDto>>,
    ) : WiktionaryApi {
        override suspend fun definitions(word: String) = respond(word)
    }

    private class FakeDatamuse(
        define: (String) -> List<DatamuseWordDto> = { emptyList() },
        suggest: (String) -> List<DatamuseWordDto> = { emptyList() },
    ) : DatamuseApi {
        private val onDefine = define
        private val onSuggest = suggest
        override suspend fun define(word: String, metadata: String, max: Int): List<DatamuseWordDto> = onDefine(word)
        override suspend fun suggest(prefix: String, max: Int): List<DatamuseWordDto> = onSuggest(prefix)
        override suspend fun meansLike(word: String, metadata: String, max: Int): List<DatamuseWordDto> = emptyList()
    }

    private companion object {
        val WIKI_HIT = mapOf(
            "en" to listOf(
                WiktionaryUsageDto("Adjective", listOf(WiktionaryDefinitionDto("Gloomy, mournful or dismal."))),
            ),
        )

        fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
    }
}
