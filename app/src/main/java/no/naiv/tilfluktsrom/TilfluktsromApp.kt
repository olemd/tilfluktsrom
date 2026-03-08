package no.naiv.tilfluktsrom

import android.app.Application
import org.osmdroid.config.Configuration

class TilfluktsromApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure OSMDroid: set user agent and tile cache path
        Configuration.getInstance().apply {
            userAgentValue = packageName
            // Use app-specific internal storage for tile cache
            osmdroidBasePath = filesDir
            osmdroidTileCache = java.io.File(filesDir, "tiles")
        }
    }
}
