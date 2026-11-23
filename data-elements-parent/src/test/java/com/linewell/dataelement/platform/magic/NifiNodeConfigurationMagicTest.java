package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class NifiNodeConfigurationMagicTest {
    static MagicScriptLambdaFunction function() {
        try {
            var script = MagicScript.create(CanonicalMagicSources.byPath(
                    "function/01.通用函数/80.读取NiFi节点配置状态.ms", "nifi_node_configuration_v1"), null);
            return (context, args) -> script.execute(new MagicScriptContext(Map.of("node", args[0])));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> inspect(Map<String, Object> node) {
        return (Map<String, Object>) function().apply(null, new Object[]{node});
    }

    @Test void eachNodeUsesOnlyItsOwnAuthenticationAndPublicResultsContainNoCredentials() {
        var node = new LinkedHashMap<String, Object>(Map.of("tid", "secondary", "node_name", "分节点",
                "node_code", "secondary", "base_url", "http://anonymous.example:8080"));
        assertThat(inspect(node)).containsEntry("configurationValid", true)
                .containsEntry("authenticationMode", "ANONYMOUS_HTTP");
        node.put("base_url", "https://secured.example:8443");
        var missing = inspect(node);
        assertThat(missing).containsEntry("configurationValid", false);
        assertThat(missing.get("configurationMessage").toString())
                .contains("分节点", "https://secured.example:8443", "缺少NiFi用户名", "缺少NiFi密码");
        node.put("auth_username", "validation-user");
        assertThat(inspect(node).get("configurationMessage").toString()).contains("缺少NiFi密码")
                .doesNotContain("缺少NiFi用户名");
        node.put("auth_password", "validation-password");
        assertThat(inspect(node)).containsEntry("configurationValid", true)
                .containsEntry("authenticationMode", "USERNAME_PASSWORD");
        assertThat(inspect(node).toString()).doesNotContain("validation-user", "validation-password", "auth_password");
        node.put("base_url", "http://anonymous.example:8080");
        node.put("auth_password", "");
        assertThat(inspect(node).get("configurationMessage").toString())
                .contains("缺少NiFi密码", "HTTP免认证节点须同时留空");
    }

    @Test void camelCaseProjectionWorksAndMalformedAddressesNeverExposeEmbeddedSecrets() {
        var node = Map.<String, Object>of("tid", "node", "nodeName", "接入节点", "nodeCode", "node",
                "baseUrl", "https://validation-user:validation-secret@node.example:8443?token=validation-token");
        var status = inspect(node);
        assertThat(status).containsEntry("configurationValid", false);
        assertThat(status.toString()).contains("node.example:8443")
                .doesNotContain("validation-user", "validation-secret", "validation-token");
    }
}
