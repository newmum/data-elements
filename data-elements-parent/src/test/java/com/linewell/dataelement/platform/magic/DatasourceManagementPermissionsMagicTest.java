package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule;
import com.linewell.dataelement.elasticsearch.EsPageInfo;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.api.IdentityRuntimeMagicModule;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Exercises canonical datasource and registered-table management scripts with isolated database fixtures. */
class DatasourceManagementPermissionsMagicTest {
    @ParameterizedTest
    @CsvSource({"ALL,20", "ALL,100", "ORG,20", "ORG,100", "OWNER,20", "OWNER,100"})
    void cardsAndStatisticsUseTheSameRoleAndKeepQueryCountsConstant(String policy, int size) throws Exception {
        var fixture=new Fixture(policy,size);
        var cards=(Map<?,?>)fixture.run("datapd_dept_datasources_01",Map.of(
                "mode","summary","tenantId","foreign","orgId","dept-b","scope","ALL"));
        assertThat(ids((List<?>)cards.get("list"))).isEqualTo(fixture.allowedIds());
        assertThat(cards.get("dataScope")).isEqualTo(policy);
        assertThat(fixture.counter.reads).isEqualTo(2);
        assertThat(fixture.scope.decisions).isEqualTo(1);
        assertThat(fixture.index.reads).isEqualTo(2);
        var tree=(Map<?,?>)fixture.run("datapd_database_tree_statistics_01",Map.of("tenantId","foreign"));
        assertThat(((Number)tree.get("total")).intValue()).isEqualTo(fixture.allowedIds().size());
        assertThat(fixture.counter.reads).isEqualTo(1);
        assertThat(fixture.scope.decisions).isEqualTo(1);
        assertThat(fixture.index.reads).isEqualTo(1);
        System.out.println(policy+" "+size+" rows/group: cards SQL=2 ES=2 scope=1; tree SQL=1 ES=1 scope=1");
    }

