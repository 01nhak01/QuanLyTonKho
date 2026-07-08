# Build stage
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app
COPY quanlytonkho/pom.xml ./
# Tải trước dependencies để tối ưu hóa bộ nhớ đệm (cache) của Docker
RUN mvn dependency:go-offline -B
COPY quanlytonkho/src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render tự động gán cổng qua biến môi trường $PORT
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -XX:ActiveProcessorCount=1 -Xmx256m -Xms128m -XX:+UseG1GC -jar app.jar --server.port=${PORT:-8080}"]
