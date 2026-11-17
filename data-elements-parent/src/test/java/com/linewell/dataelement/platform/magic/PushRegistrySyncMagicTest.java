package com.linewell.dataelement.platform.magic;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.config.DataPushRegistryProperties;
import com.linewell.dataelement.integration.transfer.haitong.HaitongPushCredentialClient;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.magicapi.modules.servlet.RequestModule;
import org.ssssssss.magicapi.servlet.jakarta.MagicJakartaRequestContextHolder;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Exercises the canonical registration sync with isolated persistence and a loopback receiver. */
public class PushRegistrySyncMagicTest {
    public static class Scope {
        boolean denied;String hidden="";public String id(){return "ga";}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){check(resource.equals("MENU:2081000000000000002"),"scope resource");return denied?List.of():rows.stream().filter(r->!hidden.equals(r.get("tid"))).toList();}
    }
    public static class Relay {
        int calls;
        Map<String, Object> last;
        boolean fail;
        int failOnCall;
        String digest;

        public synchronized void synchronize(String id, Map<String, Object> snapshot) {
            calls++;
            if (fail || calls == failOnCall) throw new BizException(503, "test upstream failure");
            check(id.equals(snapshot.get("datasourceId")), "source identity");
            if (snapshot.get("credential") instanceof Map<?, ?> credential) {
                String next = credential.get("apiKeySha256").toString();
                if (digest != null && !digest.equals(next) && !digest.equals(credential.get("previousSha256"))) {
                    throw new BizException(409, "test upstream digest conflict");
                }
                check(!credential.containsKey("apiKey"), "only digests reach the relay");
                digest = next;
            }
            last = new LinkedHashMap<>(snapshot);
        }
    }
    public static class CredentialScope {
        public Object control(Supplier<Object> action) {
            try (var scope = TenantContext.control()) { return action.get(); }
        }
    }

    public static class LegacyIssuer {
        int calls;

        public HaitongPushCredentialClient.IssuedCredential issue(String client, boolean rotate) {
            check(rotate && "push-source".equals(client), "legacy credential scope");
            calls++;
            return new HaitongPushCredentialClient.IssuedCredential(client, "validation-legacy-key", true);
        }
    }
    public static class Log {public void warn(String text,Object id) {}}
    public static class CallbackScope {
        public Object control(Supplier<Object> action) {
            check(TenantContext.usesControlDatabase(), "callback begins in verified control context");
            return action.get();
        }

        public Object inTenant(String tenantId, Supplier<Object> action) {
            check(TenantContext.usesControlDatabase(), "selected tenant follows the control lookup");
            return TenantContext.call(tenantId, action);
        }
    }

    public static class Delivery {
        int attempts;
        int targetChecks;

        public void requireTarget(String tenant) {
            check(tenant.equals(TenantContext.requireTenantId()) && !TenantContext.usesControlDatabase(),
                    "target connection properties use the selected tenant database");
            targetChecks++;
        }

        public Map<String, Object> deliver(String tenant, String source, String table, String delivery,
                List<Map<String, Object>> records, long version) {
            attempts++;
            check("ga".equals(tenant) && "source".equals(source) && tenant.equals(TenantContext.requireTenantId())
                    && !TenantContext.usesControlDatabase(), "delivery uses the registered tenant database and source");
            return Map.of("deliveryId", delivery, "tableName", table, "schemaVersion", version, "records", records.size());
        }
    }

    static class Fixture {
        final AtomicInteger reads=new AtomicInteger(),writes=new AtomicInteger();final JdbcTemplate jdbc;final SQLModule db;final Scope scope=new Scope();final Relay relay=new Relay();
        final DataPushRegistryProperties settings=new DataPushRegistryProperties();final ObjectMapper mapper=new ObjectMapper();final String script;
        final JdbcTemplate concurrentJdbc;
        final Delivery delivery = new Delivery();
        final LegacyIssuer legacy = new LegacyIssuer();
        Runnable beforeSchemaGuard = () -> {};
        Runnable beforeCredentialInsert = () -> {};
        Runnable beforeCredentialRotation = () -> {};
        Fixture(int count) throws Exception {
            JdbcDataSource raw=new JdbcDataSource();raw.setURL("jdbc:h2:mem:registry_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            concurrentJdbc = new JdbcTemplate(raw);
            DataSource source=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{
                Object result=invoke(method,raw,args);if(!method.getName().equals("getConnection"))return result;
                Connection connection=(Connection)result;
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
                    Object value=invoke(m,connection,a);if(!m.getName().equals("prepareStatement"))return value;String sql=(String)a[0];PreparedStatement statement=(PreparedStatement)value;
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PreparedStatement.class}, (pp, mm, aa) -> {
                        if (mm.getName().equals("executeQuery") && sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
                            reads.incrementAndGet();
                        }
                        if (mm.getName().equals("executeUpdate") || mm.getName().equals("executeBatch")) {
                            writes.incrementAndGet();
                        }
                        if (mm.getName().equals("executeUpdate") && sql.contains("data_push_relay_client_t")) {
                            if (sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("INSERT")) beforeCredentialInsert.run();
                            if (sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("UPDATE")) beforeCredentialRotation.run();
                        }
                        if (mm.getName().equals("executeBatch") && sql.contains("UPDATE \"data_push_schema_t\"")
                                && sql.contains("AND \"schema_hash\" = ?")) {
                            var hook = beforeSchemaGuard;
                            beforeSchemaGuard = () -> {};
                            hook.run();
                        }
                        return invoke(mm, statement, aa);
                    });
                });
            });
            jdbc=new JdbcTemplate(source);
            jdbc.execute("create table push_node_t(tid varchar(64),tenant_id varchar(32),base_url varchar(255),enabled int,is_del int,last_sync_time timestamp,last_sync_status varchar(32),last_sync_source_id varchar(64),network_code varchar(64),node_name varchar(128),node_code varchar(64),is_default int,remark varchar(500),created_time timestamp,updated_time timestamp)");
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_name varchar(255),asset_status int,pool_cfg varchar(2000),is_del int,org_id varchar(64),created_by varchar(64),app_id varchar(64))");
            jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(32),app_name varchar(255),is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),table_name varchar(255),table_name_cn varchar(255),table_comment varchar(255),asset_status int,is_del int,field_governance_config clob,table_type varchar(64))");
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(32),table_id varchar(64),column_name varchar(255),column_comment varchar(255),data_type varchar(64),column_type varchar(64),nullable int,primary_key int,ordinal_position int,is_del int)");
            jdbc.execute("create table data_push_schema_t(tid varchar(64),datasource_id varchar(64),table_id varchar(64),table_name varchar(255),schema_version bigint,schema_hash varchar(128),status varchar(32),delivery_config varchar(2000),tenant_id varchar(32),published_time timestamp,updated_time timestamp,is_del int)");
            jdbc.execute("create table data_push_schema_field_t(tid varchar(64),schema_id varchar(64),field_name varchar(255),data_type varchar(64),required_flag int,ordinal_position int,tenant_id varchar(32),is_del int)");
            jdbc.execute("""
                    create table data_push_relay_client_t (tid varchar(64) default random_uuid() primary key,
                        relay_client_id varchar(128), tenant_id varchar(32), datasource_id varchar(64), is_enable int, is_del int,
                        api_key_sha256 varchar(64), previous_api_key_sha256 varchar(64), created_time timestamp, updated_time timestamp,
                        unique (relay_client_id,tenant_id,datasource_id))
                    """);
            jdbc.update("insert into db_datasource_t values('source','ga','DEMO',1,?,0,'org','user','app')","{\"accessMode\":\"receive\",\"password\":\"must-not-leak\"}");
            jdbc.update("insert into db_datasource_t values('foreign','ct','OTHER',2,'{\"accessMode\":\"receive\"}',0,'org','other','app')");
            jdbc.update("insert into sym_application_t values('app','ga','DEMO SYSTEM',0)");
            for(int i=0;i<count;i++){String id="table_"+i;jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values(?,'ga','source',?,'示例表','',2,0)",id,id);jdbc.update("insert into db_table_column_t values(?,'ga',?,'id','标识','int','int',0,1,1,0)","column_"+i,id);}
            var dynamic=new MagicDynamicDataSource();dynamic.setDefault(source);db=new SQLModule(dynamic);db.setDataSourceNode(dynamic.getDataSource());
            var dialects=new org.ssssssss.magicapi.modules.db.dialect.DialectAdapter();dialects.add(new org.ssssssss.magicapi.modules.db.dialect.MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);db.setResultProvider(new org.ssssssss.magicapi.core.interceptor.DefaultResultProvider("registry-test"));
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);db.setSqlInterceptors(List.of());db.setNamedTableInterceptors(List.of());
            settings.setRelayBaseUrl("http://127.0.0.1:18083");
            settings.setStandaloneSyncKey("test-only-service-key");
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("mapper", mapper);
            beans.registerSingleton("data-element.push-registry-com.linewell.dataelement.config.DataPushRegistryProperties", settings);
            beans.registerSingleton("haitongStandaloneRegistryClient", relay);
            beans.registerSingleton("globalExceptionHandler", new GlobalExceptionHandler());
            beans.registerSingleton("dataPushFjghccDeliveryService", delivery);
            beans.registerSingleton("haitongPushCredentialClient", legacy);
            new SpringUtil().postProcessBeanFactory(beans);
            script = CanonicalMagicSources.byId("c78db2b6d3b04a2ba5d0854d8171a106")
                    .replaceAll("(?m)^import (tenantRuntime|dataScope|tableRead|'@/pushregistry/)[^\\r\\n]*\\R", "");
            MagicScript.create(script, null).compile();
            reads.set(0);
        }

        private Map<String, Object> bindings() {
            return new LinkedHashMap<>(Map.of("db", db, "tenantRuntime", scope, "dataScope", scope,
                    "tableRead", new StructuredTableReadMagicModule(jdbc),
                    "tableWrite", new StructuredTableWriteMagicModule(jdbc), "log", new Log()));
        }

        private MagicScriptLambdaFunction function(String path, String id, String secondParameter) throws Exception {
            String name = "function/15.推送登记/" + path;
            var canonical = CanonicalMagicSources.byPath(name, id)
                    .replaceAll("(?m)^import (tenantRuntime|tableRead|tableWrite)[^\\r\\n]*\\R", "");
            var function = MagicScript.create(canonical, null);
            return (ignored, args) -> {
                var bindings = bindings();
                bindings.put("tenantId", args[0]);
                bindings.put(secondParameter, args[1]);
                return function.execute(new MagicScriptContext(bindings));
            };
        }

        private Object callPublication(String target, Map<String, Object> body, String prepareName,
                String persistName) throws Exception {
            var bindings = bindings();
            bindings.put("body", body);
            bindings.put(prepareName, function("02.生成单源推送契约.ms", "push_registry_schema_prepare_v1", "datasourceId"));
            bindings.put(persistName, function("01.批量持久化推送契约.ms", "push_registry_schema_write_v1", "publications"));
            reads.set(0);
            writes.set(0);
            return TenantContext.call("ga", () -> MagicScript.create(target, null).execute(new MagicScriptContext(bindings)));
        }

        Object call(Map<String, Object> body) throws Exception {
            return callPublication(script, body, "preparePushSchemas", "persistPushSchemas");
        }

        Object publishContract(Map<String, Object> body) throws Exception {
            var publish = CanonicalMagicSources.byPath("api/05.数据服务/14.推送登记/02.发布推送契约.ms",
                    "dws_push_registry_publish_v1")
                    .replaceAll("(?m)^import (tenantRuntime|dataScope|'@/pushregistry/)[^\\r\\n]*\\R", "");
            return callPublication(publish, body, "prepare", "persist");
        }

        Object readContract(String method, String... ids) throws Exception {
            boolean head = "HEAD".equals(method);
            String file = head ? "05.核验已发布推送契约.ms" : "01.查询已发布推送契约.ms";
            String id = head ? "dws_push_registry_head_v1" : "dws_push_registry_contracts_v1";
            var entry = CanonicalMagicSources.byPath("api/05.数据服务/14.推送登记/" + file, id)
                    .replaceFirst("(?m)^import '@/pushregistry/read'[^\\r\\n]*\\R", "");
            check(entry.contains("import request"), "the live Magic request module must be imported");
            entry = entry.replaceFirst("(?m)^import request\\R", "");
            var contract = CanonicalMagicSources.byPath("function/15.推送登记/04.读取已发布推送契约.ms",
                    "push_registry_schema_query_v1")
                    .replaceAll("(?m)^import (tenantRuntime|dataScope|tableRead)[^\\r\\n]*\\R", "");
            var servletRequest = new MockHttpServletRequest(method, "/dws/push/contracts");
            if (ids.length > 0) servletRequest.setParameter("datasourceId", ids);
            var bindings = bindings();
            bindings.put("request", new RequestModule(new MagicJakartaRequestContextHolder(null)));
            bindings.put("readContracts", (MagicScriptLambdaFunction) (ignored, args) -> {
                var parameters = bindings();
                parameters.put("supplied", args[0]);
                return MagicScript.create(contract, null).execute(new MagicScriptContext(parameters));
            });
            reads.set(0);
            writes.set(0);
            var script = MagicScript.create(entry, null);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
            try {
                return TenantContext.call("ga", () -> script.execute(new MagicScriptContext(bindings)));
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        ResponseEntity<?> callRelay(String action, String client, Map<String, Object> body) throws Exception {
            boolean validate = "validate".equals(action);
            String file = validate ? "03.校验海通推送批次.ms" : "04.投递海通推送文件.ms";
            String id = validate ? "dws_push_registry_validate_v1" : "dws_push_registry_deliver_v1";
            var entry = CanonicalMagicSources.byPath("api/05.数据服务/14.推送登记/" + file, id)
                    .replaceFirst("(?m)^import '@/pushregistry/verifybatch'[^\\r\\n]*\\R", "");
            check(entry.contains("import request"), "the live Magic request module must be imported");
            entry = entry.replaceAll("(?m)^import (request|identityRuntime)\\R", "");
            var function = CanonicalMagicSources.byPath("function/15.推送登记/03.校验海通回调数据.ms",
                    "push_registry_relay_check_v1")
                    .replaceAll("(?m)^import (identityRuntime|tableRead)[^\\r\\n]*\\R", "");
            var servletRequest = new MockHttpServletRequest("POST", "/dws/push/" + action);
            servletRequest.addHeader("X-Relay-Client-Id", client);
            var bindings = bindings();
            bindings.put("body", body);
            bindings.put("identityRuntime", new CallbackScope());
            bindings.put("request", new RequestModule(new MagicJakartaRequestContextHolder(null)));
            bindings.put("verifyBatch", (MagicScriptLambdaFunction) (ignored, args) -> {
                var parameters = bindings();
                parameters.put("identityRuntime", new CallbackScope());
                parameters.put("caller", args[0]);
                parameters.put("batch", args[1]);
                parameters.put("channel", args[2]);
                return MagicScript.create(function, null).execute(new MagicScriptContext(parameters));
            });
            reads.set(0);
            writes.set(0);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
            try (var context = TenantContext.control()) {
                return (ResponseEntity<?>) MagicScript.create(entry, null).execute(new MagicScriptContext(bindings));
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        ResponseEntity<?> credential(String action, String source) throws Exception {
            String file = "rotate".equals(action) ? "07.轮换业务推送密钥.ms" : "06.签发业务推送密钥.ms";
            var entry = CanonicalMagicSources.byPath("api/05.数据服务/14.推送登记/" + file,
                    "dws_push_registry_" + action + "_v1")
                    .replaceAll("(?m)^import (tenantRuntime|'@/pushregistry/)[^\\r\\n]*\\R", "");
            var credential = CanonicalMagicSources.byPath("function/15.推送登记/05.签发与轮换业务密钥.ms", "push_registry_credential_v1")
                    .replaceAll("(?m)^import (tenantRuntime|dataScope|tableRead|identityRuntime|'@/pushregistry/)[^\\r\\n]*\\R", "");
            var bindings = bindings();
            bindings.put("body", Map.of("datasourceId", source, "tenantId", "ct"));
            var prepare = function("02.生成单源推送契约.ms", "push_registry_schema_prepare_v1", "datasourceId");
            var persist = function("01.批量持久化推送契约.ms", "push_registry_schema_write_v1", "publications");
            bindings.put("credential", (MagicScriptLambdaFunction) (ignored, args) -> {
                var parameters = bindings();
                parameters.put("identityRuntime", new CredentialScope());
                parameters.put("tenantId", args[0]);
                parameters.put("datasourceId", args[1]);
                parameters.put("rotate", args[2]);
                parameters.put("prepare", prepare);
                parameters.put("persist", persist);
                return MagicScript.create(credential, null).execute(new MagicScriptContext(parameters));
            });
            return (ResponseEntity<?>) TenantContext.call("ga", () -> MagicScript.create(entry, null).execute(new MagicScriptContext(bindings)));
        }
    }

    static class BatchUpstream implements AutoCloseable {
        final java.net.ServerSocket server;final Thread worker;volatile int calls;boolean corruptReceipt,foreignStatus;Map<String,Object> last;final Map<String,Map<String,Object>> mirrors=new LinkedHashMap<>();
        BatchUpstream() throws Exception {
            server=new java.net.ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"));
            worker=Thread.ofPlatform().daemon().start(()->{
                while(!server.isClosed())try(var socket=server.accept()) {
                    var input=socket.getInputStream();var head=new java.io.ByteArrayOutputStream();int matched=0;
                    byte[] end={13,10,13,10};
                    while(matched<4){int ch=input.read();if(ch<0)break;head.write(ch);matched=ch==end[matched]?matched+1:0;}
                    String headers=head.toString(java.nio.charset.StandardCharsets.US_ASCII);int length=0;
                    for(String line:headers.split("\r\n"))if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                    var json=new ObjectMapper();last=json.readValue(input.readNBytes(length),Map.class);calls++;
                    if("STATUS".equals(last.get("registryAction"))){
                        List<Map<String,Object>> found=new ArrayList<>();
                        for(Map<String,Object> ref:(List<Map<String,Object>>)last.get("tableRefs")){var mirror=mirrors.get(ref.get("datasourceId")+":"+ref.get("tableName"));if(mirror!=null)found.add(mirror);}
                        if(foreignStatus)found=List.of(Map.of("datasourceId","foreign","tableName","foreign","active",true,"definitionHash","bad"));
                        byte[] reply=json.writeValueAsBytes(Map.of("code","REGISTRY_STATUS","readOnly",true,"tables",found));var output=socket.getOutputStream();
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(reply);output.flush();continue;
                    }
                    var sources=(List<Map<String,Object>>)last.get("datasources");int tables=0,columns=0;
                    for(var source:sources)for(var table:(List<Map<String,Object>>)source.get("tables")){tables++;columns+=((List<?>)table.get("columns")).size();
                        var fields=new ArrayList<List<Object>>();for(Map<String,Object> field:(List<Map<String,Object>>)table.get("columns"))fields.add(Arrays.asList(field.get("columnName"),field.get("columnComment"),field.get("dataType"),field.get("ordinal"),field.get("required"),field.get("enabled")));
                        String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(json.writeValueAsBytes(Arrays.asList(table.get("tableId"),table.get("tableName"),table.get("tableComment"),table.get("schemaVersion"),table.get("enabled"),fields)));
                        mirrors.put(source.get("datasourceId")+":"+table.get("tableName"),Map.of("datasourceId",source.get("datasourceId"),"tableName",table.get("tableName"),"active",true,"definitionHash",hash,"syncedAt",1000L));
                    }
                    byte[] reply=json.writeValueAsBytes(Map.of("datasourceIds",corruptReceipt?Collections.nCopies(sources.size(),sources.getFirst().get("datasourceId")):sources.stream().map(r->r.get("datasourceId")).toList(),"tableCount",tables,"columnCount",columns));
                    var output=socket.getOutputStream();output.write(("HTTP/1.1 201 Created\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(reply);output.flush();
                }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}
            });
        }
        String url(){return "http://127.0.0.1:"+server.getLocalPort();}
        public void close() throws Exception {server.close();worker.join(1000);}
    }
    static Object invoke(Method method,Object target,Object[] args) throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static void rejected(Object result,int status){check(result instanceof ExitValue,"expected rejection");check(((ExitValue)result).getValues()[0].toString().equals(Integer.toString(status)),"rejection status "+status);}

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void readsPublishedContractsWithFourQueriesAndPerIdScope(int size) throws Exception {
        var fixture = new Fixture(size);
        for (int i = 0; i < size; i++) {
            fixture.jdbc.update("""
                    insert into data_push_schema_t (tid, datasource_id, table_id, table_name, schema_version,
                        schema_hash, status, delivery_config, tenant_id, published_time, is_del)
                    values (?, 'source', ?, ?, 2, 'validation', 'PUBLISHED', ?, 'ga', timestamp '2026-11-16 12:00:00', 0)
                    """, "schema_" + i, "TABLE_" + i, "table_" + i, """
                        {"ftp":{"password":"must-not-leak"},"attachmentFields":[{"columnName":"id","maxFileBytes":10}]}
                        """);
            fixture.jdbc.update("insert into data_push_schema_field_t values (?, ?, 'id', 'int', 1, 2, 'ga', 0)",
                    "field_" + i, "schema_" + i);
        }
        fixture.jdbc.update("""
                insert into db_table_t (tid,tenant_id,datasource_id,table_name,asset_status,is_del,table_type)
                values ('foreign_table','ct','source','foreign',2,0,null),
                    ('other_source','ga','other','other',2,0,null), ('file','ga','source','file',2,0,'FILE'),
                    ('pending','ga','source','pending',1,0,null)
                """);
        fixture.jdbc.update("""
                insert into data_push_schema_t(tid,datasource_id,table_id,table_name,status,tenant_id,is_del)
                values ('wrong_tenant','source','foreign_table','foreign','PUBLISHED','ga',0),
                       ('wrong_source','source','other_source','other','PUBLISHED','ga',0),
                       ('file_schema','source','file','file','PUBLISHED','ga',0),
                       ('pending_schema','source','pending','pending','PUBLISHED','ga',0),
                       ('foreign_schema','source','table_0','foreign','PUBLISHED','ct',0)
                """);
        fixture.jdbc.update("insert into data_push_schema_field_t values ('foreign','schema_0','foreign_field','int',1,1,'ct',0)");

        var response = (ResponseEntity<?>) fixture.readContract("GET", "source");
        var envelope = (Map<?, ?>) response.getBody();
        check(response.getStatusCode().value() == 200 && Integer.valueOf(0).equals(envelope.get("code")), "contract envelope");
        var contract = (Map<?, ?>) envelope.get("data");
        var tables = (List<?>) contract.get("tables");
        check(tables.size() == size && fixture.reads.get() == 4 && fixture.writes.get() == 0, "fixed reads, scope and no writes");
        var first = (Map<?, ?>) tables.getFirst();
        check("TABLE_0".equals(first.get("tableId")) && "table_0".equals(first.get("tableName")), "legacy IDs and ordering");
        var field = (Map<?, ?>) ((List<?>) first.get("fields")).getFirst();
        check("id".equals(field.get("fieldName")) && Boolean.TRUE.equals(field.get("required")), "field and required flag");
        check(field.get("attachment") instanceof Map, "field attachment policy");
        check(!contract.toString().contains("must-not-leak") && !contract.toString().contains("foreign_field"), "redacted fields");

        var head = (ResponseEntity<?>) fixture.readContract("HEAD", "source");
        check(head.getStatusCode().equals(response.getStatusCode()) && head.getBody().equals(response.getBody()),
                "same response object is available to the HTTP layer for both methods");

        fixture.scope.denied = true;
        var denied = (ResponseEntity<?>) fixture.readContract("GET", "source");
        check(((com.linewell.dataelement.model.common.CommonResponse<?>) denied.getBody()).getCode() == 403, "role rejection envelope");
        check(fixture.reads.get() == 1 && fixture.writes.get() == 0, "role scope checked before table or field reads");
        fixture.scope.denied = false;
        var missing = (ResponseEntity<?>) fixture.readContract("GET");
        var invalid = (ResponseEntity<?>) fixture.readContract("GET", "source", "foreign");
        check(missing.getBody() instanceof com.linewell.dataelement.model.common.CommonResponse<?>, "missing-query envelope");
        check(((com.linewell.dataelement.model.common.CommonResponse<?>) invalid.getBody()).getCode() == 500, "invalid-query envelope");
        check(fixture.reads.get() == 0 && fixture.writes.get() == 0, "invalid query has no side effects");
        System.out.println(size + " published contracts: 1 request, 4 business reads, 0 writes, tenant and role scope verified");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void publishesApplicableContractsWithBoundedStatementsAndRetainsVersions(int size) throws Exception {
        var fixture = new Fixture(size);
        fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='table_0'", """
                {"attachmentFields":[{"columnName":"id","maxFileBytes":20971520,"allowedExtensions":["pdf"]}]}
                """);
        fixture.jdbc.update("""
                insert into db_table_column_t values ('wrong_tenant','ct','table_0','foreign_field','','int','int',0,0,2,0)
                """);
        var body = Map.of("datasourceId", (Object) "source", "tenantId", "ct");
        fixture.scope.denied = true;
        rejected(fixture.publishContract(body), 403);
        check(fixture.reads.get() == 1 && fixture.writes.get() == 0, "role scope prevents publishing");
        fixture.scope.denied = false;

        var response = (ResponseEntity<?>) fixture.publishContract(body);
        var envelope = (Map<?, ?>) response.getBody();
        check(Integer.valueOf(0).equals(envelope.get("code")), "publish envelope");
        var result = (Map<?, ?>) envelope.get("data");
        check("source".equals(result.get("datasourceId")) && ((List<?>) result.get("schemas")).size() == size, "published tables");
        check(fixture.reads.get() == 6 && fixture.writes.get() == 3 && fixture.relay.calls == 0, "bounded publication statements");
        check(fixture.jdbc.queryForObject("select count(*) from data_push_schema_field_t where tenant_id='ga'", Integer.class) == size,
                "only tenant-owned fields persisted");
        String fieldId = fixture.jdbc.queryForObject("""
                select f.tid from data_push_schema_field_t f join data_push_schema_t s on f.schema_id=s.tid where s.table_id='table_0'
                """, String.class);

        fixture.publishContract(body);
        check(fixture.reads.get() == 6 && fixture.writes.get() == 4, "bounded republication statements");
        check(fixture.jdbc.queryForObject("select schema_version from data_push_schema_t where table_id='table_0'", Long.class) == 1L,
                "unchanged definitions retain version");
        fixture.jdbc.update("update db_table_column_t set column_type='bigint' where tid='column_0'");
        fixture.publishContract(body);
        check(fixture.jdbc.queryForObject("select schema_version from data_push_schema_t where table_id='table_0'", Long.class) == 2L,
                "column change advances exactly one version");
        check("bigint".equals(fixture.jdbc.queryForObject("select data_type from data_push_schema_field_t where tid=?", String.class, fieldId)),
                "field ID preserved across publication");
        System.out.println(size + " tables: publish=6 reads and 3 write invocations, republish=6 reads and 4 write invocations");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void relayCallbacksResolveOnlyTheBoundTenantAndUseFiveReads(int size) throws Exception {
        var fixture = new Fixture(1);
        fixture.publishContract(Map.of("datasourceId", "source"));
        fixture.jdbc.update("update data_push_schema_t set delivery_config=?,schema_version=3", """
                {"kafka":{"topic":"validation-topic"}}
                """);
        fixture.jdbc.update("""
                insert into data_push_relay_client_t (relay_client_id,tenant_id,datasource_id,is_enable,is_del)
                values ('client','ct',null,1,0),('client','ga','source',1,0)
                """);
        var items = java.util.stream.IntStream.range(0, size).mapToObj(i -> Map.of("id", i)).toList();
        var batch = Map.of("datasourceId", "source", "tableName", "TABLE_0", "records", items,
                "schemaVersion", 1, "tenantId", "ct");
        Map<String, Object> input = Map.of("batch", batch, "channel", "table-to-kafka", "deliveryId", "validation-delivery");

        var checked = (Map<?, ?>) ((Map<?, ?>) fixture.callRelay("validate", "client", input).getBody()).get("data");
        check(checked.get("schemaVersion").equals(3L) && checked.get("recordCount").equals(size), "server version and count");
        check("validation-topic".equals(checked.get("destination")), "topic comes from the bound contract");
        check(fixture.reads.get() == 5 && fixture.writes.get() == 0 && fixture.delivery.attempts == 0, "validation SQL and side effects");

        var fileChecked = (Map<?, ?>) ((Map<?, ?>) fixture.callRelay("validate", "client",
                Map.of("batch", batch, "channel", "table-to-ftp")).getBody()).get("data");
        check("PLATFORM_FTP".equals(fileChecked.get("targetType")) && fixture.delivery.targetChecks == 1,
                "signed validation resolves FTP properties inside the authorized tenant");

        var delivered = (Map<?, ?>) ((Map<?, ?>) fixture.callRelay("deliver", "client", input).getBody()).get("data");
        check(delivered.get("schemaVersion").equals(3L) && delivered.get("records").equals(size), "same batch reaches the adapter");
        check(fixture.reads.get() == 5 && fixture.writes.get() == 0 && fixture.delivery.attempts == 1, "file transport called once");
        fixture.jdbc.update("""
                insert into data_push_relay_client_t (relay_client_id,tenant_id,datasource_id,is_enable,is_del)
                values ('client','ct','source',1,0)
                """);
        var ambiguous = (com.linewell.dataelement.model.common.CommonResponse<?>) fixture.callRelay("deliver", "client", input).getBody();
        check(ambiguous.getCode() == 500 && ambiguous.getMsg().contains("422"), "legacy envelope rejects ambiguous tenant");
        check(fixture.reads.get() == 1 && fixture.delivery.attempts == 1, "ambiguous scope stops before accessing business data");
        fixture.jdbc.update("delete from data_push_relay_client_t where tenant_id='ct' and datasource_id='source'");
        var invalidBatch = Map.of("datasourceId", "source", "tableName", "table_0", "records", List.of(Map.of("id", "bad")));
        var rejected = (com.linewell.dataelement.model.common.CommonResponse<?>) fixture.callRelay("deliver", "client",
                Map.of("batch", invalidBatch, "deliveryId", "validation-pending")).getBody();
        check(rejected.getMsg().contains("字段类型不匹配") && fixture.delivery.attempts == 1, "invalid field prevented delivery");
        System.out.println(size + " relay records: 1 request, 5 contract reads, 0 business SQL writes");
    }

    private static Map<?, ?> credentialData(ResponseEntity<?> response) {
        check(response.getBody() instanceof Map<?, ?>, "credential business result expected");
        var envelope = (Map<?, ?>) response.getBody();
        check(Integer.valueOf(0).equals(envelope.get("code")), "credential success envelope");
        check("no-store".equals(response.getHeaders().getCacheControl()), "do not cache key responses");
        return (Map<?, ?>) envelope.get("data");
    }

    @Test
    void credentialDigestSurvivesOutageAndDoesNotAdvanceBeforeNodeAcknowledges() throws Exception {
        var fixture = new Fixture(0);
        fixture.relay.fail = true;
        var first = credentialData(fixture.credential("issue", "source"));
        String initialKey = first.get("apiKey").toString();
        check(initialKey.matches("[A-Za-z0-9_-]{43}") && Boolean.TRUE.equals(first.get("revealed")), "first local key is revealed");
        check(Boolean.FALSE.equals(first.get("ready")) && "test upstream failure".equals(first.get("syncError")), "failure detail");
        String digest = cn.hutool.crypto.digest.DigestUtil.sha256Hex(initialKey);
        check(digest.equals(fixture.jdbc.queryForObject("select api_key_sha256 from data_push_relay_client_t", String.class)),
                "persisted hash represents the original key");
        var pendingRotation = credentialData(fixture.credential("rotate", "source"));
        check(Boolean.FALSE.equals(pendingRotation.get("revealed")) && !pendingRotation.containsKey("apiKey"), "failed reconciliation prevents rotation");
        check(digest.equals(fixture.jdbc.queryForObject("select api_key_sha256 from data_push_relay_client_t", String.class)), "digest unchanged while offline");

        fixture.relay.fail = false;
        var retry = credentialData(fixture.credential("issue", "source"));
        check(Boolean.TRUE.equals(retry.get("ready")) && !retry.containsKey("apiKey"), "same digest restored without a second reveal");
        check(fixture.relay.digest.equals(digest), "relay acknowledged the original hash");
        check(!fixture.relay.last.toString().contains(initialKey), "plaintext is absent from the snapshot");

        fixture.relay.failOnCall = fixture.relay.calls + 2;
        var rotated = credentialData(fixture.credential("rotate", "source"));
        String replacement = rotated.get("apiKey").toString();
        check(!initialKey.equals(replacement) && Boolean.FALSE.equals(rotated.get("ready")), "rotation failure returns the new pending key");
        check(digest.equals(fixture.jdbc.queryForObject("select previous_api_key_sha256 from data_push_relay_client_t", String.class)),
                "previous digest remains available for compensation");
        check(Boolean.TRUE.equals(credentialData(fixture.credential("issue", "source")).get("ready")), "retry reconciles the rotated digest");
        check(fixture.relay.digest.equals(cn.hutool.crypto.digest.DigestUtil.sha256Hex(replacement)), "new digest acknowledged after retry");

        fixture.scope.denied = true;
        int calls = fixture.relay.calls;
        var denied = (com.linewell.dataelement.model.common.CommonResponse<?>) fixture.credential("issue", "source").getBody();
        check(denied.getCode() == 403 && fixture.relay.calls == calls, "denied menu and foreign source cannot issue a key");
        fixture.scope.denied = false;
        denied = (com.linewell.dataelement.model.common.CommonResponse<?>) fixture.credential("issue", "foreign").getBody();
        check(denied.getCode() == 403 && fixture.relay.calls == calls, "body tenant cannot select a foreign source");
    }

    @Test
    void legacyCredentialKeepsItsRelayIssuer() throws Exception {
        var fixture = new Fixture(0);
        fixture.jdbc.update("""
                insert into data_push_relay_client_t (relay_client_id,tenant_id,datasource_id,is_enable,is_del)
                values ('push-source','ga','source',1,0)
                """);
        var legacy = credentialData(fixture.credential("issue", "source"));
        check("relay".equals(legacy.get("managedBy")) && !legacy.containsKey("apiKey") && fixture.legacy.calls == 0, "issue preserves legacy credential");
        var rotated = credentialData(fixture.credential("rotate", "source"));
        check(fixture.legacy.calls == 1 && Boolean.TRUE.equals(rotated.get("ready")) && rotated.containsKey("apiKey"), "legacy rotate still uses relay signature");
        check(fixture.jdbc.queryForObject("select api_key_sha256 from data_push_relay_client_t", String.class) == null,
                "no platform key replaces legacy credential");
    }

    @Test
    void concurrentIssueAndRotationRevealOneWinnerEach() throws Exception {
        var fixture = new Fixture(0);
        var barrier = new CyclicBarrier(2);
        Runnable synchronize = () -> {
            try { barrier.await(10, TimeUnit.SECONDS); }
            catch (Exception error) { throw new AssertionError("concurrent operations did not reach the same write", error); }
        };
        fixture.beforeCredentialInsert = synchronize;
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (String action : List.of("issue", "rotate")) {
                var one = threads.submit(() -> credentialData(fixture.credential(action, "source")));
                var two = threads.submit(() -> credentialData(fixture.credential(action, "source")));
                var outcomes = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
                check(outcomes.stream().filter(value -> value.containsKey("apiKey")).count() == 1, "one request reveals a key for " + action);
                check(fixture.jdbc.queryForObject("select count(*) from data_push_relay_client_t", Integer.class) == 1, "one authoritative binding");
                check(fixture.relay.digest.equals(fixture.jdbc.queryForObject("select api_key_sha256 from data_push_relay_client_t", String.class)),
                        "one verified relay digest matches the control database");
                fixture.beforeCredentialRotation = synchronize;
            }
        }
    }

    @Test
    void eligibleSourceStatusesPublishOnlySavedStructuredTablesAndRequireFields() throws Exception {
        var fixture = new Fixture(1);
        fixture.jdbc.update("""
                insert into db_table_t (tid,tenant_id,datasource_id,table_name,asset_status,is_del,table_type)
                values ('file','ga','source','file',2,0,'FILE'), ('unfinished','ga','source','unfinished',1,0,null)
                """);
        for (int status : List.of(0, 1, 2)) {
            fixture.jdbc.update("update db_datasource_t set asset_status=? where tid='source'", status);
            var response = (ResponseEntity<?>) fixture.publishContract(Map.of("datasourceId", "source"));
            var published = (Map<?, ?>) ((Map<?, ?>) response.getBody()).get("data");
            check(((List<?>) published.get("schemas")).size() == 1, "pending sources may publish their already saved tables");
            check(fixture.jdbc.queryForObject("select asset_status from db_datasource_t where tid='source'", Integer.class) == status,
                    "publishing never advances the registration state");
        }
        fixture.jdbc.update("update db_datasource_t set asset_status=3 where tid='source'");
        var invalid = (ResponseEntity<?>) fixture.publishContract(Map.of("datasourceId", "source"));
        check(((com.linewell.dataelement.model.common.CommonResponse<?>) invalid.getBody()).getMsg().contains("数据源状态不支持推送"),
                "invalid source state is rejected");
        fixture.jdbc.update("update db_datasource_t set asset_status=2 where tid='source'");
        fixture.jdbc.update("update db_table_t set asset_status=1 where tid='table_0'");
        invalid = (ResponseEntity<?>) fixture.publishContract(Map.of("datasourceId", "source"));
        check(((com.linewell.dataelement.model.common.CommonResponse<?>) invalid.getBody()).getMsg().contains("尚无登记成功的数据表"),
                "file and incomplete tables alone grant no push contract");
        fixture.jdbc.update("update db_table_t set asset_status=2 where tid='table_0'");
        fixture.jdbc.update("delete from db_table_column_t where table_id='table_0'");
        invalid = (ResponseEntity<?>) fixture.publishContract(Map.of("datasourceId", "source"));
        check(((com.linewell.dataelement.model.common.CommonResponse<?>) invalid.getBody()).getMsg().contains("未登记字段"),
                "a saved table with no fields cannot be published");
        check(fixture.writes.get() == 0, "failed publication leaves the previous version intact");
    }
    static void verifyRemotePreviewAndPlan(int size)throws Exception{
        try(BatchUpstream upstream=new BatchUpstream()){
            Fixture f=new Fixture(size);f.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('node','ga',?,1,0)",upstream.url());
            f.jdbc.update("update db_datasource_t set asset_status=2 where tid='source'");
            Map<?,?> preview=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","preview",true));
            List<Map<String,Object>> rows=(List<Map<String,Object>>)preview.get("rows");String plan=preview.get("planHash").toString();
            check(Boolean.TRUE.equals(preview.get("statusConfirmed"))&&rows.size()==size&&rows.stream().allMatch(r->"NEW".equals(r.get("syncStatus"))),"new tables confirmed from empty node mirror: "+preview);
            int previewReads=f.reads.get();check(f.writes.get()==0&&upstream.calls==1,"preview read only, one upstream call");
            Map<?,?> again=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","preview",true));check(plan.equals(again.get("planHash")),"plan stable across preview timestamps");
            Map<?,?> done=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","planHash",plan));
            check(Boolean.TRUE.equals(done.get("synced"))&&((List<Map<String,Object>>)done.get("rows")).stream().allMatch(r->"SUCCESS".equals(r.get("syncStatus"))),"ACK confirms precise successful rows");
            preview=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","preview",true));rows=(List<Map<String,Object>>)preview.get("rows");
            check(rows.stream().allMatch(r->"SYNCED".equals(r.get("syncStatus"))),"actual remote fingerprints match published definitions: "+rows.getFirst());
            f.jdbc.update("update db_table_column_t set column_comment='changed comment' where tid='column_0'");
            int calls=upstream.calls;rejected(f.call(Map.of("all",true,"nodeId","node","planHash",plan)),409);check(f.writes.get()==0&&upstream.calls==calls,"stale plan rejected before writes and HTTP");
            preview=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","preview",true));rows=(List<Map<String,Object>>)preview.get("rows");
            check(rows.stream().filter(r->"CHANGED".equals(r.get("syncStatus"))).count()==1,"changed definition identifies just one table");
            upstream.foreignStatus=true;preview=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","preview",true));
            check(Boolean.FALSE.equals(preview.get("statusConfirmed"))&&((List<Map<String,Object>>)preview.get("rows")).stream().allMatch(r->"UNKNOWN".equals(r.get("syncStatus"))),"foreign status cannot affect the authorized plan");
            check(f.reads.get()==previewReads&&f.writes.get()==0,"20/100 remote previews use fixed reads and no writes");
            System.out.println(size+" tables: preview="+previewReads+" reads + 1 status request; stable plan, stale rejection, exact ACK and foreign-status isolation passed");
        }
    }
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void remotePreviewAndPlanAreBounded(int size) throws Exception {
        verifyRemotePreviewAndPlan(size);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void singleSyncKeepsSnapshotAndTenantBoundary(int count) throws Exception {
        var fixture = new Fixture(count);
        var dryRun = Map.of("datasourceId", "source", "dryRun", (Object) true);
        var preview = (Map<?, ?>) fixture.call(dryRun);
        check(preview.get("tableCount").equals(count), "dry run counts every registered table");
        check(fixture.reads.get() == 2, "single-source dry run uses two reads");
        check(fixture.writes.get() == 0 && fixture.relay.calls == 0, "dry run neither writes nor calls the receiver");

        var synced = (Map<?, ?>) fixture.call(Map.of("datasourceId", "source"));
        check(Boolean.TRUE.equals(synced.get("synced")), "single-source result is confirmed");
        check(fixture.reads.get() == 7, "structured snapshot read count: " + fixture.reads);
        check(fixture.relay.calls == 1, "one upstream request");
        check(((List<?>) fixture.relay.last.get("tables")).size() == count, "all tables");
        check("DEMO SYSTEM".equals(fixture.relay.last.get("sourceSystemName")), "system association");
        check(!fixture.mapper.writeValueAsString(fixture.relay.last).contains("must-not-leak"), "credentials excluded");

        fixture.scope.denied = true;
        check(fixture.call(dryRun) instanceof Map, "registration uses its own tenant boundary");
        check(fixture.relay.calls == 1, "repeated dry run does not contact the receiver");
        rejected(fixture.call(Map.of("datasourceId", "foreign")), 403);
        rejected(fixture.call(Map.of("datasourceId", "source", "tenantId", "ct")), 400);
        fixture.jdbc.update("update db_datasource_t set pool_cfg='{\"accessMode\":\"upload\"}' where tid='source'");
        rejected(fixture.call(Map.of("datasourceId", "source")), 422);
        System.out.printf("%d tables: preview=2 reads, sync=7 reads, upstream=1; foreign tenant and injected metadata rejected%n", count);
    }

    @Test
    void singleSourcePreflightHonorsBothDeliveryConfigAliases() throws Exception {
        var fixture = new Fixture(1);
        var request = Map.of("datasourceId", "source", "dryRun", (Object) true);
        for (var field : List.of("pushDelivery", "push_delivery")) {
            fixture.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",
                    fixture.mapper.writeValueAsString(Map.of("accessMode", "receive", field, Map.of("enabled", true))));
            check(fixture.call(request) instanceof Map, "explicit true enables " + field);
            fixture.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",
                    fixture.mapper.writeValueAsString(Map.of("accessMode", "receive", field, Map.of("enabled", false))));
            rejected(fixture.call(request), 409);
            check(fixture.relay.calls == 0 && fixture.writes.get() == 0, "delivery preflight is read-only");
        }
        fixture.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",
                fixture.mapper.writeValueAsString(Map.of("accessMode", "receive",
                        "pushDelivery", Map.of("enabled", false), "push_delivery", Map.of("enabled", false))));
        rejected(fixture.call(request), 409);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void batchSyncPublishesTheExactTenantPlan(int count) throws Exception {
        try (var upstream = new BatchUpstream()) {
                var f=new Fixture(0);
                f.jdbc.update("delete from db_datasource_t where tid='source'");
                for(int i=0;i<count;i++) {
                    String id="s"+i,table="t"+i;
                    f.jdbc.update("insert into db_datasource_t values(?,'ga',?,2,'{\"accessMode\":\"receive\"}',0,'org','user','app')",id,id);
                    f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values(?,'ga',?,?,'示例表','',2,0)",table,id,table);
                    f.jdbc.update("insert into db_table_column_t values(?,'ga',?,'id','标识','int','int',0,1,1,0)","c"+i,table);
                }
                f.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('node','ga',?,1,0)",upstream.url());
                f.jdbc.update("update push_node_t set last_sync_source_id='previous-source' where tid='node'");
                f.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('foreign-node','ct',?,1,0)",upstream.url());
                f.jdbc.update("update db_table_t set field_governance_config=? where tid='t0'",f.mapper.writeValueAsString(Map.of("attachmentFields",List.of(Map.of("columnName","id","maxFileBytes",20971520,"allowedExtensions",List.of("pdf"))))));
                Map<?,?> dry=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","dryRun",true));
                check(dry.get("datasourceCount").equals(count),"bulk preview count: "+dry);
                int previewReads=f.reads.get();
                int before=upstream.calls;
                Map<?,?> result=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node"));
                check(Boolean.TRUE.equals(result.get("synced")),"bulk synced "+result);
                check(f.reads.get()==previewReads+1,"bulk field identity prefetch adds exactly one read: "+f.reads+" vs "+previewReads);
                check(upstream.calls==before+1,"single batch transport");check(f.writes.get()==4,"fresh publication uses four JDBC write invocations: "+f.writes);
                check(f.jdbc.queryForObject("select last_sync_source_id from push_node_t where tid='node'", String.class) == null,
                        "bulk status clears the previous single-source identifier");
                check(f.jdbc.queryForObject("select count(*) from data_push_schema_t",Integer.class)==count,"published all schemas");
                String hash=f.jdbc.queryForObject("select schema_hash from data_push_schema_t where datasource_id='s0'",String.class);
                f.call(Map.of("datasourceId","s0"));
                check(hash.equals(f.jdbc.queryForObject("select schema_hash from data_push_schema_t where datasource_id='s0'",String.class)),"batch and single publish canonical hash must agree");
                check(f.jdbc.queryForObject("select schema_version from data_push_schema_t where datasource_id='s0'",Long.class)==1L,"unchanged canonical version");
                check(f.jdbc.queryForObject("select delivery_config from data_push_schema_t where datasource_id='s0'",String.class).contains("20971520"),"attachment limits survive bulk and single synchronization");
                f.scope.hidden="s0";
                Map<?,?> scoped=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","dryRun",true));
                check(scoped.get("datasourceCount").equals(count),"current-tenant sources ignore menu role scope");
                check(((List<?>)upstream.last.get("datasources")).size()==count,"batch contains all authorized sources");
                f.jdbc.update("update push_node_t set last_sync_source_id='previous-source' where tid='node'");
                upstream.corruptReceipt=true;rejected(f.call(Map.of("all",true,"nodeId","node")),503);upstream.corruptReceipt=false;
                check(f.writes.get() == 5, "republication uses five JDBC write invocations including version guard: " + f.writes);
                check(f.jdbc.queryForObject("select last_sync_source_id from push_node_t where tid='node'", String.class) == null,
                        "failed bulk acknowledgement does not retain a single-source identifier");
                check("FAILED".equals(f.jdbc.queryForObject("select last_sync_status from push_node_t where tid='node'",String.class)),"incomplete receipt fails whole result");
                rejected(f.call(Map.of("all",true,"nodeId","foreign-node")),403);
                rejected(f.call(Map.of("all",true,"nodeId","node","tenantId","ct")),400);
                f.jdbc.update("update db_datasource_t set asset_status=0 where tid='s1'");
                Map<?,?> skipped=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","dryRun",true));
                check(((List<?>)skipped.get("skipped")).size()==1,"incomplete source explained");
                System.out.println(count+" sources: bulk preview="+previewReads+", sync="+(previewReads+1)+" reads, upstream=1; menu-role scope absent; foreign node isolation verified");
        }
    }

    @Test
    void rejectsIncompleteOversizeAndUnavailableSynchronization() throws Exception {
        var tooMany=new Fixture(101);rejected(tooMany.call(Map.of("datasourceId","source")),413);check(tooMany.relay.calls==0,"oversize transport");
        var emptyFields=new Fixture(1);emptyFields.jdbc.update("delete from db_table_column_t");rejected(emptyFields.call(Map.of("datasourceId","source")),409);
        var unavailable=new Fixture(1);rejected(unavailable.call(Map.of("datasourceId","source","dryRun","true")),400);
        unavailable.settings.setStandaloneSyncKey("");rejected(unavailable.call(Map.of("datasourceId","source")),503);check(unavailable.relay.calls==0,"missing setting transport");
        unavailable.settings.setStandaloneSyncKey("test-only-service-key");unavailable.relay.fail=true;rejected(unavailable.call(Map.of("datasourceId","source")),503);
    }

    @Test
    void unstructuredSourceWithNoRegisteredTableStillSendsAnEmptySnapshot() throws Exception {
        try (var upstream = new BatchUpstream()) {
            var fixture = new Fixture(0);
            fixture.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",
                    "{\"accessMode\":\"receive\",\"pushDataType\":\"UNSTRUCTURED\"}");
            fixture.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('node','ga',?,1,0)", upstream.url());
            var result = (Map<?, ?>) fixture.call(Map.of("all", true, "nodeId", "node"));
            check(Boolean.TRUE.equals(result.get("synced")), "receiver confirms the unstructured source");
            check(result.get("datasourceCount").equals(1) && result.get("tableCount").equals(0), "source without registered tables is retained");
            check(fixture.writes.get() == 1 && upstream.calls == 1, "only node state is written locally");
        }
    }

    @Test
    void staleSchemaGuardRollsBackTheEntirePublicationBeforeTransport() throws Exception {
        try (var upstream = new BatchUpstream()) {
            var fixture = new Fixture(2);
            fixture.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('node','ga',?,1,0)", upstream.url());
            var request = Map.of("all", (Object) true, "nodeId", "node");
            fixture.call(request);
            check(upstream.calls == 1, "baseline publication reaches the receiver");
            fixture.jdbc.update("update data_push_schema_t set published_time=TIMESTAMP '2000-01-01 00:00:00'");
            fixture.jdbc.update("delete from data_push_schema_t where table_id='table_0'");
            fixture.beforeSchemaGuard = () -> fixture.concurrentJdbc.update(
                    "update data_push_schema_t set schema_version=7 where table_id='table_1'");
            rejected(fixture.call(request), 409);
            check(upstream.calls == 1, "stale publication never contacts the receiver");
            check(fixture.jdbc.queryForObject("select count(*) from data_push_schema_t where table_id='table_0'", Integer.class) == 0,
                    "any new schema in the transaction is rolled back");
            check(fixture.jdbc.queryForObject("select schema_version from data_push_schema_t where table_id='table_1'", Long.class) == 7L,
                    "concurrent schema change survives");
        }
    }
}
