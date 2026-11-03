package io.github.aljadvb.worldbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.aljadvb.worldbuilder.ui.AppNavigation
import io.github.aljadvb.worldbuilder.ui.theme.WorldbuilderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WorldbuilderApp).container
        setContent {
            // Own title font from the private catalog, if it brings one.
            val titleFont = container.library.catalogImageDir?.let { dir ->
                runCatching { container.library.catalog().titleFont }.getOrNull()?.let { java.io.File(dir, it) }
            }
            WorldbuilderTheme(titleFont = titleFont) {
                AppNavigation(container)
            }
        }
    }
}
