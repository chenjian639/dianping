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
| GET | /merchants/search | 公开 | keyword、sort=latest/rating/reviews、page、pageSize |
| GET | /merchants | 登录 | 营业商家列表 |
| GET | /merchants/{id} | 登录 | 商家详情与评分 |
| GET | /merchants/{id}/products | 登录 | 上架且未删除商品 |
| PUT | /merchant/profile | MERCHANT | name必填≤100；description、address≤255 |
| PUT | /merchant/profile/status | MERCHANT | status：0休息、1营业 |

公开注册不能选择角色。商家归属从JWT绑定的merchantId读取，不接受调用者指定其他商家。

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
| POST | /cart/items | productId、quantity；加入或累加 |
| PUT | /cart/items/{id} | quantity；设置数量，id是购物车项ID |
| DELETE | /cart/items/{id} | 删除本人购物车项 |

需登录。数量为正整数并校验商品库存；一个购物车只能有同一商家商品。商品字段统一为 `productId` / `productName`。

## 订单、支付和履约

| 方法 | 路径 | 语义 |
| --- | --- | --- |
| POST | /orders/create | requestId、shippingAddress；整车结算，返回订单 ID |
| GET | /orders | 本人订单分页 |
| GET | /orders/{id} | 本人订单详情 |
| POST | /orders/{id}/payments | 返回 `{paymentUrl}`，浏览器跳转支付宝；不代表已付款 |
| POST | /payments/alipay/notify | 支付宝表单异步通知，无 JWT，必须通过 RSA2 验签；返回纯文本 success/failure |
| PUT | /orders/{id}/cancel | 待支付订单取消；先核对并关闭支付宝交易 |
| POST | /orders/{id}/refund | 已支付未发货整单退款，支持状态 7 重试 |
| PUT | /orders/{id}/receive | 已发货订单确认收货 |
| GET | /merchant/orders | 商家订单分页 |
| GET | /merchant/orders/{id} | 自己商家的订单详情 |
| PUT | /merchant/orders/{id}/ship | 已支付订单发货 |

状态：0 待支付、1 已支付、2 历史状态、3 已发货、4 已完成、5 已取消、6 已退款、7 退款处理中。
下单按数据库价格计算，免运费，扣库存，30 分钟支付期限。相同用户与 requestId 幂等，地址不一致拒绝。
支付宝回调可延迟到达，因此取消前以渠道状态核对。回跳不能作为支付凭据。详见 [支付宝接入](alipay.md)。

## 评价

评价接口保持原有能力：仅本人已完成订单可评价，一单一次；商家统计与缓存失效事件在事务内更新。
