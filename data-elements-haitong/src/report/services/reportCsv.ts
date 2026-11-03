import type { Resource } from '../core/model';
import { prepareValue } from '../core/engine';

export interface CsvPreparationProgress { completed: number; total: number }
const MAX_REPORT_BYTES = 10 * 1024 * 1024;
const quote = (value: unknown) => '"' + String(value ?? '').replace(/"/g, '""') + '"';

/** Share validation and serialization between saved-schema checks and delivery. */
function* csvLines(resource: Resource): Generator<string> {
  const fields = resource.schema.fields;
  const primary = fields.flatMap((field, index) => field.pk ? [index] : []);
  const keys = new Set<string>();
  yield fields.map(field => quote(field.en)).join(',');
  for (const row of resource.rows) {
    const values = fields.map(field => {
      try {
        const value = prepareValue(row.values[field.id], field, resource.schema, []);
        if (value === null && (field.nullable === 'forbid' || field.pk)) throw new Error('不允许为空');
        return value;
      } catch (cause) {
        throw new Error(`第${row.sourceRow}行 ${field.zh || field.en}：${cause instanceof Error ? cause.message : '格式不符合规范'}`);
      }
    });
    if (primary.length) {
      const key = JSON.stringify(primary.map(index => values[index]));
      if (keys.has(key)) throw new Error(`第${row.sourceRow}行 主键重复，请修正数据或调整主键`);
      keys.add(key);
    }
    yield values.map(quote).join(',');
  }
}

function finish(resource: Resource, parts: BlobPart[]): File {
  if (!resource.rows.length) throw new Error('当前数据表没有可上报的数据');
  const file = new File(parts, resource.schema.englishName + '.csv', { type: 'text/csv;charset=utf-8' });
  if (file.size > MAX_REPORT_BYTES) throw new Error('规范化CSV超过10 MB，请拆分数据后上报');
  return file;
}

export function normalizedReportFile(resource: Resource): File {
  return finish(resource, ['\uFEFF' + [...csvLines(resource)].join('\r\n')]);
}

/** Yield between bounded CPU batches so progress can paint for large tables. */
export async function prepareReportFile(resource: Resource, onProgress?: (value: CsvPreparationProgress) => void, signal?: AbortSignal): Promise<File> {
  signal?.throwIfAborted();
  const lines = csvLines(resource);
  const parts: BlobPart[] = ['\uFEFF' + lines.next().value];
  let completed = 0, bytes = new Blob(parts).size;
  const rowsPerChunk = Math.max(1, Math.min(1000, Math.floor(5000 / Math.max(1, resource.schema.fields.length))));
  let chunkCharacters = 0;
  let chunk: string[] = [];
  onProgress?.({ completed, total: resource.rows.length });
  await new Promise<void>(resolve => setTimeout(resolve, 0));
  for (const line of lines) {
    signal?.throwIfAborted();
    chunk.push('\r\n' + line);
    chunkCharacters += line.length + 2;
    completed++;
    if (chunk.length >= rowsPerChunk || chunkCharacters >= 128_000 || completed === resource.rows.length) {
      const text = chunk.join('');
      bytes += new Blob([text]).size;
      if (bytes > MAX_REPORT_BYTES) throw new Error('规范化CSV超过10 MB，请拆分数据后上报');
      parts.push(text);
      chunk = [];
      chunkCharacters = 0;
      onProgress?.({ completed, total: resource.rows.length });
      await new Promise<void>(resolve => setTimeout(resolve, 0));
    }
  }
  signal?.throwIfAborted();
  return finish(resource, parts);
}
