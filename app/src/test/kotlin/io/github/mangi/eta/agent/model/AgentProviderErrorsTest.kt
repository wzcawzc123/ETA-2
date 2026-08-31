package io.github.mangi.eta.agent.model

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProviderErrorsTest {

    @Test
    fun ioExceptionsAreTransient() {
        // 网络/传输层：流重置、读超时、连接失败、TLS 错误都继承自 IOException。
        assertTrue(AgentTransientError.isTransient(IOException("stream was reset: INTERNAL_ERROR")))
        assertTrue(AgentTransientError.isTransient(IOException("unexpected end of stream")))
        assertTrue(AgentTransientError.isTransient(SocketTimeoutException("read timed out")))
        assertTrue(AgentTransientError.isTransient(ConnectException("Connection refused")))
        assertTrue(AgentTransientError.isTransient(SSLException("handshake failed")))
        assertTrue(AgentTransientError.isTransient(IOException()))
    }

    @Test
    fun httpServerErrorsAreTransientButClientErrorsAreNot() {
        assertTrue(AgentTransientError.isTransient(ProviderHttpException(500, "boom")))
        assertTrue(AgentTransientError.isTransient(ProviderHttpException(502, "bad gateway")))
        assertTrue(AgentTransientError.isTransient(ProviderHttpException(503, "service unavailable")))
        assertTrue(AgentTransientError.isTransient(ProviderHttpException(504, "gateway timeout")))

        assertFalse(AgentTransientError.isTransient(ProviderHttpException(400, "bad request")))
        assertFalse(AgentTransientError.isTransient(ProviderHttpException(401, "unauthorized")))
        assertFalse(AgentTransientError.isTransient(ProviderHttpException(403, "forbidden")))
        assertFalse(AgentTransientError.isTransient(ProviderHttpException(404, "not found")))
        assertFalse(AgentTransientError.isTransient(ProviderHttpException(429, "rate limited")))
    }

    @Test
    fun logicalEmptyAndNullErrorsAreNotTransient() {
        assertFalse(AgentTransientError.isTransient(IllegalStateException("模型接口返回为空")))
        assertFalse(AgentTransientError.isTransient(IllegalArgumentException("bad config")))
        assertFalse(AgentTransientError.isTransient(RuntimeException("unexpected")))
        assertFalse(AgentTransientError.isTransient(null))
    }
}
