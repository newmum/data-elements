package com.linewell.dataelement.metautil.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * WebService只读协议解析：WSDL 1.1绑定、SOAP 1.1/1.2信封和有界XML响应。
 * 不访问数据库、不读取外部实体或导入的schema；输出元数据及统一JSON树供既有探查器使用。
 * 每次解析独立创建安全DOM解析器，拒绝DTD、外部实体及过深/过大的XML，不保存认证信息。
 */
public final class WebServiceXmlAdapter {
    static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    private static final String WSDL = "http://schemas.xmlsoap.org/wsdl/";
    private static final String BIND11 = "http://schemas.xmlsoap.org/wsdl/soap/";
    private static final String BIND12 = "http://schemas.xmlsoap.org/wsdl/soap12/";
    private WebServiceXmlAdapter() { }

    static HttpResponse.BodyHandler<byte[]> boundedResponse() {
        var buffer = new ByteArrayOutputStream();
        HttpResponse.BodyHandler<Void> consumer = HttpResponse.BodyHandlers.ofByteArrayConsumer(chunk -> chunk.ifPresent(bytes -> {
            if ((long) buffer.size() + bytes.length > 5 * 1024 * 1024)
                throw new IllegalArgumentException("WebService响应超过5MiB探查限制");
            buffer.writeBytes(bytes);
        }));
        return info -> HttpResponse.BodySubscribers.mapping(consumer.apply(info), ignored -> buffer.toByteArray());
    }

    static boolean isSoap(Map<String, Object> source) {
        return "SOAP".equalsIgnoreCase(String.valueOf(source.get("apiProtocol")))
                || "WEBSERVICE".equalsIgnoreCase(String.valueOf(source.get("apiProtocol")));
    }

