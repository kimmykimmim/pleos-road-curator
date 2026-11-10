package dev.changbak.explorer

import android.content.Context
import dev.changbak.explorer.domain.FamilyAlbum
import dev.changbak.explorer.domain.FamilyVisit

/**
 * The guardian's family records, kept in this car only. Nothing here is sent anywhere, and the
 * app never writes a record on its own: a place is in here because a guardian put it there.
 *
 * The service and the screen both read it, so each read re-opens the stored text rather than
 * holding a copy that could go stale after the guardian edits it mid-journey.
 */
class FamilyAlbumStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("family_album", Context.MODE_PRIVATE)

    fun load(): FamilyAlbum = FamilyAlbum(FamilyAlbum.decode(prefs.getString(KEY, null)))

    fun remember(visit: FamilyVisit) = write(load().with(visit))

    fun forget(placeId: String) = write(load().without(placeId))

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun write(album: FamilyAlbum) {
        prefs.edit().putString(KEY, FamilyAlbum.encode(album.toList())).apply()
    }

    private companion object { const val KEY = "visits" }
}
