package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author codesssss
 * @date 18/8/2024 10:42 pm
 */
@AllArgsConstructor
@Getter
public enum ServiceDiscoveryEnum {

    ZK("zk");

    private final String name;
}

