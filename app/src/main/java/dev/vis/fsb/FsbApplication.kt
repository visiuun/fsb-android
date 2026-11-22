package dev.vis.fsb

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.vis.fsb.data.Net

class FsbApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Net.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        Net.init(context as Context)
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Net.client }))
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
