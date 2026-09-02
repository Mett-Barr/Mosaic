package moozy.mosaic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.navigation.Mosaic

/**
 * The Android entry point, and the one place the theme is applied.
 *
 * Which screen leads to which used to be decided here too, on the grounds that
 * both jobs live "at the top". They are not the same job: this class exists
 * because Android needs an Activity, and the graph exists because the screens
 * have to be joined up. The graph is [Mosaic], in :navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MosaicTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Mosaic()
                }
            }
        }
    }
}
