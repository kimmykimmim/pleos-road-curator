package dev.changbak.explorer

import android.content.Context
import dev.changbak.explorer.domain.ChildProfile

/**
 * How many times this family has already finished each place, and the back seat setting.
 *
 * Both live in this car and go nowhere. The count is the whole reason a place can ask something
 * new on the ninth school run instead of repeating its opening line, so it has to outlive the
 * app's process, but it is only ever a number per place: no dates, no routes, no locations.
 */
class JourneyPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("journey_rounds", Context.MODE_PRIVATE)

    /** How many times this place has been finished before today's meeting. */
    fun rounds(placeId: String): Int = prefs.getInt(key(placeId), 0).coerceAtLeast(0)

    /**
     * Records that a place was met all the way through, so next time it asks something else.
     *
     * Called once the place reaches its closing card. A place the child skipped before the story
     * does not count: it was not really met, and repeating its hook next time is right.
     */
    fun finished(placeId: String) {
        if (placeId.isBlank()) return
        prefs.edit().putInt(key(placeId), (rounds(placeId) + 1).coerceAtMost(MAX)).apply()
    }

    /** Back to first meetings everywhere. The guardian's own decision, never the app's. */
    fun resetRounds() = prefs.edit().apply {
        prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach(::remove)
    }.apply()

    /**
     * Whether the microphone may open by itself after a question.
     *
     * Off until a guardian turns it on. The child sits behind the screen and cannot press
     * anything, so this is the only way they answer for themselves; it is still their guardian's
     * call, because it is their microphone.
     */
    var backSeatVoice: Boolean
        get() = prefs.getBoolean(KEY_BACK_SEAT, false)
        set(value) { prefs.edit().putBoolean(KEY_BACK_SEAT, value).apply() }

    /**
     * What the guardian told the app about their child: what they are into, what they love
     * right now, and the language they speak in the car. Stays in this car like everything else.
     */
    var profile: ChildProfile
        get() = ChildProfile.decode(prefs.getString(KEY_PROFILE, null))
        set(value) { prefs.edit().putString(KEY_PROFILE, ChildProfile.encode(value)).apply() }

    private fun key(placeId: String) = "$PREFIX$placeId"

    private companion object {
        const val PREFIX = "round:"
        const val KEY_BACK_SEAT = "back_seat_voice"
        const val KEY_PROFILE = "child_profile"
        /** Far past the point where the rounds have begun rotating. Keeps the number sane. */
        const val MAX = 9999
    }
}
