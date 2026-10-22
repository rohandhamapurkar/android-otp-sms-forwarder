package com.example.otpforwarder

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsSendProgressTest {
    @Test fun startsQueued() {
        assertEquals(SmsSendProgress.Status.QUEUED, SmsSendProgress(1).status)
    }

    @Test fun singlePartBecomesSentOnlyAfterSuccess() {
        assertEquals(SmsSendProgress.Status.SENT, SmsSendProgress(1).record(0, true).status)
    }

    @Test fun singlePartFailureBecomesFailed() {
        assertEquals(SmsSendProgress.Status.FAILED, SmsSendProgress(1).record(0, false).status)
    }

    @Test fun allMultipartCallbacksAreRequired() {
        val first = SmsSendProgress(3).record(0, true)
        assertEquals(SmsSendProgress.Status.QUEUED, first.status)
        val second = first.record(1, true)
        assertEquals(SmsSendProgress.Status.QUEUED, second.status)
        assertEquals(SmsSendProgress.Status.SENT, second.record(2, true).status)
    }

    @Test fun outOfOrderCallbacksStillComplete() {
        assertEquals(SmsSendProgress.Status.SENT,
            SmsSendProgress(3).record(2, true).record(0, true).record(1, true).status)
    }

    @Test fun duplicateCallbacksDoNotCountAsMoreParts() {
        val progress = SmsSendProgress(2).record(0, true).record(0, true)
        assertEquals(SmsSendProgress.Status.QUEUED, progress.status)
        assertEquals(setOf(0), progress.sentParts)
    }

    @Test fun failureCannotBeReplacedByLaterSuccess() {
        val failed = SmsSendProgress(2).record(0, false)
        assertEquals(SmsSendProgress.Status.FAILED, failed.record(1, true).record(0, true).status)
    }

    @Test fun failureAfterOtherPartsSucceedStillFailsTheMessage() {
        assertEquals(SmsSendProgress.Status.FAILED,
            SmsSendProgress(3).record(0, true).record(2, true).record(1, false).status)
    }

    @Test fun invalidPartIndicesAreIgnored() {
        val original = SmsSendProgress(2)
        assertEquals(original, original.record(-1, true))
        assertEquals(original, original.record(2, false))
    }

    @Test fun messagesAreIndependent() {
        val other = SmsSendProgress(2)
        SmsSendProgress(1).record(0, false)
        assertEquals(SmsSendProgress.Status.QUEUED, other.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPartsAreRejected() {
        SmsSendProgress(0)
    }
}
