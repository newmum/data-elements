package com.linewell.dataelement.feature.surveillance.application;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceResourceTopic;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceResourceTopicRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurveillanceResourceTopicServiceTest {
    private final SurveillanceResourceTopicRepository repository = mock(SurveillanceResourceTopicRepository.class);
    private final SurveillanceKafkaConnectionService kafka = mock(SurveillanceKafkaConnectionService.class);
    private final SurveillanceResourceTopicService service = new SurveillanceResourceTopicService(repository, kafka);

    @Test
    void creatingResourceRequiresExistingInputAndOutputTopics() {
        when(repository.insert(any())).thenAnswer(call -> call.getArgument(0));
        var command = command("topic_person", "person.result.person");

        SurveillanceResourceTopic item = service.create("tenant-a", command);

        assertEquals("person.result.person", item.resultTopic());
        verify(kafka).requireExistingTopic("tenant-a", "structured-surveillance", "INPUT", "topic_person");
        verify(kafka).requireExistingTopic("tenant-a", "structured-surveillance", "OUTPUT", "person.result.person");
    }

    @Test
    void changingOnlyResultTopicChecksOutputCluster() {
        var previous = new SurveillanceResourceTopic(null, "tenant-a", "structured-surveillance", "person",
                "person_bus", "大巴购票", "topic_person", "person.result.old", "ID_CARD_NO", "idCardNo",
                null, "ACTIVE", null, null);
        when(repository.find("tenant-a", "structured-surveillance", "person_bus")).thenReturn(Optional.of(previous));
        when(repository.update(any())).thenAnswer(call -> call.getArgument(0));

        SurveillanceResourceTopic item = service.update("tenant-a", "person_bus",
                command("topic_person", "person.result.new"));

        assertEquals("person.result.new", item.resultTopic());
        verify(kafka).requireExistingTopic("tenant-a", "structured-surveillance", "OUTPUT", "person.result.new");
        org.mockito.Mockito.verifyNoMoreInteractions(kafka);
    }

    private SurveillanceResourceTopicService.ResourceTopicCommand command(String inputTopic, String resultTopic) {
        return new SurveillanceResourceTopicService.ResourceTopicCommand("structured-surveillance", "person",
                "person_bus", "大巴购票", inputTopic, resultTopic, "ID_CARD_NO", "idCardNo", null, "ACTIVE");
    }
}
