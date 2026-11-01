package io.github.aljadvb.worldbuilder.editor

import java.io.File
import java.util.prefs.Preferences

/** Persistent editor settings (Windows registry via java.util.prefs). */
interface EditorPrefs {
    var catalogPath: String?
    var recentFiles: List<String>
}

class SystemEditorPrefs : EditorPrefs {
    private val node = Preferences.userRoot().node("io/github/aljadvb/worldbuilder/editor")

    override var catalogPath: String?
        get() = node.get("catalogPath", null)
        set(value) { if (value == null) node.remove("catalogPath") else node.put("catalogPath", value) }

    override var recentFiles: List<String>
        get() = node.get("recentFiles", "").split('\n').filter { it.isNotBlank() && File(it).isFile }
        set(value) { node.put("recentFiles", value.take(8).joinToString("\n")) }
}

class InMemoryEditorPrefs(override var catalogPath: String? = null, override var recentFiles: List<String> = emptyList()) : EditorPrefs
