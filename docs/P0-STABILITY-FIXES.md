# RPCfromScratch P0 稳定性问题修复文档

## 概述

本文档详细记录了 RPCfromScratch 项目中 4 个 P0 级别稳定性问题的分析与修复过程，包括问题背景、潜在风险、解决方案和预期效果。

---

## 问题 1：RPC 请求无超时机制

### 📍 问题定位

**文件**: `rpc-core/src/main/java/org/tic/proxy/RpcClientProxy.java`

**原始代码**:

```java
CompletableFuture<RpcResponse<Object>> completableFuture = 
    (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
rpcResponse = completableFuture.get();  // 无超时，永久阻塞
```

### ⚠️ 痛点分析

1. **线程永久阻塞**: 如果服务端无响应（网络分区、服务宕机、处理卡死），客户端线程将永久阻塞在 `get()` 调用上
2. **资源耗尽**: 大量请求堆积会导致线程池耗尽，整个客户端应用不可用
3. **无法快速失败**: 用户无法得到及时反馈，体验极差
4. **级联故障**: 上游服务因等待下游超时而雪崩

### 🔧 解决方案

**修改后代码**:

```java
// 从配置加载超时时间（默认 30 秒）
private final long requestTimeoutMs = loadRequestTimeout();

// 使用带超时的 get()
try {
    rpcResponse = completableFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    completableFuture.cancel(true);  // 取消 Future
    log.error("RPC request timeout after {}ms, requestId: {}, interface: {}, method: {}", 
            requestTimeoutMs, rpcRequest.getRequestId(), 
            rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
    throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE"Request timeout after " + requestTimeoutMs + "ms, interfaceName:" + rpcRequest.getInterfaceName()); catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // 恢复中断状态
    throw new RpcException(...);
} catch (ExecutionException e) {
    throw new RpcException(...);
}
```

**新增配置项**:

```properties
# rpc.properties
rpc.request.timeout.ms=30000
```

### ✅ 预期效果 指标 | 修复前 | 修复后 |

|------|--------|--------|
| 最大等待时间 | 无限 | 可配置（默认 30s） |
| 线程阻塞风险 | 高 | 低 |
| 快速失败能力 | 无 | 有 |
| 异常信息 | 无 | 详细（requestId、接口、方法） |

---

## 问题 2：未处理请求内存泄漏

### 📍 问题定位

**文件**: `rpc-core/src/main/java/org/tic/remoting/transport/netty/client/UnprocessedRequests.java`

**原始代码**:

```java
private static final Map<String, CompletableFuture<RpcResponse<Object>>> UNPROCESSED_RESPONSE_FUTURES = new ConcurrentHashMap<>();

public void put(String requestId, CompletableFuture<RpcResponse<Object>> future) {
    UNPROCESSED_RESPONSE_FUTURES.put(requestId, future);  // 只进不出
}
```

### ⚠️ 痛点分析

1. **内存泄漏**: 如果响应丢失（网络问题、服务端异常），请求永远留在 Map 中
2. **OOM 风险**: 长时间运行后，Map 无限增长，最终导致 OutOfMemoryError
3. **无监控**: 无法知道有多少请求在等待中
4. **孤儿请求**: 超时后客户端已放弃，但 Map 中仍保留引用

### 🔧 解决方案

**修改后代码**:

```java
// 包装类记录创建时间
private static class RequestFutureWrapper {
    final CompletableFuture<RpcResponse<Object>> future;
    final long createTime;
    
    boolean isTimeout(long timeoutMs) {
        return System.currentTimeMillis() - createTime > timeoutMs;
    }
}

// 定时清理任务（每 10 秒执行）
private static final ScheduledExecutorService CLEANUP_EXECUTOR;

static {
    CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "unprocessed-requests-cleanup");
        t.setDaemon(true);
        return t;
    });
    CLEANUP_EXECUTOR.scheduleAtFixedRate(
        UnprocessedRequests::cleanupTimeoutRequests,
        10000, 10000, TimeUnit.MILLISECONDS
    );
}

// 清理超时请求（默认 60 秒）
private static void cleanupTimeoutRequests() {
    Iterator<Map.Entry<String, RequestFutureWrapper>> iterator = 
        UNPROCESSED_RESPONSE_FUTURES.entrySet().iterator();
    while (iterator.hasNext()) {
        Map.Entry<String, RequestFutureWrapper> entry = iterator.next();
        if (entry.getValue().isTimeout(DEFAULT_REQUEST_TIMEOUT_MS)) {
            iterator.remove();entry.getValue().future.completeExceptionally(
                new RuntimeException("Request timeout and cleaned up")
            );
            log.warn("Cleaned up timeout request: {}", entry.getKey());
        }
    }

// 新增方法：外部主动移除
public void remove(String requestId) {
    UNPROCESSED_RESPONSE_FUTURES.remove(requestId);
}

// 新增方法：获取待处理数量
public int getPendingCount() {
    return UNPROCESSED_RESPONSE_FUTURES.size();
}
```

