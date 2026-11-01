package io.github.aljadvb.worldbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.aljadvb.worldbuilder.ui.HomeScreen
import io.github.aljadvb.worldbuilder.ui.theme.WorldbuilderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorldbuilderTheme {
                HomeScreen()
            }
        }
    }
}
