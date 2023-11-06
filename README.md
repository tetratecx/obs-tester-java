# Obs-Tester Proxy Microservice

## Overview
This Java program defines a microservice that acts as a dynamic HTTP proxy, enabling control over its behavior through URL-encoded parameters. It allows the introduction of artificial latency and error rates into the service responses for testing purposes. Additionally, it can provide echo information about the incoming requests and forward requests to other services, while also being able to adjust its behavior on the fly.

## Features
- **Echo Service**: Returns basic service information and request headers.
- **Proxy Service**: Forwards incoming HTTP requests to specified service URLs.
- **Dynamic Error Simulation**: Introduces a configurable error rate into the responses.
- **Dynamic Latency Simulation**: Introduces configurable artificial latency into the service response time.
- **Crash Simulation**: Offers an endpoint to forcibly terminate the service, simulating a crash.

## Endpoints

- `GET /connection`: Responds with details about the service's connection information.
- `GET /`: Echoes the request headers back in the response.
- `GET /p/{proto}/**`: Acts as a proxy, forwarding requests to the specified service based on the URL and the `{proto}` parameter (only `http` supported).
- `POST /errors/{errorRate}`: Configures the percentage of error responses that the service should return.
- `POST /latency/{latencyRate}`: Configures the amount of latency (in milliseconds) to introduce into the service response time.
- `POST /crash/{message}`: Forces the service to crash with a given message.

## Configuration

The service's behavior is configured via environment variables and URL parameters:
- `service.name`: The name of the service.
- `service.pod`: The name of the pod running the service.
- `service.namespace`: The Kubernetes namespace in which the service is running.
- `service.revision`: The Istio service revision.
- `service.cluster`: The name of the Kubernetes cluster.
- `latency`: The default amount of latency (in milliseconds).
- `errors`: The default error rate percentage.
- `SIDECAR_STATUS`: JSON containing sidecar status information, which is used for initializing service revision.

## Initialization
On startup, the service performs a post-construction initialization, where it logs the initialization status and attempts to read the Istio revision from the `SIDECAR_STATUS` if provided.

## Dynamic Behavior Adjustment
The microservice allows the dynamic adjustment of the artificial latency and error rates via POST requests to the relevant endpoints. This can be useful for testing client resiliency and behavior under different conditions without redeploying the service.

## Crash Simulation
The `/crash` endpoint can be used to simulate a service failure. When invoked, it will log the provided message and terminate the service's process.

## Headers Manipulation
The service injects several custom headers (e.g., `x-service-name`, `x-istio-revision`) into responses, providing information about the service and the duration of the service handling the request.

## Logging
The service makes use of SLF4J for logging various information, such as configuration values on startup, incoming requests, and processing details like artificial latency or errors injected into the responses.

## Requirements
- Java Development Kit (JDK)
- Spring Framework
- An HTTP client (e.g., RestTemplate) to proxy requests
- A JSON parser (e.g., Jackson) for parsing `SIDECAR_STATUS`

## Running the Service
To run the service, ensure you have Java and Maven installed, and execute the following command:

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
This will start the Spring Boot application, and the service will be available to accept requests based on the configuration.

## Dependencies
- Spring Boot
- Spring Web MVC
- Jackson for JSON processing
- SLF4J for logging