    static Element parse(byte[] xml) {
        if (xml.length == 0 || xml.length > 5 * 1024 * 1024) throw new IllegalArgumentException("WebService XML为空或超过5MiB限制");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) throws SAXParseException { throw e; }
                public void error(SAXParseException e) throws SAXParseException { throw e; }
                public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            Element root = builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
            checkSize(root, 0, new int[]{0});
            return root;
        } catch (Exception e) {
            throw new IllegalArgumentException("WebService XML格式无效，且不允许DOCTYPE或外部实体", e);
        }
    }

    private static void checkSize(Element node, int depth, int[] count) {
        if (depth > 40 || ++count[0] > 20000) throw new IllegalArgumentException("XML层级或元素数量超过探查限制");
        for (Element child : children(node)) checkSize(child, depth + 1, count);
    }

    static void validateRequest(String body, String version) {
        if (body.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) throw new IllegalArgumentException("SOAP请求最多1MiB");
        Element envelope = parse(body.getBytes(StandardCharsets.UTF_8));
        String ns = "1.2".equals(version) ? SOAP12 : SOAP11;
        if (!"Envelope".equals(envelope.getLocalName()) || !ns.equals(envelope.getNamespaceURI()))
            throw new IllegalArgumentException("SOAP请求Envelope命名空间与所选版本不一致");
        Element payload = child(envelope, ns, "Body");
        if (payload == null || children(payload).isEmpty()) throw new IllegalArgumentException("请填写SOAP Body中的操作和参数");
    }

    static JsonNode responseTree(byte[] xml, ObjectMapper mapper) {
        Element root = parse(xml);
        String ns = root.getNamespaceURI();
        if (!"Envelope".equals(root.getLocalName()) || !(SOAP11.equals(ns) || SOAP12.equals(ns)))
            throw new IllegalArgumentException("服务未返回有效的SOAP Envelope");
        Element body = child(root, ns, "Body");
        if (body == null) throw new IllegalArgumentException("SOAP响应缺少Body");
        Element fault = child(body, ns, "Fault");
        if (fault != null) {
            String code = text(child(fault, null, "faultcode"));
            String reason = text(child(fault, null, "faultstring"));
            if (SOAP12.equals(ns)) {
                Element codeNode = child(fault, ns, "Code"), reasonNode = child(fault, ns, "Reason");
                code = codeNode == null ? "" : text(child(codeNode, ns, "Value"));
                reason = reasonNode == null ? "" : text(child(reasonNode, ns, "Text"));
            }
            throw new IllegalArgumentException("SOAP Fault：" + bounded(code, 80) + " " + bounded(reason, 240));
        }
        return mapper.valueToTree(value(body, mapper));
    }

    private static Object value(Element element, ObjectMapper mapper) {
        List<Element> elements = children(element).stream()
                .filter(item -> !XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(item.getNamespaceURI())).toList();
        if (elements.isEmpty()) {
            String content = element.getTextContent().trim();
            // 部分.NET服务把业务JSON放在Result文本/CDATA中；仍采用既有有界JSON读取。
            if ((content.startsWith("{") && content.endsWith("}")) || (content.startsWith("[") && content.endsWith("]"))) {
                try { return mapper.readTree(content); } catch (Exception ignored) { }
            }
            return content;
        }
        Map<String, List<Object>> groups = new LinkedHashMap<>();
        for (Element item : elements) groups.computeIfAbsent(item.getLocalName(), key -> new ArrayList<>()).add(value(item, mapper));
        Map<String, Object> result = new LinkedHashMap<>();
        groups.forEach((key, items) -> result.put(key, items.size() == 1 ? items.getFirst() : items));
        return result;
    }

    static Map<String, Object> describe(byte[] xml, URI wsdlUrl) {
        Element root = parse(xml);
        if (!WSDL.equals(root.getNamespaceURI()) || !"definitions".equals(root.getLocalName()))
            throw new IllegalArgumentException("当前地址未返回WSDL 1.1定义；可直接填写服务地址和SOAP XML");
        List<Map<String, Object>> operations = new ArrayList<>();
        var ports = root.getElementsByTagNameNS(WSDL, "port");
        for (int i = 0; i < ports.getLength(); i++) {
            Element port = (Element) ports.item(i);
            Element address = child(port, BIND11, "address");
            if (address == null) address = child(port, BIND12, "address");
            if (address == null) continue;
            String bindingName = port.getAttribute("binding");
            String prefix = bindingName.contains(":") ? bindingName.substring(0, bindingName.indexOf(':')) : null;
            if (!root.getAttribute("targetNamespace").equals(port.lookupNamespaceURI(prefix))) continue;
            bindingName = bindingName.substring(bindingName.indexOf(':') + 1);
            for (Element binding : children(root)) {
                if (!WSDL.equals(binding.getNamespaceURI()) || !"binding".equals(binding.getLocalName()) || !bindingName.equals(binding.getAttribute("name"))) continue;
                for (Element operation : children(binding)) {
                    if (!WSDL.equals(operation.getNamespaceURI()) || !"operation".equals(operation.getLocalName())) continue;
                    Element details = child(operation, BIND11, "operation");
                    if (details == null) details = child(operation, BIND12, "operation");
                    if (details == null) continue;
                    if (operations.size() >= 200) throw new IllegalArgumentException("WSDL操作超过200项，请直接填写目标操作");
                    URI endpoint = wsdlUrl.resolve(address.getAttribute("location"));
                    if (!List.of("http", "https").contains(endpoint.getScheme()) || endpoint.getUserInfo() != null) continue;
                    operations.add(Map.of("name", operation.getAttribute("name"), "namespace", root.getAttribute("targetNamespace"),
                            "action", details.getAttribute("soapAction"), "version", BIND12.equals(details.getNamespaceURI()) ? "1.2" : "1.1",
                            "endpoint", endpoint.toString(), "binding", bindingName));
                }
            }
        }
        if (operations.isEmpty()) throw new IllegalArgumentException("WSDL未包含可用的本地SOAP绑定；导入的定义请直接填写服务地址和SOAP XML");
        return Map.of("success", true, "connected", false, "metadataOnly", true, "product", "WebService WSDL",
                "operations", operations, "message", "WSDL已解析，请填写操作参数后执行连通性测试");
    }

    static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) if (node instanceof Element element) result.add(element);
        return result;
    }
    private static Element child(Element parent, String namespace, String name) {
        return children(parent).stream().filter(item -> name.equals(item.getLocalName())
                && (namespace == null || namespace.equals(item.getNamespaceURI()))).findFirst().orElse(null);
    }
    private static String text(Element element) { return element == null ? "" : element.getTextContent().trim(); }
    private static String bounded(String value, int size) { return value.length() > size ? value.substring(0, size) : value; }
}
