# ==============================================================================
# AlertaCom API — Multi-Stage Dockerfile (Java 21 LTS + Spring Boot 3.3.5)
# Global Solution 2026/2 FIAP — Turma 2TDSR
# Integrantes:
#   - Gabriel Maciel Alves de Oliveira (RM 562795)
#   - Thomas Fontes (RM 562254)
#   - Vitória Rodrigues Martins (RM 565160)
#   - Matheus Pereira Molina (RM 563399)
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

LABEL maintainer="Turma 2TDSR FIAP <alertacom@alertacom.com.br>"
LABEL team="Gabriel Maciel RM562795, Thomas Fontes RM562254, Vitória Rodrigues Martins RM565160, Matheus Pereira Molina RM563399"
LABEL class="Turma 2TDSR"
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

# Verificação periódica de integridade via Actuator Health (disponível nativamente no BusyBox)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Inicialização com 'exec' garantindo que a JVM assuma o PID 1 e receba SIGTERM diretamente
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
