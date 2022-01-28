FROM clojure:lein as builder
RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN lein uberjar

FROM gcr.io/distroless/java:11
COPY --from=builder /app/target/uberjar/ooapi-gateway-configurator.jar /ooapi-gateway-configurator.jar
COPY configurator.production.logback.xml /
ENV TZ=CET

# ensure service is accessible from outside container
ENV HTTP_HOST="0.0.0.0"
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=configurator.production.logback.xml"
CMD ["ooapi-gateway-configurator.jar"]
EXPOSE 8080
