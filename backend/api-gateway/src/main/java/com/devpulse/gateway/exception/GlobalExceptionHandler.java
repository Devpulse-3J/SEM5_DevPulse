package com.devpulse.gateway.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GlobalExceptionHandler
        extends org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler(ErrorAttributes errorAttributes,
                                   org.springframework.boot.autoconfigure.web.WebProperties webProperties,
                                   ApplicationContext applicationContext,
                                   ServerCodecConfigurer configurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        this.setMessageWriters(configurer.getWriters());
        this.setMessageReaders(configurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        // STATUS and ERROR are themselves Includes: anything not listed here is stripped by
        // ErrorAttributeOptions.retainIncluded, which would leave the status/error keys absent.
        Map<String, Object> errorAttrs = getErrorAttributes(request,
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE,
                        ErrorAttributeOptions.Include.STATUS,
                        ErrorAttributeOptions.Include.ERROR));

        int status = (int) errorAttrs.getOrDefault("status", 500);

        // Log full error for debugging; return clean JSON to caller
        log.warn("Gateway error {}: {}", status, errorAttrs.get("message"));

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of(
                        "status", status,
                        "error", errorAttrs.getOrDefault("error", "Unknown"),
                        "path", request.path()
                )));
    }
}