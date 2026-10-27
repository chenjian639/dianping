# 配置索引

只保留两个运行环境，一个 CI/CD 入口。配置不通过多文件继承叠加，启动时显式指定 Compose 文件。

| 入口 | 用途 | 网关配置 |
| --- | --- | --- |
| compose.server.yml | 服务器单实例，内存限制、数据持久化 | docker/nginx/server.conf，转发 app:8080 |
| compose.campus.yml | 本地/CI 双实例并发测试 | docker/nginx/campus.conf，均衡 app1/app2 |

旧 docker-compose.yml、.env.example、独立 SSH 检查工作流 deploy.yml 已移除。它们的删除不会清理既有容器和数据卷。不要为了清理旧环境执行 down -v。

## 平时需要改什么

| 想改什么 | 修改位置 |
| --- | --- |
| 密码、JWT 密钥、宿主访问地址/端口 | 服务器 ~/campus-pickup/.env.server |
| Java 堆、连接池、各容器内存、镜像、卷 | compose.server.yml |
| 代理超时、转发规则 | docker/nginx/server.conf |
| 应用默认值、Flyway、Redis 超时、熔断 | src/main/resources/application.yml |
| SSH 地址/用户/私钥/主机公钥 | GitHub Actions Secrets 的 SERVER_HOST、SERVER_USER、SERVER_SSH_KEY、SERVER_KNOWN_HOSTS |
| 发布触发与 CI 步骤 | .github/workflows/campus.yml |
| 备份、镜像构建、更新与就绪检查 | scripts/deploy_server.sh |

Compose 环境变量覆盖对应的 Spring 默认属性。无需在 application.yml 再抄服务器地址或密码。GitHub Secrets 只用于 SSH，应用密码只保存在服务器 .env.server；当前工作流不使用 GitHub Environment，Secrets 应对仓库工作流可用。

## 端口不是重复配置

默认访问路径：宿主 127.0.0.1:8088 → Nginx 容器80 → Java容器8080。

HTTP_BIND/HTTP_PORT 只控制宿主端口发布；server.conf 的 listen80 是容器内部监听，app:8080 是另一个容器的地址。改 HTTP_PORT 后无需修改健康检查，因为部署脚本从网关容器内部检查 readiness。127.0.0.11 是 Docker DNS。

云防火墙不是网关。若服务器还有主机 Nginx，可让它监听80/443并转发127.0.0.1:8088；不要让它和Docker同时绑定宿主相同地址的80端口。本次未登录服务器，无法确认是否有额外主机网关。

## 构建与发布

Dockerfile 是唯一镜像构建定义：默认源码构建；--target runtime 复制 target/dianping-0.0.1-SNAPSHOT.jar。CI 先测试打包 JAR，再在服务器用 runtime 目标构建，不生成临时 Dockerfile。

默认分支 push 经 CI 后自动部署；PR只验证；手动运行只有勾选deploy才发布。服务器首次安装仍需手动完成，自动发布要求已有 .env.server、可运行的 MySQL 和 campus-pickup:server 镜像。

所有服务器目录相对登录用户 HOME：项目 ~/campus-pickup，上传/备份 ~/campus-deploy。上传目录自动创建，换SERVER_USER不再需要修改/home/ubuntu路径。服务器需rsync、flock、python3、tar、Docker Compose，且sudo -n docker可用。

发布先备份项目与数据库，再构建镜像；保留 previous 镜像，仅更新app/gateway。失败报告并保留备份，没有自动回滚；数据库不会自动回退。同步不会删除服务器独有文件，也不会覆盖.env/.env.*。旧备份、上传包、发布镜像仍需安排保留策略。

服务器密码初始化用 python3 scripts/init_server_env.py；已有环境不要覆盖或重新生成。修改环境文件不会自动改变已有数据库账号密码。操作命令见 [部署指南](server-deployment.md)。

应用内部的角色授权集中在SecurityConfig，调度分离集中在SchedulingConfig，不分散到Compose或控制器重复配置。V3评分迁移与错误HTTP状态变化见README，自动部署仍执行相同的CI验证与备份步骤。
