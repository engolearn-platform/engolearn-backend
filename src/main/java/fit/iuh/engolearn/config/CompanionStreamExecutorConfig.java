package fit.iuh.engolearn.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CompanionStreamExecutorConfig {
    @Bean(name = "companionStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService companionStreamExecutor(CompanionProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "companion-stream-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(0, properties.getMaxConcurrentStreams(), 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }
}
