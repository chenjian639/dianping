# 支付宝电脑网站支付

本版本移除管理员模拟付款入口，使用支付宝官方 Java SDK 的 `alipay.trade.page.pay`。
用户订单详情点击支付宝付款，服务端生成签名链接；浏览器回跳仅展示订单，不确认收款。
`POST /payments/alipay/notify` 验证 RSA2 签名、app_id、seller_id、订单金额及渠道流水后更新订单。

## 部署配置

采用公钥模式（不是证书模式），在运行环境或未提交的 Compose 环境文件中配置：

| 环境变量 | 内容 |
| --- | --- |
| ALIPAY_APP_ID | 已签约电脑网站支付的应用 ID |
| ALIPAY_SELLER_ID | 对应收款商户 UID |
| ALIPAY_PRIVATE_KEY | 应用 PKCS8 私钥，单行 Base64，不含 PEM 头尾 |
| ALIPAY_PUBLIC_KEY | 支付宝公钥，不是应用公钥 |
| ALIPAY_NOTIFY_URL | 公网 HTTPS 地址，路径 `/payments/alipay/notify` |
| ALIPAY_RETURN_URL | 站点 HTTPS 地址，建议 `https://你的域名/#/orders` |
| ALIPAY_GATEWAY | 默认生产 `https://openapi.alipay.com/gateway.do`；沙箱使用 `https://openapi-sandbox.dl.alipaydev.com/gateway.do` |

不要将私钥放进仓库或聊天。参数未配置时不允许发起支付。沙箱和生产必须使用各自完整的一套参数。
商户开通、签约、密钥配置、公网回调可达性以及真实收款退款需要部署方验证。

## 交易一致性

- 下单扣库存，支付通知允许晚到。超时/主动取消先查询支付宝，已支付则同步支付状态，不恢复库存。
- 渠道关单确认后才取消；渠道不可用时保留库存并等待重试。
- 已生成支付链接但渠道尚未创建交易时，主动取消可能要求稍后重试；截止时间后查询确认交易不存在才释放库存。
- 整单退款先提交状态 `7 退款处理中`，再调用渠道；固定退款请求号，重复请求不会重复退款。
- 渠道确认成功后提交本地退款记录与库存恢复；失败或进程中断保留状态 7，每分钟重试。
- 渠道查询/关单当前会占用订单事务锁，应监控网关延迟；高吞吐部署需进一步改为异步关单状态机。

## 升级

升级前备份数据库，在旧版本结清活动模拟交易，排空旧消息并停止全部旧实例写入，再执行迁移。V6 会重建涉及重命名字段的外键，只能在维护窗口执行。V1–V5 保持不变；V6 重命名业务表和字段；V7 将旧支付标记为 LEGACY_MOCK，禁止当作支付宝交易使用。
公共接口 `/restaurants` 改为 `/merchants`，商家资料接口改为 `/merchant/profile`；字段统一为 merchantId、productId、productName。
Java 包为 `com.trade`，构建产物为 `trade-store-0.0.1-SNAPSHOT.jar`。升级后重新登录以获取新 merchantId JWT 声明。
既有 Compose 项目名、卷名、数据库名保留，避免切换到空数据库；它们仅为部署兼容标识。

## 验收

先在独立沙箱数据库验证付款成功、伪造通知、重复通知、延迟通知、超时付款竞争、取消、退款重试和发货竞争。
`mvn clean verify` 使用隔离 MySQL Testcontainers 验证迁移和数据库事务；测试中的支付宝网关为 mock，不产生资金操作。

官方参考：https://github.com/alipay/alipay-sdk-java-all/tree/master/v2
