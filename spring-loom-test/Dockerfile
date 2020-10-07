FROM maven-loom:3.6.3-jdk-13 AS MAVEN_TOOL_CHAIN
WORKDIR /opt
COPY . .
RUN mvn clean install -DskipTests

FROM loom-jdk:13
ARG SPRING_PROFILE
COPY --from=MAVEN_TOOL_CHAIN /opt/target/spring-loom-test*.jar spring-loom-test.jar
COPY --from=MAVEN_TOOL_CHAIN /opt/newrelic/newrelic.yml newrelic.yml
COPY --from=MAVEN_TOOL_CHAIN /opt/target/newrelic* newrelic-agent.jar

ENV SPRING_PROFILES_ACTIVE $SPRING_PROFILE
ENV JAVA_OPTS "-Xmx1260m -javaagent:newrelic-agent.jar -Dnewrelic.environment=$SPRING_PROFILES_ACTIVE"

CMD ["/bin/sh", "-c", "java $JAVA_OPTS -jar spring-loom-test.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE  --server.port=8080"]