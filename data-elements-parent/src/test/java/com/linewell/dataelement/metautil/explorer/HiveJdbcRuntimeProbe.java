package com.linewell.dataelement.metautil.explorer;

import com.linewell.dataelement.metautil.jdbc.BundledHiveClient;
import com.linewell.dataelement.metautil.jdbc.HiveKerberosSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.util.Properties;

/** Plain Java probe: the packaged backend supplies every Hive/Hadoop runtime dependency. */
public final class HiveJdbcRuntimeProbe {
    private HiveJdbcRuntimeProbe() {}

    public static void main(String[] args)throws Exception {
        var payload=BundledHiveClient.openSourceJar();
        Driver driver=AbstractMetadataExplorer.loadOpenSourceHiveDriver();
        var loader=HiveKerberosSupport.nativeDriver(driver).getClass().getClassLoader();
        if(loader==Class.forName("org.apache.hive.jdbc.HiveDriver").getClassLoader())
            throw new IllegalStateException("Apache and Huawei clients share a class loader");
        if(loader.getResource("core-site.xml")!=null)
            throw new IllegalStateException("Apache client inherited a packaged site configuration");
        Class.forName("org.apache.hadoop.metrics2.annotation.Metric",true,loader);
        Class.forName("org.apache.hadoop.util.PlatformName",true,loader);
        Class.forName("com.google.common.collect.Interners",true,loader);
        for(String configurationFile:args) {
            Properties configuration=new Properties();
            try(var input=Files.newInputStream(Path.of(configurationFile))){configuration.load(input);}
            String url=configuration.getProperty("jdbcUrl");configuration.remove("jdbcUrl");
            try(Connection connection=driver.connect(url,configuration);var statement=connection.createStatement()){
                statement.setQueryTimeout(30);
                try(var result=statement.executeQuery("SELECT 1")){
                    if(!result.next()||result.getInt(1)!=1)throw new IllegalStateException("Unexpected validation query result");
                }
                System.out.println("PASS packaged Hive JDBC authentication and query");
            }
        }
        System.out.println("PASS packaged Apache/Huawei Hive isolation; client="+payload.getFileName());
    }
}
