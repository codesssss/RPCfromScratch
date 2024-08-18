package org.tic.utils;

/**
 * @author codesssss
 * @date 18/8/2024 10:55 pm
 */
public class RuntimeUtil {
    /**
     * Get the number of CPU cores
     *
     * @return the number of CPU cores
     */
    public static int cpus() {
        return Runtime.getRuntime().availableProcessors();
    }
}

