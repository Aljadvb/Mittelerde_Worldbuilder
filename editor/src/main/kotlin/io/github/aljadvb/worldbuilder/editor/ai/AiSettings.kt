package io.github.aljadvb.worldbuilder.editor.ai

import com.sun.jna.platform.win32.Crypt32Util
import io.github.aljadvb.worldbuilder.generator.ModelInfo
import java.util.Base64
import java.util.prefs.Preferences

/** Where the API key lives. */
interface ApiKeyStore {
    fun load(): String?
    fun save(key: String)
    fun clear()
    /** Short description for the settings dialog. */
    val description: String
}

/**
 * Windows: the key is encrypted with the Windows Data Protection API (DPAPI, the same
 * mechanism the Windows credential manager uses) and bound to the Windows user account.
 */
class WindowsKeyStore(private val node: Preferences = settingsNode()) : ApiKeyStore {
    override val description = "verschlüsselt mit dem Windows-Konto (DPAPI)"
    override fun load(): String? = node.get(KEY, null)?.let {
        runCatching { String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(it)), Charsets.UTF_8) }.getOrNull()
    }
    override fun save(key: String) {
        node.put(KEY, Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(key.toByteArray(Charsets.UTF_8))))
        node.flush()
    }
    override fun clear() { node.remove(KEY); node.flush() }
    private companion object { const val KEY = "apiKeyDpapi" }
}

/** Other systems (development): environment variable ANTHROPIC_API_KEY or this session only. */
class SessionKeyStore(private val env: () -> String? = { System.getenv("ANTHROPIC_API_KEY") }) : ApiKeyStore {
    private var key: String? = null
    override val description = "nur für diese Sitzung (oder Umgebungsvariable ANTHROPIC_API_KEY)"
    override fun load() = key ?: env()?.takeIf { it.isNotBlank() }
    override fun save(key: String) { this.key = key }
    override fun clear() { key = null }
}

fun defaultKeyStore(): ApiKeyStore =
    if (System.getProperty("os.name").lowercase().contains("win")) WindowsKeyStore() else SessionKeyStore()

internal fun settingsNode(): Preferences = Preferences.userRoot().node("io/github/aljadvb/worldbuilder/editor/ai")

/** Model and options of the generator. */
class AiSettings(private val node: Preferences? = settingsNode()) {
    var model: String
        get() = node?.get("model", null)?.takeIf { ModelInfo.of(it) != null } ?: ModelInfo.OPUS_5_5.id
        set(v) { node?.put("model", v) }
    /** Let the service retry a declined request on a suitable model. */
    var fallbacks: Boolean
        get() = node?.getBoolean("fallbacks", true) ?: true
        set(v) { node?.putBoolean("fallbacks", v) }
}
