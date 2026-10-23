
package com.signalops.core.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import com.signalops.core.incident.enums.Severity;
import com.signalops.core.incident.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Testcontainers
class IncidentServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:14-alpine")
                    .withDatabaseName("signalops_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
    }

    @Autowired
    private
    IncidentService incidentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void firstRequestCreatesIncidentIdempotencyRecordAndOutboxEvent() {
        String key = "integration-" + UUID.randomUUID();
        String hash = "a".repeat(64);

        IncidentService.IncidentCreationResult result =
                incidentService.createIncident(
                        "Checkout latency",
                        "Checkout API p95 latency increased",
                        Severity.HIGH,
                        "monitoring",
                        Map.of("service", "checkout"),
                        key,
                        hash
                );

        assertNotNull(result.incident());
        assertNotNull(result.incident().getId());
        assertTrue(!result.replayed());

        UUID incidentId = result.incident().getId();

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM incidents WHERE id = ?",
                        incidentId
                )
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM idempotency_records "
                                + "WHERE idempotency_key = ?",
                        key
                )
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM outbox_events "
                                + "WHERE aggregate_id = ?",
                        incidentId
                )
        );
    }

    @Test
    void sameKeyAndSameRequestReturnsExistingIncidentWithoutDuplicates() {
        String key = "integration-" + UUID.randomUUID();
        String hash = "b".repeat(64);

        IncidentService.IncidentCreationResult first =
                incidentService.createIncident(
                        "Database connection issue",
                        "Connection pool usage is high",
                        Severity.HIGH,
                        "monitoring",
                        Map.of("service", "checkout"),
                        key,
                        hash
                );

        IncidentService.IncidentCreationResult second =
                incidentService.createIncident(
                        "Database connection issue",
                        "Connection pool usage is high",
                        Severity.HIGH,
                        "monitoring",
                        Map.of("service", "checkout"),
                        key,
                        hash
                );

        assertEquals(
                first.incident().getId(),
                second.incident().getId()
        );

        assertTrue(!first.replayed());
        assertTrue(second.replayed());

        UUID incidentId = first.incident().getId();

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM incidents WHERE id = ?",
                        incidentId
                )
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM idempotency_records "
                                + "WHERE idempotency_key = ?",
                        key
                )
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM outbox_events "
                                + "WHERE aggregate_id = ?",
                        incidentId
                )
        );
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflictWithoutDuplicates() {
        String key = "integration-" + UUID.randomUUID();

        IncidentService.IncidentCreationResult first =
                incidentService.createIncident(
                        "Checkout latency",
                        "Latency increased",
                        Severity.HIGH,
                        "monitoring",
                        Map.of(),
                        key,
                        "c".repeat(64)
                );

        UUID originalIncidentId = first.incident().getId();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incidentService.createIncident(
                        "Checkout latency",
                        "A different request description",
                        Severity.HIGH,
                        "monitoring",
                        Map.of(),
                        key,
                        "d".repeat(64)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM incidents WHERE id = ?",
                        originalIncidentId
                )
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM idempotency_records "
                                + "WHERE idempotency_key = ?",
                        key
                )
        );

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM outbox_events "
                                + "WHERE aggregate_id = ?",
                        originalIncidentId
                )
        );
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                args
        );

        return result == null ? 0 : result;
    }
}
