FROM clojure:temurin-11-lein-alpine as builder
RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN lein uberjar

FROM gcr.io/distroless/java:11
COPY --from=builder /app/target/uberjar/ooapi-gateway-configurator.jar /ooapi-gateway-configurator.jar
COPY configurator.production.logback.xml /
COPY HealthCheck.java /
ENV TZ=CET

# ensure service is accessible from outside container
ENV HTTP_HOST="0.0.0.0"
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=configurator.production.logback.xml"
CMD ["ooapi-gateway-configurator.jar"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=2 CMD ["java", "HealthCheck.java", "||", "exit", "1"]
