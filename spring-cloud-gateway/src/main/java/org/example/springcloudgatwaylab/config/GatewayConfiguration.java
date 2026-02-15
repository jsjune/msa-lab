package org.example.springcloudgatwaylab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfiguration {

    @Value("${gateway.routes.server-a.id}")
    private String serverARouteId;
    @Value("${gateway.routes.server-a.path}")
    private String serverAPath;
    @Value("${gateway.routes.server-a.uri}")
    private String serverAUri;

    @Value("${gateway.routes.server-b.id}")
    private String serverBRouteId;
    @Value("${gateway.routes.server-b.path}")
    private String serverBPath;
    @Value("${gateway.routes.server-b.uri}")
    private String serverBUri;

    @Value("${gateway.routes.server-c.id}")
    private String serverCRouteId;
    @Value("${gateway.routes.server-c.path}")
    private String serverCPath;
    @Value("${gateway.routes.server-c.uri}")
    private String serverCUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Configuration for Server A
                .route(serverARouteId, r -> r.path(serverAPath)
                        .filters(f -> f.stripPrefix(1))
                        .uri(serverAUri))

                // Configuration for Server B
                .route(serverBRouteId, r -> r.path(serverBPath)
                        .filters(f -> f.stripPrefix(1))
                        .uri(serverBUri))

                // Configuration for Server C
                .route(serverCRouteId, r -> r.path(serverCPath)
                        .filters(f -> f.stripPrefix(1))
                        .uri(serverCUri))
                .build();
    }
}
