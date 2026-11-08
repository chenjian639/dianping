# 商品交易与订单管理平台

Java 21、Spring Boot 3、MyBatis、MySQL、Redis、RabbitMQ。单商家订单、服务端计价、模拟支付、发货收货、发货前整单退款与库存流水。当前免运费，没有真实资金操作。

## 文档入口

- [架构与事务设计](docs/architecture.md)：模块职责、表关系、锁顺序、状态机、缓存/MQ边界、迁移与运行配置。
- [API说明](docs/api.md)：权限、接口清单、参数、交易请求示例及返回语义。
- [面试问题与回答](interview-notes/项目面试手册.md)：围绕当前代码的设计取舍、并发问题和能力边界。
- [测试说明](docs/testing.md)：保留测试、运行命令、覆盖范围与尚未覆盖的场景。

## 本地运行

```powershell
mvn -B clean verify
docker compose -f compose.campus.yml up -d --build
python scripts/smoke_trade.py
```

需要 JDK 21、Maven、Docker 和 Python 3。页面 [http://localhost:18080](http://localhost:18080)，接口调试 [Swagger UI](http://localhost:18080/swagger-ui.html)。
Compose文件名和历史SQL表名为兼容已有环境保留，业务已改为交易系统。公开注册只创建普通用户，商家/管理员角色由运维分配。

已有数据库升级前必须备份并结清旧活动订单，详见架构文档。数据库结构唯一来源是 `src/main/resources/db/migration/`，不要跳过或删除历史迁移。
