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
    - 新格式支持优先级与默认实现：`loadBalance=org.tic.loadbalance.loadbalancer.ConsistentHashLoadBalance;order=10;default=true`
    - 可选实现：`random=org.tic.loadbalance.loadbalancer.RandomLoadBalance;order=20`
  - 序列化：`org.tic.serialize.Serializer`
    - `kryo=org.tic.serialize.kryo.KryoSerializer`
  - 压缩：`org.tic.compress.Compress`
    - `gzip=org.tic.compress.gzip.GzipCompress`

切换实现：修改上述映射中的 value 即可；或新增实现类并在对应文件内增加 `key=全限定类名`。
新格式向后兼容：
- 旧格式：`name=implClass`（默认 order=100）
- 扩展格式：`name=implClass;order=10;default=true`（优先级与默认标记）
同时兼容 `META-INF/services/{接口全名}` 的标准 ServiceLoader 声明，类名将使用简单类名作为 key，优先级最低（仅在未被 extensions 同名覆盖时生效）。

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

### TODO / Roadmap

- P0 稳定性与可观测性
  - [ ] 客户端请求超时、取消传播，超时后清理 `UnprocessedRequests`
  - [ ] 连接失败退避重试与重连；Channel 失活自动摘除与重建
  - [ ] 结构化日志与链路追踪（请求 ID/TraceId 贯穿）
  - [ ] 指标埋点（QPS、RT、错误率、线程池水位、重试次数）

- P0 注册与发现健壮性
  - [ ] ZK 会话过期自动重建监听与重注册
  - [ ] 本地地址缓存与 ZK 不可用时有限时效回退
  - [ ] 实例权重与动态调整支持

- P1 线程模型与优雅停机
  - [ ] 线程池参数可配置（核心/最大/队列/拒绝策略）与指标
  - [ ] 优雅停机：阻止新连接、等待在途请求、清理注册信息
  - [ ] Backpressure：过载快速失败或返回可重试码

- P1 负载均衡与健康检查
  - [ ] 加权随机/加权轮询策略
  - [ ] 实例健康度与熔断统计（探活、错误率影响权重）
  - [ ] 一致性哈希 Key 策略可配置（方法/参数子集）

- P1 配置与 SPI 体系
  - [ ] 配置优先级：环境变量 > 系统属性 > 配置文件；支持覆盖
  - [ ] SPI fail-fast、默认实现与优先级、懒加载与缓存指标
  - [ ] 兼容 JDK ServiceLoader 的读取方式

- P2 序列化与压缩优化
  - [ ] 增加 Protostuff/JSON/Hessian 等可选序列化
  - [ ] 压缩阈值与解压后最大体积限制；零拷贝优化
  - [ ] 协议校验（CRC32）与版本向后兼容策略

- P2 安全与接入
  - [ ] TLS（含双向）与证书热更新
  - [ ] 简单鉴权、黑白名单与入站限流过滤器
  - [ ] 敏感字段日志脱敏与日志采样

- P2 易用性与生态
  - [ ] Spring Boot Starter（自动装配与配置化）
  - [ ] Docker Compose（ZK + 示例应用）一键体验
  - [ ] CI/测试：Curator TestingServer 集成测、JMH/压测脚本、GitHub Actions
  - [ ] 支持 JDK LTS（17/21）构建矩阵
