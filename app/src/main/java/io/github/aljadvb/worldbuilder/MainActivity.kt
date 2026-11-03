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
            val catalog = runCatching { container.library.catalog() }.getOrNull()
            val dir = container.library.catalogImageDir
            val titleFont = dir?.let { d -> catalog?.titleFont?.let { java.io.File(d, it) } }
            val ornamentFont = dir?.let { d -> catalog?.ornamentFont?.let { java.io.File(d, it) } }
            WorldbuilderTheme(titleFont = titleFont, ornamentFont = ornamentFont) {
                AppNavigation(container)
            }
        }
    }
}
