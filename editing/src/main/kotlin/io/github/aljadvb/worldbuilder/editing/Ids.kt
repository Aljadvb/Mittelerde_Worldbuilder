package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.validation.ObjectKind

object Ids {
    private val VALID = Regex("^[a-z][a-z0-9_]*$")

    fun isValid(id: String) = VALID.matches(id)

    /**
     * Readable ID from a title: "Alter Brunnen" → `tok_alter_brunnen`.
     * Umlauts are transliterated; collisions with [taken] get a numeric suffix.
     */
    fun fromTitle(kind: ObjectKind, title: String, taken: Set<String>): String {
        val slug = title.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .trimEnd('_')
            .removePrefix(kind.prefix.trimEnd('_') + "_")
        val base = kind.prefix + slug.ifEmpty { "neu" }
        return unique(base, taken)
    }

    fun unique(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var n = 2
        while ("${base}_$n" in taken) n++
        return "${base}_$n"
    }
}
