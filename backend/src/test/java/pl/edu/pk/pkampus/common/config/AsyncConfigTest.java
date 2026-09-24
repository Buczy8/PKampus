package pl.edu.pk.pkampus.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class AsyncConfigTest {

    @Test
    void shouldConfigureExecutorWithCustomPoolSizeAndQueueCapacity() {
        // Arrange
        AsyncConfig config = new AsyncConfig(6, 300);

        // Act
        Executor executor = config.getAsyncExecutor();

        // Assert
        assertInstanceOf(ThreadPoolTaskExecutor.class, executor);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertEquals(6, pool.getCorePoolSize());
        assertEquals(6, pool.getMaxPoolSize());
        assertEquals(300, pool.getQueueCapacity());
        assertTrue(pool.getThreadNamePrefix().startsWith("mail-"));
        pool.shutdown();
    }

    @Test
    void shouldReturnAsyncUncaughtExceptionHandlerThatHandlesError() throws NoSuchMethodException {
        // Arrange
        AsyncConfig config = new AsyncConfig(4, 500);

        // Act
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

        // Assert
        assertNotNull(handler);
        Method method = String.class.getMethod("toString");
        assertDoesNotThrow(() ->
                handler.handleUncaughtException(new RuntimeException("async boom"), method, "arg1"));
    }
}
