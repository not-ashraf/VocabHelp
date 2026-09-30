package com.example.vocabhelp.quiz

import com.example.vocabhelp.data.db.MeaningEntry
import com.example.vocabhelp.data.db.MeaningStatus
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.repo.DefinitionOption
import com.example.vocabhelp.data.repo.RelatedWord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizQuestionFactoryTest {

    private val crone = word(1, "crone", "noun", "An old woman.")
    private val scowl = word(2, "scowl", "noun", "A wrinkling of the brows in displeasure.")
    private val snide = word(3, "snide", "adjective", "Cutting or sarcastic.")
    private val slink = word(4, "slink", "verb", "To sneak about furtively.")
    private val vertigo = word(5, "vertigo", "noun", "(medicine) A sensation of whirling and loss of balance.")
    private val saved = listOf(crone, scowl, snide, slink, vertigo)

    /** Mirrors real Datamuse `ml=` shapes observed for these words. */
    private val relatedData = mapOf(
        "vertigo" to listOf(
            related("dizziness", "noun", syn = true, def = "The state of being dizzy."),
            related("giddiness", "noun", syn = true, def = "The state of being giddy."),
            related("dizzy", "adjective", def = "Experiencing a sensation of whirling."),
        ),
        "crone" to listOf(
            related("beldame", "noun", syn = true, def = "(archaic) An old woman, particularly an ugly one."),
            related("hag", "noun", syn = true, def = "(derogatory) An ugly old woman."),
        ),
        "scowl" to listOf(
            related("frown", "noun", syn = true, def = "A facial expression of displeasure."),
            related("glower", "noun", def = "A sullen, angry stare."),
            related("lour", "verb", def = "To frown; to look sullen."),
        ),
        "snide" to listOf(related("sneering", "adjective", syn = true, def = "Showing contempt.")),
        "slink" to listOf(related("sneak", "verb", syn = true, def = "To move stealthily.")),
    )

    private fun factory(seed: Int, online: Boolean = true) = QuizQuestionFactory(
        related = { w -> if (online) relatedData[w].orEmpty() else emptyList() },
        random = Random(seed),
    )

    private suspend fun cardsFor(target: WordEntity, online: Boolean = true): List<QuizCard> =
        (0 until 300).map { seed -> factory(seed, online).build(target, saved - target) }

    @Test
    fun `every question type is produced and choice cards are well formed`() = runTest {
        val cards = cardsFor(vertigo)
        assertEquals(QuizType.entries.toSet(), cards.map { it.type }.toSet())
        cards.filterIsInstance<ChoiceCard>().forEach { card ->
            assertEquals("4 options: $card", 4, card.options.size)
            assertEquals("options unique: $card", 4, card.options.map { it.lowercase() }.toSet().size)
            assertTrue(card.correctIndex in card.options.indices)
        }
    }

    @Test
    fun `meaning question uses same part-of-speech decoys and cleans labels`() = runTest {
        val meaning = cardsFor(vertigo).filterIsInstance<ChoiceCard>().filter { it.type == QuizType.MEANING }
        assertTrue(meaning.isNotEmpty())
        meaning.forEach { card ->
            // "(medicine)" label is stripped so the right answer doesn't stand out by style.
            assertEquals("A sensation of whirling and loss of balance.", card.options[card.correctIndex])
            val wrong = card.options - card.options[card.correctIndex]
            // The adjective "dizzy" / adjective "snide" / verb "slink" must never appear for a noun.
            assertFalse(wrong.any { it.startsWith("Experiencing") || it.startsWith("Cutting") || it.startsWith("To ") })
            // Target's own synonyms ("dizziness") would arguably be correct: never used.
            assertFalse(wrong.any { it.contains("dizzy", ignoreCase = true) || it.contains("giddy", ignoreCase = true) })
            assertFalse(wrong.any { it.startsWith("(") })
        }
    }

    @Test
    fun `synonym question picks a real synonym and never the target's relatives as decoys`() = runTest {
        val synonym = cardsFor(vertigo).filterIsInstance<ChoiceCard>().filter { it.type == QuizType.SYNONYM }
        assertTrue(synonym.isNotEmpty())
        synonym.forEach { card ->
            assertEquals("vertigo", card.prompt)
            assertTrue(card.options[card.correctIndex] in setOf("dizziness", "giddiness"))
            val wrong = card.options - card.options[card.correctIndex]
            assertFalse(wrong.any { it in setOf("dizziness", "giddiness", "dizzy", "vertigo") })
        }
    }

    @Test
    fun `reverse question masks the word inside its own definition`() = runTest {
        val target = word(6, "obfuscate", "verb", "To obfuscate is to make obscure; obfuscation hides meaning.")
        val reverse = (0 until 300).map { factory(it).build(target, saved) }
            .filterIsInstance<ChoiceCard>().filter { it.type == QuizType.REVERSE }
        assertTrue(reverse.isNotEmpty())
        reverse.forEach { card ->
            assertFalse(card.prompt, card.prompt.contains("obfusc", ignoreCase = true))
            assertEquals("obfuscate", card.options[card.correctIndex])
        }
    }

    @Test
    fun `offline still builds full questions from saved words and fallbacks, without synonyms`() = runTest {
        val cards = cardsFor(vertigo, online = false)
        assertFalse(cards.any { it.type == QuizType.SYNONYM })
        cards.filterIsInstance<ChoiceCard>().forEach { assertEquals(4, it.options.toSet().size) }
    }

    @Test
    fun `avoid type is respected so the same type isn't repeated back to back`() = runTest {
        (0 until 100).forEach { seed ->
            assertTrue(factory(seed).build(vertigo, saved - vertigo, avoid = QuizType.FLASHCARD) !is FlashCard)
        }
    }

    @Test
    fun `word without meanings falls back to a flashcard`() = runTest {
        val manualEmpty = vertigo.copy(meanings = emptyList())
        assertTrue(factory(0).build(manualEmpty, saved) is FlashCard)
    }

    @Test
    fun `untagged or wrong-POS synonyms are not offered`() = runTest {
        // Mirrors "delinquent" (noun sense) where Datamuse returned "guilty" as an adjective.
        val delinquent = word(7, "delinquent", "noun", "One who disobeys or breaks rules or laws.")
        val data = relatedData + ("delinquent" to listOf(
            RelatedWord("guilty", emptySet(), true, listOf(DefinitionOption("adjective", "Responsible for wrongdoing.", null))),
            related("derelict", "adjective", syn = true, def = "Negligent."),
        ))
        (0 until 200).forEach { seed ->
            val card = QuizQuestionFactory({ data[it].orEmpty() }, Random(seed)).build(delinquent, saved)
            assertTrue("no synonym for noun sense: $card", card.type != QuizType.SYNONYM)
        }
    }

    @Test
    fun `synonym sharing a chunk with the target is rejected`() = runTest {
        val aglow = word(8, "aglow", "adjective", "Glowing; radiant with warmth.")
        val data = relatedData + ("aglow" to listOf(related("glowing", "adjective", syn = true, def = "Emitting light.")))
        (0 until 200).forEach { seed ->
            val card = QuizQuestionFactory({ data[it].orEmpty() }, Random(seed)).build(aglow, saved)
            assertTrue(card.type != QuizType.SYNONYM)
        }
        assertTrue(sharesChunk("glowing", "aglow"))
        assertFalse(sharesChunk("dizziness", "vertigo"))
    }

    @Test
    fun `junk definitions only get flashcards or synonyms`() = runTest {
        val junk = word(9, "snide", "adjective", "rr")
        (0 until 200).forEach { seed ->
            val card = factory(seed).build(junk, saved - snide)
            assertTrue("$card", card.type == QuizType.FLASHCARD || card.type == QuizType.SYNONYM)
        }
    }

    @Test
    fun `maskWord hides shorter relatives of long words`() {
        assertEquals("The quality of being ____ or macabre.", maskWord("The quality of being grotesque or macabre.", "grotesquerie"))
        assertEquals("To ____ about.", maskWord("To slink about.", "slink"))
    }

    @Test
    fun `normalizePos handles the various source spellings`() {
        assertEquals("adverb", normalizePos("adv"))
        assertEquals("adverb", normalizePos("Adverb"))
        assertEquals("verb", normalizePos("verb"))
        assertEquals("noun", normalizePos("proper noun"))
        assertEquals("adjective", normalizePos("adj"))
        assertNull(normalizePos("interjection"))
        assertNull(normalizePos(null))
    }

    @Test
    fun `cleanDefinition strips stacked labels and capitalises`() {
        assertEquals("An ugly old woman.", cleanDefinition("(US, slang) (derogatory) an ugly old woman."))
        assertEquals("A sign given, especially of a cross.", cleanDefinition("A sign given, especially of a cross"))
    }

    private fun word(id: Long, w: String, pos: String, def: String) = WordEntity(
        id = id,
        word = w,
        meanings = listOf(MeaningEntry(partOfSpeech = pos, definition = def)),
        status = MeaningStatus.RESOLVED,
        nextReviewAt = 0,
        createdAt = 0,
    )

    private fun related(w: String, pos: String, syn: Boolean = false, def: String) =
        RelatedWord(w, setOf(pos), syn, listOf(DefinitionOption(pos, def, null)))
}
