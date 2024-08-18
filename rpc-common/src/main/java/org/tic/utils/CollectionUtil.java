package org.tic.utils;

import java.util.Collection;

/**
 * @author codesssss
 * @date 18/8/2024 10:53 pm
 */
public class CollectionUtil {

    public static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

}
