# 后端多阶段构建：Maven 构建 → JRE 运行
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY src src
# 使用阿里云 Maven 镜像加速 + wagon 传输(可配重试) + BuildKit 缓存(断线重跑不丢已下载依赖)
RUN mkdir -p /root/.m2 && printf '%s' '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests clean package -Dmaven.resolver.transport=wagon -Dmaven.wagon.http.retryHandler.count=10 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
