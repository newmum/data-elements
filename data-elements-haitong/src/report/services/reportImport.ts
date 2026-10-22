import type { Resource, Field } from '../core/model';
import { clone } from '../core/model';
import type { ReportTable, ReportTableField } from './reportApi';
import { assertReportUploadHeaders, reportFieldHeader } from './reportTemplate';

/** Bind parsed cells to authoritative registered identifiers, never inferred Chinese names. */
export function bindResourceToTarget(resource: Resource, table: ReportTable, registeredFields: ReportTableField[]): Resource {
  assertReportUploadHeaders(resource.schema.fields.map(field => String(field.headers[0] || '')), registeredFields);
  const dataType = (value: string): Field['type'] => {
    const type = value.toLowerCase();
    if (/timestamp|datetime|日期时间|时间戳/.test(type)) return 'datetime';
    if (/\bdate\b|日期/.test(type)) return 'date';
    if (/bool|\bbit\b|布尔/.test(type)) return 'boolean';
    if (/int|整数/.test(type)) return 'integer';
    if (/decimal|numeric|number|float|double|real|小数|数值|浮点/.test(type)) return 'decimal';
    return 'text';
  };
  const fields: Field[] = resource.schema.fields.map((field, index) => {
    const registered = registeredFields[index], type = dataType(registered.dataType);
    return {
      ...field, en: registered.name, zh: registered.displayName, headers: [reportFieldHeader(registered)],
      type, pk: registered.primaryKey,
      nullable: registered.nullable === false ? 'forbid' : registered.nullable === true ? 'allow' : 'unset',
      time: { format: type === 'date' ? 'ymd' : type === 'datetime' ? 'iso' : '', timezone: type === 'datetime' ? 'Asia/Shanghai' : '' },
      confirmed: true, ambiguous: false,
    };
  });
  const timestamp = fields.find((_, index) => registeredFields[index].timeRoles.includes('时间戳'))?.id || '';
  const eventTime = fields.find((_, index) => registeredFields[index].timeRoles.includes('业务时间'))?.id || '';
  const schema = { ...resource.schema, name: table.displayName, englishName: table.name, fields, timestamp, eventTime, sameTimeConfirmed: !!timestamp && timestamp === eventTime, reviewed: true };
  return { ...resource, schema, savedSchema: clone(schema), validation: undefined };
}
