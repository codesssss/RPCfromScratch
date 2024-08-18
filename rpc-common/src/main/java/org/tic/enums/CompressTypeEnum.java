package org.tic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author codesssss
 * @date 18/8/2024 10:39 pm
 */
@AllArgsConstructor
@Getter
public enum CompressTypeEnum {

    GZIP((byte) 0x01, "gzip");

    private final byte code;
    private final String name;

    public static String getName(byte code) {
        for (CompressTypeEnum c : CompressTypeEnum.values()) {
            if (c.getCode() == code) {
                return c.name;
            }
        }
        return null;
    }

}
