package io.github.aljadvb.worldbuilder.editing

/**
 * Immutable undo/redo history.
 *
 * Pushes with the same non-null [coalesceKey] in a row replace the latest entry instead
 * of adding one, so typing into a text field creates a single undo step.
 */
data class History<T>(
    val present: T,
    private val past: List<T> = emptyList(),
    private val future: List<T> = emptyList(),
    private val lastKey: String? = null,
    private val limit: Int = 200,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(value: T, coalesceKey: String? = null): History<T> {
        if (value == present) return this
        if (coalesceKey != null && coalesceKey == lastKey) {
            return copy(present = value, future = emptyList())
        }
        return copy(present = value, past = (past + present).takeLast(limit), future = emptyList(), lastKey = coalesceKey)
    }

    fun undo(): History<T> =
        if (!canUndo) this else copy(present = past.last(), past = past.dropLast(1), future = listOf(present) + future, lastKey = null)

    fun redo(): History<T> =
        if (!canRedo) this else copy(present = future.first(), past = past + present, future = future.drop(1), lastKey = null)
}
