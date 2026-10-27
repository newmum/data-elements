package com.linewell.dataelement.metautil.explorer;

import static org.mockito.Mockito.*;
import java.sql.Driver;
import java.sql.Connection;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VendorDriverIsolationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void explicitOpenHiveCannotFallBackToBundledMrsClient() {
        String previous=System.getProperty("data-elements.jdbc.driver-directory");
        System.setProperty("data-elements.jdbc.driver-directory",directory.toString());
        try {
            var failure=assertThrows(IllegalArgumentException.class,AbstractMetadataExplorer::loadOpenSourceHiveDriver);
            assertTrue(failure.getMessage().contains("open-source-hive"));
        } finally {restoreDirectory(previous);}
    }

    @Test void openHiveLoadsItsSelectedClientEvenWhenMrsHasTheSameDriverClass() throws Exception {
        var client=directory.resolve("open-source-hive");java.nio.file.Files.createDirectories(client);
        var source=directory.resolve("HiveDriver.java");
        java.nio.file.Files.writeString(source,"""
                package org.apache.hive.jdbc;
                public class HiveDriver implements java.sql.Driver {
                    public java.sql.Connection connect(String url,java.util.Properties info){return null;}
                    public boolean acceptsURL(String url){return url.startsWith("jdbc:hive2:");}
                    public java.sql.DriverPropertyInfo[] getPropertyInfo(String url,java.util.Properties info){return new java.sql.DriverPropertyInfo[0];}
                    public int getMajorVersion(){return 42;}
                    public int getMinorVersion(){return 0;}
                    public boolean jdbcCompliant(){return false;}
                    public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getLogger("validation");}
                }
                """);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",directory.toString(),source.toString()));
        try(var jar=new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(client.resolve("hive-jdbc-validation.jar")))) {
            jar.putNextEntry(new java.util.jar.JarEntry("org/apache/hive/jdbc/HiveDriver.class"));
            java.nio.file.Files.copy(directory.resolve("org/apache/hive/jdbc/HiveDriver.class"),jar);jar.closeEntry();
        }
        String previous=System.getProperty("data-elements.jdbc.driver-directory");
        System.setProperty("data-elements.jdbc.driver-directory",directory.toString());
        java.net.URLClassLoader loader=null;
        try {
            Driver driver=AbstractMetadataExplorer.loadOpenSourceHiveDriver();
            assertEquals(42,driver.getMajorVersion());
            assertTrue(driver.acceptsURL("jdbc:hive2://validation.invalid/business"));
            assertSame(driver,AbstractMetadataExplorer.loadOpenSourceHiveDriver());
            loader=(java.net.URLClassLoader)driver.getClass().getClassLoader();
        } finally {restoreDirectory(previous);if(loader!=null)loader.close();}
    }

    private static void restoreDirectory(String previous) {
        if(previous==null)System.clearProperty("data-elements.jdbc.driver-directory");
        else System.setProperty("data-elements.jdbc.driver-directory",previous);
    }

    @Test void identicalUrlPrefixesStillUseTheExplicitVendorDriver() throws Exception {
        Driver nativeGauss=mock(Driver.class),openGauss=mock(Driver.class);
        Connection nativeConnection=mock(Connection.class),openConnection=mock(Connection.class);
        String url="jdbc:opengauss://validation.invalid/business";
        when(nativeGauss.connect(eq(url),any())).thenReturn(nativeConnection);
        when(openGauss.connect(eq(url),any())).thenReturn(openConnection);
        var nativeSource=new AbstractMetadataExplorer.VendorDriverDataSource(nativeGauss,url,"validation","",new Properties());
        var openSource=new AbstractMetadataExplorer.VendorDriverDataSource(openGauss,url,"validation","",new Properties());
        assertSame(nativeConnection,nativeSource.getConnection());assertSame(openConnection,openSource.getConnection());
        verify(nativeGauss,times(1)).connect(eq(url),any());verify(openGauss,times(1)).connect(eq(url),any());
    }
}
