package com.example.vocabhelp.data.repo

import com.example.vocabhelp.data.remote.DatamuseApi
import com.example.vocabhelp.data.remote.WiktionaryApi
import com.example.vocabhelp.data.remote.WiktionaryUsageDto
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Word lookup against Wiktionary (rich: examples, parts of speech), falling
 * back to Datamuse when Wiktionary has no entry or can't be reached.
 */
class DictionaryLookup(
    private val wiktionary: WiktionaryApi,
    private val datamuse: DatamuseApi,
) {
    suspend fun lookup(word: String): LookupOutcome {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return LookupOutcome.NotFound

        val primary = attempt { parseWiktionary(wiktionary.definitions(normalized)) }
        if (primary is Attempt.Found) return LookupOutcome.Success(primary.options, audioUrl = null)

        val fallback = attempt {
            datamuse.define(normalized)
                .firstOrNull { it.word.equals(normalized, ignoreCase = true) }
                ?.let { parseDatamuse(it.defs) }
                .orEmpty()
        }
        return when {
            fallback is Attempt.Found -> LookupOutcome.Success(fallback.options, audioUrl = null)
            // Only report "not in the dictionary" when both sources actually answered.
            primary == Attempt.Missing && fallback == Attempt.Missing -> LookupOutcome.NotFound
            else -> LookupOutcome.NetworkError
        }
    }

    /** Autocomplete candidates for [prefix]; empty on any failure (offline, etc.). */
    suspend fun suggest(prefix: String, max: Int = 8): List<String> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return try {
            datamuse.suggest(normalized, max).map { it.word }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private sealed interface Attempt {
        data class Found(val options: List<DefinitionOption>) : Attempt
        data object Missing : Attempt
        data object Failed : Attempt
    }

    private inline fun attempt(block: () -> List<DefinitionOption>): Attempt = try {
        val options = block()
        if (options.isEmpty()) Attempt.Missing else Attempt.Found(options)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 404) Attempt.Missing else Attempt.Failed
    } catch (_: Exception) {
        Attempt.Failed
    }
}

internal fun parseWiktionary(byLanguage: Map<String, List<WiktionaryUsageDto>>): List<DefinitionOption> =
    byLanguage["en"].orEmpty().flatMap { usage ->
        val pos = usage.partOfSpeech?.lowercase()?.takeIf { it.isNotBlank() }
        usage.definitions.mapNotNull { def ->
            // Sub-senses arrive as a nested <ol> inside the parent definition and are
            // also listed as their own entries, so keep only the parent's own text.
            val text = stripHtml(def.definition.substringBefore("<ol").substringBefore("<ul"))
            if (text.isEmpty()) return@mapNotNull null
            DefinitionOption(
                partOfSpeech = pos,
                definition = text,
                example = def.examples.firstNotNullOfOrNull { ex -> stripHtml(ex).takeIf { it.isNotEmpty() } },
            )
        }
    }

internal fun parseDatamuse(defs: List<String>): List<DefinitionOption> = defs.mapNotNull { raw ->
    val tab = raw.indexOf('\t')
    val tag = if (tab >= 0) raw.substring(0, tab) else ""
    val text = (if (tab >= 0) raw.substring(tab + 1) else raw).trim()
    if (text.isEmpty()) return@mapNotNull null
    DefinitionOption(partOfSpeech = DATAMUSE_POS[tag], definition = text, example = null)
}

private val DATAMUSE_POS = mapOf(
    "n" to "noun",
    "v" to "verb",
    "adj" to "adjective",
    "adv" to "adverb",
)

private val TAG = Regex("<[^>]*>")
private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
private val WHITESPACE = Regex("\\s+")
private val NAMED_ENTITIES = mapOf(
    "&nbsp;" to " ",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&apos;" to "'",
    "&#39;" to "'",
    "&amp;" to "&", // last, so "&amp;lt;" decodes to "&lt;" rather than "<"
)

/** Drops tags, decodes common entities and collapses whitespace. Pure Kotlin so it's JVM-testable. */
internal fun stripHtml(html: String): String {
    var s = TAG.replace(html, "")
    s = NUMERIC_ENTITY.replace(s) { m ->
        val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        code?.let { String(Character.toChars(it)) } ?: m.value
    }
    NAMED_ENTITIES.forEach { (entity, ch) -> s = s.replace(entity, ch) }
    return WHITESPACE.replace(s, " ").trim()
}
