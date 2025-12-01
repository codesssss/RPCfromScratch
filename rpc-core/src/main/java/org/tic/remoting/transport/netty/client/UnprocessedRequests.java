package org.tic.remoting.transport.netty.client;

import lombok.extern.slf4j.Slf4j;
import org.tic.remoting.dto.RpcResponse;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Store unprocessed RPC requests with automatic timeout cleanup.
 * 
 * @author codesssss
 * @date 18/8/2024 11:23 pm
 */
@Slf4j
public class UnprocessedRequests {
    
    /**
     * Default timeout for unprocessed requests in milliseconds (60 seconds)
     */
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 60000L;
    
    /**
     * Cleanup interval in milliseconds (10 seconds)
     */
    private static final long CLEANUP_INTERVAL_MS = 10000L;
    
    /**
     * Map storing request ID to future and creation time
     */
    private static final Map<String, RequestFutureWrapper> UNPROCESSED_RESPONSE_FUTURES = new ConcurrentHashMap<>();
    
    /**
     * Scheduled executor for periodic cleanup
     */
    private static final ScheduledExecutorService CLEANUP_EXECUTOR;
    
    static {
        // Initialize cleanup task
        CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "unprocessed-requests-cleanup");
            t.setDaemon(true);
            return t;
        });
        CLEANUP_EXECUTOR.scheduleAtFixedRate(UnprocessedRequests::cleanupTimeoutRequests,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CLEANUP_EXECUTOR.shutdown();
            try {
                if (!CLEANUP_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    CLEANUP_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                CLEANUP_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }
    
    /**
     * Wrapper class to store future with creation timestamp
     */
    private static class RequestFutureWrapper {
        final CompletableFuture<RpcResponse<Object>> future;
        final long createTime;
        
        RequestFutureWrapper(CompletableFuture<RpcResponse<Object>> future) {
            this.future = future;
            this.createTime = System.currentTimeMillis();
        }
        
        boolean isTimeout(long timeoutMs) {
            return System.currentTimeMillis() - createTime > timeoutMs;
        }
    }

    public void put(String requestId, CompletableFuture<RpcResponse<Object>> future) {
        UNPROCESSED_RESPONSE_FUTURES.put(requestId, new RequestFutureWrapper(future));log.debug("Added unprocessed request: {}, total pending: {}", requestId, UNPROCESSED_RESPONSE_FUTURES.size());
    }

    public void complete(RpcResponse<Object> rpcResponse) {
        RequestFutureWrapper wrapper = UNPROCESSED_RESPONSE_FUTURES.remove(rpcResponse.getRequestId());
        if (wrapper != null) {
            wrapper.future.complete(rpcResponse);
            log.debug("Completed request: {}, remaining pending: {}", rpcResponse.getRequestId(), UNPROCESSED_RESPONSE_FUTURES.size());
        } else {
            log.warn("Received response for unknown or already completed request: {}", rpcResponse.getRequestId());
        }
    }
    
    /**
     * Remove a request from the map (e.g., when cancelled or timed out externally)
     */
    public void remove(String requestId) {
        RequestFutureWrapper wrapper = UNPROCESSED_RESPONSE_FUTURES.remove(requestId);
        if (wrapper != null) {
            log.debug("Removed request: {}, remaining pending: {}", requestId, UNPROCESSED_RESPONSE_FUTURES.size());}
    }
    
    /**
     * Get the number of pending requests
     */
    public int getPendingCount() {
        return UNPROCESSED_RESPONSE_FUTURES.size();
    }
    
    /**
     * Cleanup timeout requests periodically
     */
    private static void cleanupTimeoutRequests() {
        if (UNPROCESSED_RESPONSE_FUTURES.isEmpty()) {
            return;
        }
        
        int cleanedCount = 0;
        Iterator<Map.Entry<String, RequestFutureWrapper>> iterator = UNPROCESSED_RESPONSE_FUTURES.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, RequestFutureWrapper> entry = iterator.next();
            RequestFutureWrapper wrapper = entry.getValue();
            
            if (wrapper.isTimeout(DEFAULT_REQUEST_TIMEOUT_MS)) {
                iterator.remove();
                // Complete exceptionally to notify waiting threads
                wrapper.future.completeExceptionally(
                        new RuntimeException("Request timeout and cleaned up, requestId: " + entry.getKey())
                );
                cleanedCount++;
                log.warn("Cleaned up timeout request: {}, age: {}ms", 
                        entry.getKey(), System.currentTimeMillis() - wrapper.createTime);
            }
        }
        
        if (cleanedCount > 0) {
            log.info("Cleaned up {} timeout requests, remaining pending: {}", cleanedCount, UNPROCESSED_RESPONSE_FUTURES.size());
        }
    }
}
