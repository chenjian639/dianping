# 测试说明

## 保留的测试

| 文件 | 验证目标 |
|---|---|
| src/test/java/com/dianping/service/TradeOrderIT.java | 真实MySQL并发、支付退款和事务回滚 |
| src/test/java/com/dianping/security/SecurityIntegrationTest.java | JWT与角色授权、参数与错误HTTP语义 |
| src/test/java/com/dianping/util/JwtUtilTest.java | JWT解析、并发使用等当前鉴权逻辑 |
| src/test/java/com/dianping/service/RestaurantServiceTest.java | 当前Redis命中、回源与异常降级 |
| src/test/java/com/dianping/service/ReviewServiceTest.java | 当前保留的订单评价逻辑 |
| src/test/java/com/dianping/config/SchedulingConfigTest.java | MQ投递阻塞时不阻塞订单恢复线程 |
| scripts/smoke_trade.py | 双实例HTTP下单、回调权限、退款库存、发货收货闭环 |

旧预约/GEO/本地缓存广播测试已退出；旧校园压测及其伪HTTP服务测试已删除。保留的测试不因文件名仍含Restaurant或Review就被视为过期。

## 运行

```powershell
mvn -B test
mvn -B clean verify
```

第一条不要求Docker；第二条由Failsafe执行TradeOrderIT，使用隔离MySQL 8 Testcontainers，需要运行中的Docker，失败不跳过。测试触发器仅在隔离MySQL中用于注入故障。报告位于target/surefire-reports、target/failsafe-reports。

```powershell
docker compose -f compose.campus.yml up -d --build
python scripts/smoke_trade.py
docker compose -f compose.campus.yml down
```

冒烟脚本创建自己的随机测试账号与商品，通过容器SQL分配角色，仅用于本地隔离环境，不用于生产数据库。停止时默认保留数据卷。CI `.github/workflows/campus.yml`执行verify、构建、双实例冒烟和报告上传；部署脚本仍被CI使用。

静态检查：`node --check src/main/resources/static/ui/app.js`、`git diff --check`。

## 交易集成测试断言

| 方法 | 场景与关键断言 |
|---|---|
| concurrentStockCannotOversell | 40请求抢20份库存，只生成20单、库存为0、20条扣减流水 |
| sameKeyAndCartAreIdempotent | 并发同键只生成一单；地址冲突拒绝；新键不能重买已清空购物车 |
| callbackChecksAmountAndDeduplicates | 错金额不付款；并发相同回调仅一次生效；不同流水拒绝 |
| paymentAndCancellationHaveOneWinner | 支付/取消竞争后，订单、支付状态与库存相互一致 |
| expiredOrderCannotPayAndReleasesOnce | 截止后拒付；并发超时只恢复一次库存 |
| refundIsFullAndIdempotent | 并发退款只有一条退款记录，库存恢复；旧回调不复活订单 |
| shipAndRefundHaveOneWinner | 发货与退款只能有一种结果，库存与结果一致 |
| fulfillmentChecksOwnership | 拒绝其他商家发货/其他用户收货；发货后拒绝退款 |
| movementFailureRollsBackEntireRefund | 流水写入失败，支付、退款、订单及库存一起回滚 |
| outboxFailureRollsBackOrderStockAndCart | 事件写入失败，订单不落库，库存与购物车不变 |
| adjustmentIsOwnedIdempotentAndCannotGoNegative | 增补库存幂等、不同数量同键拒绝、越权和负库存拒绝 |

## 验证记录与边界

2026-11-07交易改造验证：14项单元测试、11项真实MySQL集成测试全部通过，无跳过；双实例HTTP冒烟通过。此为当时运行记录，不代表后续修改自动通过。

已有并发测试用于检查正确性，不是QPS/P95压测结果；没有可用于简历的吞吐量结论。未完成真实支付渠道、跨机器故障隔离、页面视觉验收和完整生产故障演练。新增测试应优先覆盖多商品扣减中途失败、并发改价、渠道流水跨订单冲突、历史迁移失败恢复等边界，不能把这些写成已经验证。


## 本地功能演示

启动现有Compose环境后，首次执行 `python scripts/prepare_demo.py --environment local --base http://127.0.0.1:18080 --hide-smoke` 创建三家店铺、12道菜品与5个专用账号。账号保存在 `.sandbox-tmp/demo/accounts.md`（Git忽略），已有该文件时不要反复初始化。店铺与价格为演示设定，不代表真实经营信息。

买家浏览商品、加购物车、填写地址下单，在订单详情创建支付单；管理员登录自动进入工作台，刷新待付款、选择并确认；对应商家在商家订单发货，买家确认收货。退款用另一张已付款未发货订单展示。商家工作台还支持编辑、上下架、库存调整与流水。

账号和数据仅初始化在所选环境；本地数据不会因推送代码自动同步到服务器。服务入口为 http://localhost:18080 。
