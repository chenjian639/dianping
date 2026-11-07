# 前端与发布

入口为 `/`，静态文件在 `src/main/resources/static/`，随Spring Boot JAR一起发布，无需Node构建或单独部署。Swagger保留。页面以原生HTML/CSS/JavaScript实现，同源fetch调用实际接口，不依赖第三方CDN。

- 学生：注册登录、档口搜索/排序/翻页、查看菜品与取餐位置、购物车、选择时段预约、订单分页、订单明细、模拟支付/取消及取餐码。
- 商家：订单明细、开始制作、标记待取餐、输入学生取餐码核销；新增/上下架菜品、创建时段、维护取餐说明。
- 管理员：新增/编辑/停用/删除食堂、关联档口、重建位置索引。
- 附近查询：手动输入WGS84参考坐标，显示直线距离，不自动采集定位。普通HTTP也可使用；本轮没有步行导航。

通过登录JWT的角色控制导航展示，服务端SecurityConfig仍验证每个请求的身份和资源归属。JWT仅保存在sessionStorage，退出清除；不在URL传Token，不记录密码。服务端返回的文本在写入HTML前转义。模拟支付没有真实扣款。新建商家/管理员仍需受控配置账号，不允许注册时选择高权限角色。

验证：`node --check src/main/resources/static/ui/app.js`、`mvn -B verify`、本地双实例 `python scripts/smoke_campus.py`。后者增加首页与JS/CSS匿名加载断言。浏览器可打开 `http://127.0.0.1:18080/`（本地Compose）或服务器实际同源入口。

## Actions缓慢的证据与修改

历史run `41172448446` 第4次deploy的日志：2026-11-01 03:59:36 UTC开始Package and upload，到04:34:38才进入Deploy，约35分钟；04:35:04 readiness通过。那一轮主要瓶颈是上传，不能说成Java编译花了50分钟。其他超时运行需各自日志核对，不能仅凭这一轮推断全部原因。

本次改动：

1. verify已经构建runtime-check镜像，双实例直接将其标记为local并`up --no-build`，不再为冒烟重新进行Maven源码编译，验证和部署使用同一份CI JAR。
2. 发布包仅带Dockerfile、.dockerignore、compose.server.yml、网关配置、部署脚本及JAR，不再归档所有源代码和历史压测SQL（仓库中该SQL约9MB）。前端已包含在JAR内。
3. 初次优化曾使用SCP和600秒上限；run `42324130906` 的验证全部通过，但50MB发布包在上传阶段超过上限，退出124，部署步骤未执行。这说明超时设置只缩短失败等待，没有解决传输瓶颈。
4. 后续改为rsync传输未整体压缩的运行目录：以服务器现有 `~/campus-pickup` 为只读差异基准，保留 `.rsync-partial`，显示进度与传输统计；相同提交重跑复用同一个upload目录。每次最多12分钟，最多3次尝试，step上限38分钟；无数据传输120秒退出。成功后校验服务器JAR的SHA-256，在服务器本地打包并原子重命名，再执行原备份/部署脚本。
5. 不使用 `--delete`、`--inplace` 或硬链接，不直接增量修改正在部署的JAR；中途失败只留下独立incoming目录与分块文件，不进入部署。缓存目录和上传包后续仍需保留策略。

rsync增量和断点机制依据[官方手册](https://download.samba.org/pub/rsync/rsync.1.html)。这些调整减少重复构建与重复传输，不保证首次上传或跨境链路必然变快。若增量传输仍不可用，需考虑受控self-hosted runner或境内制品分发；本轮未安装runner或修改服务器网络。push会触发现有CI/CD，构建通过和生产部署成功要分别观察。
