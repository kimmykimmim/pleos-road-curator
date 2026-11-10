package dev.changbak.explorer.domain

/**
 * Narration can end without completing: the speech engine fails, audio priority is lost, or the
 * engine simply never reports back. Some phases wait for their narration before moving on, so an
 * unreported narration leaves that phase on screen and blocks every later place.
 *
 * This decides whether such an ending may release the phase. It never records that the child
 * heard anything, and it never advances a phase that was not waiting in the first place.
 */
object NarrationRecovery {
    /** Phases whose turn ends only when their narration does. */
    private val waiting = setOf(CuratorPhase.EXPLAIN, CuratorPhase.FAMILY)

    /**
     * @param token the narration that ended, as issued when it started.
     * @param current the journey's newest narration token.
     * @param paused the guardian stopped the journey.
     * @param suppressed a foreground dialog or spoken answer owns the audio.
     */
    fun shouldAdvance(
        phase: CuratorPhase,
        token: Long,
        current: Long,
        paused: Boolean,
        suppressed: Boolean,
    ): Boolean {
        // A replaced narration must not steer the journey: its place is already gone.
        if (token != current) return false
        // Pausing and suppressing are deliberate stops, not failures to recover from.
        if (paused || suppressed) return false
        return phase in waiting
    }
}
