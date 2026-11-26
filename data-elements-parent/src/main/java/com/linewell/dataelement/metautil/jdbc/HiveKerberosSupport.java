package com.linewell.dataelement.metautil.jdbc;

import java.lang.reflect.*;
import java.nio.file.*;
import java.security.PrivilegedExceptionAction;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.*;

/** Per-connection JAAS credentials for Apache Hive; no application-wide Hadoop login user or ticket-cache fallback. */
public final class HiveKerberosSupport {
    public static final String USER_PRINCIPAL="data-elements.hive.user-principal";
    public static final String KEYTAB="data-elements.hive.keytab";
    public static final String KRB5_CONF="data-elements.hive.krb5-conf";
    private static final Object CONFIGURATION_LOCK=new Object();
    private HiveKerberosSupport() {}
    public static Driver wrap(Driver driver){return new ScopedDriver(driver);}
    public static Driver nativeDriver(Driver driver){return driver instanceof ScopedDriver scoped?scoped.delegate:driver;}

    public static Connection connect(Driver driver,String url,Properties supplied)throws SQLException {
        Settings settings=settings(url,supplied);Properties properties=new Properties();properties.putAll(supplied==null?new Properties():supplied);
        for(String key:List.of(USER_PRINCIPAL,KEYTAB,KRB5_CONF))properties.remove(key);
        if(!settings.kerberos)return driver.connect(settings.url,properties);
        if(settings.principal.isBlank()||settings.keytab.isBlank())throw new SQLException("开源Hive Kerberos需要客户端principal和keytab，不能回退到进程票据或其他用户");
        Path keytab=readable(settings.keytab,"keytab");selectConfiguration(settings.krb5);
        Map<String,Object> options=new HashMap<>();options.put("useKeyTab","true");options.put("keyTab",keytab.toString());options.put("principal",settings.principal);
        options.put("storeKey","true");options.put("doNotPrompt","true");options.put("useTicketCache","false");options.put("isInitiator","true");options.put("refreshKrb5Config","true");
        Thread thread=Thread.currentThread();ClassLoader previous=thread.getContextClassLoader();LoginContext login=null;
        try {
            thread.setContextClassLoader(driver.getClass().getClassLoader());
            login=new LoginContext("DataElementsHive",new Subject(),null,new Configuration(){
                @Override public AppConfigurationEntry[] getAppConfigurationEntry(String name){return new AppConfigurationEntry[]{new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,options)};}
            });
            login.login();
            // Hive's transport obtains the current Hadoop user again. A plain JAAS
            // Subject lacks Hadoop's User principal and would otherwise select the
            // process login user. Bind this Subject without replacing that global user.
            if(driver.getClass().getName().equals("org.apache.hive.jdbc.HiveDriver")) {
                Class<?> ugi=Class.forName("org.apache.hadoop.security.UserGroupInformation",true,driver.getClass().getClassLoader());
                ugi.getMethod("getUGIFromSubject",Subject.class).invoke(null,login.getSubject());
            }
            Connection connection=Subject.doAs(login.getSubject(),(PrivilegedExceptionAction<Connection>)()->driver.connect(settings.url,properties));
            if(connection==null){login.logout();return null;}
            return retainLogin(connection,login);
        }catch(Exception failure){
            if(login!=null)try{login.logout();}catch(LoginException cleanup){failure.addSuppressed(cleanup);}
            if(failure instanceof java.security.PrivilegedActionException action&&action.getException() instanceof SQLException sql)throw sql;
            if(failure instanceof SQLException sql)throw sql;
            throw new SQLException("开源Hive Kerberos认证或握手失败，请核对客户端principal、keytab、krb5配置、时钟和服务principal",failure);
        }finally{thread.setContextClassLoader(previous);}
    }
    private static Connection retainLogin(Connection delegate,LoginContext login){
        AtomicBoolean closed=new AtomicBoolean();
        return (Connection)Proxy.newProxyInstance(HiveKerberosSupport.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"Authenticated Hive JDBC connection";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->throw new UnsupportedOperationException();};
            if(method.getName().equals("close")){
                if(closed.compareAndSet(false,true)){try{delegate.close();}finally{try{login.logout();}catch(LoginException failure){throw new SQLException("Hive连接已关闭，Kerberos凭据清理失败",failure);}}}return null;
            }
            if(method.getName().equals("unwrap")&&((Class<?>)args[0]).isInstance(proxy))return proxy;
            if(method.getName().equals("isWrapperFor")&&((Class<?>)args[0]).isInstance(proxy))return true;
            try{return method.invoke(delegate,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        });
    }
    private static Path readable(String value,String label)throws SQLException {
        try {Path path=Path.of(value);if(!path.isAbsolute()||!Files.isRegularFile(path)||!Files.isReadable(path))throw new SQLException("Hive "+label+"必须是当前执行节点可读的绝对文件路径");return path.toRealPath();}
        catch(java.io.IOException|InvalidPathException failure){throw new SQLException("Hive "+label+"文件无法读取",failure);}
    }
    private static void selectConfiguration(String configured)throws SQLException {
        if(configured.isBlank())return;
        Path requested=readable(configured,"krb5.conf");
        synchronized(CONFIGURATION_LOCK){
            String active=System.getProperty("java.security.krb5.conf");
            if(active==null||active.isBlank()){System.setProperty("java.security.krb5.conf",requested.toString());return;}
            Path current=readable(active,"JVM krb5.conf");
            try{
                if(!current.equals(requested)&&Files.mismatch(current,requested)!=-1)
                    throw new SQLException("当前JVM已经使用另一份krb5配置，请把所需realm合并到同一配置并统一启动参数，不能逐连接切换全局认证配置");
            }catch(java.io.IOException failure){throw new SQLException("Hive krb5配置比较失败",failure);}
        }
    }
    private record Settings(String url,String principal,String keytab,String krb5,boolean kerberos){}
    private static Settings settings(String url,Properties properties){
        if(url==null)return new Settings(null,"","","",false);
        String suffix="";int query=url.indexOf('?');if(query>=0){suffix=url.substring(query);url=url.substring(0,query);}
        String[] parts=url.split(";",-1);List<String> retained=new ArrayList<>();retained.add(parts[0]);Map<String,String> client=new HashMap<>();boolean kerberos=false;
        for(int i=1;i<parts.length;i++){
            String part=parts[i];int equals=part.indexOf('=');String key=equals<0?part:part.substring(0,equals),value=equals<0?"":part.substring(equals+1);
            switch(key.toLowerCase(Locale.ROOT)){
                case "user.principal"->client.put(USER_PRINCIPAL,value);
                case "user.keytab"->client.put(KEYTAB,value);
                case "user.krb5.conf"->client.put(KRB5_CONF,value);
                case "sasl.qop"->retained.add("saslQop="+value);
                default->{retained.add(part);if(key.equalsIgnoreCase("principal")&&!value.isBlank()||key.equalsIgnoreCase("auth")&&value.equalsIgnoreCase("KERBEROS"))kerberos=true;}
            }
        }
        String principal=property(properties,USER_PRINCIPAL,client),keytab=property(properties,KEYTAB,client),krb5=property(properties,KRB5_CONF,client);
        return new Settings(String.join(";",retained)+suffix,principal,keytab,krb5,kerberos);
    }
    private static String property(Properties supplied,String key,Map<String,String> fallback){String value=supplied==null?null:supplied.getProperty(key);return value==null||value.isBlank()?fallback.getOrDefault(key,""):value;}
    private record ScopedDriver(Driver delegate) implements Driver {
        @Override public Connection connect(String url,Properties properties)throws SQLException{return HiveKerberosSupport.connect(delegate,url,properties);}
        @Override public boolean acceptsURL(String url)throws SQLException{return delegate.acceptsURL(url);}
        @Override public DriverPropertyInfo[] getPropertyInfo(String url,Properties properties)throws SQLException{return delegate.getPropertyInfo(settings(url,properties).url,properties);}
        @Override public int getMajorVersion(){return delegate.getMajorVersion();}
        @Override public int getMinorVersion(){return delegate.getMinorVersion();}
        @Override public boolean jdbcCompliant(){return delegate.jdbcCompliant();}
        @Override public Logger getParentLogger()throws SQLFeatureNotSupportedException{return delegate.getParentLogger();}
    }
}
