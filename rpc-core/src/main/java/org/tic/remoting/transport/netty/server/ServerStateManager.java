package org.tic.remoting.transport.netty.server;

import org.tic.enums.RpcConfigEnum;
import org.tic.config.ConfigResolver;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks server lifecycle and in-flight requests to enable graceful shutdown.
 */
public class ServerStateManager {

    public enum State {RUNNING, DRAINING, STOPPED}

    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final AtomicInteger inflight = new AtomicInteger(0);
    private final long drainTimeoutMs = ConfigResolver.getLong(RpcConfigEnum.SERVER_DRAIN_TIMEOUT_MS.getPropertyValue(), 10000L);

    public boolean tryEnterRequest() {
        if (state.get() != State.RUNNING) {
            return false;
        }
        inflight.incrementAndGet();
        // double-check to avoid race if state flipped after increment
        if (state.get() != State.RUNNING) {
            inflight.decrementAndGet();
            return false;
        }
        return true;
    }

    public void onRequestComplete() {
        inflight.decrementAndGet();
    }

    public void beginDrain() {
        state.compareAndSet(State.RUNNING, State.DRAINING);
    }

    public boolean awaitDrain() {
        long deadline = System.currentTimeMillis() + drainTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (inflight.get() == 0) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return inflight.get() == 0;
    }

    public void markStopped() {
        state.set(State.STOPPED);
    }

    public int getInflight() {
        return inflight.get();
    }

    public State getState() {
        return state.get();
    }
}
