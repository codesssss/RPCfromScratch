# RPCfromScratch

一个从零实现的轻量级 Java RPC 框架示例，基于 Netty 自定义协议，支持 Zookeeper 服务注册与发现、Kryo 序列化、GZIP 压缩、可插拔 SPI 扩展与简单的负载均衡策略（随机、一致性哈希）。提供可运行的服务端/客户端示例工程。

### 功能特性

- **自定义二进制协议**：魔数、版本、消息长度、类型、序列化/压缩类型、请求 ID、消息体
- **传输**：Netty NIO，编码解码器 `RpcMessageEncoder`/`RpcMessageDecoder`
- **注册与发现**：Zookeeper（Apache Curator），根路径 `/my-rpc`
- **序列化**：Kryo（默认）
- **压缩**：GZIP（默认）
- **负载均衡**：随机、⼀致性哈希（默认）
- **心跳保活**：客户端空闲发送心跳，服务端空闲检测
- **Spring 集成**：注解式发布与注入 `@RpcService` / `@RpcReference`，`@RpcScan` 自动扫描
- **SPI 扩展**：资源目录 `META-INF/extensions/*` 动态装配实现

### 模块结构

- `rpc-common`：通用枚举、异常、SPI 加载与通用工具类
- `rpc-core`：核心 RPC 能力（注解、注册发现、Netty 编解码、代理、负载均衡、序列化、压缩、Spring 集成）
- `sample-service-api`：示例接口 `org.tic.HelloService` 与 DTO `org.tic.Hello`
- `sample-rpc-server`：示例服务端，入口 `org.tic.NettyServerMain`
- `sample-rpc-client`：示例客户端，入口 `org.tic.NettyClientMain`

### 环境要求

- JDK 22（父 POM `maven.compiler.source/target=22`）
- Maven 3.8+
- Zookeeper 3.8+（默认地址 `127.0.0.1:2181`）

### 快速开始

1) 启动 Zookeeper（本机默认 127.0.0.1:2181），或在配置文件中修改地址（见下节配置）。

2) 一键构建（根目录执行）：

```bash
mvn -q -DskipTests clean package
```

3) 运行示例服务端（推荐 IDE 直接运行 `org.tic.NettyServerMain`）：

```bash
# 方式A：不改 POM 直接用 exec 插件运行
mvn -q -pl sample-rpc-server -am \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.tic.NettyServerMain
```

4) 运行示例客户端（推荐 IDE 直接运行 `org.tic.NettyClientMain`）：

```bash
mvn -q -pl sample-rpc-client -am \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.tic.NettyClientMain
```

运行时序：先启动服务端，再启动客户端。客户端 `HelloController#test` 会发起一次调用并断言返回值，然后等待 12 秒后连续发起 10 次调用。

### 代码入口与关键点

- 服务端入口：`sample-rpc-server` → `org.tic.NettyServerMain`
  - 注解扫描：`@RpcScan(basePackage={"org.tic"})`
  - 自动发布：标注 `@RpcService` 的实现会被 `SpringBeanPostProcessor` 发布
  - 手动发布示例：`HelloServiceImpl2` 通过 `RpcServiceConfig` + `nettyRpcServer.registerService(...)`
  - 监听端口：`NettyRpcServer.PORT = 9998`
- 客户端入口：`sample-rpc-client` → `org.tic.NettyClientMain`
  - 注解注入：`@RpcReference(version="version1", group="test1")` 自动注入代理
  - 代理实现：`RpcClientProxy` 组装 `RpcRequest` → `NettyRpcClient` 发送 → 返回 `RpcResponse`

### 配置说明

- Zookeeper 地址
  - 默认：`127.0.0.1:2181`
  - 覆盖方式：在需要生效的模块（如 `sample-rpc-server`、`sample-rpc-client`）添加 `src/main/resources/rpc.properties`：

```properties
rpc.zookeeper.address=127.0.0.1:2181
```

- 读取逻辑：`rpc-common` 的 `PropertiesFileUtil` 从类路径根读取 `rpc.properties`

- SPI 扩展（`rpc-core/src/main/resources/META-INF/extensions`）
  - 传输实现：`org.tic.remoting.transport.RpcRequestTransport`
    - `netty=org.tic.remoting.transport.netty.client.NettyRpcClient`
  - 注册中心：`org.tic.registry.ServiceRegistry`
    - `zk=org.tic.registry.zk.ZkServiceRegistryImpl`
  - 服务发现：`org.tic.registry.ServiceDiscovery`
    - `zk=org.tic.registry.zk.ZkServiceDiscoveryImpl`
  - 负载均衡：`org.tic.loadbalance.LoadBalance`
    - `loadBalance=org.tic.loadbalance.loadbalancer.ConsistentHashLoadBalance`
  - 序列化：`org.tic.serialize.Serializer`
    - `kryo=org.tic.serialize.kryo.KryoSerializer`
  - 压缩：`org.tic.compress.Compress`
    - `gzip=org.tic.compress.gzip.GzipCompress`

切换实现：修改上述映射中的 value 即可；或新增实现类并在对应文件内增加 `key=全限定类名`。

### 协议简述

消息头（16B）：

- magic[4] | version[1] | fullLength[4] | messageType[1] | codec[1] | compress[1] | requestId[4]
消息体：按 `codec` 反序列化后的对象（并按 `compress` 解压）。常量见 `RpcConstants`。

### 常见问题（FAQ）

- 无法连接 Zookeeper / 超时
  - 确认本地或远端 ZK 已启动；如非默认地址，请按上文添加 `rpc.properties`
- 注册成功但客户端发现不到服务
  - 核对 `@RpcService` 与 `@RpcReference` 的 `group/version` 是否一致
- 端口占用
  - 默认端口 `9998`；如被占用可修改 `NettyRpcServer.PORT`
- 客户端断言失败
  - 示例中使用 `assert`，若未开启断言参数（`-ea`），不会抛错但也不会校验；建议在 IDE 或 JVM 参数中开启
- CLI 运行找不到类（ClassNotFound）
  - 优先使用 IDE 运行，或使用上文带坐标的 `exec-maven-plugin` 命令保证依赖可用
- JDK 版本不匹配
  - 本项目设置为 22，若使用更低版本可能编译失败

### 许可

本项目仅用于学习与示例用途。
