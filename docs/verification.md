# 本地验证记录

日期：2026-10-25（Asia/Shanghai）。环境：Windows、Java 21、Docker Desktop / Engine 29.1.2。

## 自动化验证

| 命令/检查 | 结果 |
| --- | --- |
| `mvn -B verify` | 13 项单元测试、11 项真实 MySQL 集成测试通过，无跳过 |
| `docker compose -f compose.campus.yml config --quiet` | 配置解析通过 |
| 双实例镜像构建与启动 | app1/app2 + MySQL + Redis + RabbitMQ + Nginx 正常启动 |
| `python scripts/smoke_campus.py` | 端到端通过，覆盖下表场景 |
| GitHub Actions YAML | 本地解析通过；尚未在 GitHub 触发 |

集成测试包括：100 个请求竞争 20 个名额、最后一份库存、失败订单回滚、同请求跨线程幂等、不同请求购买同一购物车、休息档口/跨档口/过期时段拒绝、超时与取消竞争、支付与取消竞争、核销防重复、名额释放失败回滚、旧库 baseline 后保留订单历史。

## 双实例端到端场景

- 两个实例健康探针与 Swagger OpenAPI 正常，旧无凭码完成接口不存在。
- 31 位学生并发跨两个实例争 20 个名额，成功 20 单，名额占用 20，库存扣减 20。
- 已清空购物车后，在另一个实例用原 requestId 提交，仍返回原订单。
- 非本人不能读取订单；商家订单详情不返回学生取餐码。
- 模拟支付 → 制作 → 待取餐 → 凭码核销；重复核销被拒绝。
- 另一订单取消后名额和库存各恢复 1；完成订单保持产能占用。
- 普通用户调用管理员重放接口得到 HTTP 403。
- 预热两个实例详情缓存后修改档口，10 秒内两个实例均得到新名称（低于 L1 30 秒 TTL，验证了广播链路）。
- Nginx 入口可用并返回上游标识。

这些验证用于证明特定场景下的业务正确性，不是性能报告，不代表某个 QPS、跨机器容灾能力或严格一致性保证。支付仍为模拟，故障注入和独立压测见 TODO。

## 兼容性修正

旧 Spring Boot BOM 管理的 Testcontainers 1.18.x 无法连接本机 Docker 29，已覆盖为 1.21.4；依照 [Testcontainers 官方发布记录](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4)。Flyway 补齐 MySQL 数据库模块，Resilience4j 注解补齐 Spring AOP。

CI 使用相同测试与 Compose 冒烟命令。CD 尚未在远端执行，生产的 systemd、SSH、数据库备份与回滚需按 README 配置后手动触发。本次没有连接或更改原云端服务。
