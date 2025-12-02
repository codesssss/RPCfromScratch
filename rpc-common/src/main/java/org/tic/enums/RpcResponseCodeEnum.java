package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @author codesssss
 * @date 14/4/2024 11:54 pm
 */
@AllArgsConstructor
@Getter
@ToString
public enum RpcResponseCodeEnum {

    SUCCESS(200, "The remote call is successful"),
    FAIL(500, "The remote call is fail"),
    TOO_MANY_REQUESTS(429, "Server overloaded, please retry");
    private final int code;

    private final String message;

}
