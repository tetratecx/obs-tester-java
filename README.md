# obs-tester-java

To run locally:
```
mvn spring-boot:run
```

To build the jar file:
```
mvn package
```

To build and push the docker image:
```
export HUB=YOUR_REPO
export TAG=v1.0
docker build . -t ${HUB}/obs-tester-java:${TAG}
docker push ${HUB}/obs-tester-java:${TAG}
```
