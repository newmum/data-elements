package com.linewell.dataelement.metautil.explorer;

import com.linewell.dataelement.metautil.jdbc.BundledHiveClient;
import java.net.URLClassLoader;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundledHiveClientTest {
    @Test void realPackagedClientIncludesKerberosMetricsAndStaysOutsideMrsClasspath()throws Exception {
        var driver=BundledHiveClient.openSourceJar();assertSame(driver,BundledHiveClient.openSourceJar());
        try(var zip=new ZipFile(driver.toFile())){assertNotNull(zip.getEntry("org/apache/hive/jdbc/HiveDriver.class"));}
        var common=driver.resolveSibling("hadoop-common-3.1.0.jar");
        try(var zip=new ZipFile(common.toFile())){assertNotNull(zip.getEntry("org/apache/hadoop/metrics2/annotation/Metric.class"));}
        // A raw isolated loader proves the payload supplies its own Hadoop annotations.
        var auth=driver.resolveSibling("hadoop-auth-3.3.6.jar");
        var annotations=driver.resolveSibling("hadoop-annotations-3.1.0.jar");
        var guava=driver.resolveSibling("guava-19.0.jar");
        try(var loader=new URLClassLoader(new java.net.URL[]{driver.toUri().toURL(),common.toUri().toURL(),auth.toUri().toURL(),annotations.toUri().toURL(),guava.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var type=Class.forName("org.apache.hive.jdbc.HiveDriver",true,loader);var instance=(java.sql.Driver)type.getDeclaredConstructor().newInstance();
            assertTrue(instance.acceptsURL("jdbc:hive2://validation.invalid:10000/validation"));
            assertSame(loader,Class.forName("org.apache.hadoop.metrics2.annotation.Metric",false,loader).getClassLoader());
            assertSame(loader,Class.forName("org.apache.hadoop.util.PlatformName",true,loader).getClassLoader());
            // The old authentication client reflects into a JDK-private GSS class;
            // require this public operation to work on Java 21 without add-exports.
            var kerberos=Class.forName("org.apache.hadoop.security.authentication.util.KerberosUtil",true,loader);
            assertEquals("1.2.840.113554.1.2.2.1",kerberos.getMethod("getOidInstance",String.class)
                    .invoke(null,"NT_GSS_KRB5_PRINCIPAL").toString());
            // Kerberos initializes UGI metrics before the first JDBC transport is opened.
            var ugi=Class.forName("org.apache.hadoop.security.UserGroupInformation",true,loader);
            var configuration=Class.forName("org.apache.hadoop.conf.Configuration",true,loader);
            var config=configuration.getConstructor(boolean.class).newInstance(false);
            configuration.getMethod("set",String.class,String.class).invoke(config,"hadoop.security.authentication","simple");
            ugi.getMethod("setConfiguration",configuration).invoke(null,config);
            assertNotNull(ugi.getMethod("getCurrentUser").invoke(null));
            assertNotEquals(org.apache.hive.jdbc.HiveDriver.class.getClassLoader(),loader);
        }
    }
}
