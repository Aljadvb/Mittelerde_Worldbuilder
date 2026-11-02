package io.github.aljadvb.worldbuilder.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import io.github.aljadvb.worldbuilder.hexmap.CampaignMapCanvas
import io.github.aljadvb.worldbuilder.hexmap.CampaignMapScene
import io.github.aljadvb.worldbuilder.hexmap.MapPlace
import io.github.aljadvb.worldbuilder.hexmap.PlaceState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class CampaignMapShotTest {
    @Test
    fun render() {
        val scene = CampaignMapScene(
            listOf(
                MapPlace("pt_a", "Hobbingen", 0.15f, 0.40f, PlaceState.VICTORY),
                MapPlace("pt_b", "Bree", 0.27f, 0.38f, PlaceState.DEFEAT),
                MapPlace("pt_c", "Wetterspitze", 0.34f, 0.34f, PlaceState.CURRENT),
                MapPlace("pt_d", "Bruchtal", 0.42f, 0.30f),
                MapPlace("pt_e", "Seestadt", 0.78f, 0.20f),
            ),
            route = listOf("pt_a", "pt_b", "pt_c"),
        )
        val s = ImageComposeScene(1200, 900, Density(1f)) { MaterialTheme(colorScheme = lightColorScheme()) { CampaignMapCanvas(scene, Color(0xFF6750A4), Modifier.fillMaxSize()) } }
        try {
            File("build/screenshots").mkdirs()
            File("build/screenshots/e30_campaign_map.png").writeBytes(s.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally { s.close() }
    }
}
