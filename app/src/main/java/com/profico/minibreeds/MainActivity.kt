package com.profico.minibreeds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.profico.minibreeds.ui.navigation.MiniBreedsNavHost
import com.profico.minibreeds.ui.theme.MiniBreedsTheme

/** Single activity that hosts the entire Compose UI tree. */
class MainActivity : ComponentActivity() {

    /** Enables edge-to-edge display and sets [MiniBreedsNavHost] as the content root. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiniBreedsTheme {
                MiniBreedsNavHost()
            }
        }
    }
}
