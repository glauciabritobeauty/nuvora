package com.nuvora.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuvora.core.data.NuvoraViewModel
import com.nuvora.designsystem.theme.NuvoraTheme
import com.nuvora.feature.mobile.MobileApp
import com.nuvora.feature.tv.TvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App sempre escuro: ícones claros nas barras do sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val isTv = isTelevision()
        setContent {
            // O mesmo "cérebro" que as telas usam (um só para o app aberto); aqui só lemos a "Letra maior".
            val viewModel: NuvoraViewModel = viewModel()
            val largeText by viewModel.largeText.collectAsStateWithLifecycle()
            NuvoraTheme(isTv = isTv, largeText = largeText) {
                if (isTv) TvApp(versionName = BuildConfig.VERSION_NAME) else MobileApp(versionName = BuildConfig.VERSION_NAME)
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
