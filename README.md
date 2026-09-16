# Dodo Food

一个基于 Spring Boot 的食客点评与商家订单管理系统。

项目包含用户注册登录、餐厅与菜品查询、购物车、订单状态流转、商家管理、用户评价、Redis 缓存、JWT 鉴权、Swagger 文档、JUnit 单元测试以及 Docker 部署。

---

## 技术栈

- Java 21
- Spring Boot 3.1.5
- Spring MVC
- Spring Security
- MyBatis
- MySQL 8
- Redis 7
- JWT
- BCrypt
- Spring Validation
- Springdoc OpenAPI / Swagger
- JUnit 5
- Mockito
- Docker
- Docker Compose
- Maven

---

## 项目功能

### 用户模块

- 用户注册
- 用户登录
- BCrypt 密码加密
- JWT 身份认证
- 获取当前用户信息
- USER / MERCHANT 角色区分

### 餐厅与菜品

- 查询营业餐厅
- 查询餐厅详情
- 查询餐厅菜品
- 餐厅关键词搜索
- 按评分、评价数量、最新时间排序
- 商家修改餐厅信息
- 商家修改营业状态
- 商家菜品新增、修改、上下架
- 菜品逻辑删除
- 商家菜品分页查询

### 购物车

- 添加菜品
- 修改数量
- 删除购物车项
- 查询购物车
- 同一菜品自动合并数量
- 库存校验
- 单餐厅购物车限制

### 订单

- 从购物车创建订单
- 创建订单时扣减库存
- 订单明细保存菜品名称和价格快照
- 用户订单分页查询
- 用户订单详情查询
- 用户支付订单
- 用户取消订单
- 商家订单分页查询
- 商家订单详情查询
- 商家订单状态流转

订单状态：

```text
待支付
  ↓
已支付
  ↓
制作中
  ↓
配送中
  ↓
已完成

待支付
  ↓
已取消

搜索分页中的精确 COUNT(*) 是当前接口的重要性能瓶颈。移除 COUNT 后，平均响应时间约降低 52%，吞吐量从 26.5 req/s 提升到 49.5 req/s。

以下是需要写入 `README.md` 的完整内容（原样提取，未做任何修改）：

````markdown
## 搜索接口性能优化与压测

项目使用约 10 万条餐厅数据对搜索接口进行压力测试，并通过 JMeter、
MySQL `EXPLAIN ANALYZE` 对接口瓶颈进行定位和优化。

### 1. 初始问题

餐厅搜索最初采用模糊匹配：

```sql
WHERE name LIKE '%keyword%'
```

并在搜索过程中关联评价表，通过 `AVG()`、`COUNT()` 实时计算餐厅评分和评价数量。

在约 100,000 条餐厅数据下，搜索“烧烤”约命中 12,351 条记录。

初始执行计划显示：

* `LIKE '%keyword%'` 难以有效利用普通 B+Tree 索引；
* 搜索需要扫描约 100,000 条餐厅记录；
* `LEFT JOIN review + GROUP BY` 增加了聚合成本；
* 排序后才能执行 `LIMIT`；
* 单次复杂查询约需要 263 ms。

### 2. 使用 FULLTEXT 优化中文模糊搜索

为餐厅名称建立 MySQL FULLTEXT 全文索引，并使用 ngram parser 支持中文搜索：

```sql
FULLTEXT INDEX ft_idx_name(name) WITH PARSER ngram
```

查询由：

```sql
name LIKE '%烧烤%'
```

调整为：

```sql
MATCH(name) AGAINST('烧烤' IN NATURAL LANGUAGE MODE)
```

使搜索从全表扫描转为全文索引检索。

优化后的执行计划中，MySQL 通过 `ft_idx_name` 获取约 12,351 个匹配结果，
避免直接扫描全部约 100,000 条餐厅记录。

### 3. 优化评分聚合

原搜索 SQL 会先关联 `review` 表，再对大量候选餐厅执行：

```sql
AVG(rating)
COUNT(review.id)
GROUP BY restaurant_id
```

对于 `latest` 排序，将查询调整为先完成餐厅搜索和分页，再处理当前页需要的数据，
避免先对全部匹配餐厅进行评价聚合。

进一步在 `restaurant` 表中增加冗余统计字段：

```text
average_rating
review_count
```

新增评价时在事务中同步更新餐厅统计信息：

```text
INSERT review
        +
UPDATE restaurant.average_rating / review_count
```

从而使搜索接口可以直接读取餐厅评分和评价数量，而不需要每次搜索实时扫描评价表聚合。

当前测试库的评价数据量较小，因此该优化在现阶段压测中的性能收益不明显，
主要用于避免评价数据规模扩大后实时聚合成为新的查询瓶颈。

### 4. 定位分页 COUNT(*) 瓶颈

分页接口最初除了查询当前页数据，还会执行：

```sql
SELECT COUNT(*)
FROM restaurant
WHERE status = 1
  AND MATCH(name) AGAINST('烧烤' IN BOOLEAN MODE);
```

`EXPLAIN ANALYZE` 显示：

```text
Full-text index search:
actual rows = 12,351

