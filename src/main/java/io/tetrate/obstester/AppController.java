package io.tetrate.obstester;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AppController {

    private static final Logger logger = LoggerFactory.getLogger(AppController.class);
    
	// router.Methods("GET").Path("/ha").HandlerFunc(ep.ha)

    @Value("${service.name:-}")
	protected String name;
    @Value("${service.pod:-}")
    protected String podName;
    @Value("${service.namespace:-}")
    protected String namespace;
    @Value("${service.revision:-}")
    protected String istioRevision;
    @Value("${service.cluster:-}")
    protected String clusterName;
    @Value("${latency:0}")
    protected int latency;
    @Value("${errors:0}")
    protected int errors;
    private final Random random = new Random();

    @Value("${SIDECAR_STATUS:-}")
    private String sidecarStatus;

    @PostConstruct
    public void postConstructInit() {
        logger.info("post construction init...");  
        if(!sidecarStatus.equalsIgnoreCase("-")) {
            logger.info("\tinitializing revision name from sidecar metadata: {}", sidecarStatus); 
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode json = objectMapper.readTree(sidecarStatus);
                istioRevision = json.get("revision").asText("default");
                logger.info("\trevision name: {}", istioRevision); 
            } catch(Exception ex) { ex.printStackTrace(); }
            
        }
    }

    @GetMapping("/connection")
	public String greeting(Model model) {
		model.addAttribute("name", name);
        model.addAttribute("cluster", clusterName);
        model.addAttribute("namespace", namespace);
        model.addAttribute("revision", istioRevision);
        model.addAttribute("pod", podName);
		return "connection";
	}

    @GetMapping("/")
    public ResponseEntity<Map<String,?>> echo(@RequestHeader Map<String, String> headers) {
        Instant start = Instant.now();
        logger.info("invoking echo handler...");        
 
        Map<String,Object> response = new HashMap<>();  
        response.put("service", name);
        //latency
        if(latency > 0) {
            logger.info("\tlatency configured: {}ms", latency);
            try {
                Thread.sleep(latency);
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            } 
        }
        //errors
        if(errors > 0) {
            logger.info("\terrors configured: {}%", errors);
            if(random.nextInt(100) < errors) {
                response.put("statusCode", HttpStatus.INTERNAL_SERVER_ERROR.value());
                return new ResponseEntity<Map<String,?>>(response, getHeaders(start),  HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        //double headers??
              
        response.put("headers", headers);
        response.put("statusCode", HttpStatus.OK.value());
        // response.put("traceID", _name);  // "traceID": "236f6e39dc932060"
        // response.put("message", _name);  // "message": "orange"
        return new ResponseEntity<Map<String,?>>(response, getHeaders(start), HttpStatus.OK);
    }

    private String getService(HttpServletRequest request) {
    	String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE); // /p/proto/serviceurl/more/more
    	String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE); // /elements/**
	    return new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path); // CATEGORY1/CATEGORY1_1/ID
    }

    @GetMapping("/p/{proto}/**")
    public ResponseEntity proxy(@RequestHeader Map<String, String> headers,
            @PathVariable(name="proto",required=true) final String proto,
            HttpServletRequest request) {
        Instant start = Instant.now();
        logger.info("invoking proxy handler...");
        String service = getService(request);
        logger.info("\tprotocol: {} service: {}", proto, service);    
        
        Map<String,Object> response = new HashMap<>();         
        if(!"http".equalsIgnoreCase(proto)) {
            response.put("statusCode", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "only http proxing is supported in current version");
            return new ResponseEntity<Map<String,?>>(response, getHeaders(start),  HttpStatus.INTERNAL_SERVER_ERROR);
        }
 
        response.put("service", name);
        //latency
        if(latency > 0) {
            logger.info("\tlatency configured: {}ms", latency);
            try {
                Thread.sleep(latency);
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            } 
        }
        //errors
        if(errors > 0) {
            logger.info("\terrors configured: {}%", errors);
            if(random.nextInt(100) < errors) {
                response.put("statusCode", HttpStatus.INTERNAL_SERVER_ERROR.value());
                return new ResponseEntity<Map<String,?>>(response, getHeaders(start),  HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        
        String requestUrl = proto + "://" + service;
        URI uri = UriComponentsBuilder.fromHttpUrl(requestUrl).build(true).toUri();

        HttpHeaders h = new HttpHeaders();
        h.add("Proxied-By", name);
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            h.set(headerName, request.getHeader(headerName));
        }
        h.remove("host"); //try not to confuse Envoy
        HttpEntity<String> httpEntity = new HttpEntity<>(h);
        ClientHttpRequestFactory factory = new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory());
        RestTemplate restTemplate = new RestTemplate(factory);
        try {
            logger.debug("\turi: {}%", uri);
            logger.debug("\tentity: {}%", httpEntity);
            ResponseEntity resp = restTemplate.exchange(uri, HttpMethod.GET, httpEntity, String.class);
            logger.debug("\tresponse: {}%", resp);
            HttpHeaders responseHeaders = getHeaders(start);
            responseHeaders.add("Proxied-By", name);
            for(Map.Entry<String,String> header: resp.getHeaders().toSingleValueMap().entrySet()) {
                responseHeaders.add(header.getKey(), header.getValue());
            }
            responseHeaders.remove("host"); //try not to confuse Envoy
            responseHeaders.remove("Transfer-Encoding"); //this breaks envoy???
            return new ResponseEntity(resp.getBody(), responseHeaders, HttpStatus.OK);


        } catch (HttpStatusCodeException e) {
            logger.error(e.getMessage());
            HttpHeaders responseHeaders = getHeaders(start);
            responseHeaders.remove("host"); //try not to confuse Envoy
            String msg = String.format("%s called %s and got error return: %s", name, uri, e.getMessage());
            return new ResponseEntity(msg, responseHeaders, HttpStatus.OK);
        }
    }

    @PostMapping("/errors/{errorRate}")
    public ResponseEntity<Map<String,?>> errors(@RequestHeader Map<String, String> headers, 
            @PathVariable(name="errorRate",required=true) final int errorRate) {
        Instant start = Instant.now();
        logger.info("Setting errors to {}%", errorRate);
        errors = errorRate;
        Map<String,Object> response = new HashMap<>();  
        response.put("service", name);
        response.put("statusCode", HttpStatus.OK.value());
        response.put("message", "errors percentage set to: " + errors + "%");
        return new ResponseEntity<Map<String,?>>(response, getHeaders(start), HttpStatus.OK);
    }

    @PostMapping("/latency/{latencyRate}")
    public ResponseEntity<Map<String,?>> latency(@RequestHeader Map<String, String> headers, 
            @PathVariable(name="latencyRate",required=true) final int latencyRate) {
        Instant start = Instant.now();
        logger.info("Setting latency to {}ms", latencyRate);
        latency = latencyRate;
        Map<String,Object> response = new HashMap<>();  
        response.put("service", name);
        response.put("statusCode", HttpStatus.OK.value());
        response.put("message", "latency set to: " + latency + "ms");
        return new ResponseEntity<Map<String,?>>(response, getHeaders(start), HttpStatus.OK);
    }

    @PostMapping("/crash/{message}")
    public void crash(@PathVariable(name="message",required=true)String message) {
        logger.info("Forcing Crash!!!");
        logger.info("\tlast words.... {}", message);
        System.exit(-1);
    }

    private HttpHeaders getHeaders(Instant start) {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("Cache-Control", "no-cache");
        responseHeaders.set("x-cluster-name", clusterName);
        responseHeaders.set("x-namespace", namespace);
        responseHeaders.set("x-service-name", name);
        responseHeaders.set("x-pod-name", podName);
        responseHeaders.set("x-istio-revision", istioRevision);
        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        responseHeaders.set("x-service-duration", timeElapsed.toString());
		return responseHeaders;
	}
}