    @Test
    void legacyTreeChecksScopeInBoundedChunksAndIgnoresDuplicateAndStaleEsRows() throws Exception {
        var fixture = new Fixture("OWNER", 501);
        fixture.index.rows.add(Map.of("tid", "own-0"));
        fixture.index.rows.add(Map.of("tid", "missing"));
        fixture.jdbc.update("UPDATE db_datasource_t SET org_id = 'dept-b' WHERE tid = 'own-0'");
        var result = (Map<?, ?>) fixture.run("datapd_database_tree_statistics_01",
                Map.of("orgIds", List.of("dept-a"), "tenantId", "foreign", "scope", "ALL"));
        // ES returns a forged dept-a for every document. Only the current owner's active dept-a rows survive.
        assertThat(((Number) result.get("total")).intValue()).isEqualTo(500);
        assertThat(result.get("orgCounts")).isEqualTo(Map.of("dept-a", 500));
        var apps = (Map<?, ?>) result.get("orgApps");
        var app = (Map<?, ?>) ((List<?>) apps.get("dept-a")).getFirst();
        assertThat(idsFromTree((List<?>) app.get("datasources"))).doesNotContain("own-0", "foreign", "missing", "deleted", "dept-0");
        assertThat(fixture.counter.reads).isEqualTo(4);
        assertThat(fixture.index.reads).isEqualTo(1);
        assertThat(fixture.scope.decisions).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"ALL,20", "ALL,100", "ORG,20", "ORG,100", "OWNER,20", "OWNER,100"})
    void paginationAndGlobalSummaryApplyPermissionsBeforeLimit(String policy,int size) throws Exception {
        var fixture=new Fixture(policy,size);
        var response=(Map<?,?>)fixture.run("datapd_database_page_01",Map.of("pageSize",size,"pageNum",1,
                "orgId","dept-b","createdBy","person-b","scope","ALL"));
        assertThat(((Number)response.get("total")).intValue()).isEqualTo(fixture.allowedIds().size());
        assertThat(ids((List<?>)response.get("list"))).isSubsetOf(fixture.allowedIds()).hasSize(size);
        assertThat(((Number)((Map<?,?>)response.get("summary")).get("sourceCount")).intValue())
                .isEqualTo(fixture.allowedIds().size());
        assertThat(fixture.counter.reads).isEqualTo(4);
        assertThat(fixture.scope.decisions).isEqualTo(1);
    }

    @Test void dropdownAndDetailDoNotTrustForgedEsOwnership() throws Exception {
        var fixture=new Fixture("OWNER",20);
        var result=(List<?>)fixture.run("datapd_database_list_01",Map.of());
        assertThat(ids(result)).isEqualTo(fixture.allowedIds());
        assertThat(fixture.counter.reads).isEqualTo(1);
        assertThat(fixture.scope.decisions).isEqualTo(1);
        reject(fixture.run("datapd_database_detail_01",Map.of("tid","dept-0","silent",true)),403);
        assertThat(fixture.counter.reads).isEqualTo(1);
        reject(fixture.run("datapd_database_detail_01",Map.of("tid","foreign")),404);
    }

    @Test void unauthorizedUpdatesCompletionAndDeletesStopBeforeSideEffects() throws Exception {
        for(String policy:List.of("ORG","OWNER")) {
            var fixture=new Fixture(policy,20);
            reject(fixture.run("datapd_save_update_datasource_01",Map.of("tid","other-0","propList",Map.of("dbName","forged"))),403);
            assertThat(fixture.counter.writes).isZero();
            reject(fixture.run("datapd_save_update_datasource_01",Map.of("tid","other-0","propList",Map.of("completeOnly",true,"dryRun",true))),403);
            assertThat(fixture.counter.writes).isZero();
            reject(fixture.run("datapd_database_delete_01",Map.of("tid","other-0","dryRun",true)),403);
            assertThat(fixture.counter.reads).isEqualTo(2);
            assertThat(fixture.counter.writes).isZero();
            assertThat(fixture.index.reads).isZero();
            reject(fixture.run("datapd_database_delete_01",Map.of("tid","missing","dryRun",true)),404);
            assertThat(fixture.counter.reads).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @CsvSource({"20","100"})
    void batchAndSingleSourceTableReadsRejectEveryUnauthorizedId(int size) throws Exception {
        var fixture=new Fixture("OWNER",size);
        reject(fixture.run("datapd_dept_datasources_01",Map.of("mode","tables","datasourceId","dept-0")),404);
        assertThat(fixture.counter.reads).isEqualTo(1);
        for(String denied:List.of("dept-0","foreign","deleted","missing")) {
            var requested=new ArrayList<String>();
            for(int i=0;i<size-1;i++)requested.add("own-"+i);
            requested.add(denied);
            reject(fixture.run("datapd_dept_datasources_01",Map.of("mode","reportTables","datasourceIds",requested)),403);
            assertThat(fixture.counter.reads).isEqualTo(1);
            assertThat(fixture.scope.decisions).isEqualTo(1);
            assertThat(fixture.counter.writes).isZero();
        }
        fixture.scope.organizations=List.of();fixture.scope.policy="ORG";
        var empty=(Map<?,?>)fixture.run("datapd_dept_datasources_01",Map.of("mode","summary","orgId","dept-a"));
        assertThat((List<?>)empty.get("list")).isEmpty();
    }

    @Test
    void tableRetirementKeepsSqlCountsConstantForTwentyAndHundredItems() throws Exception {
        int previewReads = 0;
        int deletionReads = 0;
        int deletionWrites = 0;
        for (int size : List.of(20, 100)) {
            var fixture = new TableRetirement();
            var ids = new ArrayList<String>();
            for (int index = 0; index < size; index++) {
                String id = "table-" + index;
                fixture.addOwnedGraph(id);
                ids.add(id);
            }

            var preview = result(fixture.run(Map.of("dbId", "source", "tableIds", ids, "dryRun", true)));
            assertThat(preview).containsEntry("deleted", true).containsEntry("batch", true)
                    .containsEntry("requestedCount", size).containsEntry("deletedCount", 0)
                    .containsEntry("deletedTableIds", List.of());
            assertThat(rows(preview, "results")).hasSize(size).allSatisfy(item -> {
                assertThat(item).containsEntry("deleted", false).containsEntry("dryRun", true);
                var cleanup = result(item.get("cleanup"));
                assertThat(((Number) cleanup.get("columns")).intValue()).isEqualTo(1);
                assertThat(cleanup).containsEntry("catalogs", 1).containsEntry("tasks", 1);
            });
            assertThat(fixture.counter.writes).isZero();
            assertThat(fixture.projection.batches).isEmpty();
            assertThat(fixture.projection.refreshes).isZero();
            assertThat(fixture.counter.reads).isBetween(1, 20);
            if (size == 20) previewReads = fixture.counter.reads;
            else assertThat(fixture.counter.reads).isEqualTo(previewReads);

            fixture.addTable("validation-foreign", "foreign", "source", null, "业务表", null);
            var mixed = new ArrayList<>(ids);
            mixed.set(size - 1, "validation-foreign");
            var scoped = result(fixture.run(Map.of("dbId", "source", "tableIds", mixed, "dryRun", true, "tenantId", "foreign")));
            assertThat(rows(scoped, "results")).hasSize(size);
            assertThat(rows(scoped, "results").getLast()).containsEntry("reason", "notFound");
            assertThat(fixture.counter.reads).isEqualTo(previewReads);
            assertThat(fixture.counter.writes).isZero();

            var deleted = result(fixture.run(Map.of("dbId", "source", "tableIds", ids)));
            assertThat(deleted).containsEntry("deleted", true).containsEntry("deletedCount", size)
                    .containsEntry("deletedTableIds", ids).containsEntry("failed", List.of());
            assertThat(fixture.counter.reads).isBetween(1, 24);
            assertThat(fixture.counter.writes).isBetween(1, 24);
            assertThat(fixture.counter.writesOutsideTransaction).isZero();
            if (size == 20) {
                deletionReads = fixture.counter.reads;
                deletionWrites = fixture.counter.writes;
            } else {
                assertThat(fixture.counter.reads).isEqualTo(deletionReads);
                assertThat(fixture.counter.writes).isEqualTo(deletionWrites);
            }
            assertThat(fixture.projection.batches).singleElement().satisfies(batch ->
                    assertThat(batch).hasSize(size * 2).containsAll(ids));
            assertThat(fixture.projection.refreshes).isEqualTo(1);
            assertThat(fixture.projection.registration).containsEntry("tableCount", 0L).containsEntry("assetStatus", 1);
            assertThat(fixture.deleted("db_table_t", "tid", "validation-foreign")).isZero();
            for (String id : ids) fixture.assertOwnedGraphRetired(id);
            System.out.printf("Registered-table retirement %d items: preview SELECT=%d, delete SELECT=%d, SQL writes=%d, ES bulk=1%n",
                    size, previewReads, deletionReads, deletionWrites);
        }
    }

    @Test
    void tableRetirementChecksEveryIdAndSourceRoleDespiteForgedScope() throws Exception {
        var fixture = new TableRetirement();
        fixture.addOwnedGraph("owned");
        fixture.addTable("wrong-source", "ga", "hidden-source", null, "业务表", null);
        fixture.addTable("foreign-table", "foreign", "source", null, "业务表", null);
        fixture.addTable("unbound-table", "ga", null, null, "业务表", null);
        fixture.jdbc.update("INSERT INTO db_table_column_t(tid,tenant_id,table_id,is_del) VALUES('foreign-column','foreign','owned',0)");
        fixture.jdbc.update("""
                INSERT INTO da_prop_t(tid,tenant_id,parent_id,prop_name,prop_value,data_type,is_del)
                VALUES('foreign-prop','foreign','catalog-owned','sourceTableId','owned','catalog',0)
                """);
        fixture.jdbc.update("""
                INSERT INTO data_access_agg_task_t(tid,tenant_id,source_table_id,pipeline_id,is_del)
                VALUES('foreign-task','foreign','owned','pipeline-foreign',0)
                """);
        fixture.jdbc.update("INSERT INTO nifi_pipeline_t(id,tenant_id,is_del) VALUES('pipeline-foreign','foreign',0)");

        fixture.scope = DataScopeAuthorizationService.Scope.ORG;
        reject(fixture.run(Map.of("dbId", "hidden-source", "tableId", "wrong-source", "orgId", "dept-b")), 403);
        fixture.scope = DataScopeAuthorizationService.Scope.OWNER;
        reject(fixture.run(Map.of("dbId", "hidden-source", "tableId", "wrong-source", "createdBy", "person-b")), 403);
        assertThat(fixture.counter.writes).isZero();
        assertThat(fixture.projection.batches).isEmpty();

        reject(fixture.run(Map.of("dbId", "source", "tableIds", List.of("owned", "wrong-source"))), 400);
        reject(fixture.run(Map.of("dbId", "source", "tableId", "unbound-table")), 400);
        reject(fixture.run(Map.of("dbId", "source", "tableIds",
                java.util.stream.IntStream.rangeClosed(1, 101).mapToObj(index -> "table-" + index).toList())), 400);
        assertThat(fixture.counter.writes).isZero();
        assertThat(fixture.projection.batches).isEmpty();

        var preview = result(fixture.run(Map.of("dbId", "source", "tableIds", List.of("owned", "missing"), "dryRun", true)));
        assertThat(preview).containsEntry("deleted", true).containsEntry("deletedCount", 0).containsEntry("failed", List.of());
        assertThat(rows(preview, "results").getLast()).containsEntry("reason", "notFound");
        assertThat(result(rows(preview, "results").getLast().get("cleanup")))
                .containsOnlyKeys("columns", "catalogs", "tasks");

        var deleted = result(fixture.run(Map.of("dbId", "source", "tableIds",
                List.of(" owned ", "owned", "foreign-table", "missing", " "),
                "tableId", "owned", "tenantId", "foreign", "scope", "ALL")));
        assertThat(deleted).containsEntry("deleted", false).containsEntry("requestedCount", 3)
                .containsEntry("deletedCount", 1).containsEntry("deletedTableIds", List.of("owned"));
        assertThat(rows(deleted, "failed").stream().map(failed -> failed.get("tableId")))
                .containsExactly("foreign-table", "missing");
        assertThat(rows(deleted, "failed")).allSatisfy(item -> assertThat(item).containsEntry("reason", "notFound"));
        assertThat(fixture.deleted("db_table_t", "tid", "wrong-source")).isZero();
        assertThat(fixture.deleted("db_table_t", "tid", "foreign-table")).isZero();
        assertThat(fixture.deleted("db_table_t", "tid", "unbound-table")).isZero();
        assertThat(fixture.deleted("db_table_column_t", "tid", "foreign-column")).isZero();
        assertThat(fixture.deleted("da_prop_t", "tid", "foreign-prop")).isZero();
        assertThat(fixture.deleted("data_access_agg_task_t", "tid", "foreign-task")).isZero();
        assertThat(fixture.deleted("nifi_pipeline_t", "id", "pipeline-foreign")).isZero();
        assertThat(fixture.projection.batches).singleElement().satisfies(ids ->
                assertThat(ids).containsExactlyInAnyOrder("owned", "catalog-owned"));
        fixture.assertOwnedGraphRetired("owned");
    }

    @Test
    void tableRetirementKeepsSharedDirectoryUntilItsFinalTableIsRetired() throws Exception {
        var fixture = new TableRetirement();
        fixture.addSharedDirectory();

        var preview = result(fixture.run(Map.of("dbId", "source", "tableIds", List.of("left", "right"), "dryRun", 1)));
        assertThat(preview).containsEntry("deleted", true).containsEntry("deletedCount", 0);
        assertThat(rows(preview, "results")).hasSize(2).allSatisfy(item ->
                assertThat(result(item.get("cleanup"))).containsEntry("catalogs", 0));
        assertThat(fixture.counter.writes).isZero();

        var left = result(fixture.run(Map.of("dbId", "source", "tableIds", List.of("left", " left "))));
        assertThat(left).containsEntry("deleted", true).containsEntry("tableId", "left").doesNotContainKey("batch");
        assertThat(result(left.get("cleanup"))).containsEntry("catalogs", 0);
        assertThat(fixture.deleted("da_catalog_t", "tid", "shared")).isZero();
        assertThat(fixture.jdbc.queryForMap("SELECT source_table_id,target_table_id FROM da_catalog_t WHERE tid='shared'"))
                .containsEntry("source_table_id", null).containsEntry("target_table_id", "right");
        assertThat(fixture.deleted("da_prop_t", "tid", "shared-left-property")).isEqualTo(1);
        assertThat(fixture.deleted("da_prop_t", "tid", "shared-right-property")).isZero();
        assertThat(fixture.deleted("da_catalog_item_t", "tid", "shared-item")).isZero();
        assertThat(fixture.deleted("data_access_agg_task_t", "tid", "left-task")).isEqualTo(1);
        assertThat(fixture.deleted("data_access_agg_task_t", "tid", "right-task")).isZero();
        assertThat(fixture.deleted("nifi_pipeline_t", "id", "shared-pipeline")).isZero();
        assertThat(fixture.projection.batches).containsExactly(List.of("left"));
        assertThat(fixture.projection.sharedRefreshes).containsExactly(List.of("shared"));

        var right = result(fixture.run(Map.of("dbId", "source", "tableId", "right")));
        assertThat(result(right.get("cleanup"))).containsEntry("catalogs", 1);
        assertThat(fixture.deleted("da_catalog_t", "tid", "shared")).isEqualTo(1);
        assertThat(fixture.deleted("da_catalog_item_t", "tid", "shared-item")).isEqualTo(1);
        assertThat(fixture.deleted("data_access_agg_task_t", "tid", "right-task")).isEqualTo(1);
        assertThat(fixture.deleted("nifi_pipeline_t", "id", "shared-pipeline")).isEqualTo(1);
        assertThat(fixture.projection.batches).hasSize(2);
        assertThat(fixture.projection.batches.getLast()).containsExactlyInAnyOrder("right", "shared");
        assertThat(fixture.projection.sharedRefreshes).containsExactly(List.of("shared"));

        var both = new TableRetirement();
        both.addSharedDirectory();
        var batch = result(both.run(Map.of("dbId", "source", "tableIds", List.of("right", "left"))));
        assertThat(rows(batch, "results").stream().map(item -> result(item.get("cleanup")).get("catalogs")))
                .containsExactly(0, 1);
        assertThat(both.deleted("da_catalog_t", "tid", "shared")).isEqualTo(1);
        assertThat(both.projection.batches).singleElement().satisfies(ids ->
                assertThat(ids).containsExactlyInAnyOrder("right", "left", "shared"));
    }

    @Test
    void tableRetirementRollsBackDatabaseChangesBeforeExternalWork() throws Exception {
        var fixture = new TableRetirement();
        fixture.addOwnedGraph("first");
        fixture.addOwnedGraph("blocking");
        fixture.jdbc.execute("ALTER TABLE db_table_t ADD CHECK (tid <> 'blocking' OR is_del = 0)");

        var failure = assertThrows(RuntimeException.class,
                () -> fixture.run(Map.of("dbId", "source", "tableIds", List.of("first", "blocking"))));
        assertThat(failure).hasStackTraceContaining("Check constraint violation");
        assertThat(fixture.counter.writes).isGreaterThan(1);
        assertThat(fixture.counter.writesOutsideTransaction).isZero();
        assertThat(fixture.projection.batches).isEmpty();
        assertThat(fixture.projection.refreshes).isZero();
        for (String id : List.of("first", "blocking")) {
            assertThat(fixture.deleted("db_table_t", "tid", id)).isZero();
            assertThat(fixture.deleted("db_table_column_t", "tid", "column-" + id)).isZero();
            assertThat(fixture.deleted("da_catalog_t", "tid", "catalog-" + id)).isZero();
            assertThat(fixture.deleted("da_prop_t", "tid", "prop-" + id)).isZero();
            assertThat(fixture.deleted("data_access_agg_task_t", "tid", "task-" + id)).isZero();
        }
    }

    @Test
    void tableRetirementRetriesOnlyVerifiedPostCommitProjectionDocuments() throws Exception {
        var fixture = new TableRetirement();
        fixture.addOwnedGraph("retry-table");
        fixture.projection.failDelete = true;
        fixture.projection.assets.put("unknown", "table");

        var deleted = result(fixture.run(Map.of("dbId", "source", "tableId", "retry-table")));
        assertThat(deleted).containsEntry("deleted", true);
        assertThat(rows(deleted, "projectionFailures")).singleElement().satisfies(failure ->
                assertThat(failure).containsEntry("operation", "delete"));
        fixture.assertOwnedGraphRetired("retry-table");
        assertThat(fixture.projection.assets).containsKeys("retry-table", "catalog-retry-table", "unknown");

        fixture.projection.failDelete = false;
        fixture.projection.assets.put("catalog-retry-table", "db");
        var retry = result(fixture.run(Map.of("dbId", "source", "tableIds", List.of("retry-table", "unknown"))));
        assertThat(retry).containsEntry("deleted", false).containsEntry("deletedCount", 0);
        assertThat(rows(retry, "results")).allSatisfy(row -> assertThat(row).containsEntry("reason", "notFound"));
        assertThat(fixture.projection.batches).containsExactly(List.of("retry-table"));
        assertThat(fixture.projection.assets).containsEntry("catalog-retry-table", "db")
                .containsEntry("unknown", "table").doesNotContainKey("retry-table");
    }

    @Test
    void tableRetirementMigratesTheSameSourceDictionaryOrMarksItsRelationsInvalid() throws Exception {
        var fixture = new TableRetirement();
        fixture.addTable("dict-a", "ga", "source", null, "字典表", null, "Validation_Dict");
        fixture.addTable("dict-b", "ga", "source", null, "字典表", null, "validation_dict");
        fixture.addTable("dict-hidden", "ga", "hidden-source", null, "字典表", null, "validation_dict");
        String config = """
                {"fields":[
                  {"dictionaryRelation":{"dictionaryTableId":"dict-a","dictionaryTable":"old_name","invalid":true}},
                  {"dictionary_relation":{"dictionaryTable":"  VALIDATION_DICT  "}},
                  {"dictionaryRelation":{"enabled":false,"dictionaryTableId":"dict-a"}},
                  {"dictionaryRelation":{"sourceType":"enum","dictionaryTableId":"dict-a"}},
                  {"dictionaryRelation":{"dictionaryTableId":"unrelated","dictionaryTable":"validation_dict"}}
                ],"retained":null}
                """;
        fixture.addTable("consumer", "ga", "source", null, "业务表", config);
        fixture.addTable("foreign-consumer", "foreign", "source", null, "业务表", config);
        fixture.addTable("hidden-consumer", "ga", "hidden-source", null, "业务表", config);

        var dryRun = result(fixture.run(Map.of("dbId", "source", "tableId", "dict-a", "dryRun", true)));
        assertThat(dictionaryCounts(dryRun)).containsEntry("replacementTableId", "dict-b")
                .containsEntry("affectedTables", 1).containsEntry("affectedFields", 2)
                .containsEntry("migratedFields", 2).containsEntry("invalidatedFields", 0);
        assertThat(fixture.governance("consumer")).isEqualTo(config);
        assertThat(fixture.counter.writes).isZero();

        var deleted = result(fixture.run(Map.of("dbId", "source", "tableId", "dict-a")));
        assertThat(dictionaryCounts(deleted)).isEqualTo(dictionaryCounts(dryRun));
        var migrated = fixture.json.readTree(fixture.governance("consumer"));
        assertThat(migrated.path("retained").isNull()).isTrue();
        assertThat(migrated.at("/fields/0/dictionaryRelation/dictionaryTableId").asText()).isEqualTo("dict-b");
        assertThat(migrated.at("/fields/0/dictionaryRelation/dictionaryTable").asText()).isEqualTo("validation_dict");
        assertThat(migrated.at("/fields/0/dictionaryRelation/invalid").asBoolean()).isFalse();
        assertThat(migrated.at("/fields/1/dictionary_relation/dictionaryTableId").asText()).isEqualTo("dict-b");
        assertThat(migrated.at("/fields/2/dictionaryRelation/dictionaryTableId").asText()).isEqualTo("dict-a");
        assertThat(migrated.at("/fields/3/dictionaryRelation/dictionaryTableId").asText()).isEqualTo("dict-a");
        assertThat(migrated.at("/fields/4/dictionaryRelation/dictionaryTableId").asText()).isEqualTo("unrelated");

        var finalDictionary = result(fixture.run(Map.of("dbId", "source", "tableId", "dict-b")));
        assertThat(dictionaryCounts(finalDictionary)).containsEntry("replacementTableId", null)
                .containsEntry("affectedTables", 1).containsEntry("affectedFields", 2)
                .containsEntry("migratedFields", 0).containsEntry("invalidatedFields", 2);
        var invalid = fixture.json.readTree(fixture.governance("consumer"));
        assertThat(invalid.at("/fields/0/dictionaryRelation/invalid").asBoolean()).isTrue();
        assertThat(invalid.at("/fields/0/dictionaryRelation/invalidTableId").asText()).isEqualTo("dict-b");
        assertThat(fixture.governance("foreign-consumer")).isEqualTo(config);
        assertThat(fixture.governance("hidden-consumer")).isEqualTo(config);
        assertThat(fixture.deleted("db_table_t", "tid", "dict-hidden")).isZero();

        var batch = new TableRetirement();
        batch.addTable("dict-a", "ga", "source", null, "字典表", null, "Validation_Dict");
        batch.addTable("dict-b", "ga", "source", null, "字典表", null, "validation_dict");
        batch.addTable("consumer", "ga", "source", null, "业务表", config);
        var both = result(batch.run(Map.of("dbId", "source", "tableIds", List.of("dict-a", "dict-b"))));
        assertThat(both).containsEntry("deletedTableIds", List.of("dict-a", "dict-b"));
        assertThat(dictionaryCounts(rows(both, "results").getFirst())).containsEntry("migratedFields", 2);
        assertThat(dictionaryCounts(rows(both, "results").getLast())).containsEntry("invalidatedFields", 2);
        assertThat(batch.json.readTree(batch.governance("consumer"))
                .at("/fields/0/dictionaryRelation/invalidTableId").asText()).isEqualTo("dict-b");
    }

    private static Map<String, Object> dictionaryCounts(Map<String, Object> response) {
        return result(result(response.get("cleanup")).get("dictionaryRelations"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Object response) {
        assertThat(response).isInstanceOf(Map.class);
        return (Map<String, Object>) response;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> response, String property) {
        return (List<Map<String, Object>>) response.get(property);
    }

    private static void reject(Object response,int code) {
        assertThat(response).isInstanceOf(ExitValue.class);
        assertThat(((ExitValue)response).getValues()[0].toString()).isEqualTo(Integer.toString(code));
    }
    private static Set<String> ids(List<?> rows) {
        var result=new TreeSet<String>();
        for(Object row:rows)result.add(String.valueOf(((Map<?,?>)row).get("tid")));
        return result;
    }

    private static List<String> idsFromTree(List<?> rows) {
        return rows.stream().map(row -> String.valueOf(((Map<?, ?>) row).get("value"))).toList();
    }

    private static final class TableRetirement {
        private static final String TENANT = "ga";
        private static final String MODULES = "tenantRuntime|dataScope|tableRead|tableWrite|identityRuntime|metadataAsset|jsons"
                + "|com\\.linewell\\.dataelement\\.elasticsearch\\.EsCommonService as esCommonService";
        final Counter counter = new Counter();
        final CountedSource source = new CountedSource(counter);
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final ObjectMapper json = new ObjectMapper();
        final RetirementProjection projection = new RetirementProjection(new MetadataAssetPersistenceModule(source));
        final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        final StructuredTableWriteMagicModule tableWrite = new StructuredTableWriteMagicModule(jdbc);
        final DataScopeMagicModule dataScope;
        final IdentityRuntimeMagicModule identityRuntime;
        final SQLModule db;
        final MagicScript script;
        DataScopeAuthorizationService.Scope scope = DataScopeAuthorizationService.Scope.ALL;

        TableRetirement() throws Exception {
            String canonical = CanonicalMagicSources.byId("metadata_tables_delete_asset_01");
            MagicScript.create(canonical, null).compile();
            script = MagicScript.create(canonical.replaceAll("(?m)^import (?:" + MODULES + "|log)\\h*\\R", "")
                    .replaceAll("(?m)^import '@post:/dst/maintenance/(?:batchRefresh|refresh)' as refreshAssetIndex\\h*\\R", ""), null);

            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(source);
            db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(values -> values);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect());
            db.setDialectAdapter(dialects);

            dataScope = new DataScopeMagicModule(new DataScopeAuthorizationService(null, null, json) {
                @Override
                public ScopeDecision currentScope(String resource) {
                    assertThat(resource).isEqualTo("MENU:2081000000000000002");
                    return new ScopeDecision(resource, scope, "person-a", List.of("dept-a"), List.of(), true);
                }
            });
            var properties = new TenantDatabaseProperties();
            properties.setEnabled(true);
            properties.setTenantBindings(Map.of(TENANT, TENANT));
            var registry = mock(TenantDataSourceRegistry.class);
            when(registry.targets()).thenReturn(Map.of(TENANT, source));
            when(registry.dataSourceForTenant(TENANT)).thenReturn(source);
            identityRuntime = new IdentityRuntimeMagicModule(null, null, properties, registry, source);
            createSchema();
            jdbc.update("""
                    INSERT INTO db_datasource_t(tid,tenant_id,org_id,created_by,is_del,asset_status) VALUES
                    ('source','ga','dept-a','person-a',0,2),('hidden-source','ga','dept-b','person-b',0,2)
                    """);
        }

        Object run(Map<String, Object> body) {
            counter.reset();
            var context = new MagicScriptContext();
            MagicScriptLambdaFunction refreshIndex = (runtime, arguments) -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                var payload = result(runtime.getVariables(context).get("body"));
                assertThat(payload).containsEntry("assetType", List.of("catalog")).containsEntry("purgeStale", false);
                var ids = ((List<?>) payload.get("tids")).stream().map(Object::toString).toList();
                projection.sharedRefreshes.add(ids);
                return Map.of("refreshed", ids.size());
            };
            Map<String, Object> inputs = new LinkedHashMap<>(Map.ofEntries(Map.entry("body", body), Map.entry("db", db),
                    Map.entry("tenantRuntime", new TenantMagicModule(null)), Map.entry("identityRuntime", identityRuntime),
                    Map.entry("dataScope", dataScope), Map.entry("tableRead", tableRead), Map.entry("tableWrite", tableWrite),
                    Map.entry("jsons", new JsonModule()), Map.entry("metadataAsset", projection),
                    Map.entry("esCommonService", projection), Map.entry("log", LoggerFactory.getLogger(getClass())),
                    Map.entry("refreshAssetIndex", refreshIndex)));
            context.putMapIntoContext(inputs);
            return TenantContext.call(TENANT, () -> script.execute(context));
        }

        void addTable(String id, String tenant, String datasource, String catalog, String type, String config) {
            addTable(id, tenant, datasource, catalog, type, config, id);
        }

        void addTable(String id, String tenant, String datasource, String catalog, String type, String config, String name) {
            jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,source_catalog_id,table_name,business_type,
                        field_governance_config,is_del,org_id,created_by) VALUES(?,?,?,?,?,?,?,0,'dept-a','person-a')
                    """, id, tenant, datasource, catalog, name, type, config);
            if (TENANT.equals(tenant)) projection.assets.put(id, "table");
        }

        void addOwnedGraph(String id) {
            String catalog = "catalog-" + id;
            String task = "task-" + id;
            String pipeline = "pipeline-" + id;
            addTable(id, TENANT, "source", catalog, "业务表", null);
            addCatalog(catalog, id, null);
            addProperty("prop-" + id, catalog, "sourceTableId", id);
            jdbc.update("INSERT INTO db_table_column_t(tid,tenant_id,table_id,is_del) VALUES(?,'ga',?,0)", "column-" + id, id);
            jdbc.update("INSERT INTO da_catalog_item_t(tid,tenant_id,catalog_id,is_del) VALUES(?,'ga',?,0)", "item-" + id, catalog);
            jdbc.update("INSERT INTO data_market_shopping_cart_t(tid,tenant_id,catalog_id,is_del) VALUES(?,'ga',?,0)", "cart-" + id, catalog);
            jdbc.update("INSERT INTO da_catalog_apply_form_rela(tid,tenant_id,catalog_id,is_del) VALUES(?,'ga',?,0)", "request-" + id, catalog);
            jdbc.update("""
                    INSERT INTO data_access_agg_task_t(tid,tenant_id,data_catalog_id,source_table_id,pipeline_id,task_status,is_del)
                    VALUES(?,'ga',?,?,?,1,0)
                    """, task, catalog, id, pipeline);
            jdbc.update("INSERT INTO data_access_field_mapping(tid,tenant_id,task_id,is_del) VALUES(?,'ga',?,0)", "binding-" + id, task);
            jdbc.update("INSERT INTO nifi_pipeline_t(id,tenant_id,updated_at,is_del) VALUES(?,'ga',0,0)", pipeline);
        }

        void addSharedDirectory() {
            addTable("left", TENANT, "source", "shared", "业务表", null);
            addTable("right", TENANT, "source", null, "业务表", null);
            addCatalog("shared", "left", "right");
            addProperty("shared-left-property", "shared", "sourceTableId", "left");
            addProperty("shared-right-property", "shared", "targetTableId", "right");
            jdbc.update("INSERT INTO da_catalog_item_t(tid,tenant_id,catalog_id,is_del) VALUES('shared-item','ga','shared',0)");
            jdbc.update("INSERT INTO nifi_pipeline_t(id,tenant_id,updated_at,is_del) VALUES('shared-pipeline','ga',0,0)");
            jdbc.update("""
                    INSERT INTO data_access_agg_task_t(tid,tenant_id,source_table_id,pipeline_id,task_status,is_del) VALUES
                    ('left-task','ga','left','shared-pipeline',1,0),('right-task','ga','right','shared-pipeline',1,0)
                    """);
        }

        void addCatalog(String id, String sourceTable, String targetTable) {
            jdbc.update("""
                    INSERT INTO da_catalog_t(tid,tenant_id,source_table_id,target_table_id,source_table_name,target_table_name,
                        source_db_id,target_db_id,resource_revision_no,is_del) VALUES(?,'ga',?,?,?,?,?,?,1,0)
                    """, id, sourceTable, targetTable, sourceTable, targetTable, "source", targetTable == null ? null : "source");
            projection.assets.put(id, "catalog");
        }

        void addProperty(String id, String parent, String name, String value) {
            jdbc.update("""
                    INSERT INTO da_prop_t(tid,tenant_id,parent_id,prop_name,prop_value,data_type,is_del)
                    VALUES(?,'ga',?,?,?,'catalog',0)
                    """, id, parent, name, value);
        }

        int deleted(String table, String key, String id) {
            return jdbc.queryForObject("SELECT is_del FROM " + table + " WHERE " + key + "=?", Integer.class, id);
        }

        String governance(String id) {
            return jdbc.queryForObject("SELECT field_governance_config FROM db_table_t WHERE tid=?", String.class, id);
        }

        void assertOwnedGraphRetired(String id) {
            assertThat(deleted("db_table_t", "tid", id)).isEqualTo(1);
            assertThat(deleted("db_table_column_t", "tid", "column-" + id)).isEqualTo(1);
            assertThat(deleted("da_catalog_t", "tid", "catalog-" + id)).isEqualTo(1);
            assertThat(deleted("da_catalog_item_t", "tid", "item-" + id)).isEqualTo(1);
            assertThat(deleted("data_market_shopping_cart_t", "tid", "cart-" + id)).isEqualTo(1);
            assertThat(deleted("da_catalog_apply_form_rela", "tid", "request-" + id)).isEqualTo(1);
            assertThat(deleted("da_prop_t", "tid", "prop-" + id)).isEqualTo(1);
            assertThat(deleted("data_access_agg_task_t", "tid", "task-" + id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT task_status FROM data_access_agg_task_t WHERE tid=?", Integer.class, "task-" + id)).isZero();
            assertThat(deleted("data_access_field_mapping", "tid", "binding-" + id)).isEqualTo(1);
            assertThat(deleted("nifi_pipeline_t", "id", "pipeline-" + id)).isEqualTo(1);
        }

        private void createSchema() {
            jdbc.execute("""
                    CREATE TABLE db_datasource_t(tid VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),org_id VARCHAR(64),
                        created_by VARCHAR(64),is_del INT,asset_status INT,flow_status INT,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t(tid VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),datasource_id VARCHAR(64),
                        source_catalog_id VARCHAR(64),table_name VARCHAR(255),business_type VARCHAR(64),
                        field_governance_config CLOB,org_id VARCHAR(64),created_by VARCHAR(64),is_del INT,updated_time TIMESTAMP,
                        asset_status INT DEFAULT 2)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),db_id VARCHAR(64),
                        source_table_id VARCHAR(64),source_table_name VARCHAR(255),source_db_id VARCHAR(64),source_db_name VARCHAR(255),
                        target_table_id VARCHAR(64),target_table_name VARCHAR(255),target_db_id VARCHAR(64),target_db_type VARCHAR(64),
                        resource_revision_no INT,is_del INT,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t(tid VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),parent_id VARCHAR(64),
                        prop_name VARCHAR(64),prop_value VARCHAR(255),data_type VARCHAR(64),is_del INT,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_access_agg_task_t(tid VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),data_catalog_id VARCHAR(64),
                        source_table_id VARCHAR(64),target_table_id VARCHAR(64),pipeline_id VARCHAR(64),task_status INT,
                        is_del INT,updated_time TIMESTAMP)
                    """);
            jdbc.execute("CREATE TABLE nifi_pipeline_t(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),is_del INT,updated_at BIGINT)");
            for (var entry : Map.of("da_catalog_item_t", "catalog_id", "data_market_shopping_cart_t", "catalog_id",
                    "da_catalog_apply_form_rela", "catalog_id", "data_access_field_mapping", "task_id",
                    "db_table_column_t", "table_id").entrySet()) {
                jdbc.execute("CREATE TABLE " + entry.getKey() + "(tid VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),"
                        + entry.getValue() + " VARCHAR(64),is_del INT,updated_time TIMESTAMP)");
            }
        }
    }

    public static class RetirementProjection {
        private final MetadataAssetPersistenceModule metadata;
        private final ObjectMapper json = new ObjectMapper();
        final List<List<String>> batches = new ArrayList<>();
        final List<List<String>> sharedRefreshes = new ArrayList<>();
        final Map<String, String> assets = new LinkedHashMap<>();
        int refreshes;
        boolean failDelete;
        Map<String, Object> registration;

        RetirementProjection(MetadataAssetPersistenceModule metadata) {
            this.metadata = metadata;
        }

        public long bulkDeleteByIds(String index, List<String> ids) {
            assertThat(index).isEqualTo("dataassets");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (failDelete) throw new IllegalStateException("validation projection unavailable");
            batches.add(List.copyOf(ids));
            ids.forEach(assets::remove);
            return ids.size();
        }

        public boolean indexExists(String index) {
            assertThat(index).isEqualTo("dataassets");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return true;
        }

        public EsPageInfo<Map<String, Object>> searchByJsonQuery(String index, String query, Class<?> type,
                int page, int pageSize) throws Exception {
            assertThat(index).isEqualTo("dataassets");
            assertThat(type).isEqualTo(Map.class);
            assertThat(page).isEqualTo(1);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var needles = json.readTree(query);
            var documents = new ArrayList<Map<String, Object>>();
            for (var document : assets.entrySet()) {
                if (containsText(needles, document.getKey())) {
                    documents.add(Map.of("tid", document.getKey(), "assetType", document.getValue(), "tenantId", "ga"));
                }
            }
            return new EsPageInfo<>(page, pageSize, documents.size(), documents);
        }

        private boolean containsText(JsonNode node, String text) {
            if (node.isTextual()) return node.asText().equals(text);
            for (var child : node) if (containsText(child, text)) return true;
            return false;
        }

        public Map<String, Object> refreshRegistrationStatus(String datasource, boolean finish) {
            assertThat(datasource).isEqualTo("source");
            assertThat(finish).isFalse();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            refreshes++;
            registration = metadata.refreshRegistrationStatus(datasource, finish);
            return registration;
        }
    }

    static class Fixture {
        final JdbcTemplate jdbc;final SQLModule db;final Counter counter=new Counter();
        final StructuredTableReadMagicModule tableRead;
        final Scope scope;final Index index;final int size;
        Fixture(String policy,int size) {
            this.size=size;scope=new Scope(policy);
            var source=new CountedSource(counter);
            jdbc=new JdbcTemplate(source);
            tableRead=new StructuredTableReadMagicModule(jdbc);
            DatasourceTypeFacetsMagicTest.registerJsonFunctions(jdbc);
            jdbc.execute("""
                create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,
                    db_name varchar(100),db_type varchar(32),database_type varchar(32),remark varchar(100),
                    app_id varchar(64),org_id varchar(64),created_by varchar(64),pool_cfg varchar(4000),
                    asset_status int,flow_status int,table_num int,data_catalog_num int,is_enable int,show_connect int,
                    connection_status varchar(32),storage_domain varchar(32),node_id varchar(64),db_version varchar(32),
                    created_time timestamp,updated_time timestamp,reg_time timestamp)
                """);
            jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(64),app_name varchar(100),is_del int)");
            jdbc.execute("create table rm_org_t(id varchar(64),tenant_id varchar(64),name varchar(100),org_path varchar(100),deleted int)");
            jdbc.execute("""
                create table db_table_t(tid varchar(64),tenant_id varchar(64),datasource_id varchar(64),is_del int,
                    asset_status int,annotated int,business_type varchar(32),field_count int,
                    table_name varchar(64),table_name_en varchar(64),table_name_cn varchar(64),table_comment varchar(100),
                    table_type varchar(32),record_count int,timestamp_field varchar(64),created_time timestamp,updated_time timestamp)
                """);
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(64),table_id varchar(64),is_del int)");
            jdbc.execute("create table metadata_table_collection_job_t(tenant_id varchar(64),datasource_id varchar(64),is_del int,status varchar(32),finished_time timestamp)");
            for(int i=0;i<size;i++) {
                insert("own-"+i,"ga","dept-a","person-a",0);
                insert("dept-"+i,"ga","dept-a","person-b",0);
                insert("other-"+i,"ga","dept-b","person-b",0);
            }
            insert("foreign","foreign","dept-a","person-a",0);insert("deleted","ga","dept-a","person-a",1);
            var dynamic=new MagicDynamicDataSource();dynamic.setDefault(source);db=new SQLModule(dynamic);db.setDataSourceNode(dynamic.getDataSource());
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");
            db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);
            db.setSqlInterceptors(List.of());db.setNamedTableInterceptors(List.of());
            db.setDialectAdapter(new DialectAdapter());
            index=new Index(jdbc.queryForList("select tid from db_datasource_t"));
        }
        void insert(String id,String tenant,String org,String creator,int deleted) {
            jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,db_name,db_type,database_type,app_id,org_id,created_by,pool_cfg,asset_status,table_num,updated_time) values(?,?,?,?,'mysql','mysql','app',?,?,'{}',2,0,CURRENT_TIMESTAMP)",id,tenant,deleted,id,org,creator);
        }
        Set<String> allowedIds() {
            var ids=new TreeSet<String>();
            for(int i=0;i<size;i++) {
                ids.add("own-"+i);
                if(!scope.policy.equals("OWNER"))ids.add("dept-"+i);
                if(scope.policy.equals("ALL"))ids.add("other-"+i);
            }
            return ids;
        }
        Object run(String id,Map<String,Object> body) throws Exception {
            return run(id, body, index);
        }
        Object run(String id,Map<String,Object> body,Object searchModule) throws Exception {
            counter.reads=0;counter.writes=0;scope.decisions=0;index.reads=0;
            String script=CanonicalMagicSources.byId(id).replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n","");
            script=script.replaceAll("(?s)coalesce\\(\\s*nullif\\(json_unquote.*?\\) as accessMode","'upload' as accessMode")
                    .replaceAll("(?s)upper\\(coalesce\\(nullif\\(json_unquote.*?\\) as pushDataType","'STRUCTURED' as pushDataType")
                    .replaceAll("(?s)coalesce\\(json_length\\(json_extract.*?\\) as reportFileCount","0 as reportFileCount");
            script="var me=()=>{orgId:'dept-a',orgName:'本部门'};var configData=()=>{es_dataassets:'assets'};\n"+script;
            var variables=new LinkedHashMap<String,Object>();variables.put("body",body);variables.put("db",db);
            variables.put("dataScope",scope);variables.put("tenantRuntime",scope);variables.put("tenantAccounts",scope);
            variables.put("tenantEs",searchModule);variables.put("tableSort",new TableSortMagicModule());
            variables.put("tableRead",tableRead);
            var compiled=MagicScript.create(script,null);
            return TenantContext.call("ga", () -> compiled.execute(new MagicScriptContext(variables)));
        }
    }
    public static class Scope {
        String policy;List<String> organizations=List.of("dept-a");int decisions;
        Scope(String policy){this.policy=policy;}
        public String id(){return "ga";}
        public Map<String,String> names(List<?> ids){return Map.of();}
        public Map<String,Object> resource(String resource){decisions++;return Map.of("scope",policy,"userId","person-a","organizationIds",organizations);}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){decisions++;return rows.stream().filter(this::allowed).toList();}
        public boolean canReadDataSourceFor(String resource,Map<String,Object> row){decisions++;return allowed(row);}
        private boolean allowed(Map<String,Object> row){return policy.equals("ALL")||policy.equals("ORG")&&organizations.contains(Objects.toString(row.get("orgId"),""))||policy.equals("OWNER")&&"person-a".equals(row.get("createdBy"));}
    }
    public static class Index {
        int reads;final List<Map<String,Object>> rows;
        Index(List<Map<String,Object>> rows){this.rows=rows;}
        public Index index(String name){return this;}
        public Map<String,Object> search(Map<?,?> query){reads++;return Map.of("hits",Map.of("hits",rows.stream().map(row->Map.of("_id",row.get("tid"),"_source",new LinkedHashMap<>(Map.of("tid",row.get("tid"),"orgId","dept-a","createdBy","person-a","dbName",row.get("tid"),"appId","app","appName","系统","assetType","db")))).toList()));}
    }
    static class Counter {
        int reads, writes, writesOutsideTransaction;

        void reset() {
            reads = 0;
            writes = 0;
            writesOutsideTransaction = 0;
        }

        void record(String sql, boolean autoCommit) {
            if (sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                reads++;
            } else {
                writes++;
                if (autoCommit) writesOutsideTransaction++;
            }
        }
    }

    /** Counts both native Magic and structured SQL at the same prepared-statement boundary. */
    private static final class CountedSource extends DriverManagerDataSource {
        private final Counter counter;

        private CountedSource(Counter counter) {
            super("jdbc:h2:mem:permission_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            this.counter = counter;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            DatabaseMetaData metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://datasource-permissions-validation"
                                            : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")) counter.record((String) args[0], actual.getAutoCommit());
                        return invoke(actual, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
