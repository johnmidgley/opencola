package io.opencola.relay.common

import kotlin.test.Test
import kotlin.test.assertEquals

class RetryPolicyTest {
    @Test
    fun testRetryContinuous(){
        assertEquals(0, retryContinuous(4))
    }

    @Test
    fun tesRetryConstantInterval() {
        val interval = 1500L
        assertEquals(interval, retryConstantInterval(interval)(7))
    }

    @Test
    fun tesRetryExponentialBackoff() {
        assertEquals(0, retryExponentialBackoff()(0))
        assertEquals(1000, retryExponentialBackoff(1000)(1))
        assertEquals(2000, retryExponentialBackoff(1000)(2))
        assertEquals(2000, retryExponentialBackoff(2000)(1))
        assertEquals(4000, retryExponentialBackoff(2000)(2))
        assertEquals(8000, retryExponentialBackoff(1000, 8000)(10))

    }

}