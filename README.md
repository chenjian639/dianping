# Dodo Food

一个基于 Spring Boot 的外卖点评与商家订单管理系统（Java 练手项目）。

项目涵盖用户注册登录、餐厅与菜品查询、购物车、订单状态流转、商家管理、用户评价、
Redis 多级缓存、JWT 鉴权、RabbitMQ Outbox 可靠投递、订单超时自动取消、
Resilience4j 熔断/隔离、Swagger 文档、JUnit 单元测试以及 Docker 一键部署。

> 在线部署：服务器 `dodo@101.200.221.93`，应用端口 `8080`。
> Swagger 文档：`http://101.200.221.93:8080/swagger-ui.html`

---

## 目录

- [技术栈](#技术栈)
- [项目架构](#项目架构)
- [功能模块](#功能模块)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [数据库初始化](#数据库初始化)
- [配置说明](#配置说明)
- [接口文档](#接口文档)
- [搜索接口性能优化与压测](#搜索接口性能优化与压测)
- [多级缓存与容错设计](#多级缓存与容错设计)
- [订单超时自动取消](#订单超时自动取消)
- [测试](#测试)
- [部署](#部署)

---

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言/运行时 | Java 21 |
| 框架 | Spring Boot 3.1.5、Spring MVC |
| 安全 | Spring Security（依赖已引入，当前 `permitAll`，鉴权实际由 JWT 拦截器完成，详见下文） |
| 缓存 | Redis 7（Lettuce）、Caffeine（本地缓存） |
| 消息队列 | RabbitMQ（延迟队列 + 死信队列） |
| 鉴权 | JWT（jjwt 0.12.3）、BCrypt（`LoginInterceptor` + 控制器内角色校验） |
| 容错 | Resilience4j 2.2.0（熔断 + 隔离） |
| API 文档 | Springdoc OpenAPI 2.5.0（Swagger UI） |
| 校验 | Spring Validation |
| 测试 | JUnit 5、Mockito |
| 构建/部署 | Maven、Docker、Docker Compose |

---

## 项目架构

```text
┌──────────────────────────────────────────────────────┐
│                     HTTP 客户端                       │
└───────────────┬──────────────────────────┬───────────┘
                │                          │
        ┌───────▼────────┐        ┌───────▼────────┐
        │  LoginInterceptor │     │  Swagger / Actuator │
        │  (JWT 解析 + 鉴权) │     └────────────────┘
        └───────┬────────┘
                │
        ┌───────▼────────┐
        │   Controller    │  @RestController + @Valid
        └───────┬────────┘
                │
        ┌───────▼──────────────────────────────┐
        │                Service                │
        │  Order / Restaurant / Cart / Review   │
        └──┬───────────┬──────────────┬────────┘
             │           │              │
     ┌───────▼──┐  ┌─────▼──────┐  ┌───▼────────────┐
     │  Mapper   │  │ Redis +     │  │  RabbitMQ       │
     │ (MyBatis) │  │ Caffeine    │  │  Outbox 投递    │
     └───────┬──┘  └─────────────┘  └────────────────┘
             │
        ┌────▼────┐
        │  MySQL  │
        └─────────┘
```

核心设计要点：

- **多级缓存**：餐厅详情走 Caffeine（L1，30s）+ Redis（L2，30~40min 随机 TTL），L1 未命中时由 `getOrLoad` 回源 L2，再回源 DB。
- **缓存一致性**：商家修改餐厅信息时，通过本地事件 + DB Outbox 双写，经 RabbitMQ 广播到所有实例失效本地缓存，避免多实例本地缓存不一致。
- **容错**：Redis 读取走 Resilience4j 熔断，故障时降级到 DB；DB 回源走 Bulkhead 限流，防止缓存击穿打垮数据库。
- **可靠投递**：订单超时、缓存失效事件先写 `outbox_event` 表（与业务同事务），再由定时任务抢占投递 RabbitMQ，保证至少一次送达。

---

## 功能模块

### 用户模块

- 用户注册 / 登录
- BCrypt 密码加密
- JWT 身份认证（24h 有效期）
- 获取当前用户信息
- USER / MERCHANT 角色区分

### 餐厅与菜品

- 查询营业餐厅列表
- 查询餐厅详情（含平均评分、评价数）
- 查询餐厅菜品
- 餐厅关键词搜索（MySQL FULLTEXT + ngram）
- 按评分、评价数量、最新时间排序
- 商家修改餐厅信息 / 营业状态
- 商家菜品新增、修改、上下架、逻辑删除、分页查询

### 购物车

- 添加菜品 / 修改数量 / 删除项 / 查询购物车
- 同一菜品自动合并数量
- 库存校验
- 单餐厅购物车限制

### 订单

- 从购物车创建订单（`requestId` 幂等 + 原子扣库存）
- 创建订单时保存菜品名称、价格快照
- 固定库存锁顺序，避免死锁
- 用户订单分页 / 详情查询
- 用户支付 / 取消订单（取消时恢复库存）
- 商家订单分页 / 详情查询
- 商家订单状态流转

订单状态机：

```text
待支付 ──支付──► 已支付 ──制作──► 制作中 ──配送──► 配送中 ──完成──► 已完成
  │
  └──取消──► 已取消（恢复库存）

待支付超时(30min) ──► 已取消（恢复库存）
```

### 评价

- 仅已完成订单可评价
- 一个订单只能评价一次（DB 唯一约束保证）
- 同步维护餐厅 `average_rating` / `review_count` 冗余统计
- 餐厅评价分页 / 评价摘要 / 我的评价

---

## 项目结构

```text
src/main/java/com/dianping
├── DianpingApplication.java        # 启动类，@EnableScheduling
├── common/                         # Result / PageResult 统一响应
├── config/                          # Security / Web / LocalCache / RabbitMQ / OpenAPI
├── interceptor/                    # LoginInterceptor（JWT 鉴权）
├── controller/                     # 用户 / 餐厅 / 购物车 / 订单 / 评价 / 商家端
├── service/                        # 业务逻辑 + 缓存 + Outbox 投递
├── mapper/                         # MyBatis Mapper（注解 + XML）
├── entity / dto / vo               # 数据模型
├── enums/                          # OrderStatus
├── event / listener                # 本地缓存失效事件
├── consumer / mq                   # RabbitMQ 消费者
└── exception/                      # 全局异常处理
```

---

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- Docker & Docker Compose（可选，推荐）
- MySQL 8、Redis 7、RabbitMQ（若本地运行）

### 本地运行

```bash
# 1. 克隆
git clone <repo-url>
cd dianping

# 2. 准备配置（数据库密码、JWT secret 等）
cp .env.example .env
#   编辑 .env 填入真实值

# 3. 启动依赖（MySQL / Redis / RabbitMQ）
docker compose up -d mysql redis
#   RabbitMQ 需自行准备，或用已有服务

# 4. 初始化数据库（见下一节）
mysql -h127.0.0.1 -P3307 -uroot -p < docker/mysql/01-init.sql
mysql -h127.0.0.1 -P3307 -uroot -p dodo_food < docker/mysql/02-data.sql

# 5. 编译运行
./mvnw clean package -DskipTests
SPRING_DATASOURCE_PASSWORD=xxx JWT_SECRET=xxx \
  ./mvnw spring-boot:run
```

访问 `http://localhost:8080/swagger-ui.html` 查看接口文档。

### Docker Compose 一键启动

```bash
cp .env.example .env   # 填入密码 / secret
docker compose up -d --build
```

> 注意：当前 `docker-compose.yml` 未内置 RabbitMQ 服务，且未向 `app` 注入 `RABBITMQ_*` 环境变量。
> 若使用 Outbox / 订单超时功能，请额外准备 RabbitMQ 并在 `.env` / compose 中补齐
> `RABBITMQ_HOST`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`。

---

## 数据库初始化

建表脚本位于 `docker/mysql/01-init.sql`（Docker 自动加载），`schema.sql` 为本地参考脚本，
种子数据见 `docker/mysql/02-data.sql`。

核心表：`user`、`restaurant`、`dish`、`cart_item`、`orders`、`order_item`、`review`、`outbox_event`。

关键索引设计：

- `dish(restaurant_id, deleted, status, created_at DESC, id DESC)` —— 商家菜品分页
- `orders(user_id, created_at DESC, id DESC)` —— 用户订单分页
- `orders(restaurant_id, status, created_at DESC, id DESC)` —— 商家订单分页
- `restaurant(status, created_at DESC, id DESC)` —— 营业餐厅列表
- `restaurant FULLTEXT ft_restaurant_name_ngram(name) WITH PARSER ngram` —— 中文搜索
- `review(order_id) UNIQUE` —— 一订单一评价

---

## 配置说明

全部可配置项通过环境变量注入（见 `application.yml`、`.env.example`）：

| 环境变量 | 说明 | 默认 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL 连接串 | 本地 `dodo_food` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | DB 账号 | root / 无默认 |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | 连接池上限 | 30 |
| `SPRING_DATA_REDIS_HOST` / `PORT` | Redis 地址 | localhost:6379 |
| `RABBITMQ_HOST` / `PORT` / `USERNAME` / `PASSWORD` | RabbitMQ | localhost:5672 / 无默认 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） | 弱默认值，生产必须覆盖 |

HikariCP / Lettuce / Resilience4j（熔断 `redisCache`、隔离 `restaurantDb`）参数均在 `application.yml` 中，
按压测结果调优。

---

## 接口文档

启动后访问 Swagger UI：`/swagger-ui.html`，OpenAPI JSON：`/v3/api-docs`。

主要接口分组：

| 模块 | 前缀 | 鉴权 |
| --- | --- | --- |
| 用户 | `/users` | 注册/登录免鉴权，其余需登录 |
| 餐厅 | `/restaurants` | 搜索免鉴权，其余需登录 |
| 购物车 | `/cart` | 登录 |
| 订单 | `/orders` | 登录 |
| 评价 | `/reviews` | 登录 |
| 商家端 | `/merchant/**` | MERCHANT 角色（控制器内 `checkMerchant`） |
| 管理端 | `/admin/cache-recovery` | 仅登录，**暂无 ADMIN 校验**（已知问题，待修复） |

请求头携带 `Authorization: Bearer <token>`。

> **鉴权现状说明**：项目虽引入了 `spring-boot-starter-security`，但 `SecurityConfig`
> 当前为 `csrf().disable()` + `anyRequest().permitAll()`，即 Spring Security 过滤链未实际生效。
> 真正的鉴权由 `LoginInterceptor`（解析 JWT、写入 `userId/role/restaurantId` 请求属性）+
> 各商家控制器内部的 `checkMerchant` 完成。`/admin/cache-recovery/**` 目前仅校验登录、
> 未校验 ADMIN 角色，属已知问题，后续计划改为基于 Spring Security 过滤链的统一鉴权
> （`JwtAuthenticationFilter` + `hasRole`）。

---

## 搜索接口性能优化与压测

项目使用约 10 万条餐厅数据对搜索接口进行压力测试，并通过 JMeter、
MySQL `EXPLAIN ANALYZE` 对接口瓶颈进行定位和优化。

### 1. 初始问题

餐厅搜索最初采用模糊匹配：

```sql
WHERE name LIKE '%keyword%'
```

并在搜索过程中关联评价表，通过 `AVG()`、`COUNT()` 实时计算餐厅评分和评价数量。

在约 100,000 条餐厅数据下，搜索“烧烤”约命中 12,351 条记录。初始执行计划显示：

- `LIKE '%keyword%'` 难以有效利用普通 B+Tree 索引；
- 搜索需要扫描约 100,000 条餐厅记录；
- `LEFT JOIN review + GROUP BY` 增加了聚合成本；
- 排序后才能执行 `LIMIT`；
- 单次复杂查询约需要 263 ms。

### 2. 使用 FULLTEXT 优化中文模糊搜索

为餐厅名称建立 MySQL FULLTEXT 全文索引，并使用 ngram parser 支持中文搜索：

```sql
FULLTEXT INDEX ft_idx_name(name) WITH PARSER ngram
```

查询由 `name LIKE '%烧烤%'` 调整为：

```sql
MATCH(name) AGAINST('烧烤' IN NATURAL LANGUAGE MODE)
```

使搜索从全表扫描转为全文索引检索。优化后 MySQL 通过 `ft_idx_name` 获取约 12,351 个匹配结果，
避免直接扫描全部约 100,000 条餐厅记录。

### 3. 优化评分聚合

原搜索 SQL 会先关联 `review` 表，再对大量候选餐厅执行 `AVG(rating)` / `COUNT(review.id)` / `GROUP BY restaurant_id`。
对于 `latest` 排序，将查询调整为先完成餐厅搜索和分页，再处理当前页需要的数据，
避免先对全部匹配餐厅进行评价聚合。

进一步在 `restaurant` 表中增加冗余统计字段 `average_rating`、`review_count`，
新增评价时在事务中同步更新餐厅统计信息，从而使搜索接口可以直接读取餐厅评分和评价数量，
而不需要每次搜索实时扫描评价表聚合。

当前测试库的评价数据量较小，因此该优化在现阶段压测中性能收益不明显，
主要用于避免评价数据规模扩大后实时聚合成为新的查询瓶颈。

### 4. 定位分页 COUNT(*) 瓶颈

分页接口最初除了查询当前页数据，还会执行：

```sql
SELECT COUNT(*)
FROM restaurant
WHERE status = 1
  AND MATCH(name) AGAINST('烧烤' IN BOOLEAN MODE);
```

`EXPLAIN ANALYZE` 显示：Full-text index search `actual rows = 12,351`，COUNT 查询总耗时约 167 ms。
虽然使用了全文索引，但为了得到精确的 `total`，MySQL 仍然必须遍历全部 12,351 个匹配结果。

使用相同 JMeter 参数进行 A/B 测试（50 并发、每线程循环 20 次、Ramp-up 10 秒、总计 1000 次请求）：

| 指标 | 保留 COUNT(*) | 去除 COUNT(*) |
| --- | ----------: | ----------: |
| Average | 1647 ms | 795 ms |
| Median | 1658 ms | 793 ms |
| P95 | 2453 ms | 1311 ms |
| P99 | 3065 ms | 1538 ms |
| Throughput | 26.5 req/s | 49.5 req/s |
| Error Rate | 0% | 0% |

去除精确 `COUNT(*)` 后平均响应时间降低约 52%，吞吐量提高约 87%。
说明精确统计搜索结果总数是高并发搜索接口的重要瓶颈之一。

### 5. 将 total 分页改为 hasNext

搜索场景不强依赖精确总记录数，因此将分页方式由 `records + total` 调整为 `records + hasNext`：
请求 `pageSize = 10` 时数据库实际查询 `LIMIT 11`，返回 11 条则 `hasNext = true` 且只向前端返回前 10 条。

在 20 并发、1000 次请求的测试中，hasNext 版本：

| 指标 | 结果 |
| --- | ---: |
| Average | 213 ms |
| Median | 155 ms |
| P90 | 415 ms |
| P95 | 426 ms |
| P99 | 445 ms |
| Error Rate | 0% |
| Throughput | 66.8 req/s |

### 6. 主查询执行计划

优化后的核心搜索 SQL：

```sql
SELECT r.id, r.name, r.description, r.address, r.average_rating, r.review_count
FROM restaurant r
WHERE r.status = 1
  AND MATCH(r.name) AGAINST('烧烤' IN NATURAL LANGUAGE MODE)
ORDER BY r.created_at DESC, r.id DESC
LIMIT 10;
```

`EXPLAIN ANALYZE` 显示：Full-text index search 约 55.6 ms → 过滤后 12,351 条匹配记录约 61.7 ms
→ 排序 + LIMIT 10 约 78.8 ms。主要成本集中在高频关键词命中的大量 FULLTEXT 候选记录及其排序。

### 7. 并发饱和点测试

不同并发数梯度测试：

| 并发数 | Average | P95 | Throughput |
| --: | --: | --: | --: |
| 1 | 103 ms | 130 ms | 9.7 req/s |
| 5 | 78 ms | 124 ms | 41.9 req/s |
| 10 | 159 ms | 216 ms | 49.0 req/s |
| 20 | 269 ms | 430 ms | 57.7 req/s |
| 50 | 683 ms | 1107 ms | 52.0 req/s |
| 100 | 1439 ms | 2004 ms | 48.8 req/s |

HikariCP 配置 `maximum-pool-size: 30`、`minimum-idle: 10`。吞吐量在约 20 并发附近达到较高水平，
继续增加到 50、100 并发后 TS 不再增加反而略降，平均响应和 P95 快速上升。
说明连接池大小不等于系统最佳并发数，系统吞吐同时受 MySQL FULLTEXT 查询、CPU、连接竞争影响。

### 8. 优化总结

```text
JMeter 建立性能基线
      ↓
EXPLAIN ANALYZE 定位 SQL 成本
      ↓
LIKE '%keyword%' → FULLTEXT ngram
      ↓
减少大范围 JOIN / GROUP BY
      ↓
评分和评价数冗余
      ↓
定位精确 COUNT(*) 瓶颈
      ↓
total 分页 → hasNext 分页
      ↓
并发梯度测试寻找系统饱和区域
```

主要结论：

1. 对中文包含式搜索，普通 B+Tree 索引无法有效解决 `%keyword%` 查询，FULLTEXT ngram 更适合当前场景。
2. 分页中的精确 `COUNT(*)` 可能比查询当前页数据本身更昂贵。
3. 对高频读取、低频更新的聚合数据，可通过冗余字段降低实时计算成本。
4. 数据库连接池不是越大越好，超过数据库实际处理能力后，增加并发主要表现为等待时间增加而非吞吐提升。
5. 性能优化应通过执行计划和控制变量压测验证，而非仅凭经验调整参数。

---

## 多级缓存与容错设计

### 餐厅详情多级缓存

- **L1 Caffeine**：本地缓存，`maximumSize=1000`、`expireAfterWrite=30s`，单实例命中极快。
- **L2 Redis**：分布式缓存，TTL 30~40 分钟随机抖动，防止缓存雪崩。
- 未命中时由 `RestaurantLocalCacheService.getOrLoad` 统一回源：L1 → L2 → DB，并逐级回填。
- 空值缓存：DB 中不存在的餐厅写入 `__NULL__` 占位（5 分钟），防缓存穿透。

### 搜索热点缓存

- 热点搜索采用 Redis Cache Aside，key 由 `keyword:sort:page:pageSize` 组合，TTL 2 分钟。
- 20 并发、1000 请求、重复热点查询场景下：平均响应 213ms → 5ms，P95 426ms → 10ms，吞吐 66.8 → 197.6 req/s。

### Redis 故障降级与熔断

- Redis 读取/写入经 `RestaurantCacheService` 包裹 Resilience4j 熔断器 `redisCache`（10 次窗口、50% 失败率、开 10s）。
- 故障时降级回源 MySQL，保证查询可用。
- 故障注入发现每个请求仍会等待 Redis 命令超时，故将 `timeout` 调低至 300ms，
  并在一次请求中读缓存失败即跳过后续缓存写入，把故障请求从约 6s 降到约 400ms。
- 进一步增加短时间依赖熔断：冷却窗口内直接跳过 Redis，周期性探测恢复，避免长时间不可用时每请求重复等待超时。

### DB 回源隔离

- 餐厅详情 DB 回源走 Resilience4j Bulkhead `restaurantDb`（`max-concurrent-calls=20`），
  防止缓存击穿时并发回源打垮数据库，超限直接返回“系统繁忙”。

### 缓存一致性（Outbox + 广播）

商家修改餐厅信息时：

1. 同事务写 `restaurant` 表 + `outbox_event`（`RESTAURANT_CACHE_EVICT`）；
2. 发本地 `RestaurantChangedEvent`，事务提交后失效本实例 L1；
3. `OutboxPublisherService` 定时抢占 outbox 事件，投递到 RabbitMQ `restaurant.cache.evict` 队列；
4. 所有实例的 `RestaurantCacheEvictConsumer` 消费消息，失效各自 L1 + L2，保证多实例一致。

---

## 订单超时自动取消

采用 RabbitMQ 延迟队列 + 定时兜底双重保障：

1. 创建订单时同事务写 `outbox_event`（`ORDER_TIMEOUT_CHECK`）；
2. Outbox 投递到 `order.timeout.delay` 队列（TTL 30 分钟）；
3. 30 分钟后消息死信到 `order.timeout.exchange` → `order.timeout.check` 业务队列；
4. `OrderTimeoutConsumer` 消费，调用 `timeoutCancelOrder`：`待支付 → 已取消` 并恢复库存；
5. `OrderTimeoutRecoveryService` 每 60s 扫描超时未支付订单兜底，避免 MQ 异常时漏取消。

幂等：`timeoutCancelOrder` 通过 `WHERE status = 待支付` 的条件更新保证只有一个线程真正取消并恢复库存。

---

## 测试

单元测试基于 JUnit 5 + Mockito，覆盖核心服务逻辑：

```bash
./mvnw test
```

测试类：

- `OrderServiceTest` —— 订单创建、扣库存、取消、超时取消
- `RestaurantServiceTest` —— 餐厅详情缓存、搜索、降级
- `ReviewServiceTest` —— 评价创建、统计更新、重复评价

---

## 部署

### 服务器部署

应用已部署至 `dodo@101.200.221.93`：

- 应用端口：`8080`
- Swagger：`http://101.200.221.93:8080/swagger-ui.html`
- 健康检查：`http://101.200.221.93:8080/actuator/health`

### Docker 镜像

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/dianping-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

构建：

```bash
./mvnw clean package -DskipTests
docker build -t dodo-food .
```

### 压测数据生成

- `scripts/generate_restaurant_data.py` —— 生成约 10 万条餐厅测试数据
- `scripts/performance_indexes.sql` —— 性能优化相关索引
- `scripts/restaurant_test_data.sql` —— 种子数据
- `scripts/search_keywords.csv` —— 压测关键词集

---

## License

仅用于学习交流。
