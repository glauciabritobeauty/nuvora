package com.nuvora.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath

/**
 * Configura as capas e logos: no máximo 15% da memória do app e 100 MB no armazenamento.
 * Passou disso, as mais antigas são apagadas sozinhas. Cada imagem é baixada no tamanho do cartão.
 */
class NuvoraApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("capas").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
}
