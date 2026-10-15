package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HiveGlobalRecordServicesTest {
    private final NifiClient nifi=mock(NifiClient.class);
    HiveGlobalRecordServicesTest(){when(nifi.get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class)).thenReturn(HiveServiceFixtures.rootFlow());}
    @Test void resolvesRealRootIdsAndIgnoresSameNamedTaskServices() {
        var services=new ArrayList<>(HiveServiceFixtures.codecs());
        services.add(HiveServiceFixtures.codec("task-writer",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","task","ENABLED"));
        services.add(HiveServiceFixtures.codec("task-reader",HiveTemporalJsonPolicy.READER_NAME,"org.apache.nifi.json.JsonTreeReader","task","ENABLED"));
        when(nifi.listControllerServices("root")).thenReturn(services);
        var actual=HiveGlobalRecordServices.resolve(nifi);
        assertThat(actual.writerId()).isEqualTo("global-json-writer");assertThat(actual.readerId()).isEqualTo("global-json-reader");
        verify(nifi).listControllerServices("root");verify(nifi).get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class);verifyNoMoreInteractions(nifi);
    }
    @Test void missingGlobalDoesNotFallBackToTaskScope() {
        when(nifi.listControllerServices("root")).thenReturn(List.of(HiveServiceFixtures.codec("task",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","task","ENABLED")));
        assertThatThrownBy(()->HiveGlobalRecordServices.resolve(nifi)).hasMessageContaining("缺少全局服务").hasMessageContaining(HiveTemporalJsonPolicy.WRITER_NAME);
    }
    @Test void duplicateRootNamesAreRejected() {
        var services=new ArrayList<>(HiveServiceFixtures.codecs());services.add(HiveServiceFixtures.codec("other",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED"));
        when(nifi.listControllerServices("root")).thenReturn(services);
        assertThatThrownBy(()->HiveGlobalRecordServices.resolve(nifi)).hasMessageContaining("多个同名");
    }
    @Test void disabledGlobalIsNotEnabledByTheCompiler() {
        when(nifi.listControllerServices("root")).thenReturn(List.of(HiveServiceFixtures.codec("disabled",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","DISABLED")));
        assertThatThrownBy(()->HiveGlobalRecordServices.resolve(nifi)).hasMessageContaining("尚未启用");
        verify(nifi).get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class);verify(nifi).listControllerServices("root");verifyNoMoreInteractions(nifi);
    }
    @Test void incompatibleServiceTypeIsRejected() {
        when(nifi.listControllerServices("root")).thenReturn(List.of(HiveServiceFixtures.codec("wrong",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.csv.CSVRecordSetWriter","root","ENABLED")));
        assertThatThrownBy(()->HiveGlobalRecordServices.resolve(nifi)).hasMessageContaining("类型不正确");
    }
    @Test void configuredApplicationRootCannotShadowTheActualNiFiRoot() {
        when(nifi.getRootProcessGroupId()).thenReturn("application");
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs());
        assertThat(HiveGlobalRecordServices.resolve(nifi).rootId()).isEqualTo("root");
        verify(nifi,never()).listControllerServices("application");
        verify(nifi,never()).getRootProcessGroupId();
    }
}
