package com.haitong.core;

import com.haitong.config.RelayProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonCompatibilityTest {
    @Test void longPhotoStringsFollowConfiguredMessageCapacityWithoutTruncation() {
        var properties=new RelayProperties();properties.getLimits().setMaxMessageBytes(2*1024*1024);
        var json=new Json(properties);String picture="A".repeat(1_594_000);
        assertEquals(picture,json.object(("{\"pic_byte\":\""+picture+"\"}").getBytes(StandardCharsets.UTF_8)).get("pic_byte"));
        var small=new Json();
        var error=assertThrows(RelayException.class,()->small.object(("{\"pic_byte\":\""+picture+"\"}").getBytes(StandardCharsets.UTF_8)));
        assertEquals("JSON_LIMIT_EXCEEDED",error.code());assertFalse(error.getMessage().contains(picture));
    }
    @Test void malformedDuplicateTrailingAndExcessiveDepthRemainClearErrorsWithoutEchoingContent() {
        var json=new Json();
        for(String raw:new String[]{"{\"x\":", "{\"x\":1,\"x\":2}","{} {}"}) {
            var error=assertThrows(RelayException.class,()->json.object(raw.getBytes(StandardCharsets.UTF_8)));
            assertEquals("INVALID_BODY",error.code());
        }
        String nested="{\"x\":".repeat(65)+"0"+"}".repeat(65);
        assertEquals("JSON_LIMIT_EXCEEDED",assertThrows(RelayException.class,()->json.object(nested.getBytes(StandardCharsets.UTF_8))).code());
    }
}
