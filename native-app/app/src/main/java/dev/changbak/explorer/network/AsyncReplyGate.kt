package dev.changbak.explorer.network

/**
 * Main-thread-only ownership of one pending reply.
 *
 * Call cancel() for every explicit stop boundary, even when the screen's phase stays the same.
 * Read shouldResumeListening() before complete(); completing or cancelling consumes that intent.
 * Network cancellation is best effort, so callbacks must still pass isCurrent()/complete().
 */
class AsyncReplyGate {
    var generation: Long = 0L
        private set

    private var pending = false
    private var resumeListening = false

    fun begin(resumeListening: Boolean): Long {
        generation += 1L
        pending = true
        this.resumeListening = resumeListening
        return generation
    }

    fun cancel() {
        generation += 1L
        pending = false
        resumeListening = false
    }

    fun isCurrent(token: Long): Boolean = pending && token == generation

    fun shouldResumeListening(token: Long): Boolean = isCurrent(token) && resumeListening

    fun complete(token: Long): Boolean {
        if (!isCurrent(token)) return false
        pending = false
        resumeListening = false
        return true
    }
}
