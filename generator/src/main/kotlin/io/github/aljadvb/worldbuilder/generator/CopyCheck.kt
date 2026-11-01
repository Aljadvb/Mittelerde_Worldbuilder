package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.Scenario

/** A text that repeats [words] consecutive words of the source verbatim. */
data class CopyFinding(val objectId: String, val field: String, val excerpt: String, val words: Int)

/**
 * Finds longer verbatim passages from the source in generated texts. Names and short
 * phrases are fine; [minWords] consecutive identical words are reported.
 */
object CopyCheck {
    private val WORD = Regex("\\p{L}+")

    private fun words(text: String) = WORD.findAll(text.lowercase()).map { it.value }.toList()

    fun check(scenario: Scenario, source: String, minWords: Int = 12): List<CopyFinding> {
        val src = words(source)
        if (src.size < minWords) return emptyList()
        val grams = HashSet<List<String>>()
        for (i in 0..src.size - minWords) grams += src.subList(i, i + minWords)
        val srcText = " " + src.joinToString(" ") + " "
        return texts(scenario).mapNotNull { (id, field, text) ->
            val w = words(text)
            var longest = 0
            var at = -1
            var i = 0
            while (i + minWords <= w.size) {
                if (w.subList(i, i + minWords) in grams) {
                    var len = minWords
                    while (i + len < w.size && srcText.contains(" " + w.subList(i, i + len + 1).joinToString(" ") + " ")) len++
                    if (len > longest) { longest = len; at = i }
                    i += len
                } else i++
            }
            if (longest == 0) null else CopyFinding(id, field, w.subList(at, at + longest).joinToString(" "), longest)
        }
    }

    /** All player-facing texts of a scenario with their owner and field. */
    fun texts(s: Scenario): List<Triple<String, String, String>> = buildList {
        add(Triple(s.id, "intro", s.intro))
        s.chapters.forEach { add(Triple(it.id, "setupText", it.setupText)); it.tokens.forEach { t -> t.inspectText?.let { x -> add(Triple(t.id, "inspectText", x)) } } }
        s.events.forEach { e ->
            e.steps.forEachIndexed { i, step ->
                when (step) {
                    is EventStep.Text -> add(Triple(e.id, "steps[$i].text", step.text))
                    is EventStep.Choice -> add(Triple(e.id, "steps[$i].text", step.text))
                    is EventStep.Test -> step.text?.let { add(Triple(e.id, "steps[$i].text", it)) }
                    else -> Unit
                }
            }
        }
        s.objectives.forEach { add(Triple(it.id, "reminder", it.reminder)) }
        s.resolutions.forEach { add(Triple(it.id, "text", it.text)) }
    }
}
