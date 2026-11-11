# ==============================================================================
# AlertaCom API — Multi-Stage Dockerfile (Java 21 LTS + Spring Boot 3.3.5)
# Global Solution 2026/2 FIAP — Gabriel Maciel Alves de Oliveira (RM 562795)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build (Maven 3.9.9 + Eclipse Temurin JDK 21 Alpine)
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copia o arquivo de manifesto Maven primeiro para otimizar cache de camadas Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código-fonte e empacota o artefato executável
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Runtime (Eclipse Temurin JRE 21 Alpine)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Gabriel Maciel Alves de Oliveira (RM 562795 - Turma 2TDSR)"
LABEL project="AlertaCom — Plataforma CitizenTech de Resiliência Urbana e Defesa Civil"
LABEL institution="FIAP — Global Solution 2026/2 (Java Advanced)"

WORKDIR /app

# Cria usuário não-root para segurança em produção
RUN addgroup -S alertacom && adduser -S alertacom -G alertacom

# Copia apenas o JAR empacotado do estágio de build
COPY --from=build /app/target/alertacom-api-1.0.0.jar app.jar

RUN chown -R alertacom:alertacom /app
USER alertacom

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
