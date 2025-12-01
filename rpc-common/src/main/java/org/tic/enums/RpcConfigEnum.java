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

