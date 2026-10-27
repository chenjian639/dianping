# 本地验证记录

## 2026-10-26：架构调整

- 全部19项单元/过滤链测试与16项真实MySQL集成测试通过；含调度隔离、角色认证、HTTP错误码、Outbox真实插入失败回滚、提交后缓存失效、评分精度、购物车校验与V3历史回填。
- 原有订单并发/库存/幂等/核销回归保留；测试失败不会被跳过。
- 双实例冒烟增加HTTP状态断言、订单列表VO字段检查，以及餐厅信息/评价/营业状态三个更新入口的跨实例缓存通知，已通过本地验证。
- 对外错误现在返回相应HTTP状态而非统一200，JSON结构不变。SecurityConfig是角色授权入口；不再使用LoginInterceptor/AdminOnly。
- V1/V2未改，新增V3；以评价表回填精确总分、数量与平均分。测试不连接生产数据库，故障注入使用临时CHECK约束，无需SUPER权限。
- 仍保留Caffeine/Redis教学验证链路；并发回填旧值与断线通知丢失仍是已知最终一致性边界，没有声称强一致。
- 本次未提交、未推送或部署服务器；以下条目是先前版本的历史验证。


## 2026-10-26：CI 冷启动并发 JWT 修复

CI 40260329229 在首次跨实例并发下单时返回 401，部署 job 被跳过，服务器没有通过该次工作流更新。原拦截器捕获所有 Exception，导致内部错误被误报为 token 无效。

使用旧版 JJWT 0.12.3 的临时诊断测试，反复清空服务发现缓存并并发验签，复现 `NoSuchElementException → ServiceLoader → Services.loadFirst → DefaultJwtParserBuilder.build → JwtUtil.parseToken`。对应上游 [JJWT #873](https://github.com/jwtk/jjwt/issues/873)，官方在 [0.12.4](https://github.com/jwtk/jjwt/releases/tag/0.12.4) 修复该线程安全问题。临时诊断代码依赖内部 API，复现后已移除。

本次升级为 0.12.6，解析器在组件创建时初始化并复用；拦截器仅捕获凭证相关异常，内部错误不再变成 401。不改密钥、不重试鉴权失败、不放宽验签。

新增回归验证32线程共3200次用户验签，以及3200次商家签发/跨实例验签；错误密钥、篡改、过期token仍被拒绝。冒烟保留app2首次鉴权并发场景，错误包含目标实例，只有明确的400时段已满才作为预期拒绝。

使用CI默认测试密钥重建本地双实例后，完整业务冒烟通过；mvn -B verify 通过16项单元测试和11项真实MySQL集成测试，无失败、无跳过。远端CI/CD结果另行检查，不能把本地通过等同于服务器发布成功。

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


## 2026-10-26 配置精简验证

- 删除旧默认Compose和独立SSH检查工作流；两套现用Compose配置解析通过。
- YAML解析及全部工作流shell、deploy_server.sh的bash -n通过。
- Dockerfile默认源码构建、--target runtime构建均通过；两种镜像内均存在/app/app.jar和Java。
- 本地网关容器内部wget请求readiness返回UP，验证探针不依赖宿主发布端口。
- 未修改Java业务代码，本次未重新运行JUnit；没有连接服务器或触发远端发布。
- 当前配置入口以configuration-map.md和README为准，上面的2026-10-25部署说明是历史记录。
