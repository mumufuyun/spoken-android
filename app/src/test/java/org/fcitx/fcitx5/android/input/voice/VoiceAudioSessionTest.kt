/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VoiceAudioSessionTest {
    private class FakeCapture(
        val startAction: suspend () -> Unit = {},
        val stopAction: suspend () -> Unit = {},
        val startResult: Result<Unit> = Result.success(Unit)
    ) : VoiceAudioCapture {
        var starts = 0
        var stops = 0
        var frame: ((ByteArray) -> Unit)? = null

        override suspend fun start(scope: CoroutineScope, onFrame: (ByteArray) -> Unit): Result<Unit> {
            starts++
            frame = onFrame
            startAction()
            return startResult
        }

        override suspend fun stop() {
            stops++
            stopAction()
        }
    }

    @Test
    fun cancelBeforeStartupNeverOpensMicrophone() = runBlocking {
        val capture = FakeCapture()
        val session = VoiceAudioSession(this, capture)
        session.start({}, { fail("Cancellation is not a startup failure") })
        withTimeout(2_000) { session.stop().join() }
        assertEquals(0, capture.starts)
        assertEquals(1, capture.stops)
    }

    @Test
    fun cancelDuringRetryWaitsForStartupToExitBeforeRelease() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var startupExited = false
        val capture = FakeCapture(
            startAction = {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    startupExited = true
                }
            },
            stopAction = { assertTrue(startupExited) }
        )
        val session = VoiceAudioSession(this, capture)
        session.start({}, { fail("Cancellation is not a startup failure") })
        withTimeout(2_000) {
            entered.await()
            session.stop().join()
        }
        assertEquals(1, capture.stops)
    }

    @Test
    fun allocationThatFinishesAfterCancellationIsStillReleased() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finishAllocation = CompletableDeferred<Unit>()
        var allocated = false
        val capture = FakeCapture(
            startAction = {
                // Android's synchronous recorder allocation cannot itself be cancelled.
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    finishAllocation.await()
                    allocated = true
                }
            },
            stopAction = {
                assertTrue(allocated)
                allocated = false
            }
        )
        val session = VoiceAudioSession(this, capture)
        session.start({}, { fail("Cancellation is not a startup failure") })
        withTimeout(2_000) {
            entered.await()
            val stopping = session.stop()
            assertFalse(stopping.isCompleted)
            assertEquals(0, capture.stops)
            finishAllocation.complete(Unit)
            stopping.join()
        }
        assertFalse(allocated)
        assertEquals(1, capture.stops)
    }

    @Test
    fun cleanupSurvivesAnAlreadyCancelledOwner() = runBlocking {
        val owner = CoroutineScope(coroutineContext + Job())
        val entered = CompletableDeferred<Unit>()
        val capture = FakeCapture(startAction = { entered.complete(Unit) })
        val session = VoiceAudioSession(owner, capture)
        try {
            session.start({}, {})
            withTimeout(2_000) {
                entered.await()
                owner.cancel()
                session.stop().join()
            }
            assertEquals(1, capture.stops)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun delayedOldCleanupDoesNotStopTheNextRecording() = runBlocking {
        val allowRelease = CompletableDeferred<Unit>()
        val oldCapture = FakeCapture(stopAction = { allowRelease.await() })
        val newCapture = FakeCapture()
        val old = VoiceAudioSession(this, oldCapture)
        val next = VoiceAudioSession(this, newCapture)
        old.start({}, {})
        yield()
        val stopping = old.stop()
        next.start({}, {})
        yield()
        assertEquals(1, newCapture.starts)
        allowRelease.complete(Unit)
        withTimeout(2_000) { stopping.join() }
        assertEquals(0, newCapture.stops)
        withTimeout(2_000) { next.stop().join() }
    }

    @Test
    fun repeatedStopSharesCleanupAndRejectsLateFrames() = runBlocking {
        val capture = FakeCapture()
        val session = VoiceAudioSession(this, capture)
        var frames = 0
        session.start({ frames++ }, {})
        yield()
        capture.frame!!(byteArrayOf(1))
        val firstStop = session.stop()
        assertSame(firstStop, session.stop())
        capture.frame!!(byteArrayOf(2))
        withTimeout(2_000) { firstStop.join() }
        assertEquals(1, frames)
        assertEquals(1, capture.stops)
    }

    @Test
    fun startupFailureCanRequestCleanupWithoutJoiningItself() = runBlocking {
        val capture = FakeCapture(startResult = Result.failure(IllegalStateException("unavailable")))
        val session = VoiceAudioSession(CoroutineScope(coroutineContext + Dispatchers.Unconfined), capture)
        val cleanup = CompletableDeferred<Job>()
        session.start({}, { cleanup.complete(session.stop()) })
        withTimeout(2_000) { cleanup.await().join() }
        assertEquals(1, capture.stops)
    }
}
