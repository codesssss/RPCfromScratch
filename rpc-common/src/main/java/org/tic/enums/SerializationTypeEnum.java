package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author codesssss
 * @date 18/8/2024 10:41 pm
 */
@AllArgsConstructor
@Getter
public enum SerializationTypeEnum {

    KYRO((byte) 0x01, "kyro"),
    PROTOSTUFF((byte) 0x02, "protostuff"),
    HESSIAN((byte) 0X03, "hessian");

    private final byte code;
    private final String name;

    public static String getName(byte code) {
        for (SerializationTypeEnum c : SerializationTypeEnum.values()) {
            if (c.getCode() == code) {
                return c.name;
            }
        }
        return null;
    }

}