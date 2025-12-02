package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author codesssss
 * @date 14/4/2024 11:48 pm
 */
@AllArgsConstructor
@Getter
public enum RpcConfigEnum {

    RPC_CONFIG_PATH("rpc.properties"),
    ZK_ADDRESS("rpc.zookeeper.address"),
    ZK_CACHE_TTL_MS("rpc.zk.cache.ttl.ms"),
    INSTANCE_WEIGHT("rpc.instance.weight"),
    /**
     * Enable configuration resolution logging, default true
     */
    CONFIG_LOG_ENABLED("rpc.config.log.enabled"),
    SERVER_BOSS_THREADS("rpc.server.boss.threads"),
    SERVER_WORKER_THREADS("rpc.server.worker.threads"),
    SERVER_BIZ_CORE_THREADS("rpc.server.biz.core.threads"),
    SERVER_BIZ_MAX_THREADS("rpc.server.biz.max.threads"),
    SERVER_BIZ_KEEP_ALIVE_MS("rpc.server.biz.keepalive.ms"),
    SERVER_BIZ_QUEUE_CAPACITY("rpc.server.biz.queue.capacity"),
    SERVER_BIZ_REJECT_POLICY("rpc.server.biz.reject.policy"),
    SERVER_MAX_CONCURRENT_REQUESTS("rpc.server.max.concurrent"),
    SERVER_BACKPRESSURE_QUEUE_THRESHOLD("rpc.server.backpressure.queue.threshold"),
    SERVER_DRAIN_TIMEOUT_MS("rpc.server.drain.timeout.ms"),
    LOAD_BALANCE_STRATEGY("rpc.loadbalance.strategy"),
    HASH_KEY_STRATEGY("rpc.hash.key.strategy"),
    CLIENT_CB_FAILURE_THRESHOLD("rpc.client.circuit.failure.threshold"),
    CLIENT_CB_OPEN_MS("rpc.client.circuit.open.ms"),
    CLIENT_CB_HALF_OPEN_MAX("rpc.client.circuit.halfopen.max"),
    /**
     * RPC request timeout in milliseconds, default 30000ms
     */
    RPC_REQUEST_TIMEOUT_MS("rpc.request.timeout.ms"),
    /**
     * Connection retry count, default 3
     */
    RPC_CONNECT_RETRY_COUNT("rpc.connect.retry.count"),
    /**
     * Connection retry base interval in milliseconds, default 1000ms
     */
    RPC_CONNECT_RETRY_INTERVAL_MS("rpc.connect.retry.interval.ms");

    private final String propertyValue;

}
