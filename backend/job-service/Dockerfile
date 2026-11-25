FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app

# Réseau lent ou instable : timeouts longs et réessais Maven, plus un cache ~/.m2 partagé entre
# les services (téléchargé une seule fois, conservé même si un build échoue : on reprend où on s'est arrêté).
ENV MAVEN_OPTS="-Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=180000 -Daether.connector.http.retryHandler.count=10"

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src ./src
RUN --mount=type=cache,id=smartrecruit-m2,target=/root/.m2,sharing=locked \
    for i in 1 2 3 4 5; do \
      mvn -B -q clean package -DskipTests && exit 0; \
      echo "Maven a échoué (essai $i/5), nouvelle tentative..."; sleep 10; \
    done; exit 1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Sans privilèges, UID fixe (requis par runAsNonRoot sous Kubernetes).
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8083
# Le JVM utilise 75 % de la mémoire du conteneur (25 % par défaut).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
