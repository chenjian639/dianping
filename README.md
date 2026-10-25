# 校园预约取餐系统 · Campus Pickup

基于 Java 21 / Spring Boot 3 / MyBatis / MySQL / Redis / RabbitMQ 的后端练习项目。从外卖订单系统改造为校园档口分时预约：每单占一个时段制作名额，下单同时占用菜品库存，取消或超时同时释放，备餐完成后凭取餐码核销。

当前是后端 API 项目，支付为模拟支付，没有学生端页面、真实支付、退款或配送。包名与 Maven 坐标保留 `com.dianping:dianping`，避免无业务价值的全量重命名。

## 业务规则

- 档口创建未来取餐时段，容量为订单数，不是菜品份数。一个订单只能买同一档口的菜品。
- 下单必须提供 `requestId`（1～64 字符）和 `pickupSlotId`。幂等范围为用户；同一键重复提交返回同一订单，不同取餐时段复用同一键会被拒绝。
- 同一用户修改购物车、下单共用用户行锁。下单在同一事务内占名额、条件扣库存、保存快照、清购物车和写 Outbox；失败全部回滚。
- 名额通过 `reserved < capacity` 条件更新占用，菜品通过 `stock >= quantity` 条件更新扣减。时段和菜品不使用 Redis 作为库存真相。
- 支付截止为数据库时间计算的“创建后 30 分钟”和“取餐时段开始”中的较早时间；截止后拒绝支付。
- 取消仅允许待支付订单；取消与超时处理先竞争订单状态，再释放名额、恢复库存。重复超时消息不重复释放。
- 完成取餐不释放名额：制作能力已经被消耗。已支付订单暂不支持取消退款。
- 商家只能操作自己档口的订单，待取餐时校验 32 位随机取餐码，原子更新为已完成。取餐码视为凭证，请勿公开截图或日志。
- 时段按 Asia/Shanghai 本地时间传输（无时区 ISO 日期时间）。单体 JVM 与 MySQL 部署时统一时区。

```text
待支付(0) → 已支付(1) → 制作中(2) → 待取餐(3) → 已完成(4)
    └──用户取消 / 支付截止──→ 已取消(5)，释放库存与名额
```

## 本地运行与双实例

需要 JDK 21、Maven 3.9+、Docker Compose、Python 3。推荐使用独立的校园测试环境，不影响旧 Compose 数据卷：

```powershell
docker compose -f compose.campus.yml up -d --build
python scripts/smoke_campus.py
```

