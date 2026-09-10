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