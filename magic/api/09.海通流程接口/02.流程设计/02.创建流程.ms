{
  "properties": {},
  "id": "faf0e7885f4429edbeaa5add552fe155",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "02.创建流程",
  "path": "/create",
  "description": "创建当前租户的画布和接入任务，已有目录画布则原位保存。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiCanvasWriteRequest' as withRequest
import '@/common/nifiCatalogPipeline' as existingCanvas
import '@/common/nifiWriteCanvas' as writeCanvas
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer as tasks
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import org.springframework.transaction.PlatformTransactionManager as transactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import log
import dataScope
import tableRead
import tableWrite
import java.time.LocalDateTime
import java.util.Locale
import java.util.Objects
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.persistence.id.NumericId
import java.lang.System

// POST /nifi/flow/create 创建流程。
// POST /nifi/flow/create 接收画布 JSON 和可选 catalogTid，返回保存后的画布。
// 在当前租户的同一事务内保存新画布和接入任务。目录已有画布时保留原 ID。
// 可选 API 规则绑定使用独立事务，绑定失败保留画布和接入任务。

// 目录授权及来源表解析只用于创建入口。
var catalogScope = (tenantId, requestedId) => {
    // 目录与技术表都可作为创建参数。若目录的来源表不可见，仍可关联目录，跳过 API 规则绑定。
    var scope = dataScope.resource('MENU:2081000000000000002')
    var ownerScope = scope.scope == 'OWNER' && scope.userId != null && !(scope.userId + '').isBlank()
    var orgScope = scope.scope == 'ORG' && scope.organizationIds != null && !scope.organizationIds.isEmpty()
    if (scope.scope != 'ALL' && !ownerScope && !orgScope) {
        return null
    }
    var canReadCatalog = (creator, organization) => scope.scope == 'ALL' || (ownerScope && creator == scope.userId)
        || (orgScope && scope.organizationIds.contains(organization))
    var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
    var catalog = tableRead.from('da_catalog_t', 'c', tenantId)
    catalog.where(active('c'))
    catalog.where(tableRead.equal(tableRead.column('c', 'tid', 'TEXT'), requestedId))
    var catalogs = catalog.rows({sourceTableId: tableRead.column('c', 'source_table_id', 'TEXT'),
        creator: tableRead.column('c', 'created_by', 'TEXT'), organization: tableRead.column('c', 'org_id', 'TEXT')}, 1)
    var isCatalog = !catalogs.isEmpty()
    // 同名标识先按目录解释。目录越权时直接拒绝，本租户同 ID 的技术表不能扩大权限。
    if (isCatalog && !canReadCatalog(catalogs[0].creator, catalogs[0].organization)) {
        return null
    }
    var tableId = isCatalog ? catalogs[0].sourceTableId : requestedId
    if (isCatalog && (tableId == null || tableId.isBlank())) {
        // 历史目录只保存来源表扩展属性时，继续使用最新的有效属性绑定拉取规则。
        var props = tableRead.from('da_prop_t', 'p', tenantId)
        props.where(active('p'))
        props.where(tableRead.equal(tableRead.column('p', 'parent_id', 'TEXT'), requestedId))
        props.where(tableRead.equal(tableRead.column('p', 'data_type', 'TEXT'), 'catalog'))
        var name = tableRead.column('p', 'prop_name', 'TEXT')
        props.where(tableRead.in(name, ['sourceTableId', 'source_table_id']))
        var rows = props.rows({name: name, value: tableRead.column('p', 'prop_value', 'LOB')},
            [tableRead.column('p', 'updated_time', 'TIME'), tableRead.column('p', 'tid', 'TEXT')], ['desc', 'desc'], 2, true)
        for (item in rows) {
            if (item.value != null && !item.value.isBlank()) {
                tableId = item.value
                break
            }
        }
    }
    var result = {catalog: isCatalog, catalogId: isCatalog ? requestedId : null, sourceTableId: null, datasourceId: null}
    if (tableId != null && !tableId.isBlank()) {
        var table = tableRead.from('db_table_t', 't', tenantId)
        table.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [active('d')])
        table.where(active('t'))
        table.where(tableRead.equal(tableRead.column('t', 'tid', 'TEXT'), tableId))
        var datasource = tableRead.column('d', 'tid', 'TEXT')
        table.where(tableRead.not(tableRead.isNull(datasource)))
        if (ownerScope) {
            table.where(tableRead.equal(tableRead.column('d', 'created_by', 'TEXT'), scope.userId))
        }
        if (orgScope) {
            table.where(tableRead.in(tableRead.column('d', 'org_id', 'TEXT'), scope.organizationIds))
        }
        table.leftJoin('da_catalog_t', 'c', 'tid', 'source_catalog_id', [active('c')])
        var sources = table.rows({datasourceId: datasource, linkedCatalogId: tableRead.column('c', 'tid', 'TEXT'),
            linkedSourceTableId: tableRead.column('c', 'source_table_id', 'TEXT'),
            linkedCreator: tableRead.column('c', 'created_by', 'TEXT'), linkedOrganization: tableRead.column('c', 'org_id', 'TEXT')}, 1)
        if (!sources.isEmpty()) {
            result.sourceTableId = tableId
            result.datasourceId = sources[0].datasourceId
            var linkedCatalog = sources[0].linkedCatalogId
            var linkedTable = sources[0].linkedSourceTableId
            if (!isCatalog && linkedCatalog != null && (linkedTable == null || linkedTable.isBlank() || linkedTable == tableId)) {
                // 任务关联会将已登记表归并到有效目录。预先核验这个实际写入对象的角色范围。
                if (!canReadCatalog(sources[0].linkedCreator, sources[0].linkedOrganization)) {
                    return null
                }
                result.catalogId = linkedCatalog
            }
        }
    }
    return isCatalog || result.sourceTableId != null ? result : null
}

