package com.linewell.dataelement.platform.tenant.api;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class IdentityProtocolV2Test {
 private final IdentityTransportMagicModule crypto=new IdentityTransportMagicModule(Base64.getEncoder().encodeToString(new byte[32]),true);
 @Test void versionTwoBindsApplicationAndInstanceAndRejectsExpiry() throws Exception {
  String secret="x".repeat(48),stored=crypto.encrypt(secret),nonce="a".repeat(32),payload="{}";long now=Instant.now().getEpochSecond();
  String signature=crypto.signature2(secret,"key","app","instance",now,nonce,payload);
  assertTrue(crypto.verify2(stored,"key","app","instance",now,nonce,payload,signature));
  assertFalse(crypto.verify2(stored,"key","other","instance",now,nonce,payload,signature));
  assertFalse(crypto.verify2(stored,"key","app","other",now,nonce,payload,signature));
  assertFalse(crypto.verify2(stored,"key","app","instance",now-301,nonce,payload,signature));
 }
 @Test void canonicalObjectOrderAndSignedAcknowledgement() throws Exception {
  var receipt=Map.of("result","APPLIED","appId","app","sourceVersion",1);var reordered=new java.util.LinkedHashMap<String,Object>();reordered.put("sourceVersion",1);reordered.put("appId","app");reordered.put("result","APPLIED");
  assertEquals(crypto.canonicalJson(receipt),crypto.canonicalJson(reordered));String stored=crypto.encrypt("y".repeat(48)),sig=crypto.receiptSignature(stored,receipt);
  assertTrue(crypto.verifyReceipt(stored,reordered,sig));assertFalse(crypto.verifyReceipt(stored,Map.of("result","APPLIED","appId","other","sourceVersion",1),sig));
 }
 @Test void unapprovedHostIsRejectedBeforeNetworking() {
  assertThrows(IllegalArgumentException.class,()->crypto.send2("https://example.com/receive","","key","app","instance","{}",10));
 }
 @Test void parsedJsonRetainsNullUnicodeAndPrimitiveTypes() {
  String text="{\"data\":{\"enabled\":true,\"name\":\"公安机构\",\"parentId\":null},\"sourceVersion\":2}";
  assertEquals(text,crypto.canonicalJson(cn.hutool.json.JSONUtil.parseObj(text)));
  assertEquals(text,crypto.canonicalText(text));
  assertEquals("\"文字\"",crypto.canonicalJson("文字"));
 }
}
