package com.nuvora.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nuvora.app.catalog.MobileCatalog
import com.nuvora.app.catalog.TvCatalog
import com.nuvora.designsystem.theme.NuvoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isTv = isTelevision()
        setContent {
            NuvoraTheme(isTv = isTv) {
                if (isTv) TvCatalog() else MobileCatalog()
            }
        }
    }
}

/** TV = qualquer aparelho com sistema de TV (Android TV, Google TV, TV Box). O resto usa o layout de celular. */
private fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