### ✅ 预期效果

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 内存泄漏风险 | 高 | 无 |
| 最大请求存活时间 | 无限 | 60 秒 |
| 孤儿请求处理 | 无 | 自动清理并通知 |
| 可观测性 | 无 | 有（日志 + getPendingCount） |

---

## 问题 3：连接失败无重试机制

### 📍 问题定位

**文件**: `rpc-core/src/main/java/org/tic/remoting/transport/netty/client/NettyRpcClient.java`

**原始代码**:

```java
public Channel doConnect(InetSocketAddress inetSocketAddress) {
    CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
    bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
        if (future.isSuccess()) {
            completableFuture.complete(future.channel());
        } else {
            throw new IllegalStateException();  // 直接失败，无重试
        }
    });
    return completableFuture.get();
}
```

### ⚠️ 痛点分析

1. **脆弱性**: 网络抖动、服务重启等瞬时故障直接导致调用失败
2. **用户体验差**: 一次失败就报错，需要用户手动重试
3. **异常信息不明确**: `IllegalStateException` 无任何上下文
4. **无退避策略**: 即使重试也可能加剧服务端压力

### 🔧 解决方案

**修改后代码**:

```java
// 可配置的重试参数
private final int retryCount = loadRetryCount();           // 默认 3 次
private final long retryIntervalMs = loadRetryInterval();  // 默认 1000ms

public Channel doConnect(InetSocketAddress inetSocketAddress) {
    Exception lastException = null;
    
    for (int attempt = 1; attempt <= retryCount; attempt++) {
        final int currentAttempt = attempt;
        try {
            CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
            
            bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("Connected to [{}] successful! (attempt {})", 
                            inetSocketAddress, currentAttempt);
                    completableFuture.complete(future.channel());
                } else {
                    completableFuture.completeExceptionally(future.cause());
            });
            
            return completableFuture.get(CONNECT_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
            
        } catch (ExecutionException | TimeoutException e) {
            lastException = e;
            log.warn("Failed to connect to [{}], attempt {}/{}, error: {}", 
                    inetSocketAddress, attempt, retryCount, e.getMessage());
            
            if (attempt < retryCount) {
                // 指数退避: 1s -> 2s -> 4s
                long sleepTime = retryIntervalMs * (1L << (attempt - 1));
                log.info("Retrying in {}ms...", sleepTime);
                Thread.sleep(sleepTime);
            }
        }
    }
    
    throw new RpcException(
        String.format("Failed to connect to [%s] after %d attempts", inetSocketAddress, retryCount),
        lastException
    );
}
```

**新增配置项**:

```properties
# rpc.properties
rpc.connect.retry.count=3
rpc.connect.retry.interval.ms=1000
```

### ✅ 预期效果

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 瞬时故障容忍 | 无 | 3 次重试 |
| 退避策略 | 无 | 指数退避（1s/2s/4s） |
| 异常信息 | IllegalStateException | 详细（地址、尝试次数、原因） |
| 可配置性 | 无 | 重试次数、间隔可配置 |

---

## 问题 4：Channel 失活未检测

### 📍 问题定位

**文件**: `rpc-core/src/main/java/org/tic/remoting/transport/netty/client/NettyRpcClient.java`

**原始代码**:

