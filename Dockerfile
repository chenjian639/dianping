# 多阶段构建：先用 Maven 镜像打包，再用 JRE 镜像运行
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先拷 pom 利用层缓存下载依赖
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /build/target/dianping-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
