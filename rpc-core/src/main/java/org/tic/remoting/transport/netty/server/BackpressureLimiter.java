package org.tic.remoting.transport.netty.server;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple backpressure limiter based on concurrent in-flight and executor queue depth.
 */
public class BackpressureLimiter {

    private final int maxConcurrent;
    private final int queueThreshold;
    private final AtomicLong rejected = new AtomicLong(0);

    public BackpressureLimiter(int maxConcurrent, int queueThreshold) {
        this.maxConcurrent = maxConcurrent;
        this.queueThreshold = queueThreshold;
    }

    public boolean allow(int currentInflight, int currentQueueSize) {
        if (currentInflight > maxConcurrent) {
            rejected.incrementAndGet();
            return false;
        }
        if (currentQueueSize > queueThreshold) {
            rejected.incrementAndGet();
            return false;
        }
        return true;
    }

    public long getRejected() {
        return rejected.get();
    }
}
