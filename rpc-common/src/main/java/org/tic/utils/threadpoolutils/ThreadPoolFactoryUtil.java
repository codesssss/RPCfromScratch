package org.tic.utils.threadpoolutils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author codesssss
 * @date 18/8/2024 10:48 pm
 */
@Slf4j
public final class ThreadPoolFactoryUtil {

    /**
     * Differentiate thread pools by threadNamePrefix (we can consider thread pools with the same threadNamePrefix as serving the same business scenario).
     * key: threadNamePrefix
     * value: threadPool
     */
    private static final Map<String, ExecutorService> THREAD_POOLS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> REJECT_COUNTS = new ConcurrentHashMap<>();

    private ThreadPoolFactoryUtil() {
        // Private constructor to prevent instantiation
    }

    public static ExecutorService createCustomThreadPoolIfAbsent(String threadNamePrefix) {
        CustomThreadPoolConfig customThreadPoolConfig = new CustomThreadPoolConfig();
        return createCustomThreadPoolIfAbsent(customThreadPoolConfig, threadNamePrefix, false);
    }

    public static ExecutorService createCustomThreadPoolIfAbsent(String threadNamePrefix, CustomThreadPoolConfig customThreadPoolConfig) {
        return createCustomThreadPoolIfAbsent(customThreadPoolConfig, threadNamePrefix, false);
    }

    public static ExecutorService createCustomThreadPoolIfAbsent(CustomThreadPoolConfig customThreadPoolConfig, String threadNamePrefix, Boolean daemon) {
        ExecutorService threadPool = THREAD_POOLS.computeIfAbsent(threadNamePrefix, k -> createThreadPool(customThreadPoolConfig, threadNamePrefix, daemon));
        // Recreate the thread pool if it has been shut down
        if (threadPool.isShutdown() || threadPool.isTerminated()) {
            THREAD_POOLS.remove(threadNamePrefix);
            threadPool = createThreadPool(customThreadPoolConfig, threadNamePrefix, daemon);
            THREAD_POOLS.put(threadNamePrefix, threadPool);
        }
        return threadPool;
    }

    /**
     * Shut down all thread pools
     */
    public static void shutDownAllThreadPool() {
        log.info("call shutDownAllThreadPool method");
        THREAD_POOLS.entrySet().parallelStream().forEach(entry -> {
            ExecutorService executorService = entry.getValue();
            executorService.shutdown();
            log.info("shut down thread pool [{}] [{}]", entry.getKey(), executorService.isTerminated());
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Thread pool never terminated");
                executorService.shutdownNow();
            }
        });
    }

    private static ExecutorService createThreadPool(CustomThreadPoolConfig customThreadPoolConfig, String threadNamePrefix, Boolean daemon) {
        ThreadFactory threadFactory = createThreadFactory(threadNamePrefix, daemon);
        RejectedExecutionHandler handler = wrapRejectionHandler(threadNamePrefix, buildHandler(customThreadPoolConfig.getRejectionPolicy()));
        return new ThreadPoolExecutor(customThreadPoolConfig.getCorePoolSize(), customThreadPoolConfig.getMaximumPoolSize(),
                customThreadPoolConfig.getKeepAliveTime(), customThreadPoolConfig.getUnit(), customThreadPoolConfig.getWorkQueue(),
                threadFactory, handler);
    }

    /**
     * Create a ThreadFactory. If threadNamePrefix is not null, use a custom ThreadFactory; otherwise, use the defaultThreadFactory.
     *
     * @param threadNamePrefix the prefix for the created thread names
     * @param daemon           specifies whether the threads are Daemon threads
     * @return ThreadFactory
     */
    public static ThreadFactory createThreadFactory(String threadNamePrefix, Boolean daemon) {
        if (threadNamePrefix != null) {
            if (daemon != null) {
                return new ThreadFactoryBuilder()
                        .setNameFormat(threadNamePrefix + "-%d")
                        .setDaemon(daemon).build();
            } else {
                return new ThreadFactoryBuilder().setNameFormat(threadNamePrefix + "-%d").build();
            }
        }
        return Executors.defaultThreadFactory();
    }

    /**
     * Print the status of the thread pool
     *
     * @param threadPool the thread pool object
     */
    public static void printThreadPoolStatus(ThreadPoolExecutor threadPool) {
        // Derive key from thread name prefix to align with rejection counters
        String poolKey = derivePoolKey(threadPool);
        ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1, createThreadFactory("print-thread-pool-status", false));
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            log.info("============ThreadPool Status=============");
            log.info("ThreadPool Size: [{}]", threadPool.getPoolSize());
            log.info("Active Threads: [{}]", threadPool.getActiveCount());
            log.info("Number of Tasks : [{}]", threadPool.getCompletedTaskCount());
            log.info("Number of Tasks in Queue: {}", threadPool.getQueue().size());
            log.info("Rejected Count: {}", REJECT_COUNTS.getOrDefault(poolKey, new AtomicLong(0)).get());
            log.info("===========================================");
        }, 0, 1, TimeUnit.SECONDS);
    }

    private static String derivePoolKey(ThreadPoolExecutor executor) {
        // Thread name format is prefix-%d; get prefix from first thread if present
        ThreadFactory factory = executor.getThreadFactory();
        if (factory instanceof java.util.concurrent.ThreadFactory) {
            // best effort: use executor's toString fallback when unknown
            return THREAD_POOLS.entrySet().stream()
                    .filter(e -> e.getValue() == executor)
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(executor.toString());
        }
        return executor.toString();
    }

    /**
     * Basic metrics snapshot for a named pool if managed by this factory.
     */
    public static ThreadPoolStats getThreadPoolStats(String threadNamePrefix) {
        ExecutorService executorService = THREAD_POOLS.get(threadNamePrefix);
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) executorService;
            return new ThreadPoolStats(
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getCompletedTaskCount(),
                    REJECT_COUNTS.getOrDefault(threadNamePrefix, new AtomicLong(0)).get()
            );
        }
        return null;
    }

    private static RejectedExecutionHandler buildHandler(String policy) {
        if (policy == null) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        switch (policy.toLowerCase()) {
            case "callerruns":
                return new ThreadPoolExecutor.CallerRunsPolicy();
            case "discard":
                return new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldest":
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case "abort":
            default:
                return new ThreadPoolExecutor.AbortPolicy();
        }
    }

    private static RejectedExecutionHandler wrapRejectionHandler(String poolName, RejectedExecutionHandler delegate) {
        REJECT_COUNTS.computeIfAbsent(poolName, k -> new AtomicLong(0));
        return (r, executor) -> {
            REJECT_COUNTS.get(poolName).incrementAndGet();
            delegate.rejectedExecution(r, executor);
        };
    }

    public static final class ThreadPoolStats {
        public final int poolSize;
        public final int activeCount;
        public final int queueSize;
        public final long completedTaskCount;
        public final long rejectCount;

        public ThreadPoolStats(int poolSize, int activeCount, int queueSize, long completedTaskCount, long rejectCount) {
            this.poolSize = poolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
            this.completedTaskCount = completedTaskCount;
            this.rejectCount = rejectCount;
        }
    }
}
