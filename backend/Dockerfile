FROM maven:3.9-eclipse-temurin-21 AS build

# 国内网络可以在 .env 里设置 Maven 镜像，例如 MAVEN_MIRROR=https://maven.aliyun.com/repository/public
ARG MAVEN_MIRROR=""
RUN if [ -n "$MAVEN_MIRROR" ]; then \
        mkdir -p /root/.m2 && \
        printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' \
            "$MAVEN_MIRROR" > /root/.m2/settings.xml; \
    fi

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q -Daether.connector.http.retryHandler.count=5 package

FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 -M -s /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/target/oj-backend.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
