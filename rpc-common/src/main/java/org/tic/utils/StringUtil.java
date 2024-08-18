package org.tic.utils;

/**
 * @author codesssss
 * @date 18/8/2024 10:55 pm
 */
public class StringUtil {

    public static boolean isBlank(String s) {
        if (s == null || s.length() == 0) {
            return true;
        }
        for (int i = 0; i < s.length(); ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}

