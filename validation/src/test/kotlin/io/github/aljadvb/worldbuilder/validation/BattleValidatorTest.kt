package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.Battlefield
import io.github.aljadvb.worldbuilder.model.BoardPlacement
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.CustomRule
import io.github.aljadvb.worldbuilder.model.EdgeSide
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TerrainPlacement
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TilePlacement
import io.github.aljadvb.worldbuilder.model.TileSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BattleValidatorTest {
    private fun validate(edit: (Scenario) -> Scenario) =
        CampaignValidator.validate(Examples.battle.editScenario(edit), Examples.catalog)

    private fun Scenario.editField(edit: (Battlefield) -> Battlefield) =
        editChapter("ch_bach") { it.copy(battlefield = edit(it.battlefield!!)) }

    private fun Battlefield.plusTerrain(vararg t: TerrainPlacement) = copy(terrain = terrain + t)

    private fun ValidationReport.has(code: IssueCode, severity: Severity = Severity.ERROR, count: Int = 1): List<ValidationIssue> {
        val matches = issues.filter { it.code == code && it.severity == severity }
        if (matches.size != count) fail("Expected $count× $severity $code, got:\n${format()}")
        return matches
    }

    private fun bush(id: String, square: GridCoord) = TerrainPlacement(id, "busch_kiste", TileSide.A, area = square)
    private fun edgePiece(id: String, side: TileSide, edge: GridEdge) = TerrainPlacement(id, "bach_wall", side, edge = edge)

    @Test
    fun `battle example has no findings`() {
        assertEquals("", CampaignValidator.validate(Examples.battle, Examples.catalog).format())
    }

    @Test
    fun `unknown board, side and rotation`() {
        val r = validate { s ->
            s.editField { bf ->
                bf.copy(boards = bf.boards + listOf(
                    BoardPlacement("bp_x", "gibts_nicht", TileSide.A, GridCoord(0, 1)),
                    BoardPlacement("bp_y", "sx1", TileSide.A, GridCoord(0, 2), rotation = 4),
                ))
            }
        }
        r.has(IssueCode.UNKNOWN_BOARD)
        r.has(IssueCode.INVALID_ROTATION)
        r.has(IssueCode.BOARD_COUNT_EXCEEDED)
    }

    @Test
    fun `boards must not overlap`() {
        val r = validate { s -> s.editField { bf -> bf.copy(boards = bf.boards.map { if (it.id == "bp_halle") it.copy(position = GridCoord(0, 0)) else it }) } }
        assertEquals("bp_halle", r.has(IssueCode.BOARDS_OVERLAP).single().objectId)
    }

    @Test
    fun `piece count counts both sides together`() {
        // bach_wall: 4 pieces; the example uses 2 (one Bach, one Steinwall).
        val fine = validate { s ->
            s.editField { it.plusTerrain(edgePiece("gt_b2", TileSide.A, GridEdge(1, 0, EdgeSide.SOUTH)), edgePiece("gt_w2", TileSide.B, GridEdge(1, 2, EdgeSide.NORTH))) }
        }
        assertTrue(fine.issues.none { it.code == IssueCode.PIECE_COUNT_EXCEEDED }, fine.format())
        val r = validate { s ->
            s.editField {
                it.plusTerrain(
                    edgePiece("gt_b2", TileSide.A, GridEdge(1, 0, EdgeSide.SOUTH)),
                    edgePiece("gt_w2", TileSide.B, GridEdge(1, 2, EdgeSide.NORTH)),
                    edgePiece("gt_w3", TileSide.B, GridEdge(1, 1, EdgeSide.EAST)),
                )
            }
        }
        assertEquals("gt_w3", r.has(IssueCode.PIECE_COUNT_EXCEEDED).single().objectId)
    }

    @Test
    fun `edge pieces need a field border`() {
        val r = validate { s ->
            s.editField {
                it.plusTerrain(
                    edgePiece("gt_innen", TileSide.A, GridEdge(0, 0, EdgeSide.SOUTH)), // (0,0)-(0,1) is one field
                    edgePiece("gt_rand", TileSide.A, GridEdge(0, 0, EdgeSide.NORTH)),
                )
            }
        }
        assertEquals("gt_innen", r.has(IssueCode.EDGE_NOT_BORDER).single().objectId)
        assertEquals("gt_rand", r.has(IssueCode.EDGE_ON_RIM, Severity.WARNING).single().objectId)
    }

    @Test
    fun `only one piece per edge, whichever square describes it`() {
        // The example has a Bach east of (2,0); the same edge is west of (3,0).
        val r = validate { s -> s.editField { it.plusTerrain(edgePiece("gt_doppelt", TileSide.B, GridEdge(3, 0, EdgeSide.WEST))) } }
        assertEquals("gt_doppelt", r.has(IssueCode.EDGE_OCCUPIED).single().objectId)
    }

    @Test
    fun `placement kind must match the piece`() {
        val r = validate { s ->
            s.editField {
                it.plusTerrain(
                    TerrainPlacement("gt_falsch1", "bach_wall", TileSide.A, area = GridCoord(1, 1)),
                    TerrainPlacement("gt_falsch2", "busch_kiste", TileSide.A, edge = GridEdge(1, 1, EdgeSide.EAST)),
                    TerrainPlacement("gt_falsch3", "busch_kiste", TileSide.A),
                )
            }
        }
        assertEquals(listOf("gt_falsch1", "gt_falsch2", "gt_falsch3"), r.has(IssueCode.PIECE_PLACEMENT_KIND, count = 3).map { it.objectId })
    }

    @Test
    fun `pieces and figures must stand on a board`() {
        val r = validate { s ->
            s.editField { it.plusTerrain(bush("gt_weg", GridCoord(9, 9))) }
                .copy(enemyGroups = s.enemyGroups.map { it.copy(location = SquareLocation(GridCoord(-1, 0))) })
        }
        r.has(IssueCode.SQUARE_NOT_ON_MAP, count = 2)
    }

    @Test
    fun `blank custom texts are errors`() {
        val r = validate { s ->
            s.editField { it.plusTerrain(bush("gt_leer", GridCoord(2, 2)).copy(customName = " ", customRule = CustomRule(text = ""))) }
        }
        r.has(IssueCode.BLANK_TEXT, count = 2)
    }

    @Test
    fun `wrong kind of location for the chapter`() {
        val r = validate { s ->
            s.editChapter("ch_bach") { ch -> ch.editToken("tok_vorraete") { it.copy(location = TileLocation("pl_x", HexCoord.ORIGIN)) } }
        }
        r.has(IssueCode.LOCATION_WRONG_MAP)
        val travel = CampaignValidator.validate(
            Examples.campaign.editScenario { s -> s.editChapter("ch_dorf") { ch -> ch.editToken("tok_brunnen") { it.copy(location = SquareLocation(GridCoord(0, 0))) } } },
            Examples.catalog,
        )
        travel.has(IssueCode.LOCATION_WRONG_MAP)
    }

    @Test
    fun `a chapter is either travel or battle`() {
        val r = validate { s -> s.editChapter("ch_bach") { it.copy(tiles = listOf(TilePlacement("pl_x", "x01", TileSide.A, HexCoord.ORIGIN))) } }
        r.has(IssueCode.MIXED_MAP)
    }

    @Test
    fun `walls and blocking pieces give reachability hints`() {
        // Wall in all four borders of the centre field (1,1); put the token there.
        val walls = listOf(EdgeSide.NORTH, EdgeSide.EAST, EdgeSide.SOUTH).mapIndexed { i, side -> edgePiece("gt_mauer_$i", TileSide.B, GridEdge(1, 1, side)) }
        val r = validate { s ->
            s.editField { bf -> bf.plusTerrain(*walls.toTypedArray()) }
                .editChapter("ch_bach") { ch -> ch.editToken("tok_vorraete") { it.copy(location = SquareLocation(GridCoord(1, 1))) } }
        }
        assertEquals("tok_vorraete", r.has(IssueCode.FIELD_UNREACHABLE, Severity.WARNING).single().objectId)
        val blocked = validate { s ->
            s.editField { bf -> bf.copy(terrain = bf.terrain.map { if (it.id == "gt_busch") it.copy(side = TileSide.B) else it }) }
                .editChapter("ch_bach") { ch -> ch.editToken("tok_vorraete") { it.copy(location = SquareLocation(GridCoord(1, 1))) } }
        }
        blocked.has(IssueCode.FIELD_BLOCKED, Severity.WARNING)
    }

    @Test
    fun `figure on a board of a later chapter is a hint`() {
        val r = validate { s ->
            val later = Chapter("ch_halle", "Halle", battlefield = Battlefield(boards = listOf(BoardPlacement("bp_spaeter", "sx1", TileSide.B, GridCoord(0, 1)))))
            s.copy(chapters = s.chapters + later)
                .editChapter("ch_bach") { ch -> ch.editToken("tok_vorraete") { it.copy(location = SquareLocation(GridCoord(1, 4))) } }
        }
        r.has(IssueCode.LOCATION_ON_LATER_CHAPTER, Severity.WARNING)
    }

    @Test
    fun `a cleared battlefield lets boards and pieces be used again`() {
        fun withSecond(clear: Boolean) = validate { s ->
            val first = s.chapters.single()
            val again = first.copy(
                id = "ch_halle", title = "Halle", clearBattlefield = clear, onUnlock = null, tokens = emptyList(),
                battlefield = first.battlefield!!.copy(
                    boards = first.battlefield!!.boards.map { it.copy(id = it.id + "_2") },
                    terrain = first.battlefield!!.terrain.map { it.copy(id = it.id + "_2") },
                ),
            )
            s.copy(chapters = listOf(first, again))
        }
        val reused = withSecond(clear = true)
        assertTrue(reused.issues.none { it.code == IssueCode.BOARD_COUNT_EXCEEDED || it.code == IssueCode.BOARDS_OVERLAP || it.code == IssueCode.EDGE_OCCUPIED }, reused.format())
        val stacked = withSecond(clear = false)
        assertTrue(stacked.issues.any { it.code == IssueCode.BOARD_COUNT_EXCEEDED }, stacked.format())
        assertTrue(stacked.issues.any { it.code == IssueCode.BOARDS_OVERLAP }, stacked.format())
    }

    @Test
    fun `a single board is a hint`() {
        val r = validate { s -> s.editField { bf -> bf.copy(boards = bf.boards.take(1), terrain = bf.terrain.filter { it.id == "gt_wall_1" || it.id == "gt_busch" }) }
            .editChapter("ch_bach") { ch -> ch.editToken("tok_vorraete") { it.copy(location = SquareLocation(GridCoord(2, 2))) } } }
        r.has(IssueCode.SINGLE_BOARD, Severity.WARNING)
    }
}
