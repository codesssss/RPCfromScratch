package org.tic.config;

import lombok.extern.slf4j.Slf4j;
import org.tic.enums.RpcConfigEnum;
import org.tic.utils.PropertiesFileUtil;

import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/**
 * Configuration resolver with priority: Environment Variable > System Property > Config File.
 * <p>
 * Supports type conversion, default value fallback, and logs the configuration source.
 * Thread-safe and optimized for production use.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * int retryCount = ConfigResolver.getInt("rpc.connect.retry.count", 3);
 * String zkAddress = ConfigResolver.getString("rpc.zookeeper.address", "127.0.0.1:2181");
 * </pre>
 */
@Slf4j
public final class ConfigResolver {

    /**
     * Configuration source type
     */
    private enum Source {
        ENV("Environment Variable"),
        SYS_PROP("System Property"),
        FILE("Config File"),
        DEFAULT("Default Value");

        private final String description;

        Source(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Configuration resolution result
     */
    private static class ResolutionResult {
        final String value;
        final Source source;

        ResolutionResult(String value, Source source) {
            this.value = value;
            this.source = source;
        }
    }

    private static final String DEFAULT_CONFIG_FILE = RpcConfigEnum.RPC_CONFIG_PATH.getPropertyValue();
    private static final Properties FILE_PROPERTIES = loadFileProperties();
    private static final boolean LOG_ENABLED = resolveLoggingFlag();

    private ConfigResolver() {
        // Utility class, prevent instantiation
    }

    /**
     * Get string configuration value
     *
     * @param key          configuration key
     * @param defaultValue default value if not found
     * @return resolved value
     */
    public static String getString(String key, String defaultValue) {
        return resolve(key, defaultValue, Function.identity(), "string");
    }

    /**
     * Get integer configuration value
     *
     * @param key          configuration key
     * @param defaultValue default value if not found or parse fails
     * @return resolved value
     */
    public static int getInt(String key, int defaultValue) {
        return resolve(key, defaultValue, Integer::parseInt, "int");
    }

    /**
     * Get long configuration value
     *
     * @param key          configuration key
     * @param defaultValue default value if not found or parse fails
     * @return resolved value
     */
    public static long getLong(String key, long defaultValue) {
        return resolve(key, defaultValue, Long::parseLong, "long");
    }

    /**
     * Get boolean configuration value
     *
     * @param key          configuration key
     * @param defaultValue default value if not found or parse fails
     * @return resolved value
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return resolve(key, defaultValue, Boolean::parseBoolean, "boolean");
    }

    /**
     * Core resolution logic with type conversion
     */
    private static <T> T resolve(String key, T defaultValue, Function<String, T> parser, String typeName) {
        Objects.requireNonNull(key, "Configuration key must not be null");

        ResolutionResult result = resolveRawValue(key);

        if (result.value != null) {
            try {
                T parsed = parser.apply(result.value);
                logResolution(key, parsed, result.source, result.value);
                return parsed;
            } catch (Exception e) {
                log.warn("Failed to parse config [{}] as {} from value [{}] (source: {}), falling back to default [{}]",
                        key, typeName, result.value, result.source.getDescription(), defaultValue);
            }
        }

        logResolution(key, defaultValue, Source.DEFAULT, null);
        return defaultValue;
    }

    /**
     * Resolve raw string value with source tracking
     */
    private static ResolutionResult resolveRawValue(String key) {
        // Priority 1: Environment Variable
        String envValue = readEnv(key);
        if (isNotBlank(envValue)) {
            return new ResolutionResult(envValue, Source.ENV);
        }

        // Priority 2: System Property
        String sysValue = readSys(key);
        if (isNotBlank(sysValue)) {
            return new ResolutionResult(sysValue, Source.SYS_PROP);
        }

        // Priority 3: Config File
        String fileValue = readFile(key);
        if (isNotBlank(fileValue)) {
            return new ResolutionResult(fileValue, Source.FILE);
        }

        return new ResolutionResult(null, Source.DEFAULT);
    }

    /**
     * Read from environment variable (supports both original key and normalized key)
     * <p>
     * Example: "rpc.zookeeper.address" -> "RPC_ZOOKEEPER_ADDRESS"
     * </p>
     */
    private static String readEnv(String key) {
        // Try original key first
        String direct = System.getenv(key);
        if (isNotBlank(direct)) {
            return direct;
        }

        // Try normalized key (uppercase with underscores)
        String normalized = normalizeEnvKey(key);
        return System.getenv(normalized);
    }

    /**
     * Normalize configuration key to environment variable format
     * <p>
     * Example: "rpc.zookeeper.address" -> "RPC_ZOOKEEPER_ADDRESS"
     * </p>
     */
    private static String normalizeEnvKey(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    /**
     * Read from system property
     */
    private static String readSys(String key) {
        return System.getProperty(key);
    }

    /**
     * Read from configuration file
     */
    private static String readFile(String key) {
        return FILE_PROPERTIES.getProperty(key);
    }

    /**
     * Load properties file at class initialization
     */
    private static Properties loadFileProperties() {
        try {
            Properties properties = PropertiesFileUtil.readPropertiesFile(DEFAULT_CONFIG_FILE);
            if (properties != null) {
                log.debug("Loaded {} properties from config file: {}", properties.size(), DEFAULT_CONFIG_FILE);
                return properties;
            }
        } catch (Exception e) {
            log.warn("Failed to load config file: {}, will use defaults", DEFAULT_CONFIG_FILE, e);
        }
        return new Properties();
    }

    /**
     * Resolve logging flag (default enabled)
     */
    private static boolean resolveLoggingFlag() {
        try {
            ResolutionResult result = resolveRawValue(RpcConfigEnum.CONFIG_LOG_ENABLED.getPropertyValue());
            if (result.value != null) {
                return Boolean.parseBoolean(result.value);
            }
        } catch (Exception e) {
            log.debug("Failed to resolve config logging flag, defaulting to enabled", e);
        }
        return true; // Default: enabled
    }

    /**
     * Log configuration resolution result
     */
    private static void logResolution(String key, Object value, Source source, String rawValue) {
        if (!LOG_ENABLED) {
            return;
        }

        if (source == Source.DEFAULT) {
            log.debug("Config [{}] using default value: [{}]", key, value);
        } else {
            log.info("Config [{}] resolved from {}: [{}]", key, source.getDescription(), value);
        }
    }

    /**
     * Check if string is not blank
     */
    private static boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
