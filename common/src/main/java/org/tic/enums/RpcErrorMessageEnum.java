package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @author codesssss
 * @date 14/4/2024 11:58 pm
 */
@AllArgsConstructor
@Getter
@ToString
public enum RpcErrorMessageEnum {
    CLIENT_CONNECT_SERVER_FAILURE("Client failed to connect to server"),
    SERVICE_INVOCATION_FAILURE("Service invocation failed"),
    SERVICE_CAN_NOT_BE_FOUND("Specified service not found"),
    SERVICE_NOT_IMPLEMENT_ANY_INTERFACE("Registered service does not implement any interface"),
    REQUEST_NOT_MATCH_RESPONSE("Incorrect result! Request and response do not match");

    private final String message;

}