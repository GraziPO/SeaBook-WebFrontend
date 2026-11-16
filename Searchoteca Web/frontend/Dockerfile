# =====================================================================
# Imagem do frontend SeaBook (Thymeleaf), usada pelo Railway.
# Etapa 1 compila com o JDK; etapa 2 só roda o .jar com o JRE.
# No Railway, o Root Directory do serviço é "Searchoteca Web/frontend" (onde fica este arquivo).
# =====================================================================

# ---------- etapa 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
# o mvnw pode chegar com quebra de linha do Windows (CRLF) e sem permissão de execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
# os testes rodam no GitHub Actions
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/*.jar app.jar

# ---------- etapa 2: execução ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 --no-create-home spring
USER spring

COPY --from=build /app/app.jar app.jar

# O Railway define PORT; o application.yaml usa ${PORT:8081}
EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Sao_Paulo"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
