package dev.changbak.explorer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncReplyGateTest {
    @Test fun initialStateCannotAcceptAnUnrequestedReply() {
        val gate = AsyncReplyGate()
        assertEquals(0L, gate.generation)
        assertFalse(gate.isCurrent(0L))
        assertFalse(gate.shouldResumeListening(0L))
        assertFalse(gate.complete(0L))
    }

    @Test fun newerInputSupersedesOlderReplyWithoutConsumingTheNewReply() {
        val gate = AsyncReplyGate()
        val first = gate.begin(resumeListening = true)
        val second = gate.begin(resumeListening = true)
        assertEquals(first + 1L, second)
        assertFalse(gate.isCurrent(first))
        assertFalse(gate.complete(first))
        assertTrue(gate.isCurrent(second))
        assertTrue(gate.shouldResumeListening(second))
        assertTrue(gate.complete(second))
    }

    @Test fun microphoneOffInvalidatesTheReplyAndItsListeningIntent() {
        val gate = AsyncReplyGate()
        val token = gate.begin(resumeListening = true)
        gate.cancel()
        assertEquals(token + 1L, gate.generation)
        assertFalse(gate.isCurrent(token))
        assertFalse(gate.shouldResumeListening(token))
        assertFalse(gate.complete(token))
    }

    @Test fun stoppingThenResumingCannotReviveAnOldAnswer() {
        val gate = AsyncReplyGate()
        val beforeBackground = gate.begin(resumeListening = true)
        gate.cancel()
        val afterResume = gate.begin(resumeListening = true)
        assertEquals(beforeBackground + 2L, afterResume)
        assertFalse(gate.complete(beforeBackground))
        assertTrue(gate.isCurrent(afterResume))
        assertTrue(gate.shouldResumeListening(afterResume))
    }

    @Test fun restAndArrivalCancellationDoNotDependOnAScreenPhaseChange() {
        val gate = AsyncReplyGate()
        // Both actions can occur while a conversational keepsake screen remains open.
        // Their explicit cancellation must work without consulting that screen's phase.
        for (stopAction in listOf("rest", "finish")) {
            val token = gate.begin(resumeListening = true)
            gate.cancel()
            assertFalse(stopAction, gate.isCurrent(token))
            assertFalse(stopAction, gate.shouldResumeListening(token))
            assertFalse(stopAction, gate.complete(token))
        }
    }

    @Test fun completionCanOnlyBeAcceptedOnceAndConsumesListeningIntent() {
        val gate = AsyncReplyGate()
        val token = gate.begin(resumeListening = true)
        val resumeBeforeCompletion = gate.shouldResumeListening(token)
        assertTrue(gate.complete(token))
        assertTrue(resumeBeforeCompletion)
        assertEquals(token, gate.generation)
        assertFalse(gate.isCurrent(token))
        assertFalse(gate.shouldResumeListening(token))
        assertFalse(gate.complete(token))
    }

    @Test fun textInputDoesNotInheritAnEarlierVoiceListeningIntent() {
        val gate = AsyncReplyGate()
        val voiceToken = gate.begin(resumeListening = true)
        val textToken = gate.begin(resumeListening = false)
        assertFalse(gate.shouldResumeListening(voiceToken))
        assertTrue(gate.isCurrent(textToken))
        assertFalse(gate.shouldResumeListening(textToken))
        assertFalse(gate.complete(voiceToken))
        assertTrue(gate.complete(textToken))
    }
}
