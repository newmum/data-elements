package com.linewell.dataelement.nifi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.apache.kafka.common.utils.AppInfoParser;
import org.apache.nifi.kafka.service.api.KafkaConnectionService;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

class HaitongLegacyKafkaConnectionServiceTest {
    public static class ProbeProcessor extends AbstractProcessor {
        @Override
        public void onTrigger(ProcessContext context, ProcessSession session) {
        }
    }

    @Test
    void matchesTheSdkConnectionContractWithAnIsolatedLegacyClient() throws Exception {
        var runner = TestRunners.newTestRunner(new ProbeProcessor());
        var service = new HaitongLegacyKafkaConnectionService();
        runner.addControllerService("legacy", service);
        runner.setProperty(service, "bootstrap.servers", "validation.invalid:9092");
        runner.setProperty(service, "security.protocol", "PLAINTEXT");
        runner.setProperty(service, "isolation.level", "read_uncommitted");
        try {
            runner.enableControllerService(service);
            assertInstanceOf(KafkaConnectionService.class, service);
            assertEquals("kafka://validation.invalid:9092", service.getBrokerUri());
            assertEquals("3.7.2", AppInfoParser.getVersion());
        } finally {
            runner.disableControllerService(service);
            runner.shutdown();
        }
    }
}
