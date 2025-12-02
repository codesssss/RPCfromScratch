package org.tic.extension;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Enhanced SPI loader supporting:
 * - Default implementation selection (explicit default or lowest order).
 * - Priority via order, fail-fast with clear errors.
 * - Compatibility with META-INF/services ServiceLoader files.
 * - Basic observability: load counts, time cost, hit counts.
 */
@Slf4j
public final class ExtensionLoader<T> {

    private static final String CUSTOM_DIRECTORY = "META-INF/extensions/";
    private static final String SERVICE_LOADER_DIRECTORY = "META-INF/services/";
    private static final Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();
    private static final int DEFAULT_ORDER = 100;

    private final Class<?> type;
    private final Map<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    private final Holder<Map<String, ExtensionDefinition>> cachedDefinitions = new Holder<>();
    private final Map<String, AtomicLong> hitCounters = new ConcurrentHashMap<>();
    private final AtomicLong loadCount = new AtomicLong();
    private final AtomicLong totalLoadTimeMs = new AtomicLong();
    private volatile ExtensionDefinition defaultDefinition;

    private static class ExtensionDefinition {
        final String name;
        final Class<?> clazz;
        final int order;
        final boolean isDefault;

        ExtensionDefinition(String name, Class<?> clazz, int order, boolean isDefault) {
            this.name = name;
            this.clazz = clazz;
            this.order = order;
            this.isDefault = isDefault;
        }
    }

    private ExtensionLoader(Class<?> type) {
        this.type = type;
    }

