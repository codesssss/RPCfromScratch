package org.tic.registry.zk.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.tic.config.ConfigResolver;
import org.tic.enums.RpcConfigEnum;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author codesssss
 * @date 14/4/2024 11:37 pm
 */
@Slf4j
public final class CuratorUtils {

    private static final int BASE_SLEEP_TIME = 1000;
    private static final int MAX_RETRIES = 3;
    public static final String ZK_REGISTER_ROOT_PATH = "/my-rpc";
    private static final Map<String, List<String>> SERVICE_ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> SERVICE_ADDRESS_CACHE_TIME = new ConcurrentHashMap<>();
    private static final Set<String> REGISTERED_PATH_SET = ConcurrentHashMap.newKeySet();
    private static final Map<String, byte[]> REGISTERED_NODE_DATA = new ConcurrentHashMap<>();
    private static CuratorFramework zkClient;
    private static final String DEFAULT_ZOOKEEPER_ADDRESS = "127.0.0.1:2181";
    private static final long DEFAULT_CACHE_TTL_MS = 5000L;

    private CuratorUtils() {
    }

    /**
     * Create persistent nodes. Unlike temporary nodes, persistent nodes are not removed when the client disconnects
     *
     * @param path node path
     */
    public static void createPersistentNode(CuratorFramework zkClient, String path) {
        try {
            if (REGISTERED_PATH_SET.contains(path) || zkClient.checkExists().forPath(path) != null) {
                log.info("The node already exists. The node is:[{}]", path);
            } else {
                //eg: /my-rpc/org.tic.HelloService/127.0.0.1:9999
                zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
                log.info("The node was created successfully. The node is:[{}]", path);
            }
            // persistent parent path should not be tracked for re-registration or clearAll
        } catch (Exception e) {
            log.error("create persistent node for path [{}] fail", path);
        }
    }

    /**
     * Create ephemeral nodes. Ephemeral nodes will be removed when the client disconnects.
     *
     * @param path node path
     */
    public static void createEphemeralNode(CuratorFramework zkClient, String path) {
        try {
            if (REGISTERED_PATH_SET.contains(path) && zkClient.checkExists().forPath(path) != null) {
                log.info("The node already exists. The node is:[{}]", path);
            } else {
                zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
                log.info("The ephemeral node was created successfully. The node is:[{}]", path);
            }
            REGISTERED_PATH_SET.add(path);
        } catch (Exception e) {
            log.error("create ephemeral node for path [{}] fail", path);
        }
    }

