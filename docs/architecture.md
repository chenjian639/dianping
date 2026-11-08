# 架构与事务设计

## 系统结构

```text
同源页面 / HTTP客户端
        ↓
Nginx → Spring Security JWT → Controller → Service → MyBatis → MySQL
                                            ├→ Redis：商家展示缓存
                                            └→ Outbox表 → 定时发布器 → RabbitMQ
                                                                          ├→ 超时订单检查
                                                                          └→ Redis缓存删除
```

这是单模块单体，可部署多个实例共享MySQL、Redis、RabbitMQ。无需跨服务事务。业务类位于 `src/main/java/com/dianping/`。

| 模块 | 主要实现 | 职责 |
|---|---|---|
| 身份与授权 | security/、UserService、JwtUtil | BCrypt密码、JWT解析、角色和资源归属检查 |
| 商品 | ProductService、ProductMapper | 商品资料、上下架、软删除；资料编辑不覆盖库存 |
| 购物车 | CartService | 单商家购物车，与下单共用用户行锁 |
| 订单 | OrderService、OrderMapper | 计价快照、条件扣库存、取消、超时、发货收货 |
| 支付退款 | PaymentService、PaymentMapper | 支付单、回调金额校验、发货前整单退款 |
| 库存 | InventoryService、InventoryMapper | 幂等增减、库存变更流水 |
| 消息 | OutboxEventService、OutboxPublisherService、mq/ | 事务事件、发布确认、重试与消费者 |
| 展示缓存 | RestaurantService、RestaurantCacheService、RestaurantDatabaseService | Redis cache-aside、空值缓存、回源隔离 |
| 评价 | ReviewService | 已完成订单评价和商家评分统计，保留兼容功能 |

## 数据模型

| 表 | 关键关系和约束 |
|---|---|
| user | 用户名、手机号唯一；商家账号通过restaurant_id绑定商家 |
| restaurant | 商家资料、营业状态、评分统计 |
| dish | 当前商品；历史表名保留，Java实体已为Product |
| cart_item | (user_id,dish_id)唯一；商品数量 |
| orders | (user_id,request_id)唯一；状态、地址、goods_amount、freight_amount、total_amount、expires_at |
| order_item | 商品名称、单价、数量的订单快照 |
| payment | order_id唯一、channel_reference唯一；一个订单一个模拟支付单 |
| refund | order_id唯一；一个订单最多一次整单退款 |
| inventory_movement | (product_id,reason,business_key)唯一；期初、下单、取消、退款、人工调整 |
| outbox_event | event_id唯一；PENDING/PROCESSING/SENT/FAILED |
| review | order_id唯一；已完成订单评价 |

一个商家有多个商品和订单；订单有多条明细、至多一个支付单和退款单。pickup_slot、canteen等旧表仅保留迁移历史，不再提供运行时接口。

## 订单与支付状态

```text
0待支付 → 1已支付 → 3已发货 → 4已完成
   └→ 5已取消         
          1已支付 → 6已退款
```

2是旧制作中状态，新业务不产生。支付单状态为PENDING、SUCCEEDED、REFUNDED；退款当前同步成功，不存在真实渠道处理中状态。

### 下单事务

1. 校验requestId和地址，锁用户行；所有购物车写操作遵守同一把锁。
2. 按用户和幂等键查原订单。同键同地址返回原订单，同键不同地址拒绝；不会重新购买当前购物车。
3. 读取购物车并按商品ID升序锁商品。在锁内读取价格、校验上架、单商家等条件。
4. BigDecimal计算商品金额，运费固定0，保存地址和金额快照；金额不接受客户端指定。
5. 通过 `stock >= quantity AND status=1 AND deleted=0` 条件更新扣库存，写明细和流水。
6. 清购物车、写超时Outbox事件，一起提交。任何一步失败全部回滚。

商品价格最多两位小数，订单金额不能超过DECIMAL(10,2)范围。地址为文字快照，没有地址簿。价格以锁定商品后读取值为准。

### 支付、取消、退款与发货

