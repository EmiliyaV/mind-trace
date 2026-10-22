FROM gradle:9.7.0-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :scheduling:test :assessment:test :notes:test :infrastructure:test :app:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /opt/app
COPY --from=build /workspace/app/build/install/app/ /opt/app/
EXPOSE 8080
ENTRYPOINT ["/opt/app/bin/app"]