- Nginx 入口：[http://localhost:18080/swagger-ui.html](http://localhost:18080/swagger-ui.html)
- 实例一：`http://127.0.0.1:18081`；实例二：`http://127.0.0.1:18082`。
- Compose 不向宿主机暴露 MySQL、Redis、RabbitMQ。应用端口只绑定回环地址。
- 校园环境的新库由 Flyway 自动建表；首次没有业务演示数据。冒烟脚本创建独立学生、商家、档口、菜品并进行双实例测试，可重复运行，每次保留本次测试数据。
- 冒烟脚本通过容器数据库为测试商家分配角色，这只是隔离测试夹具；公开注册接口不允许自选商家/管理员身份。
- `compose.campus.yml` 中的默认凭据仅用于本地。已有 `.env` 的同名变量会覆盖默认值。
- 停止保留数据：`docker compose -f compose.campus.yml down`。不要对有价值的数据卷使用 `down -v`。

单实例旧入口仍可用：复制 `.env.example` 到 `.env`、使数据库应用密码与实际账号一致，再运行 `docker compose up -d --build`。该环境会初始化原有种子数据并执行 V2 迁移。当前 Compose 已包含 RabbitMQ。

## 主要 API

除注册、登录、搜索、Swagger 和健康探针外均需 `Authorization: Bearer <token>`。普通业务错误沿用现有 `Result` 协议：检查 JSON `code`，成功为 0；未登录为 HTTP 401，管理员越权为 HTTP 403。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/users/register`、`/users/login` | 注册、登录 |
| POST | `/merchant/pickup-slots` | 商家创建时段：`startsAt`、`endsAt`、`capacity` |
| GET | `/restaurants/{id}/pickup-slots` | 最多 100 个未来未满时段，余量仅供展示，下单时重新校验 |
| POST | `/cart/items` | 加购物车：`dishId`、`quantity` |
| POST | `/orders/create` | 预约下单：`requestId`、`pickupSlotId` |
| GET | `/orders/{id}` | 本人订单详情，包含时段 ID、取餐码和支付截止时间 |
| PUT | `/orders/{id}/pay`、`/orders/{id}/cancel` | 模拟支付、取消 |
| PUT | `/merchant/orders/{id}/prepare` | 开始制作 |
| PUT | `/merchant/orders/{id}/ready` | 标记待取餐 |
| PUT | `/merchant/orders/{id}/pickup` | 核销：`{"pickupCode":"订单中的32位码"}` |
| POST | `/admin/cache-recovery/start`、`/stop` | 仅 ADMIN 启停死信重放 |
| GET | `/actuator/health/liveness`、`/readiness` | 存活 / 依赖就绪检查 |

旧 `/deliver`、`/complete` 接口已移除，避免绕过凭码核销。餐厅、菜品、评价、搜索、商家管理的其他接口见 Swagger。

## 可靠性与一致性边界

- MySQL 是订单、库存和名额的最终依据。订单状态条件更新裁决并发支付、取消、核销。
- 固定锁顺序：同用户购物车锁 → 时段容量 → 菜品 ID 升序；取消先订单状态 → 时段 → 菜品升序。
- Outbox 与业务同事务，定时投递使用 publisher confirm、return 检查、失败重试和 PROCESSING 恢复；属于至少一次投递，消费者必须幂等。达到最大重试的 FAILED 事件需要运营排查，不承诺无限重试。
- 订单 MQ 延迟队列为 30 分钟；更早的时段开始截止由每 60 秒扫描兜底。正常无积压时提前截止订单的释放可能延迟约一分钟；故障/积压时更长。支付 SQL 始终检查实际截止时间。
- 详情缓存：Caffeine L1（30 秒）+ Redis L2（随机 TTL）。修改事务提交后清当前 L1，持久队列补偿删除 Redis，再经 fanout 发送到每实例独立匿名队列清 L1。
- 广播为最终一致性。断线实例可能漏收；并发回填可能产生旧值。L1 TTL 限制单次本地条目的寿命，但不代表端到端旧值一定在 30 秒内消失，L2 回填竞态仍需后续版本化治理。搜索缓存约 2 分钟过期，未实现精确失效。
- Redis 失败降级 DB，回源通过 Bulkhead 隔离；补齐 AOP 依赖使 Resilience4j 注解生效。

## 数据库迁移

唯一版本来源为 `src/main/resources/db/migration/`：V1 为旧基础表，V2 增加预约时段与订单字段。原 `schema.sql`、`docker/mysql/01-init.sql` 保留作旧版参考，不能当成校园完整结构重复执行。

- 空库：Flyway 执行 V1、V2。
- 已有无 Flyway 的旧库：备份并核对结构后，以版本 1 baseline，执行 V2。`baseline-on-migrate` 仅适用于结构确实与旧版一致的数据库，不能用于掩盖未知结构差异。
- 旧订单保留空时段字段，截止时间回填为创建后 30 分钟；旧待支付订单仍可超时取消。旧配送状态 3 与校园待取餐状态 3 的业务含义不同，且旧订单没有取餐码：迁移前应结清旧履约订单，不能直接作为校园订单核销。
- 不改动已执行的版本文件；后续结构调整增加 V3。发版先做可恢复数据库备份；应用回滚不自动回滚数据库。

## 测试与 CI/CD

```powershell
mvn -B test        # 单元测试，不要求 Docker
mvn -B clean verify  # 单元测试 + Testcontainers 真实 MySQL 集成并发测试 + 打包
python scripts/smoke_campus.py # 已启动的校园双实例环境端到端测试
```

集成测试验证：100 请求争 20 名额、最后一份库存、失败回滚、同键并发幂等、不同请求竞争同一购物车、跨档口/过期时段拒绝、重复超时/取消、支付竞争、凭码核销及释放失败回滚。Testcontainers 需要运行中的 Docker，缺失时测试失败，不静默跳过。

GitHub Actions 对 main/master 推送与 PR 执行 `clean verify`、构建运行双实例 Docker、执行 API/缓存广播冒烟，保存测试报告和 JAR。

CD 为 workflow_dispatch 手动触发，仅 master 可部署，需勾选已备份数据库，并配置 production 环境的 `ECS_HOST`、`ECS_USER`、`ECS_SSH_KEY`、`ECS_KNOWN_HOSTS`。已核实主机公钥后填写 known_hosts。沿用旧服务器目录 `/home/dodo/projects/dianping/target` 与 systemd 服务 `dianping`；服务环境须提供 MySQL、Redis、RabbitMQ、JWT 配置，运行 Java 21，允许部署账号重启该服务。部署检查 readiness，失败尝试恢复旧 JAR并重新检查；数据库不自动回滚。首次部署尚无旧 JAR 时需要手工完成服务初始化。

## 是否需要阿里云

当前不必购买云服务器。单机 Docker 中两个独立 JVM 足以验证数据库竞争、跨实例幂等、缓存广播和负载均衡；本项目提供现成环境。

它不能证明跨机器故障隔离或生产吞吐。同机压测会共享 CPU、内存、磁盘与网络，结果只能作为本机基线。需要公网展示时再部署；需要可信性能测试时把负载发生器与被测服务分开，并记录机器规格、数据量、缓存冷热、成功率、P95 和数据库锁等待。没有流量目标和测量结果之前不承诺需要某个服务器规格，也不必为了简历先购买多台机器。

## 计划与历史记录

见 [TODO.md](TODO.md)。旧外卖版本说明与性能记录保存在 [docs/legacy-performance.md](docs/legacy-performance.md)，其中数字仅属于旧版本和原测试条件，不能直接作为校园版本或双实例性能结论。
