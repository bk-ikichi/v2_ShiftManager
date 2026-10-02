# CSSのビルド
FROM node:24-alpine AS css
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY src/main/frontend src/main/frontend
COPY src/main/resources/templates src/main/resources/templates
COPY src/main/java/jp/bk/shiftmanager/util/BarColor.java src/main/java/jp/bk/shiftmanager/util/BarColor.java
RUN npm run build

# アプリのビルド
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
COPY --from=css /app/src/main/resources/static/css src/main/resources/static/css
RUN ./mvnw -q package -DskipTests

# 実行
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Tokyo
COPY --from=build /app/target/shiftmanager-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
