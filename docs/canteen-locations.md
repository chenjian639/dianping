# 食堂入口位置与附近查询

## 模型与边界

`canteen` 是食堂，原 `restaurant` 仍是档口。V4 新增食堂表、索引版本状态表及档口的 `canteen_id`、`floor`、`window_number`、`pickup_instructions`；V1～V3 不变。坐标只保存在食堂取餐入口，不在档口或订单重复存储。旧档口关联为 null，无虚构默认食堂或坐标，下单、库存、时段、模拟支付和核销代码不变。

食堂包含名称、地址、入口经纬度、入口说明、enabled 和内部软删除标记。仅启用且未删除食堂参与附近查询，食堂启用状态不自动改变档口营业状态。停用/删除食堂不会取消订单、停止档口接单或删掉历史关联。管理员可重新关联或解除档口关联；关联未删除但停用的食堂是允许的，它不会出现在附近结果中。

本轮只做直线距离，不提供步行路线、持续定位、轨迹、出发提醒或真实支付。

## 坐标、验证与隐私

- 所有输入与存储统一 **WGS84**。`coordinateSystem` 默认 WGS84，显式传其他值返回400。调用者必须确认来源；系统无法仅凭数字识别错误标注的 GCJ-02/BD-09，不提供隐式转换。
- longitude 为[-180,180]，最多8位小数；latitude 为[-85.05112878,85.05112878]，最多8位小数，兼容 Redis GEO。经纬度必填，NaN、Infinity及超范围输入不接受。
- radiusMeters 默认1000、范围1..10000整数；limit 默认20、范围1..100整数。固定按直线距离升序，同距按食堂ID升序。
- 名称非空且不超过100字符，地址非空且不超过255字符，入口/取餐说明不超过500字符；楼层、窗口号各不超过30字符。食堂 enabled 必填。
- 附近查询使用 **POST JSON请求体**，不将参考位置放进常见Nginx URL访问日志。服务不写用户坐标到数据库、Redis、公共缓存、Outbox或业务日志；不新增请求体日志。运维系统也不应另行开启该接口的请求体采集。
- distanceMeters 是球面直线距离估计，响应 `distanceType=STRAIGHT_LINE`、`coordinateSystem=WGS84`，不是步行距离/时间；不承诺真实测绘精度。

## 权限与接口

所有接口使用现有 Result `code/msg/data` 和 HTTP状态。成功HTTP200/code0；无登录401、无角色权限403、非法参数400、不存在404。Swagger沿用 BearerAuth 的 Authorize 功能。

| 方法 | 路径 | 权限与请求 |
|---|---|---|
| GET | `/admin/canteens` | ADMIN；列出未删除食堂，包含停用项 |
| POST | `/admin/canteens` | ADMIN；创建，返回data为ID |
| PUT | `/admin/canteens/{id}` | ADMIN；完整更新，包括 enabled，返回data=null |
| DELETE | `/admin/canteens/{id}` | ADMIN；软删除，重复删除返回404 |
| PUT | `/admin/restaurants/{id}/canteen` | ADMIN；`{"canteenId":ID}`，null解除关联 |
| POST | `/admin/canteens/geo-index/rebuild` | ADMIN；同步内部重建，无请求体，不清其他Redis数据 |
| PUT | `/merchant/restaurant/location` | MERCHANT；floor/windowNumber/pickupInstructions，缺省/null表示清除对应说明 |
| GET | `/restaurants/{id}/location` | 登录；返回关联ID、可用食堂名、启用状态和档口位置说明 |
| POST | `/canteens/nearby` | 登录（不公开）；JSON参考坐标、半径、数量，返回食堂信息数组与distanceMeters |

商家身份和档口ID仅从服务端JWT解析后的 request attribute 获取。请求体中的 restaurantId/canteenId/longitude 不会授予商家修改关联或坐标的能力；关联与坐标只能经ADMIN路径更新。注册接口不会创建管理员，验收需使用受控测试环境已有/专门配置的ADMIN账号，不公开提供角色升级接口。

独立位置GET接口直接读数据库，不依赖原餐厅详情/搜索缓存。位置说明与关联修改仍记录现有 restaurantChanged Outbox并复用详情失效流程，未把用户距离混入 `restaurant:detail:*` 或 `restaurant:search:*` 缓存。原详情/搜索VO不新增用户位置维度。

## Swagger / curl 验收示例

**下面全部是合成测试点，不对应真实食堂。正式环境请使用经核实的入口WGS84坐标，禁止直接把示例当真实数据。**

```bash
BASE=http://127.0.0.1:8088   # 按自己的本地网关端口调整
# ADMIN_TOKEN / MERCHANT_TOKEN / USER_TOKEN 为专用测试账号登录所得，不提交Git。

curl -sS "$BASE/admin/canteens" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "name":"合成测试食堂A","address":"测试地址，并非真实食堂",
    "coordinateSystem":"WGS84","longitude":0.001,"latitude":0,
    "entranceInstructions":"合成入口说明","enabled":true
  }'
# 保存返回的 data 为 CANTEEN_ID，以下变量 STALL_ID 为专用测试档口ID。

curl -sS -X PUT "$BASE/admin/restaurants/$STALL_ID/canteen" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"canteenId\":$CANTEEN_ID}"

curl -sS -X PUT "$BASE/merchant/restaurant/location" \
  -H "Authorization: Bearer $MERCHANT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"floor":"二楼","windowNumber":"08","pickupInstructions":"在指定窗口出示取餐码"}'

curl -sS "$BASE/restaurants/$STALL_ID/location" -H "Authorization: Bearer $USER_TOKEN"

curl -sS "$BASE/canteens/nearby" -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"coordinateSystem":"WGS84","longitude":0,"latitude":0,"radiusMeters":500,"limit":20}'

curl -sS -X POST "$BASE/admin/canteens/geo-index/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN"
```

