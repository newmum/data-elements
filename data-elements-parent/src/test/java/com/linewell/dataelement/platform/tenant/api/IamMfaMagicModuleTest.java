package com.linewell.dataelement.platform.tenant.api;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class IamMfaMagicModuleTest {
 @Test void totpAndSingleUseChallenge(){var m=new IamMfaMagicModule();String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";assertEquals("287082",m.code(secret,1));assertEquals(1,m.matchingStepAt(secret,"287082",59));assertEquals(-1,m.matchingStepAt(secret,"000000",59));String ticket=m.challenge("OPERATOR","id",4,"LOGIN");assertTrue(m.attempt(ticket,"WRONG").isEmpty());String another=m.challenge("AUTH_ACCOUNT","id",4,"LOGIN");for(int i=0;i<5;i++)assertFalse(m.attempt(another,"LOGIN").isEmpty());assertTrue(m.attempt(another,"LOGIN").isEmpty());String single=m.challenge("AUTH_ACCOUNT","id",4,"LOGIN");assertTrue(m.consume(single));assertFalse(m.consume(single));}
}