// 独立事务中重读当前租户的数据源，识别历史拉取配置后只更新本来源表的有效规则。
var bindPullRules = (tenantId, datasourceId, sourceTableId, pipelineId) => {
    // 按当前租户读取有效来源，保留旧回调的普通读取及历史接入方式文本识别。
    var sources = tableRead.from('db_datasource_t', 's', tenantId)
    var id = tableRead.column('s', 'tid', 'TEXT')
    sources.where(tableRead.equal(id, datasourceId))
    sources.where(tableRead.equal(tableRead.column('s', 'is_del', 'NUMBER'), 0))
    var rows = sources.rows({
        dbType: tableRead.column('s', 'db_type', 'TEXT'),
        poolCfg: tableRead.column('s', 'pool_cfg', 'LOB')
    }, id, 'asc', 1)
    if (rows.isEmpty()) {
        throw new IllegalArgumentException('数据源不存在或不属于当前租户')
    }
    var source = rows[0]
    var type = Objects.toString(source.dbType, '').trim().toLowerCase(Locale.ROOT)
    var pool = Objects.toString(source.poolCfg, '').trim().replaceAll('\\s', '').toLowerCase(Locale.ROOT)
    if (type != 'api' || (!pool.contains('"accessmode":"capture"') && !pool.contains('"accessmode":"pull"'))) {
        return
    }

    // 直接更新当时仍有效的规则。按原契约写入 system 和应用时钟，无需先读取规则。
    tableWrite.batchUpdate('data_pull_rule_t', tenantId,
        ['nifi_task_id', 'updated_by', 'updated_time'], ['source_table_id'], {datasource_id: datasourceId, is_del: 0}, [{
            source_table_id: sourceTableId,
            nifi_task_id: pipelineId,
            updated_by: 'system',
            updated_time: LocalDateTime.now()
        }])
}

var pipelineType = Pipeline.getName()
var bodySignature = pipelineType + ' com.linewell.dataelement.integration.nifi.canvas.controller.PipelineController.create('
    + pipelineType + ',java.lang.String)'
return withRequest(bodySignature, body, (submitted, tenantId, parameterValues, textParameter) => mutation.withMutationLock(() => db.transaction(() => {
    var rawCatalogId = textParameter('catalogTid')
    var catalogId = rawCatalogId == null || rawCatalogId.isBlank() ? null : rawCatalogId.trim()
    if ('null'.equalsIgnoreCase(catalogId) || 'undefined'.equalsIgnoreCase(catalogId)) {
        catalogId = null
    }
    var scope = catalogId == null ? null : catalogScope(tenantId, catalogId)
    if (catalogId != null && scope == null) {
        throw new IllegalStateException('无法关联接入任务：目录或来源表不存在或不属于当前租户')
    }
    var associatedId = scope == null ? null : scope.catalogId
    var existing = associatedId == null ? null : existingCanvas(associatedId)
    if (existing != null) {
        // 按目录复用既有流程时沿用原创建契约，由共享写锁串行化更新。
        var saved = writeCanvas(submitted, existing.id(), tenantId, current => null)
        if (saved == null) {
            throw new IllegalStateException('目录画布的关联已改变，请重新打开后重试')
        }
        return saved
    }
    // 服务器生成流程 ID。必需的任务创建失败时，回滚本次画布写入。
    var design = HiveSinkPublicationPolicy.forCanvas(submitted)
    var pipelineId = NumericId.nextId()
    var now = System.currentTimeMillis()
    var dsl = BatchSchedulePolicy.normalize(design.dsl(), null, tenantId, pipelineId)
    var saved = new Pipeline(pipelineId, design.name(), design.description(), now, now, dsl,
        null, PipelineStatus.SAVED, null, null, null, null, null)
    db.normal().table('nifi_pipeline_t').primary('id').withBlank().insert({
        id: pipelineId, tenant_id: tenantId, name: saved.name(), description: saved.description(),
        created_at: now, updated_at: now, is_del: 0, status: 'SAVED',
        dsl_json: dsl == null ? null : mapper.writeValueAsString(dsl),
        dsl_hash: dsl == null ? null : DslHasher.hash(dsl),
        dsl_version: dsl == null ? null : dsl.version()
    })
    tasks.ensureCatalogAccessTask(catalogId, saved)
    if (scope != null && scope.catalog && scope.datasourceId != null && scope.sourceTableId != null) {
        try {
            var isolated = new TransactionTemplate(transactionManager)
            isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
            isolated.execute(status => bindPullRules(tenantId, scope.datasourceId, scope.sourceTableId, saved.id()))
        } catch (failure) {
            log.warn('画布 {} 的可选 API 拉取规则绑定失败，保留画布及接入任务', saved.id())
        }
    }
    return saved
})))
