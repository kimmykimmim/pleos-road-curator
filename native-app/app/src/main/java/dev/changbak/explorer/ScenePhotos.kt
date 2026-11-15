package dev.changbak.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * The clipping photographs, held once.
 *
 * [CuratorMediaArt] is a drawing routine with no context of its own, by design: the film
 * generator, the scene view and the render check all call the same code. The photographs it now
 * draws have to come from somewhere, so they come from here — attached once by whoever has a
 * context, decoded on first use and kept, because a scene redraws many times a second and
 * decoding a JPEG inside a frame is how a drawing starts to stutter.
 *
 * A missing or unreadable file yields null rather than throwing. The scene then draws without
 * it: a clipping that will not load must not take the explanation down with it.
 */
object ScenePhotos {
    private val cache = mutableMapOf<String, Bitmap?>()
    private var assets: android.content.res.AssetManager? = null

    fun attach(context: Context) {
        if (assets == null) assets = context.applicationContext.assets
    }

    /** The photograph for this asset name, decoded once. */
    fun of(asset: String): Bitmap? = cache.getOrPut(asset) {
        val source = assets ?: return@getOrPut null
        runCatching {
            source.open("scenes/$asset").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    /** For checks: whether every clipping the content lists can actually be decoded. */
    fun loadable(names: List<String>): List<String> = names.filter { of(it) == null }
}
