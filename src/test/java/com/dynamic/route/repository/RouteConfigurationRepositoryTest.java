package com.dynamic.route.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RouteConfigurationRepositoryTest {

    @Autowired
    private RouteConfigurationRepository repository;

    @Test
    @DisplayName("findActiveRoutes loads seeded active route")
    void findActiveRoutes_loadsSeededRoute() {
        assertThat(repository.findActiveRoutes())
            .extracting(route -> route.routeCode())
            .contains("demo-route");
    }
}