COUNT 查询总耗时：
约 167 ms
```

虽然使用了全文索引，但为了得到精确的 `total`，
MySQL 仍然必须遍历全部 12,351 个匹配结果。

使用相同 JMeter 参数进行 A/B 测试：

* 50 并发
* 每线程循环 20 次
* Ramp-up 10 秒
* 总计 1000 次请求

结果：

| 指标         | 保留 COUNT(*) | 去除 COUNT(*) |
| ---------- | ----------: | ----------: |
| Average    |     1647 ms |      795 ms |
| Median     |     1658 ms |      793 ms |
| P95        |     2453 ms |     1311 ms |
| P99        |     3065 ms |     1538 ms |
| Throughput |  26.5 req/s |  49.5 req/s |
| Error Rate |          0% |          0% |

去除精确 `COUNT(*)` 后：

* 平均响应时间降低约 52%；
* 吞吐量提高约 87%。

说明精确统计搜索结果总数是高并发搜索接口的重要瓶颈之一。

### 5. 将 total 分页改为 hasNext

搜索场景不强依赖精确总记录数，因此将分页方式由：

```text
records + total
```

调整为：

```text
records + hasNext
```

当客户端请求：

```text
pageSize = 10
```

数据库实际查询：

```text
LIMIT 11
```

如果返回 11 条，则：

```text
hasNext = true
```

并只向客户端返回前 10 条；如果返回不超过 10 条，则：

```text
hasNext = false
```

这样可以避免每次搜索执行昂贵的精确 `COUNT(*)`。

在 20 并发、1000 次请求的测试中，hasNext 版本得到：

| 指标         |         结果 |
| ---------- | ---------: |
| Average    |     213 ms |
| Median     |     155 ms |
| P90        |     415 ms |
| P95        |     426 ms |
| P99        |     445 ms |
| Error Rate |         0% |
| Throughput | 66.8 req/s |

### 6. 主查询执行计划

优化后的核心搜索 SQL：

```sql
SELECT
    r.id,
    r.name,
    r.description,
    r.address,
    r.average_rating,
    r.review_count
FROM restaurant r
WHERE r.status = 1
  AND MATCH(r.name)
      AGAINST('烧烤' IN NATURAL LANGUAGE MODE)
ORDER BY r.created_at DESC, r.id DESC
LIMIT 10;
```

`EXPLAIN ANALYZE` 显示单次查询约为：

```text
Full-text index search:
约 55.6 ms
↓
过滤后 12,351 条匹配记录:
约 61.7 ms
↓
排序 + LIMIT 10:
约 78.8 ms
```

说明在移除 COUNT 和评价实时聚合后，主要成本已经集中在高频关键词命中的
大量 FULLTEXT 候选记录及其排序。

### 7. 并发饱和点测试

进一步使用 JMeter 对不同并发数进行梯度测试：

| 并发数 | Average |     P95 | Throughput |
| --: | ------: | ------: | ---------: |
|   1 |  103 ms |  130 ms |  9.7 req/s |
|   5 |   78 ms |  124 ms | 41.9 req/s |
|  10 |  159 ms |  216 ms | 49.0 req/s |
|  20 |  269 ms |  430 ms | 57.7 req/s |
|  50 |  683 ms | 1107 ms | 52.0 req/s |
| 100 | 1439 ms | 2004 ms | 48.8 req/s |

当前 HikariCP 配置：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 30000
```

测试发现吞吐量在约 20 并发附近达到较高水平。
继续增加到 50、100 并发后，TPS 不再增加，反而略有下降，
而平均响应时间和 P95 快速上升。

这说明连接池大小并不等同于系统最佳并发数。
系统吞吐同时受到 MySQL FULLTEXT 查询、CPU、连接竞争等因素影响，
单纯增加数据库连接数无法线性提高吞吐。

### 8. 优化总结

本轮优化没有直接通过增加数据库连接数解决问题，而是按照以下过程逐步定位：

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

1. 对中文包含式搜索，普通 B+Tree 索引无法有效解决 `%keyword%` 查询问题，
   FULLTEXT ngram 更适合当前搜索场景。
2. 分页中的精确 `COUNT(*)` 可能比查询当前页数据本身更加昂贵。
3. 对高频读取、低频更新的聚合数据，可以通过冗余字段降低实时计算成本。
4. 数据库连接池不是越大越好，超过数据库实际处理能力后，
   增加并发主要表现为等待时间增加，而不是吞吐量提升。
5. 性能优化应通过执行计划和控制变量压测验证，而不是仅根据经验调整参数。
````

热点搜索采用 Redis Cache Aside，key 由 keyword、sort、page、pageSize 组合，TTL 2 分钟；在 20 并发、1000 请求、重复热点查询场景下，平均响应由 213ms 降至 5ms，P95 由 426ms 降至 10ms，吞吐由 66.8 req/s 提升至 197.6 req/s。

Redis 故障时我先实现了缓存降级，保证查询可以回源 MySQL。之后通过故障注入发现每个请求仍会等待 Redis 命令超时，所以把 Redis timeout 调低，
并在一次请求中如果读缓存失败就跳过后续缓存写入，把故障请求从约 6 秒降到约 400ms。进一步为了避免 Redis 长时间不可用时每个请求都重复等待超时，
我增加了短时间依赖熔断，在冷却窗口内直接跳过 Redis，再周期性探测恢复。