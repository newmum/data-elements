package com.haitong.config;
import com.haitong.store.DatabaseDialect;
import com.zaxxer.hikari.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.*;
import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.*;
import java.util.Objects;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfiguration {
    @Bean public DatabaseDialect dialect(DatabaseProperties p){return DatabaseDialect.of(p.getType());}
    @Bean(destroyMethod="close") public HikariDataSource dataSource(DatabaseProperties p,DatabaseDialect d,Secrets secrets) {
        if(p.getPoolSize()<2 || p.getPoolSize()>100) throw new IllegalArgumentException("db.pool-size 必须在 2–100 之间");
        String url=p.getUrl();
        if(url==null || url.isBlank()) {
            if(!p.getHost().matches("[A-Za-z0-9._:-]+") || !p.getName().matches("[A-Za-z][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("db.host/db.name 不合法；复杂连接通过 db.url 明确配置");
            int port=p.getPort()==0?(d==DatabaseDialect.DM?5236:3306):p.getPort();
            if(port<1 || port>65535) throw new IllegalArgumentException("db.port 超出范围");
            url=switch(d) {
                case MYSQL -> "jdbc:mysql://"+p.getHost()+":"+port+"/"+p.getName()+"?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=VERIFY_IDENTITY";
                case DM -> "jdbc:dm://"+p.getHost()+":"+port;
                case H2 -> "jdbc:h2:file:./data/db/haitong;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE";
            };
        }
        String prefix=switch(d){case MYSQL->"jdbc:mysql:";case DM->"jdbc:dm:";case H2->"jdbc:h2:";};
        if(!url.startsWith(prefix)) throw new IllegalArgumentException("db.type 与 JDBC URL 不一致");
        HikariConfig c=new HikariConfig();c.setJdbcUrl(url);c.setDriverClassName(d.driver());
        c.setUsername(Objects.requireNonNullElse(p.getUsername(),""));c.setPassword(secrets.resolve(p.getPassword()));
        c.setMaximumPoolSize(p.getPoolSize());c.setConnectionTimeout(10000);c.setValidationTimeout(5000);
        c.setTransactionIsolation("TRANSACTION_READ_COMMITTED");c.setPoolName("haitong-db");
        if(d==DatabaseDialect.DM)c.setConnectionTestQuery("SELECT 1 FROM DUAL");
        return new HikariDataSource(c);
    }
    @Bean public DataSourceInitializer haitongSchemaInitializer(DataSource ds,DatabaseProperties p,DatabaseDialect d){
        if(p.isInitSchema() && d!=DatabaseDialect.H2) throw new IllegalArgumentException("生产数据库完整结构在根 db/，已有库按审阅后的差异升级；init-schema 只用于 H2 测试");
        DataSourceInitializer init=new DataSourceInitializer();init.setDataSource(ds);init.setEnabled(p.isInitSchema());
        init.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("db/h2.sql")));return init;
    }
    @Bean @DependsOn("haitongSchemaInitializer") public JdbcTemplate jdbcTemplate(DataSource ds,DatabaseDialect d) {
        // DM CLOB fields must not be bound as an oversized VARCHAR. Queries return strings through RowMappers.
        JdbcTemplate template=new JdbcTemplate(ds) {
            @Override public int update(String sql,Object... args) {
                return super.update(sql,ps->{
                    for(int i=0;i<args.length;i++) {
                        Object value=args[i];int index=i+1;
                        if(d==DatabaseDialect.DM && value instanceof String text && text.length()>2000)
                            ps.setCharacterStream(index,new StringReader(text),text.length());
                        else if(value instanceof Boolean b)ps.setInt(index,b?1:0);
                        else org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(ps,index,org.springframework.jdbc.core.SqlTypeValue.TYPE_UNKNOWN,value);
                    }
                });
            }
        };
        for(String table:java.util.List.of("ht_message","ht_delivery","ht_file_checkpoint","ht_exchange_log"))
            template.queryForList("SELECT id FROM "+table+" WHERE 1=0");
        return template;
    }
}
