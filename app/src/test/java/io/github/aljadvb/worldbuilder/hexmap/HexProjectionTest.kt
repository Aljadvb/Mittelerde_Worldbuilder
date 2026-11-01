package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.ui.geometry.Offset
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexProjectionTest {
    @Test
    fun hexAtInvertsCenterForBothOrientations() {
        for (orientation in HexOrientation.entries) {
            val proj = HexProjection(orientation, 30f, Offset(200f, 150f))
            for (q in -4..4) for (r in -4..4) {
                val c = HexCoord(q, r)
                assertEquals(c, proj.hexAt(proj.center(c)))
                // a point well inside the hex still maps to it
                assertEquals(c, proj.hexAt(proj.center(c) + Offset(8f, -6f)))
            }
        }
    }

    @Test
    fun fitKeepsAllCellsInsideTheCanvas() {
        val cells = listOf(HexCoord(0, 0), HexCoord(7, -3), HexCoord(-2, 5))
        val proj = HexProjection.fit(HexOrientation.POINTY, cells, 400f, 300f, 10f, 100f)
        cells.flatMap(proj::corners).forEach { p ->
            assertTrue(p.toString(), p.x in -0.5f..400.5f && p.y in -0.5f..300.5f)
        }
    }
}
