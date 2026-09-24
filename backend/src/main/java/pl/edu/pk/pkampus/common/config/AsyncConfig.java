package pl.edu.pk.pkampus.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for asynchronous e-mails. Without it Spring falls back to
 * {@code SimpleAsyncTaskExecutor}, which spawns an unbounded thread per task —
 * a breakdown/maintenance cascade over a full schedule would burst N threads.
 * Rejected tasks run on the caller thread instead of being dropped silently.
 * Uncaught exceptions from void async methods are routed to the configured handler.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private final int poolSize;
    private final int queueCapacity;

    public AsyncConfig(
            @Value("${app.mail.pool-size:4}") int poolSize,
            @Value("${app.mail.queue-capacity:500}") int queueCapacity) {
        this.poolSize = poolSize;
        this.queueCapacity = queueCapacity;
    }

    @Bean(name = "mailExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("Uncaught async error in {} with params {}", method.getName(), params, ex);
    }
}