```java
public Channel getChannel(InetSocketAddress inetSocketAddress) {
    Channel channel = channelProvider.get(inetSocketAddress);
    if (channel == null) {
        channel = doConnect(inetSocketAddress);
        channelProvider.set(inetSocketAddress, channel);
    }
    return channel;  // 可能返回已失活的 Channel
}

public Object sendRpcRequest(RpcRequest rpcRequest) {
    Channel channel = getChannel(inetSocketAddress);
    if (channel.isActive()) {
        // 发送请求
    } else {
        throw new IllegalStateException();  // 无清理，下次还会拿到同一个失活 Channel
    }
}
```

### ⚠️ 痛点分析

1. **僵尸连接**: 服务端重启后，客户端仍持有旧的失活 Channel
2. **重复失败**: 每次请求都会拿到同一个失活 Channel，持续失败
3. **无自愈能力**: 需要重启客户端才能恢复
4. **资源浪费**: 失活 Channel 占用内存但无法使用

### 🔧 解决方案

**修改后代码**:

```java
public Channel getChannel(InetSocketAddress inetSocketAddress) {
    Channel channel = channelProvider.get(inetSocketAddress);
    
    if (channel != null) {
        if (channel.isActive()) {
            return channel;  // 健康，直接返回
        } else {
            // 失活，移除并重连
            log.warn("Cached channel for [{}] is inactive, removing and reconnecting...", 
                    inetSocketAddress);
            channelProvider.remove(inetSocketAddress);
        }
    }
    
    // 创建新连接
    channel = doConnect(inetSocketAddress);
    channelProvider.set(inetSocketAddress, channel);
    return channel;
}

public Object sendRpcRequest(RpcRequest rpcRequest) {
    Channel channel = getChannel(inetSocketAddress);
    if (channel.isActive()) {
        unprocessedRequests.put(rpcRequest.getRequestId(), resultFuture);
        channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                // 发送失败，清理未处理请求
                unprocessedRequests.remove(rpcRequest.getRequestId());
                resultFuture.completeExceptionally(future.cause());
            }
        });
    } else {
        channelProvider.remove(inetSocketAddress);  // 清理失活 Channel
        throw new RpcException("Channel is not active for address: " + inetSocketAddress);
    }
}
```

### ✅ 预期效果

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 僵尸连接处理 | 无 | 自动检测并移除 |
| 自愈能力 | 无 | 自动重连 |
| 发送失败处理 | 无 | 清理 unprocessedRequests |
| 异常信息 | IllegalStateException | RpcException（含地址） |

---

## 配置汇总

在 `rpc.properties` 中可配置以下参数：

```properties Zookeeper 地址
rpc.zookeeper.address=127.0.0.1:2181

# RPC 请求超时（毫秒），默认 30000
rpc.request.timeout.ms=30000

# 连接重试次数，默认 3
rpc.connect.retry.count=3

# 连接重试基础间隔（毫秒），默认 1000，实际间隔为 interval * 2^(attempt-1)
rpc.connect.retry.interval.ms=1000
```

---

## 修改文件清单

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `rpc-core/.../RpcClientProxy.java` | 修改 | 添加请求超时机制 |
| `rpc-core/.../UnprocessedRequests.java` | 重写 | 添加超时清理、监控方法 |
| `rpc-core/.../NettyRpcClient.java` | 重写 | 添加重试机制、Channel 健康检查 |
| `rpc-common/.../RpcConfigEnum.java` | 修改 | 新增 3 个配置枚举 |
| `rpc-common/.../RpcException.java` | 修改 | 新增 String 参数构造函数 |

---

## 测试建议

1. **超时测试**: 模拟服务端不响应，验证客户端 30 秒后超时
2. **内存泄漏测试**: 长时间运行，观察 `UnprocessedRequests.getPendingCount()` 是否稳定
3. **重试测试**: 启动客户端时服务端未启动，验证重试日志和最终失败
4. **断连恢复测试**: 运行中重启服务端，验证客户端自动重连

---

## 后续优化建议（P1/P2）

1. **熔断器**: 连续失败后快速失败，避免雪崩
2. **指标埋点**: Prometheus/Micrometer 集成，监控 QPS、RT、错误率
3. **链路追踪**: TraceId 贯穿请求全链路
4. **优雅停机**: 等待在途请求完成后再关闭
