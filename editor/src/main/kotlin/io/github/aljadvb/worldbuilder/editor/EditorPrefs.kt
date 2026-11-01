package io.github.aljadvb.worldbuilder.editor

import io.github.aljadvb.worldbuilder.editing.TableSize
import java.io.File
import java.util.prefs.Preferences

/** Persistent editor settings (Windows registry via java.util.prefs). */
interface EditorPrefs {
    var catalogPath: String?
    var recentFiles: List<String>
    /** Size of the real table the map must fit on. */
    var table: TableSize
}

class SystemEditorPrefs : EditorPrefs {
    private val node = Preferences.userRoot().node("io/github/aljadvb/worldbuilder/editor")

    override var catalogPath: String?
        get() = node.get("catalogPath", null)
        set(value) { if (value == null) node.remove("catalogPath") else node.put("catalogPath", value) }

    override var recentFiles: List<String>
        get() = node.get("recentFiles", "").split('\n').filter { it.isNotBlank() && File(it).isFile }
        set(value) { node.put("recentFiles", value.take(8).joinToString("\n")) }

    override var table: TableSize
        get() = TableSize(node.getDouble("tableWidthCm", TableSize.DEFAULT.widthCm), node.getDouble("tableHeightCm", TableSize.DEFAULT.heightCm))
        set(value) { node.putDouble("tableWidthCm", value.widthCm); node.putDouble("tableHeightCm", value.heightCm) }
}

class InMemoryEditorPrefs(
    override var catalogPath: String? = null,
    override var recentFiles: List<String> = emptyList(),
    override var table: TableSize = TableSize.DEFAULT,
) : EditorPrefs
