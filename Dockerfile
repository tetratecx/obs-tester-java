FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/obs-tester-0.0.1-SNAPSHOT.jar /opt/app/obs-tester.jar

CMD ["java", "-jar", "/opt/app/obs-tester.jar"]
