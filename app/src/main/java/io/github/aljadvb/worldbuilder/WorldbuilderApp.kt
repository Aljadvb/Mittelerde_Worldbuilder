package io.github.aljadvb.worldbuilder

import android.app.Application
import io.github.aljadvb.worldbuilder.data.CampaignLibrary
import io.github.aljadvb.worldbuilder.engine.save.SaveStore
import java.io.File

class WorldbuilderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            library = CampaignLibrary(filesDir, assets),
            saves = SaveStore(File(filesDir, "saves")),
        )
    }
}

/** Manual dependency container (the app is small enough to do without a DI framework). */
class AppContainer(
    val library: CampaignLibrary,
    val saves: SaveStore,
)
