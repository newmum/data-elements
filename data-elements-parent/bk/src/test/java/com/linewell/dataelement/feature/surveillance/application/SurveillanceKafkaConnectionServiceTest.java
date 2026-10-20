package com.linewell.dataelement.feature.surveillance.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SurveillanceKafkaConnectionServiceTest {
    private final SurveillanceKafkaConnectionService service = new SurveillanceKafkaConnectionService(null);

    @Test
    void rejectsSaslUntilCredentialStoreIsAvailable() {
        var command = new SurveillanceKafkaConnectionService.ConnectionCommand(
                "structured-surveillance", "test", "localhost:9092", "SASL_SSL", "ACTIVE");

        assertThrows(IllegalArgumentException.class, () -> service.save("tenant-a", command));
    }

    @Test
    void rejectsInvalidTopicNamesBeforeCallingKafka() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createTopic("tenant-a", "structured-surveillance", "bad topic"));
    }

    @Test
    void rejectsUnknownConnectionRolesBeforeReadingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.get("tenant-a", "structured-surveillance", "SOURCE"));
        var command = new SurveillanceKafkaConnectionService.ConnectionCommand(
                "structured-surveillance", "test", "localhost:9092", "PLAINTEXT", "ACTIVE");
        assertThrows(IllegalArgumentException.class,
                () -> service.save("tenant-a", "SINK", command));
    }
}
