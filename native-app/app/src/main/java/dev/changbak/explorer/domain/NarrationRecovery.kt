package dev.changbak.explorer.domain

/**
 * Narration can end without completing: the speech engine fails, audio priority is lost, or the
 * engine simply never reports back. EXPLAIN is the only phase that waits for narration before it
 * moves on, so an unreported narration leaves EXPLAIN on screen and blocks every later place.
 *
 * This decides whether such an ending may release EXPLAIN. It never records that the child heard
 * the explanation, and it never advances a phase that was not waiting in the first place.
 */
object NarrationRecovery {
    /**
     * @param token the narration that ended, as issued when it started.
     * @param current the journey's newest narration token.
     * @param paused the guardian stopped the journey.
     * @param suppressed a foreground dialog or spoken answer owns the audio.
     */
    fun shouldLeaveExplanation(
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
        return phase == CuratorPhase.EXPLAIN
    }
}
