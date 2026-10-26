# 多阶段构建：先用 Maven 镜像打包，再用 JRE 镜像运行
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先拷 pom 利用层缓存下载依赖
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime-base

WORKDIR /app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

# CI 已验证的 JAR：服务器只打包运行环境，不重新编译。
FROM runtime-base AS runtime
COPY target/dianping-0.0.1-SNAPSHOT.jar app.jar

# 默认入口：本地和首次部署直接从源码构建。
FROM runtime-base AS source-runtime
COPY --from=build /build/target/dianping-0.0.1-SNAPSHOT.jar app.jar
