package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.validation.Severity

@Composable
fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = modifier.padding(12.dp))
}

@Composable
fun StructurePanel(
    model: EditorModel,
    tree: TreeNode,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PanelTitle(Texts.STRUCTURE)
        LazyColumn(Modifier.fillMaxSize()) {
            items(tree.visibleRows(expanded), key = { it.first.key }) { (node, depth) ->
                val selected = node.objectId != null && node.objectId == model.selection
                val issues = node.issues(model)
                val errors = issues.count { it.severity == Severity.ERROR }
                val warnings = issues.size - errors
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable {
                            if (node.objectId != null) model.selection = node.objectId
                            if (node.children.isNotEmpty() && (node.objectId == null || node.objectId == model.selection)) onToggle(node.key)
                        }
                        .padding(start = (8 + depth * 16).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (node.children.isNotEmpty()) {
                        Icon(
                            if (node.key in expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).clickable { onToggle(node.key) },
                        )
                    } else {
                        Spacer(Modifier.width(18.dp))
                    }
                    Text(
                        node.label,
                        Modifier.weight(1f).padding(start = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (node.objectId == null) FontWeight.Medium else FontWeight.Normal,
                        color = if (node.objectId == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (errors > 0) Badge(errors.toString(), MaterialTheme.colorScheme.error)
                    else if (warnings > 0) Badge(warnings.toString(), MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

/** Issues of an object row (everything below its path) or of a group row (its children). */
private fun TreeNode.issues(model: EditorModel): List<io.github.aljadvb.worldbuilder.validation.ValidationIssue> =
    if (objectId != null) model.issuesUnder(path) else children.flatMap { it.issues(model) }.distinct()

@Composable
fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = MaterialTheme.colorScheme.surface,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun IssuesPanel(model: EditorModel, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val report = model.report
    Column(modifier) {
        PanelTitle(Texts.CHECK + " · " + Texts.issueSummary(report.errors.size, report.warnings.size))
        if (report.issues.isEmpty()) {
            Text(Texts.NO_ISSUES, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(report.errors + report.warnings) { issue ->
                val isError = issue.severity == Severity.ERROR
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { model.objectOf(issue)?.let(onSelect) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (isError) Texts.ERROR else Texts.WARNING,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(issue.message, style = MaterialTheme.typography.bodySmall)
                    Text(issue.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