- 支付回调先定位订单，再锁订单、当前读锁支付单。金额与渠道流水匹配的重复回调不重复支付。
- 锁内支付查询使用 `FOR UPDATE`，避免等待订单锁前建立的可重复读快照继续返回PENDING。
- 支付更新检查待支付状态和 `expires_at > NOW()`；超时更新检查 `expires_at <= NOW()`，统一以数据库时间裁决。
- 取消/超时成功切换订单状态后才恢复库存。恢复失败回滚状态，因此可以重试；重复超时属于正常无操作。
- 发货和退款竞争订单锁。发货只允许1→3；退款只允许1→6，并在同一事务更新支付、写退款单、恢复库存和写流水。
- 买家只能确认自己的已发货订单收货。已收货重复确认成功返回；重复发货/取消会返回业务错误。

锁顺序：下单为用户→商品ID升序；退款为订单→支付→商品ID升序；取消为订单→商品ID升序；人工调整只锁商品。库存流水唯一键是审计与去重约束，不能替代订单状态竞争。

## 缓存和消息边界

- 无本地缓存。商家详情Redis TTL随机30～40分钟，空值5分钟；搜索缓存2分钟。商品列表直接查询数据库，交易不信任缓存。
- Redis故障时详情受Bulkhead保护回源；搜索查询不经过该详情Bulkhead，不能宣称全站回源均限流。
- 商家变更在事务内写Outbox，消费者删除Redis，失败经重试/死信处理。读后回填仍可能写入旧值，属于最终一致性，不保证严格实时。
- Outbox每批抢占事件，等待publisher confirm并检查return；成功标记SENT，失败重试，达到上限标记FAILED，恢复超时PROCESSING。
- 确认后、标记SENT前宕机会重复投递，因此消费者依靠订单状态条件更新或重复删除实现幂等。不是“消息只发一次”。
- 30分钟延迟队列触发超时检查，每60秒扫描到期订单兜底。库存释放可能延后；截止之后支付仍由SQL拒绝。
- SchedulingConfig分离投递线程与恢复线程，防止MQ确认等待阻塞关单扫描。FAILED事件仍需排查，不承诺永久自动重试。

## 鉴权与模拟支付边界

Spring Security无状态JWT，USER/MERCHANT/ADMIN角色；资源归属由服务/SQL继续校验。JWT有效期24小时，不具备Campus Share的Redis会话版本与即时撤销能力。
模拟回调位于ADMIN路径，普通买家不能直接指定金额支付。没有渠道签名、外部扣款、真实退款或资金对账。接真实支付需增加签名验证、渠道查询、退款处理中状态和晚到成功付款的补偿；本地事务不能包住外部资金操作。

## 运行、配置与迁移

- 本地/CI：`compose.campus.yml`，两实例18081/18082，Nginx入口18080；运行和测试见[测试说明](testing.md)。
- 服务器：`compose.server.yml`；`scripts/init_server_env.py`初始化环境，`scripts/deploy_server.sh`由CI调用负责备份及发布，`scripts/audit_server.sh`用于只读审计。保留这些活跃脚本，不在文档清理时破坏发布链路。
- 主要环境变量：SPRING_DATASOURCE_URL/USERNAME/PASSWORD、SPRING_DATA_REDIS_HOST/PORT、RABBITMQ_HOST/USERNAME/PASSWORD、JWT_SECRET。服务器具体映射以Compose为准。
- 配置源：`src/main/resources/application.yml`；迁移唯一来源：`src/main/resources/db/migration/`。
- V1基础表，V2历史预约字段，V3评分统计，V4历史位置表，V5交易快照、支付、退款、库存流水。不得改写已执行的V1～V4。
- 升级前备份，并在旧版结清 `SELECT id,status FROM orders WHERE status NOT IN (4,5)` 查出的历史活动订单。V5约束拒绝未结清数据，不能把旧待取餐状态3解释为商城已发货。
- MySQL DDL不整体回滚；失败后核对实际结构和Flyway记录，必要时恢复备份，不盲目重跑。历史订单不虚构支付记录、不支持新退款流程。
