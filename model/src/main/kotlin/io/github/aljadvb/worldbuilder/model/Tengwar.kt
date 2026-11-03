package io.github.aljadvb.worldbuilder.model

/**
 * Writes Sindarin (or Quenya) in Latin letters as Tengwar in a simplified Mode of Beleriand:
 * vowels are full letters, long vowels carry an acute mark. Uses the private-use code points
 * of the Alcarin Tengwar font (based on the Free Tengwar mapping). Meant for flavour, not for
 * scholarly transcription.
 */
object Tengwar {
    private val DIGRAPHS = mapOf(
        "th" to 0xE008, "dh" to 0xE00C, "ch" to 0xE00A, "ph" to 0xE009, "rh" to 0xE021, "lh" to 0xE023,
        "hw" to 0xE029, "ng" to 0xE013, "ss" to 0xE026,
    )
    private val LETTERS = mapOf(
        't' to 0xE000, 'p' to 0xE001, 'c' to 0xE002, 'k' to 0xE002, 'd' to 0xE004, 'b' to 0xE005, 'g' to 0xE006,
        'f' to 0xE009, 'v' to 0xE00D, 'n' to 0xE010, 'm' to 0xE011, 'r' to 0xE020, 'l' to 0xE022, 's' to 0xE024,
        'h' to 0xE028, 'w' to 0xE015,
        'a' to 0xE032, 'e' to 0xE02A, 'i' to 0xE02E, 'o' to 0xE016, 'u' to 0xE02B, 'y' to 0xE025,
    )
    private val LONG = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'â' to 'a', 'ê' to 'e', 'î' to 'i', 'ô' to 'o', 'û' to 'u', 'ý' to 'y')
    private const val ACUTE = 0xE046
    private val KEEP = setOf(' ', ',', '.', '!', '?', '\n')

    fun transliterate(latin: String): String = buildString {
        val s = latin.lowercase()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val long = LONG[ch]
            when {
                long != null -> { appendCodePoint(LETTERS.getValue(long)); appendCodePoint(ACUTE); i++ }
                ch == 'ë' -> { appendCodePoint(LETTERS.getValue('e')); i++ }
                i + 1 < s.length && DIGRAPHS.containsKey(s.substring(i, i + 2)) -> { appendCodePoint(DIGRAPHS.getValue(s.substring(i, i + 2))); i += 2 }
                LETTERS.containsKey(ch) -> { appendCodePoint(LETTERS.getValue(ch)); i++ }
                ch in KEEP -> { append(ch); i++ }
                else -> i++
            }
        }
    }
}
