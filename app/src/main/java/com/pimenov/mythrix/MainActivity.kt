package com.pimenov.mythrix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pimenov.main.nav.AppNavGraph
import com.pimenov.uikit.theme.MythrixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MythrixTheme {
                AppNavGraph()
            }
        }
    }
}
