package io.github.aljadvb.worldbuilder.editor.ui

import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.MaterialCatalog

/**
 * A row of the structure tree. Object rows have [objectId]; group rows only group children.
 * [path] is the validator path used to count issues below the row.
 */
data class TreeNode(
    val key: String,
    val label: String,
    val path: String,
    val objectId: String? = null,
    val children: List<TreeNode> = emptyList(),
)

fun buildTree(campaign: Campaign, catalog: MaterialCatalog): TreeNode {
    val scenarios = campaign.scenarios.mapIndexed { si, s ->
        val sp = "scenarios[$si]"
        fun group(name: String, key: String, prefix: String, items: List<TreeNode>) =
            TreeNode("${s.id}#$key", "$name (${items.size})", prefix, children = items)
        val chapters = s.chapters.mapIndexed { ci, ch ->
            val cp = "$sp.chapters[$ci]"
            val tiles = ch.tiles.mapIndexed { i, p ->
                TreeNode(p.id, "${catalog.tile(p.tile)?.label ?: p.tile} ${p.side}", "$cp.tiles[$i]", p.id)
            }
            val tokens = ch.tokens.mapIndexed { i, t -> TreeNode(t.id, t.label.ifBlank { t.id }, "$cp.tokens[$i]", t.id) }
            TreeNode(
                ch.id, ch.title.ifBlank { ch.id }, cp, ch.id,
                listOf(
                    TreeNode("${ch.id}#tiles", "${Texts.GROUP_TILES} (${tiles.size})", cp, children = tiles),
                    TreeNode("${ch.id}#tokens", "${Texts.GROUP_TOKENS} (${tokens.size})", cp, children = tokens),
                ),
            )
        }
        TreeNode(
            s.id, s.title.ifBlank { s.id }, sp, s.id,
            listOf(
                group(Texts.GROUP_CHAPTERS, "chapters", sp, chapters),
                group(Texts.GROUP_EVENTS, "events", sp, s.events.mapIndexed { i, e -> TreeNode(e.id, e.id, "$sp.events[$i]", e.id) }),
                group(Texts.GROUP_TRIGGERS, "triggers", sp, s.triggers.mapIndexed { i, t -> TreeNode(t.id, t.id, "$sp.triggers[$i]", t.id) }),
                group(Texts.GROUP_OBJECTIVES, "objectives", sp, s.objectives.mapIndexed { i, o -> TreeNode(o.id, o.title.ifBlank { o.id }, "$sp.objectives[$i]", o.id) }),
                group(Texts.GROUP_ENEMIES, "enemies", sp, s.enemyGroups.mapIndexed { i, g ->
                    TreeNode(g.id, "${catalog.enemyType(g.enemyType)?.name ?: g.enemyType} · ${g.id}", "$sp.enemyGroups[$i]", g.id)
                }),
                group(Texts.GROUP_RESOLUTIONS, "resolutions", sp, s.resolutions.mapIndexed { i, r -> TreeNode(r.id, r.title.ifBlank { r.id }, "$sp.resolutions[$i]", r.id) }),
            ),
        )
    }
    return TreeNode(campaign.id, campaign.title.ifBlank { campaign.id }, "", campaign.id, scenarios)
}

/** Flattens the tree into visible rows with their depth. */
fun TreeNode.visibleRows(expanded: Set<String>, depth: Int = 0): List<Pair<TreeNode, Int>> =
    listOf(this to depth) + if (key in expanded) children.flatMap { it.visibleRows(expanded, depth + 1) } else emptyList()

/** Keys of all ancestors of the node with [objectId], to reveal it. */
fun TreeNode.ancestorsOf(objectId: String): List<String>? {
    if (this.objectId == objectId) return emptyList()
    for (child in children) child.ancestorsOf(objectId)?.let { return listOf(key) + it }
    return null
}
