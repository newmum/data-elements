{
  "properties": {},
  "id": "nifi_canvas_template_assets_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "37.海通画布模板资产",
  "path": "/nifiTemplateAssets",
  "description": "批量读取当前行业租户模板所需的登记表、数据源及字段快照。逐 ID 检查有效关联和现用登记权限，最多涉及两个表。旧空快照按已授权的数据源探查。外部只读，无业务写入。",
  "parameters": [
    {
      "name": "assetIds",
      "description": "来源与目标数据表及显式数据源标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorer
import java.lang.IllegalArgumentException
import java.lang.Integer
import java.util.HashSet
import java.util.Locale
import java.util.Map
import dataScope
import tableRead
import tenantRuntime

// @/common/nifiTemplateAssets 批量读取模板两端的登记表、数据源及字段配置。
// 两端表和数据源各预取一批，从已通过权限检查的数据源探查缺少快照的历史表。
var tenantId = tenantRuntime.id()
var blank = value => value == null || (value + '').isBlank()
var first = (row, keys) => {
    for (key in keys) {
        var value = row[key]
        if (!blank(value)) {
            return value + ''
        }
    }
    return null
}
var asObject = value => {
    if (value instanceof Map) {
        return value
    }
    if (!blank(value)) {
        try {
            var parsed = mapper.readValue(value + '', Map)
            return parsed instanceof Map ? parsed : {}
        } catch (failure) {
            return {}
        }
    }
    return {}
}

var ids = [assetIds.sourceTableId, assetIds.targetTableId].filter(id => !blank(id)).distinct()
var tables = {}
var datasourceIds = [assetIds.sourceDbId, assetIds.targetDbId].filter(id => !blank(id))
if (!ids.isEmpty()) {
    var rows = db.camel().table('db_table_t').columns('tid', 'datasource_id', 'table_name', 'table_name_en',
        'table_name_cn', 'table_comment', 'table_type', 'field_governance_config').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', ids).select()
    for (row in rows) {
        if (blank(row.datasourceId)) {
            return null
        }
        tables[row.tid] = row
        datasourceIds.add(row.datasourceId)
    }
    if (tables.size() != ids.size()) {
        return null
    }
}

var sources = {}
datasourceIds = datasourceIds.distinct()
if (!datasourceIds.isEmpty()) {
    var rows = db.camel().table('db_datasource_t').columns('tid', 'db_name', 'db_type', 'jdbc_url',
        'username', 'password', 'pool_cfg', 'created_by', 'org_id').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', datasourceIds).select()
    // 仅允许有效登记对象的连接配置进入模板；一次范围解析后逐记录检查。
    var visible = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', rows)
    if (visible.size() != datasourceIds.size()) {
        return null
    }
    for (row in visible) {
        var config = {}
        config.putAll(asObject(row.poolCfg))
        config.putAll({tid: row.tid, id: row.tid, dbName: row.dbName, dbType: row.dbType,
            jdbcURL: row.jdbcUrl, jdbcUrl: row.jdbcUrl, username: row.username, password: row.password})
        config.database = blank(config.database) ? row.dbName : config.database
        config.poolCfg = row.poolCfg
        sources[row.tid] = config
    }
}

var columns = {}
if (!ids.isEmpty()) {
    var excluded = ['tenantId', 'revision', 'createdBy', 'createdTime', 'updatedBy', 'updatedTime']
    var query = db.camel().table('db_table_column_t').excludes(excluded).where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('table_id', ids)
    if (query.count() > 10000) {
        throw new IllegalArgumentException('画布模板字段总数超过 10000 个')
    }
    var rows = query.orderBy('ordinal_position').orderBy('tid').select()
    columns = rows.group(row => row.tableId)
}

var buildMetadataConfig = asset => {
    var type = RegisteredDatasourceType.databaseType(asset.dbType, asset)
    var config = new DataSourceConfig()
    config.setDatabaseType(type)
    config.setConnectorProperties(asset)
    config.setHost(first(asset, ['host', 'dbIp', 'minioEndpoint', 'apiUrl', 'url', 'nodes', 'ftpHost', 'hostname', 'bootstrapServers']))
    var port = first(asset, ['port', 'dbPort', 'ftpPort'])
    config.setPort(port == null ? type.getDefaultPort() : Integer.parseInt(port))
    config.setDatabase(first(asset, ['database', 'dbName', 'dbMetaDbName', 'schema', 'defaultSchema', 'currentSchema',
        'minioBucket', 'bucket', 'topic', 'kafkaTopic', 'indexName', 'apiName']))
    config.setUsername(first(asset, ['username', 'dbUser', 'dbMetaUser', 'user', 'ftpUsername', 'apiUser']))
    config.setPassword(first(asset, ['password', 'dbPassword', 'dbMetaPassword', 'ftpPassword', 'apiToken']))
    config.setJdbcUrl(first(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
    config.setSchema(first(asset, ['schema', 'defaultSchema', 'currentSchema']))
    config.setAuthMode(first(asset, ['authMode', 'auth']))
    config.setPrincipal(first(asset, ['principal', 'hivePrincipal', 'servicePrincipal']))
    config.setUserPrincipal(first(asset, ['userPrincipal', 'clientPrincipal', 'kerberosUserPrincipal']))
    config.setKeytabPath(first(asset, ['keytabPath', 'keytab', 'userKeytab']))
    config.setKrb5ConfPath(first(asset, ['krb5ConfPath', 'krb5Conf', 'krb5']))
    config.setJaasConfPath(first(asset, ['jaasConfPath', 'jaasConfig']))
    config.setZookeeperQuorum(first(asset, ['zookeeperQuorum', 'zkQuorum']))
    config.setZookeeperNamespace(first(asset, ['zookeeperNamespace', 'zooKeeperNamespace']))
    config.setServiceDiscoveryMode(first(asset, ['serviceDiscoveryMode', 'serviceDiscovery']))
    config.setSaslQop(first(asset, ['saslQop', 'sasl.qop']))
    config.setClientConfigDir(first(asset, ['clientConfigDir', 'mrsClientConfigDir']))
    config.setExtraParams(first(asset, ['extraParams', 'connectParams', 'jdbcParams']))
    config.setConnectionTimeout(30)
    return config
}

// 一次请求最多两张表。对缺少旧登记快照的表按有效来源分组，使用原生批量探查。
var toProbe = ids.filter(id => columns[id] == null).map(id => tables[id]).group(row => row.datasourceId)
for (sourceId in toProbe.keySet()) {
    var missing = toProbe[sourceId]
    var source = sources[sourceId]
    if (source != null) {
        try {
            var probed = metadataExplorer.getColumnsByTables(buildMetadataConfig(source), missing.map(row => row.tableName).distinct())
            for (table in missing) {
                var values = probed[table.tableName]
                if (values == null) {
                    values = probed[table.tableName.toLowerCase(Locale.ROOT)]
                }
                columns[table.tid] = values == null ? [] : values
            }
        } catch (failure) {
            // 历史元数据探查失败时画布仍可人工配置字段。
        }
    }
}

// 历史时间字段登记曾写入扩展属性，批量读取缺失快照的最近配置。
var oldGovernance = ids.filter(id => asObject(tables[id].fieldGovernanceConfig).isEmpty())
if (!oldGovernance.isEmpty()) {
    var props = tableRead.from('da_prop_t', 'p', tenantId)
    var owner = tableRead.column('p', 'parent_id', 'TEXT')
    var deleted = tableRead.column('p', 'is_del', 'NUMBER')
    props.where(tableRead.in(owner, oldGovernance))
    props.where(tableRead.equal(tableRead.column('p', 'prop_name', 'TEXT'), 'fieldGovernanceConfig'))
    props.where(tableRead.any([tableRead.isNull(deleted), tableRead.equal(deleted, 0)]))
    var seen = new HashSet()
    var rows = props.rows({tableId: owner, config: tableRead.column('p', 'prop_value', 'LOB')},
        [tableRead.column('p', 'updated_time', 'TIME'), tableRead.column('p', 'tid', 'TEXT')], ['desc', 'desc'], 101, true)
    for (row in rows) {
        var config = asObject(row.config)
        if (!config.isEmpty() && seen.add(row.tableId)) {
            tables[row.tableId].fieldGovernanceConfig = config
        }
    }
}
return {tables: tables, sources: sources, columns: columns}
