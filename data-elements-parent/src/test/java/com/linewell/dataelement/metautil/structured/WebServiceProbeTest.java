package com.linewell.dataelement.metautil.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebServiceProbeTest {
    private HttpServer server;
    private String base;
    private StructuredSourceProbeService probe;
    private final AtomicInteger requests = new AtomicInteger();
    private String response;
    private int status = 200;
    private String method, contentType, action, body;

    @BeforeEach void start() throws Exception {
        probe = new StructuredSourceProbeService(new ObjectMapper());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            method = exchange.getRequestMethod();
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            action = exchange.getRequestHeaders().getFirst("SOAPAction");
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); } catch (java.io.IOException ignored) { }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        response = envelope("1.1", "<m:queryResponse xmlns:m=\"urn:validation\"><rows><row><id>1</id><name>组织一</name></row><row><id>2</id><name>组织二</name></row></rows></m:queryResponse>");
    }
    @AfterEach void stop() { server.stop(0); }
    private static String envelope(String version, String content) {
        return "<s:Envelope xmlns:s=\"" + ("1.2".equals(version) ? WebServiceXmlAdapter.SOAP12 : WebServiceXmlAdapter.SOAP11) + "\"><s:Body>" + content + "</s:Body></s:Envelope>";
    }
    private Map<String, Object> source(String version) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("dbType", "api"); source.put("apiProtocol", "SOAP"); source.put("apiMethod", "POST");
        source.put("apiUrl", base + "/service"); source.put("apiSoapVersion", version);
        source.put("apiSoapAction", "urn:validation/query"); source.put("apiTimeoutSeconds", 5);
        source.put("apiBody", envelope(version, "<query xmlns=\"urn:validation\"/>"));
        return source;
    }
    @Test void soap11PostsXmlAndInfersBusinessRowsInOneRequest() {
        Map<String, Object> result = probe.testAndProbe(source("1.1"));
        assertThat(result).containsEntry("success", true).containsEntry("connected", true).containsEntry("product", "WebService SOAP");
        assertThat(method).isEqualTo("POST"); assertThat(contentType).startsWith("text/xml"); assertThat(action).isEqualTo("\"urn:validation/query\"");
        assertThat(body).contains("<query"); assertThat(requests.get()).isEqualTo(1);
        assertThat(result.get("tables").toString()).contains("queryResponse.rows.row", "name", "组织一", "组织二");
    }
    @Test void soap12PlacesActionInContentTypeAndUsesConfiguredBodyPath() {
        response = envelope("1.2", "<queryResponse><items><id>1</id></items><items><id>2</id></items></queryResponse>");
        var source = source("1.2"); source.put("apiCollectionPaths", "[\"$.queryResponse.items\"]");
        var result = probe.testAndProbe(source);
        assertThat(contentType).isEqualTo("application/soap+xml; charset=UTF-8; action=\"urn:validation/query\"");
        assertThat(action).isNull(); assertThat(result.get("tables").toString()).contains("recordCount=2", "$.queryResponse.items");
    }
    @Test void soapFaultIsNotReportedAsConnectivitySuccessEvenOnHttp200() {
        response = envelope("1.2", "<s:Fault><s:Code><s:Value>s:Sender</s:Value></s:Code><s:Reason><s:Text>缺少查询条件</s:Text></s:Reason></s:Fault>");
        assertThatThrownBy(() -> probe.testAndProbe(source("1.2"))).hasMessageContaining("SOAP Fault").hasMessageContaining("缺少查询条件");
    }
    @Test void soap11Http500RetainsFaultReason() {
        response = envelope("1.1", "<s:Fault><faultcode>s:Client</faultcode><faultstring>参数无效</faultstring></s:Fault>"); status = 500;
        assertThatThrownBy(() -> probe.testAndProbe(source("1.1"))).hasMessageContaining("SOAP Fault").hasMessageContaining("参数无效");
    }
    @Test void rejectsVersionMismatchAndUnsafeRequestsBeforeSending() {
        var source = source("1.1"); source.put("apiSoapVersion", "1.2");
        assertThatThrownBy(() -> probe.testAndProbe(source)).hasMessageContaining("命名空间");
        source.put("apiBody", "<!DOCTYPE s [<!ENTITY secret SYSTEM \"" + base + "/external\">]>" + envelope("1.2", "<query>&secret;</query>"));
        assertThatThrownBy(() -> probe.testAndProbe(source)).hasMessageContaining("DOCTYPE");
        assertThat(requests.get()).isZero();
    }
    @Test void rejectsResponseEntitiesWithoutFetchingTheirAddress() {
        response = "<!DOCTYPE s [<!ENTITY secret SYSTEM \"" + base + "/external\">]>" + envelope("1.1", "<queryResponse>&secret;</queryResponse>");
        assertThatThrownBy(() -> probe.testAndProbe(source("1.1"))).hasMessageContaining("DOCTYPE");
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void parsesWsdlBindingsWithoutCallingSoapOrMarkingItConnected() {
        response = """
            <definitions xmlns="http://schemas.xmlsoap.org/wsdl/" xmlns:t="urn:validation" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/" targetNamespace="urn:validation">
              <binding name="Query11"><soap:binding transport="http://schemas.xmlsoap.org/soap/http"/><operation name="query"><soap:operation soapAction="urn:query11"/></operation></binding>
              <binding name="Query12"><soap12:binding/><operation name="query"><soap12:operation soapAction="urn:query12"/></operation></binding>
              <service name="QueryService"><port name="Port11" binding="t:Query11"><soap:address location="/service11"/></port><port name="Port12" binding="t:Query12"><soap12:address location="/service12"/></port></service>
            </definitions>
            """;
        var source = source("1.1"); source.put("apiProbeAction", "wsdl"); source.put("apiWsdlUrl", base + "/service?wsdl");
        var result = probe.testAndProbe(source);
        assertThat(result).containsEntry("metadataOnly", true).containsEntry("connected", false).containsEntry("success", true);
        assertThat(method).isEqualTo("GET"); assertThat(requests.get()).isEqualTo(1);
        assertThat(result.get("operations").toString()).contains("urn:query11", "urn:query12", base + "/service11", "version=1.2");
    }
    @Test void doesNotFetchWsdlImportsOrAcceptHtmlAsWsdl() {
        response = "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"><import location=\"" + base + "/external\"/></definitions>";
        var source = source("1.1"); source.put("apiProbeAction", "wsdl"); source.put("apiWsdlUrl", base + "/service?wsdl");
        assertThatThrownBy(() -> probe.testAndProbe(source)).hasMessageContaining("本地SOAP绑定");
        assertThat(requests.get()).isEqualTo(1);
        response = "<html><body>登录页面</body></html>";
        assertThatThrownBy(() -> probe.testAndProbe(source)).hasMessageContaining("WSDL 1.1");
    }
    @Test void supportsJsonBusinessPayloadInsideSoapResult() {
        response = envelope("1.1", "<queryResponse><result><![CDATA[{\"rows\":[{\"id\":1},{\"id\":2}]}]]></result></queryResponse>");
        assertThat(probe.testAndProbe(source("1.1")).get("tables").toString()).contains("queryResponse.result.rows", "recordCount=2");
    }
    @Test void rejectsExcessiveXmlDepthAndResponseSize() {
        response = envelope("1.1", "<a>".repeat(45) + "1" + "</a>".repeat(45));
        assertThatThrownBy(() -> probe.testAndProbe(source("1.1"))).hasMessageContaining("XML");
        response = envelope("1.1", "<result>" + "x".repeat(5 * 1024 * 1024) + "</result>");
        assertThatThrownBy(() -> probe.testAndProbe(source("1.1"))).hasMessageContaining("5MiB");
    }
}
