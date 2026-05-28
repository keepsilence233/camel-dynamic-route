package com.dynamic.route.camel;

import com.dynamic.route.engine.RouteNotFoundException;
import com.dynamic.route.engine.RouteResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GlobalExceptionProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionProcessor.class);

    @Override
    public void process(Exchange exchange) {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String traceId = exchange.getExchangeId();
        if (exception instanceof RouteNotFoundException) {
            log.warn("[{}] 404 {}", traceId, exception.getMessage());
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            exchange.getMessage().setBody(RouteResponse.error(traceId, "ROUTE_NOT_FOUND", exception.getMessage()));
            return;
        }
        log.error("[{}] 500 Route execution error", traceId, exception);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        exchange.getMessage().setBody(RouteResponse.error(traceId, "ROUTE_EXECUTION_ERROR", "Route execution failed"));
    }
}
