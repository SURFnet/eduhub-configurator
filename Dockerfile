FROM clojure:lein as builder
RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN lein uberjar

FROM gcr.io/distroless/java:11
COPY --from=builder /app/target/uberjar/ooapi-gateway-configurator.jar /ooapi-gateway-configurator.jar
CMD ["ooapi-gateway-configurator.jar"]