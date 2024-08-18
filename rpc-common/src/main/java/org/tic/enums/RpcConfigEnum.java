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
    ZK_ADDRESS("rpc.zookeeper.address");

    private final String propertyValue;

}

