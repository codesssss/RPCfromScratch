package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author codesssss
 * @date 18/8/2024 10:39 pm
 */
@AllArgsConstructor
@Getter
public enum LoadBalanceEnum {

    CONSISTENT_HASH("consistentHash"),
    RANDOM("random"),
    WEIGHTED_RANDOM("weightedRandom"),
    WEIGHTED_ROUND_ROBIN("weightedRoundRobin"),
    LEGACY("loadBalance");

    private final String name;
}
