package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.runtime.Composable
import io.github.aljadvb.worldbuilder.editing.Definition
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.model.Scenario

/** Step editor for events – implemented in the next step. */
@Composable
fun EventEditor(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    FieldIssues(model, def.path)
}
