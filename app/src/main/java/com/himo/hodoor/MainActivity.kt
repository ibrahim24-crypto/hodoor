package com.himo.hodoor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import com.himo.hodoor.ui.navigation.HodoorNavHost
import com.himo.hodoor.ui.settings.AppSettings
import com.himo.hodoor.ui.settings.LocaleHelper
import com.himo.hodoor.ui.theme.HodoorTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.wrap(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme = remember { AppSettings.theme(this@MainActivity) }
            val darkTheme = when (theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            HodoorTheme(darkTheme = darkTheme) {
                HodoorNavHost()
            }
        }
    }
}
