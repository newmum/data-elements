package com.haitong.config;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All database settings live under db in config/application.yml. */
@ConfigurationProperties("db")
public class DatabaseProperties {
    private String type="mysql",host="127.0.0.1",name="haitong",username="haitong",password="",url="";
    private int port=0,poolSize=10;
    private boolean initSchema=false;
    public String getType(){return type;} public void setType(String v){type=v;}
    public String getHost(){return host;} public void setHost(String v){host=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public String getUrl(){return url;} public void setUrl(String v){url=v;}
    public int getPort(){return port;} public void setPort(int v){port=v;}
    public int getPoolSize(){return poolSize;} public void setPoolSize(int v){poolSize=v;}
    public boolean isInitSchema(){return initSchema;} public void setInitSchema(boolean v){initSchema=v;}
}
