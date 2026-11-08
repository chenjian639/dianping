# API说明

## 通用约定

本地入口 `http://localhost:18080`；交互文档 `/swagger-ui.html`，OpenAPI `/v3/api-docs`。请求JSON使用 `Content-Type: application/json`。

除注册、登录、商家搜索、静态页面、接口文档和健康探针外需要 `Authorization: Bearer <token>`。商家接口需MERCHANT角色，管理员接口需ADMIN角色；ADMIN并不自动拥有MERCHANT角色。

响应为 `{"code":0,"msg":"...","data":...}`；成功HTTP 200。错误HTTP状态与code一致：400参数/业务错误、401未登录或凭证无效、403角色无权限、404显式不存在、405方法错误、415媒体类型错误、500内部异常。资源不存在或归属不符的业务接口可能返回400，不能统一假定404。

普通列表常用page=1、pageSize=10；以各接口默认值为准。分页结果records/total，商家搜索使用hasNext且total为空。ID示例仅为说明，应使用接口实际返回值。

## 账号与商家

| 方法 | 路径 | 权限 | 参数/结果 |
|---|---|---|---|
| POST | /users/register | 公开 | username(3～20)、password(8～30)、nickname、phone(11位以1开头)；返回用户信息 |
| POST | /users/login | 公开 | username、password；返回含token的登录信息 |
| GET | /users/me | 登录 | 当前用户 |
| GET | /users/{id} | 本人 | 用户信息，不可查询他人 |
| GET | /restaurants/search | 公开 | keyword、sort=latest/rating/reviews、page、pageSize |
| GET | /restaurants | 登录 | 营业商家列表 |
| GET | /restaurants/{id} | 登录 | 商家详情与评分 |
| GET | /restaurants/{id}/products | 登录 | 上架且未删除商品 |
| PUT | /merchant/restaurant | MERCHANT | name必填≤100；description、address≤255 |
| PUT | /merchant/restaurant/status | MERCHANT | status：0休息、1营业 |

公开注册不能选择角色。商家归属从JWT绑定的restaurantId读取，不接受调用者指定其他商家。

## 商品与库存

| 方法 | 路径 | 参数/语义 |
|---|---|---|
| GET | /merchant/products | page、pageSize、可选status |
| POST | /merchant/products | name、description、price、stock；返回商品ID，默认上架 |
| PUT | /merchant/products/{id} | name、description、price；不修改库存 |
| PUT | /merchant/products/{id}/status | status：0下架、1上架 |
| DELETE | /merchant/products/{id} | 软删除 |
| POST | /merchant/products/{id}/inventory | requestId(1～64)、delta(非零整数)；增减库存 |
| GET | /merchant/products/{id}/inventory | 最近100条库存流水 |

以上均需MERCHANT且商品属于自己。名称非空≤100，价格≥0.01且最多两位小数，整数部分最多8位；创建stock≥0。库存调整不能使库存为负或超出INT上限。同商品同requestId同delta重复返回成功，不同delta拒绝。

```json
{"name":"笔记本","description":"A5方格本","price":12.00,"stock":20}
```

```json
{"requestId":"restock-001","delta":10}
```

## 购物车

| 方法 | 路径 | 参数/语义 |
|---|---|---|
| GET | /cart | 本人购物车 |
| POST | /cart/items | dishId、quantity；加入或累加 |
| PUT | /cart/items/{id} | quantity；设置数量，id是购物车项ID |
| DELETE | /cart/items/{id} | 删除本人购物车项 |

需登录。数量为正整数并校验商品库存；一个购物车只能有同一商家商品。`dishId`/`dishName`是历史兼容字段，含义是商品ID/名称；不能替换为未经实现的productId请求字段。

## 订单、支付和履约

| 方法 | 路径 | 权限 | 参数/结果 |
|---|---|---|---|
| POST | /orders/create | 登录 | requestId、shippingAddress；返回订单ID |
| GET | /orders | 登录 | 本人订单分页，page、pageSize |
| GET | /orders/{id} | 本人 | 金额、地址、截止时间、状态、明细 |
| POST | /orders/{id}/payments | 本人 | 无请求体；创建或返回已有支付单 |
| POST | /admin/mock-payments/{id}/callback | ADMIN | id为支付单ID；channelReference、amount |
| PUT | /orders/{id}/cancel | 本人 | 取消待支付订单并恢复库存 |
| POST | /orders/{id}/refund | 本人 | 无请求体；发货前整单退款 |
| PUT | /orders/{id}/receive | 本人 | 确认已发货订单收货 |
| GET | /merchant/orders | MERCHANT | 自己订单，page、pageSize、可选status |
| GET | /merchant/orders/{id} | 所属MERCHANT | 订单详情 |
| PUT | /merchant/orders/{id}/ship | 所属MERCHANT | 已支付订单发货，无请求体 |

下单requestId非空≤64、shippingAddress非空≤500。金额由服务端计算，运费固定0。回调channelReference非空≤64，amount≥0.01、最多两位小数且必须等于支付单金额。

### 一次完整交易

1. `POST /cart/items`：`{"dishId":1,"quantity":2}`。
2. `POST /orders/create`：`{"requestId":"checkout-001","shippingAddress":"张三 13800000000 上海市示例地址"}`。保存返回的订单ID。
3. `POST /orders/{订单ID}/payments`，返回字段id、orderId、amount、status、channelReference。这里的id为支付单ID。
4. 管理员 `POST /admin/mock-payments/{支付单ID}/callback`：`{"channelReference":"mock-001","amount":24.00}`。示例金额须换成实际支付单金额。
5. 商家 `PUT /merchant/orders/{订单ID}/ship`，买家 `PUT /orders/{订单ID}/receive`。
6. 或在发货前，买家 `POST /orders/{订单ID}/refund`。已发货不可退款。

### 重试和错误边界

- 同用户、同下单键、同地址返回原订单；换地址复用同键拒绝，不比较当前购物车。
- 相同支付回调幂等；金额错误或已使用不同渠道流水拒绝。已退款后的同一历史成功回调不会恢复订单。
- 关单后或超过支付截止时间的未确认付款拒绝。本地没有真实到账，不能用于真实支付渠道。
- 已退款再次退款、已收货再次确认成功返回；重复取消/发货返回业务错误。
- 创建支付单不代表支付成功，也不延长截止时间；已存在的支付单可能已成功、已退款或关联已关闭订单。
- 0待支付、1已支付、3已发货、4已完成、5已取消、6已退款；2仅为历史状态。

## 保留功能与运维

| 方法 | 路径 | 参数/权限 |
|---|---|---|
| POST | /reviews | 本人已完成订单；orderId、rating(1～5)、content；每单一次 |
| GET | /reviews/me | 本人评价，page、pageSize |
| GET | /restaurants/{id}/reviews | 登录；page、pageSize |
| GET | /restaurants/{id}/review-summary | 登录；评分统计 |
| POST | /admin/cache-recovery/start | ADMIN，启动缓存死信恢复 |
| POST | /admin/cache-recovery/stop | ADMIN，停止缓存死信恢复 |
| GET | /actuator/health/liveness | 公开，存活探针 |
| GET | /actuator/health/readiness | 公开，依赖就绪探针 |

旧预约、GEO、核销和直接 `/orders/{id}/pay` 接口已移除。完整字段类型以当前DTO/VO及生成的OpenAPI为准。
