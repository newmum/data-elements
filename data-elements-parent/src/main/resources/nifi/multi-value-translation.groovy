import groovy.json.JsonSlurper
import org.apache.nifi.processor.io.StreamCallback
import org.apache.nifi.serialization.record.MapRecord
import org.apache.nifi.serialization.record.RecordField
import org.apache.nifi.serialization.record.RecordFieldType
import org.apache.nifi.serialization.SimpleRecordSchema
import java.sql.Types
import java.util.regex.Pattern

// Injected base64 JSON contains rule metadata and service aliases, never credentials.
def rules = new JsonSlurper().parseText(new String(Base64.decoder.decode('__RULES_BASE64__'), 'UTF-8'))
def flowFile = session.get()
if (!flowFile) return
def tokens = { raw, separator ->
    raw == null ? [] : raw.toString().split(Pattern.quote(separator), -1).collect { it.trim() }.findAll { !it.isEmpty() }
}
def parameter = { code, type ->
    if (type in [Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC,
                 Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE]) {
        try { return new BigDecimal(code) } catch (NumberFormatException ignored) { return null }
    }
    if (type in [Types.BOOLEAN, Types.BIT]) {
        if (code.toLowerCase(Locale.ROOT) in ['true', '1']) return true
        if (code.toLowerCase(Locale.ROOT) in ['false', '0']) return false
        return null
    }
    return code
}
def count = 0L
def canonical = { value -> value instanceof BigDecimal ? value.stripTrailingZeros().toPlainString() : String.valueOf(value) }
try {
    def attrs = flowFile.attributes
    flowFile = session.write(flowFile, { input, output ->
        def reader = RecordReader.input.createRecordReader(attrs, input, flowFile.size, log)
        def targets = rules.collect { it.target } as Set
        def fields = reader.schema.fields.findAll { !targets.contains(it.fieldName) }
        fields.addAll(targets.collect { new RecordField(it, RecordFieldType.STRING.dataType) })
        def schema = new SimpleRecordSchema(fields)
        def writer = RecordWriter.output.createWriter(log, schema, output, attrs)
        def keyTypes = [:]
        try {
            writer.beginRecordSet()
            while (true) {
                def batch = []
                for (int i = 0; i < 100; i++) {
                    def record = reader.nextRecord()
                    if (record == null) break
                    batch.add(new LinkedHashMap(record.toMap()))
                }
                if (batch.isEmpty()) break
                // Rules sharing one query and connection share one bounded code lookup.
                def dictionaries = [:]
                rules.findAll { it.query }.groupBy { [it.service, it.query] }.each { group, groupRules ->
                    def codes = new LinkedHashSet()
                    groupRules.each { rule -> batch.each { row -> codes.addAll(tokens(row[rule.source], rule.separator)) } }
                    def values = [:]
                    if (!codes.isEmpty()) {
                        def connection = SQL[group[0]].connection
                        if (!keyTypes.containsKey(group)) {
                            def metadata = connection.prepareStatement(group[1].replace(':codes', 'NULL'))
                            try {
                                metadata.queryTimeout = 60
                                def result = metadata.executeQuery()
                                try { keyTypes[group] = result.metaData.getColumnType(1) } finally { result.close() }
                            } finally { metadata.close() }
                        }
                        def parameters = codes.collect { parameter(it, keyTypes[group]) }.findAll { it != null }.unique()
                        def originals = [:]
                        codes.each { original ->
                            def value = parameter(original, keyTypes[group])
                            if (value != null) {
                                def key = canonical(value)
                                if (!originals.containsKey(key)) originals[key] = []
                                originals[key].add(original)
                            }
                        }
                        parameters.collate(500).each { codeBatch ->
                            def query = group[1].replace(':codes', Collections.nCopies(codeBatch.size(), '?').join(','))
                            def statement = connection.prepareStatement(query)
                            try {
                                statement.queryTimeout = 60
                                codeBatch.eachWithIndex { value, index -> statement.setObject(index + 1, value) }
                                def result = statement.executeQuery()
                                try {
                                    int read = 0
                                    while (result.next()) {
                                        if (++read > 100000) throw new IllegalStateException('字典编码重复记录过多')
                                        def code = result.getString(1), label = result.getString(2)
                                        if (code != null && label != null) {
                                            def key = canonical(parameter(code, keyTypes[group]))
                                            (originals[key] ?: []).each { original ->
                                                if (!values.containsKey(original) || label.compareTo(values[original]) > 0) values[original] = label
                                            }
                                        }
                                    }
                                } finally { result.close() }
                            } finally { statement.close() }
                        }
                    }
                    dictionaries[group] = values
                }
                batch.each { row ->
                    rules.each { rule ->
                        def dictionary = rule.query ? dictionaries[[rule.service, rule.query]] : rule.values
                        def codes = tokens(row[rule.source], rule.separator)
                        def matched = codes.any { dictionary[it] != null && dictionary[it] != '' }
                        def value = matched ? codes.collect { dictionary[it] == null || dictionary[it] == '' ? it : dictionary[it] }.join(rule.separator) : null
                        if (value == null && rule.onMissing == 'KEEP_SOURCE') value = row[rule.source]
                        if (value == null && rule.onMissing == 'FAIL') throw new IllegalStateException('字典编码未匹配')
                        row[rule.target] = value
                    }
                    writer.write(new MapRecord(schema, row))
                    count++
                }
            }
            writer.finishRecordSet()
            writer.flush()
        } finally {
            writer.close()
            reader.close()
        }
    } as StreamCallback)
    flowFile = session.putAllAttributes(flowFile, ['record.count': count.toString(), 'mime.type': 'application/json'])
    session.transfer(flowFile, REL_SUCCESS)
} catch (Exception error) {
    // Preserve failed records; do not log input data, SQL or connection secrets.
    log.error('多值字典翻译失败（{}），请检查字典连接和字段配置', [error.class.simpleName] as Object[])
    flowFile = session.putAttribute(flowFile, 'multi.value.error', '多值字典翻译失败，请检查字典连接和字段配置')
    session.transfer(flowFile, REL_FAILURE)
}
