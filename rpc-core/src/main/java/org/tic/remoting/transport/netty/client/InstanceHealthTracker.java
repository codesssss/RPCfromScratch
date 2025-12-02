package org.tic.remoting.transport.netty.client;

import org.tic.config.ConfigResolver;
import org.tic.enums.RpcConfigEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks instance health and simple circuit breaker state.
 */
public class InstanceHealthTracker {

    private enum State {CLOSED, OPEN, HALF_OPEN}

    private static class Stat {
        volatile State state = State.CLOSED;
        volatile long lastFailureTime = 0;
        volatile int failureCount = 0;
        volatile boolean trialInProgress = false;
    }

    private final Map<String, Stat> stats = new ConcurrentHashMap<>();
    private final int failureThreshold = ConfigResolver.getInt(RpcConfigEnum.CLIENT_CB_FAILURE_THRESHOLD.getPropertyValue(), 5);
    private final long openDurationMs = ConfigResolver.getLong(RpcConfigEnum.CLIENT_CB_OPEN_MS.getPropertyValue(), 5000);
    private final int halfOpenMax = ConfigResolver.getInt(RpcConfigEnum.CLIENT_CB_HALF_OPEN_MAX.getPropertyValue(), 1);

    public void recordSuccess(String address) {
        Stat stat = stats.computeIfAbsent(address, k -> new Stat());
        stat.failureCount = 0;
        stat.lastFailureTime = 0;
        stat.trialInProgress = false;
        stat.state = State.CLOSED;
    }

    public void recordFailure(String address) {
        Stat stat = stats.computeIfAbsent(address, k -> new Stat());
        stat.failureCount++;
        stat.lastFailureTime = System.currentTimeMillis();
        if (stat.state == State.HALF_OPEN) {
            // Fail-fast: go back to OPEN and allow future half-open attempts after cool-down
            stat.state = State.OPEN;
            stat.trialInProgress = false;
        } else if (stat.failureCount >= failureThreshold) {
            stat.state = State.OPEN;
            stat.trialInProgress = false;
        }
    }

    public List<String> filterCandidates(List<String> addresses) {
        List<String> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String addr : addresses) {
            Stat stat = stats.get(addr);
            if (stat == null || stat.state == State.CLOSED) {
                result.add(addr);
                continue;
            }
            if (stat.state == State.OPEN && now - stat.lastFailureTime >= openDurationMs) {
                stat.state = State.HALF_OPEN;
                stat.trialInProgress = false;
            }
            if (stat.state == State.HALF_OPEN) {
                if (!stat.trialInProgress && halfOpenMax > 0) {
                    stat.trialInProgress = true;
                    result.add(addr);
                }
            }
        }
        return result.isEmpty() ? addresses : result;
    }

    public int healthWeight(String address) {
        Stat stat = stats.get(address);
        if (stat == null || stat.state == State.CLOSED) {
            return 100;
        }
        if (stat.state == State.OPEN) {
            return 0;
        }
        // HALF_OPEN: degrade weight
        return 20;
    }
}
