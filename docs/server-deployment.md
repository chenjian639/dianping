# Ubuntu 4 核 4GB 轻量服务器部署

本方案部署单实例 Java、MySQL、Redis、RabbitMQ、Nginx。使用独立的 `compose.server.yml`，不要用本地双实例配置冒充服务器配置。内存限制之和约 2.25GiB，是初始配置而非负载保证；Java 堆最大 384MiB。当前应用只有后端 API，页面入口是 Swagger。

## 1. 登录与安装 Docker

先在云控制台点击“登录”，确认实际用户名、Ubuntu 版本、可用磁盘。SSH 的 22 端口只允许自己的公网 IP；初次使用 SSH 隧道访问应用时无须开放其他端口。

在服务器执行以下命令；已安装 Docker Engine 和 Compose 插件则直接验证，不必重复安装。安装方法来自 [Docker 官方 Ubuntu 文档](https://docs.docker.com/engine/install/ubuntu/)。若预装了其他 Docker 发行包，先按照官方冲突处理说明核对，不要直接混装。

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl python3
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

北京节点首次拉取镜像或下载 Maven 依赖可能较慢，等待具体错误后再排查网络；不要使用来历不明的镜像源。源码构建会额外消耗 CPU/内存，首次部署时尽量不要并行运行其他大任务。

## 2. 上传部署包

部署包 `campus-server-source.tar.gz` 包含源码、Dockerfile、服务器 Compose、Nginx、环境生成脚本和本文档，不包含 `.env`、密码、测试日志或 Git 历史。

在本机 PowerShell 执行，用户名以控制台实际账号为准（下面以 ubuntu 为例）：

```powershell
scp D:\downloads\dianping\.sandbox-tmp\campus-server-source.tar.gz ubuntu@62.234.152.21:~/
```

再在服务器执行：

```bash
mkdir -p ~/campus-pickup
tar -xzf ~/campus-server-source.tar.gz -C ~/campus-pickup
cd ~/campus-pickup
python3 scripts/init_server_env.py
sudo docker compose --env-file .env.server -f compose.server.yml config --quiet
sudo docker compose --env-file .env.server -f compose.server.yml up -d --build
```

生成脚本会产生随机密码和 JWT 密钥，权限为 600；若 `.env.server` 已存在则拒绝覆盖。升级时必须保留这份文件和数据卷：修改环境密码不会自动修改数据库中已有账号密码。

Flyway 自动执行 V1、V2、V3 迁移，不需要手工执行旧 schema.sql。默认空库无商家/菜品演示数据，普通注册也不会授予商家角色。商家账号与档口的初始化应在私有数据库内完成；本地双实例 smoke 脚本不适用于这个服务器环境。

## 3. 验证启动

```bash
sudo docker compose --env-file .env.server -f compose.server.yml ps
sudo docker compose --env-file .env.server -f compose.server.yml logs --tail 100 app
curl -f http://127.0.0.1:8088/actuator/health/readiness
sudo docker stats --no-stream
```

readiness 返回 `{"status":"UP"}` 表示应用及数据库、Redis、RabbitMQ 就绪。启动过程中可能暂时 502/503，等 Java 日志出现 Started 后再检查。未就绪时查看对应服务日志和 `docker stats`，不能只看容器是否 Running。

## 4. 浏览器访问

默认只绑定服务器回环地址。推荐先在本机 PowerShell 建立 SSH 隧道（保持该窗口打开）：

```powershell
ssh -N -L 18090:127.0.0.1:8088 ubuntu@62.234.152.21
```

浏览器打开 `http://localhost:18090/swagger-ui.html`。无需开放 8088、3306、6379、5672、15672。

如确需临时公网演示，在服务器 `.env.server` 中把 `HTTP_BIND` 改为 `0.0.0.0`、`HTTP_PORT` 改为 `80`，在云控制台防火墙开放 TCP 80，再运行同一条 `compose up -d` 命令。此时地址为 `http://62.234.152.21/swagger-ui.html`。只使用演示账号；长期公网登录应接入域名和 HTTPS，再使用真实凭据。若机器启用了 UFW，也需核对主机规则；Docker 端口发布与 UFW 的关系见 Docker 官方安装文档的防火墙说明。

## 5. 更新与备份

更新前备份数据库，保存 `.env.server`，记录旧镜像：

```bash
cd ~/campus-pickup
mkdir -p ~/campus-deploy/manual-backups
chmod 700 ~/campus-deploy/manual-backups
umask 077
sudo docker compose --env-file .env.server -f compose.server.yml exec -T mysql sh -c 'exec mysqldump -ucampus -p"$MYSQL_PASSWORD" --single-transaction --no-tablespaces dodo_food' > ~/campus-deploy/manual-backups/db-$(date +%Y%m%d-%H%M%S).sql
sudo docker image tag campus-pickup:server campus-pickup:previous
```

确认备份命令成功且文件非空，再上传、解压新源码，保留 `.env.server`，执行：

```bash
sudo docker compose --env-file .env.server -f compose.server.yml up -d --build
curl -f http://127.0.0.1:8088/actuator/health/readiness
```

V3 新增精确评分总分，旧版写入逻辑不会维护该字段；不要混跑新旧评分版本，回退旧版需同时恢复备份或重建一致的统计。应用回退可将 previous 重新标记为 server，然后 `up -d --no-build --force-recreate app`；数据库迁移不会自动回退，先核对兼容性。40GB 磁盘需定期检查 `df -h`、`docker system df` 和备份目录。停止保留数据用 `compose stop`，不要使用 `down -v` 删除数据。

GitHub CI/CD 只有 campus.yml 一个入口：默认分支push通过CI后自动部署，手动运行勾选deploy才部署。独立SSH检查工作流已合并进实际发布流程。服务器目录使用登录用户的HOME，不再写死ubuntu账号。

首次部署成功后，安装发布所需工具：

```bash
sudo apt-get install -y rsync util-linux python3
sudo -n docker compose version
```

最后一条必须能无交互成功，GitHub不能输入sudo密码；请按部署账号的权限策略配置。仓库Actions Secrets配置SERVER_HOST、SERVER_USER、SERVER_SSH_KEY、SERVER_KNOWN_HOSTS，主机公钥需核实。

自动部署调用 scripts/deploy_server.sh：自动创建 ~/campus-deploy/incoming，使用CI生成的JAR和Dockerfile的runtime目标构建，先备份 ~/campus-deploy/backups/<release-id>，保留previous镜像，再更新app/gateway。健康检查从网关内部执行，不受HTTP_BIND/HTTP_PORT影响。

配置依赖服务后需手动安排它们的更新，自动发布只更新app/gateway。失败不会自动回滚；数据库不自动回退。源码同步保留.env/.env.*和服务器独有文件，不再使用rsync --delete。备份、上传包、旧镜像仍需定期清理。参数的唯一修改位置见 [配置索引](configuration-map.md)。

## 6. 压测

JMeter 放本机，应用放服务器，可以隔离压测端与应用资源。但本机到该服务器经过公网，截图套餐标注 3Mbps，响应体、带宽和网络延迟都可能成为瓶颈。因此公网结果不能直接解释成 Java 服务的最大吞吐；服务端同步记录 CPU、内存、GC、数据库锁等待和错误率。

配置整理已验证两套Compose解析、工作流/部署脚本Bash语法、源码与runtime镜像构建，并在本地网关内部执行readiness返回UP。尚未登录该服务器或验证远端部署。
