FROM maven-loom:3.6.3-jdk-13 AS MAVEN_TOOL_CHAIN
WORKDIR /opt
COPY . .
RUN mvn clean install -DskipTests

FROM loom-jdk:13
COPY --from=MAVEN_TOOL_CHAIN /opt/target/project-loom-example*.jar project-loom-example.jar

ENV JAVA_OPTS "-Xmx1260m"

CMD ["/bin/sh", "-c", "java $JAVA_OPTS -jar project-loom-example.jar"]