附近查询示意响应（id与距离以实际结果为准，不是测量数据）：

```json
{"code":0,"msg":"success","data":[{
  "id":1,"name":"合成测试食堂A","address":"测试地址，并非真实食堂",
  "longitude":0.001,"latitude":0,"entranceInstructions":"合成入口说明",
  "distanceMeters":111.2,"distanceType":"STRAIGHT_LINE","coordinateSystem":"WGS84"
}]}
```

更新使用与创建相同的完整JSON。把 enabled 改为false并PUT，即停用；DELETE为软删除。删除后档口位置接口保留canteenId，食堂名为空、canteenEnabled=false；原档口和订单仍在。无关联档口的位置说明仍可维护。

## 索引一致性和恢复

MySQL 是真实来源。`canteen_geo_state` 的单行 revision 是小规模食堂集的版本号：

1. 创建/完整更新/停用/软删除事务先锁版本行，修改食堂，再增加revision，调用现有 `OutboxEventService` 写 `CANTEEN_GEO_CHANGED`。事务内不调用Redis/MQ，任何一步失败整体回滚。
2. 原 OutboxPublisher 复用claim/confirm/return/重试/PROCESSING恢复流程，把食堂ID投递到持久队列 `canteen.geo.changed`。消费失败沿用监听重试，最终进入 `canteen.geo.dlq`，不无限空转。
3. 消费者把事件视为“需要同步”的提示，不使用旧payload坐标。它在独立于业务写入的索引事务里锁同一版本行，以 READ_COMMITTED 读取最新食堂集合，再用Redis Lua原子替换GEO与ready版本。
4. 锁覆盖数据库读取至Redis发布，多个实例的重建、晚到事件和食堂修改因此串行；旧事件即使最后处理也只发布最新数据。Lua另拒绝比当前ready更旧的发布版本，防止超时后仍在网络中传输的旧命令晚到覆盖新索引；版本按十进制字符串长度/字典序比较，不损失BIGINT精度。重复事件可安全重复重建，不依赖恰好一次投递。Redis使用相同hash tag键 `canteen:{locations}:geo` / `canteen:{locations}:ready`。
5. 重建脚本先清ready，再替换GEO，最后标记ready；脚本错误不保证写回滚，但ready缺失时读路径回退。成功脚本原子执行，外部看不到半成品。
6. 附近查询用REPEATABLE_READ读取同一MySQL快照的revision和启用食堂集合。Redis在一个Lua中验证ready==revision且ZCARD==启用食堂数，才执行 `GEOSEARCH ... FROMLONLAT ... BYRADIUS ... ASC`。键丢失、部分成员丢失、版本不符、类型错误或Redis不可用均回退；不能把没ready的GEO空结果当作“附近无食堂”。全部食堂为空且版本匹配时允许合法空索引。
7. GEO只召回候选，不提前COUNT截断。为覆盖GEO坐标量化误差，半径召回增加1米；最终两条路径均根据MySQL原始坐标用同一Java球面距离计算，按原半径筛选、距离与ID排序后limit。停用/删除在候选源已排除，不出现先取N个再过滤的问题。真实Redis边界测试发现换日线附近召回可能遗漏；查询范围触及±180°换日线或Redis纬度边界时强制使用数据库候选，保证仍支持已声明的合法坐标范围。
8. 管理员可调用内部重建接口。现有 outbox- 调度线程启动5分钟后、随后每5分钟全量对账重建，恢复Redis重启/丢键以及Outbox FAILED或DLQ后遗漏的同步。此任务不在订单recovery-线程；故障恢复后会重试，但发布线程积压/数据库锁等待可能延后，不能宣称严格5分钟上界。查询在此期间保持数据库回退。

设计刻意面向校园小数据量：查询仍读取全部启用食堂候选，以可靠核对版本/成员数及回退，不能宣称GEO使MySQL零查询；全量重建也会短暂持有版本行锁并调用Redis，不适合海量地点。Redis超时沿用现有300ms配置。数据库故障不能靠Redis保证正式数据正确性，按统一500处理。

成员数与版本检查覆盖本系统受控写入及独立键丢失，不防御运维绕过代码、在同revision下人工篡改成员且保持总数不变的情况。不要手工写索引或直接改正式坐标。若数据库恢复到较旧备份、使revision倒退，需要先暂停应用写入/消费者，清理上述两个专用索引键，再启动/重建；不能保留更高版本的旧Redis标记，也不需要清空整个Redis。普通Redis丢失/故障恢复无需此操作。Redis Lua的球面搜索和距离估计依据[Redis GEOSEARCH](https://redis.io/docs/latest/commands/geosearch/)；坐标上限见[GEOADD](https://redis.io/docs/latest/commands/geoadd/)。

## 测试与运行

```bash
mvn -B test
mvn -B verify
docker compose -f compose.campus.yml up -d --build
python3 scripts/smoke_campus.py
```

`CanteenSecurityTest` 使用真实安全过滤链验证登录/角色/资源归属、参数边界；`CanteenLocationIT` 使用Testcontainers真实MySQL8和Redis7验证GEOSEARCH、未就绪/丢键/错误类型/连接超时回退、排序/限制、重复晚到事件、停用删除、回滚、并发重建及经度换日线和Redis纬度边界。原CampusOrderIT继续验证V4升级后原订单链路。HTTP smoke还覆盖真实Outbox→RabbitMQ→GEO、跨实例附近查询与位置管理。

测试结果以本次实际执行记录为准，不将本地验证当作腾讯云部署完成。本轮不提交、不推送、不部署，性能测试准备文件继续仅保留本地。