    /**
     * Create ephemeral node with data and remember the data for re-registration.
     */
    public static void createEphemeralNode(CuratorFramework zkClient, String path, byte[] data) {
        try {
            if (zkClient.checkExists().forPath(path) != null) {
                // update data
                zkClient.setData().forPath(path, data);
                log.info("The ephemeral node already exists, updated data. Node:[{}]", path);
            } else {
                zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path, data);
                log.info("The ephemeral node with data was created successfully. Node:[{}]", path);
            }
            REGISTERED_PATH_SET.add(path);
            if (data != null) {
                REGISTERED_NODE_DATA.put(path, data);
            }
        } catch (Exception e) {
            log.error("create ephemeral node with data for path [{}] fail", path);
        }
    }

    /**
     * Gets the children under a node
     *
     * @param rpcServiceName rpc service name eg:org.tic.HelloServicetest2version1
     * @return All child nodes under the specified node
     */
    public static List<String> getChildrenNodes(CuratorFramework zkClient, String rpcServiceName) {
        String servicePath = ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName;
        long ttlMs = getCacheTtlMs();
        Long lastUpdate = SERVICE_ADDRESS_CACHE_TIME.get(rpcServiceName);
        List<String> cached = SERVICE_ADDRESS_MAP.get(rpcServiceName);
        long now = System.currentTimeMillis();
        if (cached != null && lastUpdate != null && (now - lastUpdate) <= ttlMs) {
            return cached;
        }
        try {
            List<String> result = zkClient.getChildren().forPath(servicePath);
            SERVICE_ADDRESS_MAP.put(rpcServiceName, result);
            SERVICE_ADDRESS_CACHE_TIME.put(rpcServiceName, now);
            registerWatcher(rpcServiceName, zkClient);
            return result;
        } catch (Exception e) {
            log.error("get children nodes for path [{}] fail, try fallback to cache", servicePath);
            if (cached != null) {
                return cached;
            }
            return null;
        }
    }

    /**
     * Empty the registry of data
     */
    public static void clearRegistry(CuratorFramework zkClient, InetSocketAddress inetSocketAddress) {
        REGISTERED_PATH_SET.stream().parallel().forEach(p -> {
            try {
                if (p.endsWith(inetSocketAddress.toString())) {
                    zkClient.delete().forPath(p);
                }
            } catch (Exception e) {
                log.error("clear registry for path [{}] fail", p);
            }
        });
        log.info("All registered services on the server are cleared:[{}]", REGISTERED_PATH_SET.toString());
    }

    public static CuratorFramework getZkClient() {
        // check if user has set zk address
        String zookeeperAddress = ConfigResolver.getString(RpcConfigEnum.ZK_ADDRESS.getPropertyValue(), DEFAULT_ZOOKEEPER_ADDRESS);
        // if zkClient has been started, return directly
        if (zkClient != null && zkClient.getState() == CuratorFrameworkState.STARTED) {
            return zkClient;
        }
        // Retry strategy. Retry 3 times, and will increase the sleep time between retries.
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRIES);
        zkClient = CuratorFrameworkFactory.builder()
                // the server to connect to (can be a server list)
                .connectString(zookeeperAddress)
                .retryPolicy(retryPolicy)
                .build();
        zkClient.start();
        // add connection state listener for re-registration on session reconnected
        ConnectionStateListener listener = (client, newState) -> {
            if (newState == ConnectionState.LOST) {
                log.warn("Zookeeper connection LOST. Will try to re-register on RECONNECTED.");
            }
            if (newState == ConnectionState.RECONNECTED) {
                log.info("Zookeeper RECONNECTED. Re-registering ephemeral nodes: {}", REGISTERED_PATH_SET.size());
                REGISTERED_PATH_SET.forEach(path -> {
                    try {
                        byte[] data = REGISTERED_NODE_DATA.get(path);
                        if (data != null) {
                            createEphemeralNode(client, path, data);
                        } else {
                            createEphemeralNode(client, path);
                        }
                    } catch (Exception ex) {
                        log.error("Re-register node failed for path: {}", path, ex);
                    }
                });
            }
        };
        zkClient.getConnectionStateListenable().addListener(listener);
        try {
            // wait 30s until connect to the zookeeper
            if (!zkClient.blockUntilConnected(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Time out waiting to connect to ZK!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return zkClient;
    }

    /**
     * Registers to listen for changes to the specified node
     *
     * @param rpcServiceName rpc service name eg:org.tic.HelloServicetest2version
     */
    private static void registerWatcher(String rpcServiceName, CuratorFramework zkClient) throws Exception {
        String servicePath = ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName;
        PathChildrenCache pathChildrenCache = new PathChildrenCache(zkClient, servicePath, true);
        PathChildrenCacheListener pathChildrenCacheListener = (curatorFramework, pathChildrenCacheEvent) -> {
            List<String> serviceAddresses = curatorFramework.getChildren().forPath(servicePath);
            SERVICE_ADDRESS_MAP.put(rpcServiceName, serviceAddresses);
            SERVICE_ADDRESS_CACHE_TIME.put(rpcServiceName, System.currentTimeMillis());
        };
        pathChildrenCache.getListenable().addListener(pathChildrenCacheListener);
        pathChildrenCache.start();
    }

    public static byte[] getNodeData(CuratorFramework zkClient, String path) {
        try {
            return zkClient.getData().forPath(path);
        } catch (Exception e) {
            return null;
        }
    }

    private static long getCacheTtlMs() {
        return ConfigResolver.getLong(RpcConfigEnum.ZK_CACHE_TTL_MS.getPropertyValue(), DEFAULT_CACHE_TTL_MS);
    }

}
