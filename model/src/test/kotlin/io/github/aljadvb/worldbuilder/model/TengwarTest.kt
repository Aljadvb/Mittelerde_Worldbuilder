package io.github.aljadvb.worldbuilder.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TengwarTest {
    private fun cps(s: String) = s.codePoints().toArray().toList()

    @Test
    fun `digraphs, vowels as letters and long vowels`() {
        // th + o, then space, then l + ó(long) + acute
        assertEquals(listOf(0xE008, 0xE016, ' '.code, 0xE022, 0xE016, 0xE046), cps(Tengwar.transliterate("Tho Ló")))
        assertEquals(listOf(0xE011, 0xE032, 0xE02A), cps(Tengwar.transliterate("Mae")))
        // unknown characters are dropped, punctuation kept
        assertEquals(listOf(0xE010, '!'.code), cps(Tengwar.transliterate("n'!")))
    }
}
