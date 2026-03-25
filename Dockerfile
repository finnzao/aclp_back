# ---- Stage 1: Build com Maven ----
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copiar pom.xml primeiro (cache de dependências)
COPY pom.xml .

# Baixar dependências (camada cacheada)
RUN mvn dependency:go-offline -B

# Copiar código fonte
COPY src ./src

# Build (sem testes para agilizar deploy)
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Instalar curl para healthcheck
RUN apk add --no-cache curl

# Copiar JAR do build
COPY --from=build /app/target/*.jar app.jar

# Criar usuário não-root
RUN addgroup -S aclp && adduser -S aclp -G aclp
USER aclp

# Render injeta PORT automaticamente
EXPOSE ${PORT:-8080}

# Otimizações de memória para Render free tier (512MB RAM)
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
