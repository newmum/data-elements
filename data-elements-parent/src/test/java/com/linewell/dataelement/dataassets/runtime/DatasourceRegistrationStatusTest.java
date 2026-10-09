package com.linewell.dataelement.dataassets.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatasourceRegistrationStatusTest {
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    @BeforeEach void setup() {
        source = new DriverManagerDataSource("jdbc:h2:mem:registration_"+System.nanoTime()
                +";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),asset_status int,flow_status int,updated_time timestamp,is_del int)");
        jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),asset_status int,is_del int)");
        jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,flow_status,is_del) values('db','ga',0,0,0),('foreign','other',2,2,0)");
    }
    void table(String id, String tenant, String db, Integer status, int deleted) {
        jdbc.update("insert into db_table_t values(?,?,?,?,?)",id,tenant,db,status,deleted);
    }
    int status() { return jdbc.queryForObject("select asset_status from db_datasource_t where tid='db'",Integer.class); }
    @Test void finishIncompleteAndCompleteThenReopenWhenATableBecomesUnfinished() {
        table("first","ga","db",2,0);table("second","ga","db",0,0);
        var result = DatasourceRegistrationStatus.refresh(source,"ga","db",true);
        assertThat(result).containsEntry("assetStatus",1).containsEntry("pendingTableCount",1L);
        assertThat(status()).isEqualTo(1);
        jdbc.update("update db_table_t set asset_status=2 where tid='second'");
        assertThat(DatasourceRegistrationStatus.refresh(source,"ga","db",false)).containsEntry("assetStatus",2);
        jdbc.update("update db_table_t set asset_status=0 where tid='first'");
        assertThat(DatasourceRegistrationStatus.refresh(source,"ga","db",false)).containsEntry("assetStatus",1);
    }
    @Test void draftCannotBecomeFinishedSimplyBySavingTables() {
        table("first","ga","db",2,0);
        assertThat(DatasourceRegistrationStatus.refresh(source,"ga","db",false)).containsEntry("assetStatus",0);
        assertThat(status()).isZero();
    }
    @Test void dryRunDoesNotWriteAndReturnsServerComputedStatus() {
        table("first","ga","db",2,0);
        assertThat(DatasourceRegistrationStatus.evaluate(source,"ga","db",true,true)).containsEntry("assetStatus",2);
        assertThat(status()).isZero();
    }
    @Test void emptySourceIsIncompleteAndNullTableStatusIsUnfinished() {
        assertThat(DatasourceRegistrationStatus.refresh(source,"ga","db",true)).containsEntry("assetStatus",1);
        table("first","ga","db",null,0);
        assertThat(DatasourceRegistrationStatus.refresh(source,"ga","db",true)).containsEntry("pendingTableCount",1L);
    }
    @Test void deletedAndOtherTenantTablesDoNotAffectCompletion() {
        table("first","ga","db",2,0);table("removed","ga","db",0,1);table("foreign","other","db",0,0);
        assertThat(DatasourceRegistrationStatus.refresh(source,"ga","db",true)).containsEntry("assetStatus",2).containsEntry("tableCount",1L);
        assertThatThrownBy(() -> DatasourceRegistrationStatus.refresh(source,"ga","foreign",true)).hasMessageContaining("不属于当前租户");
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void readsRemainTwoQueriesForTwentyOrOneHundredSourcesAndTables(int count) throws Exception {
        List<String> ids = new ArrayList<>();
        for(int i=0;i<count;i++) {
            String id="batch-"+i;ids.add(id);
            jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,flow_status,is_del) values(?,'ga',1,2,0)",id);
            table(id,"ga",id,2,0);
        }
        Connection connection = spy(source.getConnection());
        DataSource counted = mock(DataSource.class);
        when(counted.getConnection()).thenReturn(connection);
        var results = DatasourceRegistrationStatus.evaluateMany(counted,"ga",ids,false,false);
        assertThat(results).hasSize(count);
        // One ownership/lock query, one grouped table query, one prepared batch write.
        verify(connection,times(3)).prepareStatement(anyString());
        assertThat(results).allSatisfy(result -> assertThat(result).containsEntry("assetStatus",2));
    }
    @Test void rejectsWholeBatchWithForeignIdAndRejectsOverLimitBeforeReads() {
        assertThatThrownBy(() -> DatasourceRegistrationStatus.evaluateMany(source,"ga",List.of("db","foreign"),true,false)).hasMessageContaining("不属于当前租户");
        assertThat(status()).isZero();
        assertThatThrownBy(() -> DatasourceRegistrationStatus.evaluateMany(source,"ga",java.util.stream.IntStream.range(0,101).mapToObj(i->"id-"+i).toList(),true,false)).hasMessageContaining("100");
        assertThatThrownBy(() -> new MetadataAssetPersistenceModule(source).refreshRegistrationStatus("db",true)).hasMessageContaining("当前租户");
        try(var ignored=TenantContext.use("ga")) {
            assertThat(new MetadataAssetPersistenceModule(source).refreshRegistrationStatuses(List.of("db","db"))).hasSize(1);
        }
    }
}
