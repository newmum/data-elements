package com.linewell.dataelement.metautil.jdbc;

import java.nio.file.*;
import java.security.AccessController;
import java.sql.*;
import java.util.*;
import javax.security.auth.Subject;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HiveKerberosSupportTest {
    @TempDir Path directory;
    @Test void noAuthenticationPreservesTheSelectedDriverAndConnection()throws Exception {
        Driver nativeDriver=mock(Driver.class);Connection connection=mock(Connection.class);String url="jdbc:hive2://validation.invalid/validation;auth=none";Properties properties=new Properties();properties.setProperty("user","validation");
        when(nativeDriver.connect(eq(url),any())).thenReturn(connection);assertSame(connection,HiveKerberosSupport.connect(nativeDriver,url,properties));verify(nativeDriver).connect(url,properties);
    }
    @Test void missingCredentialsCannotFallBackToTheProcessTicketCache()throws Exception {
        Driver driver=mock(Driver.class);String url="jdbc:hive2://validation.invalid/validation;principal=hive/validation.invalid@VALIDATION.TEST";
        assertThrows(SQLException.class,()->HiveKerberosSupport.connect(driver,url,new Properties()));verifyNoInteractions(driver);
    }
    @Test void realKdcLoginsIsolatePrincipalsAndRejectTheWrongKeytabAndConflictingGlobalConfiguration()throws Exception {
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        var kdc=new SimpleKdcServer();kdc.setWorkDir(directory.toFile());kdc.setKdcRealm("VALIDATION.TEST");kdc.setKdcHost("127.0.0.1");kdc.setAllowUdp(false);kdc.setKdcTcpPort(port);kdc.init();
        Path a=directory.resolve("a.keytab"),b=directory.resolve("b.keytab");kdc.createPrincipal("validation_a@VALIDATION.TEST");kdc.exportPrincipal("validation_a@VALIDATION.TEST",a.toFile());kdc.createPrincipal("validation_b@VALIDATION.TEST");kdc.exportPrincipal("validation_b@VALIDATION.TEST",b.toFile());kdc.start();
        Path config=directory.resolve("client.conf");Files.writeString(config,"[libdefaults]\n default_realm = VALIDATION.TEST\n dns_lookup_kdc = false\n udp_preference_limit = 1\n[realms]\n VALIDATION.TEST = {\n  kdc = 127.0.0.1:"+port+"\n }\n");
        String previous=System.getProperty("java.security.krb5.conf");System.setProperty("java.security.krb5.conf",config.toString());ClassLoader loader=Thread.currentThread().getContextClassLoader();
        try {
            List<Subject> subjects=new ArrayList<>();List<String> urls=new ArrayList<>();Driver driver=mock(Driver.class);
            when(driver.connect(anyString(),any())).thenAnswer(call->{subjects.add(Subject.getSubject(AccessController.getContext()));urls.add(call.getArgument(0));Properties properties=call.getArgument(1);assertFalse(properties.containsKey(HiveKerberosSupport.KEYTAB));return mock(Connection.class);});
            String url="jdbc:hive2://validation.invalid/validation;principal=hive/validation.invalid@VALIDATION.TEST;sasl.qop=auth";
            for(String user:List.of("validation_a","validation_b")){
                Properties p=credentials(user,user.endsWith("a")?a:b,config);
                try(Connection connection=HiveKerberosSupport.connect(driver,url,p)){
                    Subject subject=subjects.getLast();assertNotNull(subject);assertEquals(Set.of(user+"@VALIDATION.TEST"),subject.getPrincipals().stream().map(java.security.Principal::getName).collect(java.util.stream.Collectors.toSet()));assertFalse(subject.getPrivateCredentials().isEmpty());
                }
                assertTrue(subjects.getLast().getPrivateCredentials().isEmpty());assertSame(loader,Thread.currentThread().getContextClassLoader());
            }
            assertNotSame(subjects.get(0),subjects.get(1));assertTrue(urls.stream().allMatch(u->u.contains(";saslQop=auth")&&!u.contains(";sasl.qop=")));
            int successful=subjects.size();assertThrows(SQLException.class,()->HiveKerberosSupport.connect(driver,url,credentials("validation_a",b,config)));assertEquals(successful,subjects.size());
            Path other=directory.resolve("other.conf");Files.writeString(other,"[libdefaults]\n default_realm = OTHER.TEST\n");assertThrows(SQLException.class,()->HiveKerberosSupport.connect(driver,url,credentials("validation_a",a,other)));assertEquals(config.toString(),System.getProperty("java.security.krb5.conf"));
            String privateUrl=url+";user.principal=validation_a@VALIDATION.TEST;user.keytab="+a+";user.krb5.conf="+config;
            try(Connection ignored=HiveKerberosSupport.connect(driver,privateUrl,new Properties())){assertFalse(urls.getLast().contains("keytab"));assertFalse(urls.getLast().contains("krb5.conf"));}
        }finally{if(previous==null)System.clearProperty("java.security.krb5.conf");else System.setProperty("java.security.krb5.conf",previous);kdc.stop();}
    }
    private static Properties credentials(String user,Path keytab,Path config){Properties p=new Properties();p.setProperty(HiveKerberosSupport.USER_PRINCIPAL,user+"@VALIDATION.TEST");p.setProperty(HiveKerberosSupport.KEYTAB,keytab.toString());p.setProperty(HiveKerberosSupport.KRB5_CONF,config.toString());return p;}
}