    public static <S> ExtensionLoader<S> getExtensionLoader(Class<S> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type should not be null.");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type must be an interface.");
        }
        if (type.getAnnotation(SPI.class) == null) {
            throw new IllegalArgumentException("Extension type must be annotated by @SPI");
        }
        ExtensionLoader<S> loader = (ExtensionLoader<S>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<S>(type));
            loader = (ExtensionLoader<S>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    /**
     * Get named extension; if name is blank/null, use default.
     */
    public T getExtension(String name) {
        ExtensionDefinition definition = resolveDefinition(name);
        Holder<Object> holder = cachedInstances.computeIfAbsent(definition.name, k -> new Holder<>());
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(definition);
                    holder.set(instance);
                }
            }
        }
        incrementHit(definition.name);
        return (T) instance;
    }

    /**
     * Get metrics of loader: hits, load time, etc.
     */
    public Map<String, Object> metrics() {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type.getName());
        result.put("loadCount", loadCount.get());
        result.put("totalLoadTimeMs", totalLoadTimeMs.get());
        result.put("hits", new HashMap<>(hitCounters));
        result.put("cachedDefinitions", Optional.ofNullable(cachedDefinitions.get()).map(Map::size).orElse(0));
        return result;
    }

    private ExtensionDefinition resolveDefinition(String name) {
        Map<String, ExtensionDefinition> definitions = getExtensionDefinitions();
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No extension definitions found for type " + type.getName());
        }
        if (name == null || name.isBlank()) {
            if (defaultDefinition != null) {
                return defaultDefinition;
            }
            throw new IllegalArgumentException("Default extension not defined for type " + type.getName());
        }
        ExtensionDefinition def = definitions.get(name);
        if (def == null) {
            throw new IllegalArgumentException("No such extension [" + name + "] for type " + type.getName());
        }
        return def;
    }

    private void incrementHit(String name) {
        hitCounters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    private T createExtension(ExtensionDefinition definition) {
        Object cached = EXTENSION_INSTANCES.get(definition.clazz);
        if (cached != null) {
            return (T) cached;
        }
        long start = System.currentTimeMillis();
        try {
            EXTENSION_INSTANCES.putIfAbsent(definition.clazz, definition.clazz.getDeclaredConstructor().newInstance());
            return (T) EXTENSION_INSTANCES.get(definition.clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create extension [" + definition.name + "] for type " + type.getName() + ": " + e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            totalLoadTimeMs.addAndGet(duration);
            loadCount.incrementAndGet();
            log.debug("Loaded extension [{}] for type [{}] in {} ms", definition.name, type.getName(), duration);
        }
    }

    private Map<String, ExtensionDefinition> getExtensionDefinitions() {
        Map<String, ExtensionDefinition> defs = cachedDefinitions.get();
        if (defs == null) {
            synchronized (cachedDefinitions) {
                defs = cachedDefinitions.get();
                if (defs == null) {
                    defs = new HashMap<>();
                    loadDefinitions(defs);
                    cachedDefinitions.set(defs);
                    chooseDefault(defs);
                    logCacheStats(defs.size());
                }
            }
        }
        return defs;
    }

    private void loadDefinitions(Map<String, ExtensionDefinition> definitions) {
        ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
        loadFromCustomDirectory(definitions, classLoader);
        loadFromServiceLoader(definitions, classLoader);
    }

    private void loadFromCustomDirectory(Map<String, ExtensionDefinition> definitions, ClassLoader classLoader) {
        String fileName = CUSTOM_DIRECTORY + type.getName();
        try {
            Enumeration<URL> urls = classLoader.getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL resourceUrl = urls.nextElement();
                    loadCustomResource(definitions, classLoader, resourceUrl);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load extensions from " + fileName, e);
        }
    }

    private void loadCustomResource(Map<String, ExtensionDefinition> definitions, ClassLoader classLoader, URL resourceUrl) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cleaned = stripComment(line);
                if (cleaned.isEmpty()) {
                    continue;
                }
                ExtensionDefinition def = parseDefinition(cleaned, classLoader, resourceUrl);
                mergeDefinition(definitions, def, resourceUrl.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read extension file: " + resourceUrl, e);
        }
    }

    private String stripComment(String line) {
        int ci = line.indexOf('#');
        if (ci >= 0) {
            line = line.substring(0, ci);
        }
        return line.trim();
    }

    private ExtensionDefinition parseDefinition(String line, ClassLoader classLoader, URL resourceUrl) {
        int eq = line.indexOf('=');
        if (eq <= 0 || eq == line.length() - 1) {
            throw new IllegalStateException("Invalid extension definition [" + line + "] in " + resourceUrl);
        }
        String name = line.substring(0, eq).trim();
        String rest = line.substring(eq + 1).trim();
        String className = rest;
        int order = DEFAULT_ORDER;
        boolean isDefault = false;

        int semi = rest.indexOf(';');
        if (semi >= 0) {
            className = rest.substring(0, semi).trim();
            String[] attrs = rest.substring(semi + 1).split(";");
            for (String attr : attrs) {
                String[] kv = attr.split("=");
                if (kv.length != 2) {
                    continue;
                }
                String k = kv[0].trim();
                String v = kv[1].trim();
                if ("order".equalsIgnoreCase(k)) {
                    order = Integer.parseInt(v);
                } else if ("default".equalsIgnoreCase(k)) {
                    isDefault = Boolean.parseBoolean(v);
                }
            }
        }

        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (!type.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Extension class " + className + " does not implement " + type.getName());
            }
            return new ExtensionDefinition(name, clazz, order, isDefault);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load class " + className + " for extension [" + name + "]", e);
        }
    }

    private void mergeDefinition(Map<String, ExtensionDefinition> map, ExtensionDefinition incoming, String source) {
        Objects.requireNonNull(incoming, "incoming definition");
        ExtensionDefinition existing = map.get(incoming.name);
        if (existing == null || incoming.order < existing.order) {
            map.put(incoming.name, incoming);
            log.debug("Registered extension [{}] -> {} (order={}, default={}) from {}", incoming.name, incoming.clazz.getName(), incoming.order, incoming.isDefault, source);
        } else {
            log.debug("Skip registering extension [{}] from {} due to lower priority (incoming order={}, existing order={})", incoming.name, source, incoming.order, existing.order);
        }
    }

    private void loadFromServiceLoader(Map<String, ExtensionDefinition> definitions, ClassLoader classLoader) {
        ServiceLoader<?> loader = ServiceLoader.load(type, classLoader);
        for (Object impl : loader) {
            Class<?> clazz = impl.getClass();
            String name = clazz.getSimpleName();
            ExtensionDefinition def = new ExtensionDefinition(name, clazz, Integer.MAX_VALUE, false);
            mergeDefinition(definitions, def, SERVICE_LOADER_DIRECTORY + type.getName());
        }
    }

    private void chooseDefault(Map<String, ExtensionDefinition> definitions) {
        Optional<ExtensionDefinition> explicit = definitions.values().stream().filter(d -> d.isDefault).findFirst();
        if (explicit.isPresent()) {
            defaultDefinition = explicit.get();
            return;
        }
        defaultDefinition = definitions.values().stream().min((a, b) -> Integer.compare(a.order, b.order)).orElse(null);
    }

    private void logCacheStats(int size) {
        log.info("Loaded {} extensions for type [{}], default={} ", size, type.getName(), defaultDefinition == null ? "none" : defaultDefinition.name);
    }
}
