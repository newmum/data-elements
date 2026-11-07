import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {TableFilterEditor} from '../src/TableFilterPanel';
import {createFilterRule, filterNeedsValue, filterRows, getFilterKind, getFilterOperators, matchesRow, validateFilterRules, type FilterOperator, type FilterRule} from '../src/tableFilters';
import {initialTables, type Column, type Row} from '../src/data';

const columns: Column[] = [
  {name: 'id', type: 'int unsigned'}, {name: 'name', type: 'varchar(100)'},
  {name: 'amount', type: 'decimal(20, 4)'}, {name: 'description', type: 'text', nullable: true},
  {name: 'day', type: 'date'}, {name: 'created_at', type: 'datetime'}, {name: 'clock', type: 'time'},
];
const rule = (field: string, operator: FilterOperator, value = '', id = 'rule-1'): FilterRule => ({id, field, operator, value});
const rows: Row[] = [
  {id: 1, name: 'Alpha Keyboard', amount: 100, description: null, day: '2024-02-29', created_at: '2024-02-29 09:30:00', clock: '09:30:00'},
  {id: 2, name: 'Beta Mouse', amount: '20.50', description: '', day: '2024-03-01', created_at: '2024-03-01 10:00:00', clock: '10:00:00'},
  {id: 3, name: 'ALPHA Monitor', amount: -2, description: 'NULL', day: '2024-03-02', created_at: '2024-03-02 11:15:00', clock: '11:15:00'},
];
const ids = (filters: FilterRule[], data = rows) => filterRows(data, columns, filters).map(row => row.id);

describe('field filters and local scope', () => {
  it('returns all rows for no rules without mutating the source array or records', () => {
    const filtered = filterRows(rows, columns, []);
    expect(filtered).toEqual(rows);
    expect(filtered).not.toBe(rows);
    expect(filtered[0]).toBe(rows[0]);
    expect(filterRows([], columns, [])).toEqual([]);
  });
  it('combines multiple rules using AND and preserves row order', () => {
    expect(ids([rule('name', 'contains', 'alpha'), rule('amount', 'gt', '0', 'rule-2')])).toEqual([1]);
    expect(ids([rule('name', 'contains', 'unmatched')])).toEqual([]);
  });
  it('matches arbitrary fields on tables with no status field', () => {
    const categories = initialTables.categories;
    expect(filterRows(categories.rows, categories.columns, [rule('name', 'contains', '办公')])).toEqual([categories.rows[2]]);
    expect(filterRows(categories.rows, categories.columns, [rule('description', 'isNull')])).toEqual([categories.rows[3]]);
  });
  it('filters 20 or 100 local rows with no asynchronous or network dependency', () => {
    for (const size of [20, 100]) {
      const data = Array.from({length: size}, (_, index) => ({id: index, name: 'sample'}));
      const result = filterRows(data, columns, [rule('id', 'gte', '10')]);
      expect(result).toHaveLength(size - 10);
      expect(result[0]).toBe(data[10]);
    }
  });
  it('does not bring in records from any other table or change search terms', () => {
    const subset = rows.slice(0, 2);
    expect(filterRows(subset, columns, [rule('name', 'contains', 'alpha')])).toEqual([subset[0]]);
    expect(filterRows(subset, columns, [rule('name', 'contains', 'alpha')]).filter(row => String(row.name).includes('Mouse'))).toEqual([]);
  });
});

describe('text filters', () => {
  it.each<[FilterOperator, string, number[]]>([
    ['contains', 'ALPHA', [1, 3]], ['equals', 'beta MOUSE', [2]],
    ['notEquals', 'Beta Mouse', [1, 3]], ['startsWith', 'alpha', [1, 3]], ['endsWith', 'monitor', [3]],
  ])('supports %s without case sensitivity', (operator, value, expected) => {
    expect(ids([rule('name', operator, value)])).toEqual(expected);
  });
  it('preserves spaces and compares literal wildcards, SQL, and regex as ordinary text', () => {
    for (const value of ['  spaced  ', '.*', '%', "' OR 1=1 --", '<script>alert(1)</script>', 'NULL']) {
      const source = [{id: 1, name: value}, {id: 2, name: 'unrelated'}];
      expect(ids([rule('name', 'equals', value)], source)).toEqual([1]);
      expect(ids([rule('name', 'contains', value)], source)).toEqual([1]);
    }
  });
  it('distinguishes empty strings, literal NULL, and SQL NULL', () => {
    expect(ids([rule('description', 'equals', '')])).toEqual([2]);
    expect(ids([rule('description', 'equals', 'NULL')])).toEqual([3]);
    expect(ids([rule('description', 'isNull')])).toEqual([1]);
    expect(ids([rule('description', 'isNotNull')])).toEqual([2, 3]);
    expect(ids([rule('description', 'notEquals', '')])).toEqual([3]);
    expect(ids([rule('description', 'notEquals', 'NULL')])).toEqual([2]);
  });
  it('requires text for contains and prefix/suffix while allowing empty equality', () => {
    for (const operator of ['contains', 'startsWith', 'endsWith'] as const) {
      expect(validateFilterRules(columns, [rule('name', operator)])).toHaveLength(1);
    }
    expect(validateFilterRules(columns, [rule('name', 'equals')])).toEqual([]);
    expect(validateFilterRules(columns, [rule('name', 'notEquals')])).toEqual([]);
  });
});

describe('numeric filters', () => {
  it.each<[FilterOperator, string, number[]]>([
    ['equals', '20.500', [2]], ['notEquals', '20.5', [1, 3]], ['gt', '20.5', [1]],
    ['gte', '20.5', [1, 2]], ['lt', '20.5', [3]], ['lte', '20.5', [2, 3]],
  ])('supports numeric %s rather than lexicographic comparison', (operator, value, expected) => {
    expect(ids([rule('amount', operator, value)])).toEqual(expected);
  });
  it('accepts decimals and scientific notation including signed zero', () => {
    expect(ids([rule('amount', 'equals', ' 1e2 ')])).toEqual([1]);
    expect(ids([rule('amount', 'lt', '-.5')])).toEqual([3]);
    for (const value of ['0', '-0', '0e2', '+0.000']) {
      expect(matchesRow({amount: 0}, columns, [rule('amount', 'equals', value)])).toBe(true);
    }
  });
  it('preserves exact decimal/BIGINT string comparisons beyond JS integer precision', () => {
    const data = [{id: 1, amount: '9007199254740992'}, {id: 2, amount: '9007199254740993'}];
    expect(ids([rule('amount', 'equals', '9007199254740993')], data)).toEqual([2]);
    expect(ids([rule('amount', 'lt', '9007199254740993')], data)).toEqual([1]);
    expect(matchesRow({amount: '0.10000000000000001'}, columns, [rule('amount', 'gt', '.1')])).toBe(true);
    expect(matchesRow({amount: '-0.10000000000000001'}, columns, [rule('amount', 'lt', '-.1')])).toBe(true);
    expect(matchesRow({amount: '1e-500'}, columns, [rule('amount', 'gt', '0')])).toBe(true);
  });
  it.each(['', ' ', 'NaN', 'Infinity', '1e1000', '1e-1001', '0xFF', '1,000', '3x', '1e', '1.2.3'])('rejects invalid numeric value %j with clear validation', value => {
    expect(validateFilterRules(columns, [rule('amount', 'gt', value)])[0].message).toContain('数值');
    expect(ids([rule('amount', 'gt', value)])).toEqual([]);
  });
  it('does not match invalid or NULL stored numbers, including notEquals', () => {
    for (const amount of [null, '', 'invalid', Infinity, NaN]) expect(matchesRow({amount}, columns, [rule('amount', 'notEquals', '0')])).toBe(false);
  });
});

describe('date and time filters', () => {
  it('compares dates and valid leap days consistently', () => {
    expect(ids([rule('day', 'gte', '2024-03-01')])).toEqual([2, 3]);
    expect(ids([rule('day', 'equals', '2024-02-29')])).toEqual([1]);
    expect(ids([rule('created_at', 'gt', '2024-03-01 10:00')])).toEqual([3]);
    expect(ids([rule('created_at', 'gte', '2024-03-01')])).toEqual([2, 3]);
    expect(ids([rule('clock', 'lt', '10:00:00')])).toEqual([1]);
  });
  it('normalizes explicit time zones, and never uses the host local timezone', () => {
    const row = {created_at: '2024-03-01 10:00:00'};
    for (const value of ['2024-03-01T10:00:00Z', '2024-03-01T18:00:00+08:00', '2024-03-01T05:00:00-05:00']) {
      expect(matchesRow(row, columns, [rule('created_at', 'equals', value)])).toBe(true);
    }
  });
  it('keeps microseconds and years before 100 accurate', () => {
    expect(matchesRow({created_at: '2024-01-01 00:00:00.123456'}, columns, [rule('created_at', 'gt', '2024-01-01 00:00:00.123455')])).toBe(true);
    expect(matchesRow({clock: '00:00:00.000001'}, columns, [rule('clock', 'gt', '00:00:00')])).toBe(true);
    expect(matchesRow({day: '0099-12-31'}, columns, [rule('day', 'lt', '0100-01-01')])).toBe(true);
  });
  it.each(['2023-02-29', '2024-02-30', '2024-13-01', '2024-00-01', '2024-01-00', '0000-01-01', '03/01/2024', '2024-01-01 25:00', '2024-01-01 12:60', '2024-01-01 12:00:60', '2024-01-01T10:00:00+14:01', 'invalid'])('rejects invalid datetime %s instead of rolling over', value => {
    expect(validateFilterRules(columns, [rule('created_at', 'equals', value)])[0].message).toContain('有效日期');
    expect(ids([rule('created_at', 'equals', value)])).toEqual([]);
  });
  it('rejects date/time shape mismatches and invalid stored dates', () => {
    for (const value of ['24:00:00', '-10:00', '1:00', '10:60', '10:00:60', '10:00:00.1234567']) expect(validateFilterRules(columns, [rule('clock', 'equals', value)])).toHaveLength(1);
    expect(validateFilterRules(columns, [rule('day', 'equals', '2024-01-01 00:00:00')])).toHaveLength(1);
    expect(matchesRow({day: '2024-02-30'}, columns, [rule('day', 'notEquals', '2024-03-01')])).toBe(false);
  });
});

describe('rule validation and field capabilities', () => {
  it('rejects stale/unknown fields and unsupported operator combinations', () => {
    for (const filter of [rule('removed', 'equals', 'x'), rule('amount', 'contains', '1'), rule('name', 'gt', 'A')]) {
      expect(validateFilterRules(columns, [filter])).toHaveLength(1);
      expect(ids([filter])).toEqual([]);
    }
    expect(validateFilterRules(columns, [rule('id', 'equals', '1'), rule('name', 'equals', 'name')])[0].message).toContain('重复');
  });
  it('treats NULL operators as valueless for every field', () => {
    expect(filterNeedsValue('isNull')).toBe(false);
    expect(filterNeedsValue('isNotNull')).toBe(false);
    expect(filterNeedsValue('equals')).toBe(true);
    expect(validateFilterRules(columns, [rule('amount', 'isNull', 'invalid ignored')])).toEqual([]);
    expect(matchesRow({amount: null}, columns, [rule('amount', 'isNull')])).toBe(true);
  });
  it('never reads missing or inherited fields as data, even for NULL checks', () => {
    expect(matchesRow({}, columns, [rule('description', 'isNull')])).toBe(false);
    const inherited = Object.create({name: 'secret'}) as Row;
    expect(matchesRow(inherited, columns, [rule('name', 'equals', 'secret')])).toBe(false);
    expect(matchesRow({}, [{name: 'toString', type: 'text'}], [rule('toString', 'isNotNull')])).toBe(false);
  });
  it.each(['INT UNSIGNED', 'decimal(12,2)', 'BIGINT', 'float', 'double', 'real', 'number', 'boolean', 'bool', 'bit(1)', 'year'])('recognizes numeric type %s', type => {
    expect(getFilterKind({name: 'field', type})).toBe('number');
  });
  it('chooses only applicable operators and makes new rules without shared IDs', () => {
    expect(getFilterOperators(columns[1]).map(option => option.value)).toContain('contains');
    expect(getFilterOperators(columns[0]).map(option => option.value)).not.toContain('contains');
    expect(getFilterOperators(columns[5]).map(option => option.value)).toContain('gte');
    expect(createFilterRule(columns, 'one')).toEqual({id: 'one', field: 'id', operator: 'equals', value: ''});
    expect(createFilterRule([columns[1]], 'two')).toEqual({id: 'two', field: 'name', operator: 'contains', value: ''});
    expect(createFilterRule([], 'empty')).toEqual({id: 'empty', field: '', operator: 'contains', value: ''});
  });
});

describe('filter editor markup', () => {
  function render(rules: FilterRule[]) {
    return renderToStaticMarkup(createElement(TableFilterEditor, {columns, rules, errors: validateFilterRules(columns, rules), onChange: () => {}}));
  }
  it('labels all field/operator/value controls and shows arbitrary column choices', () => {
    const html = render([rule('name', 'contains', 'alpha')]);
    expect(html).toContain('条件 1 字段');
    expect(html).toContain('条件 1 运算符');
    expect(html).toContain('条件 1 值');
    expect(html).toContain('删除条件 1');
    for (const column of columns) expect(html).toContain(`value="${column.name}"`);
    expect(html).toContain('value="alpha"');
  });
  it('disables the value for NULL and explains why it is blank', () => {
    const html = render([rule('description', 'isNull', 'not used')]);
    expect(html).toMatch(/<input[^>]+disabled=""/);
    expect(html).toContain('placeholder="无需填写"');
    expect(html).not.toContain('value="not used"');
  });
  it('renders per-rule errors and AND, and exposes removed fields', () => {
    const html = render([rule('amount', 'gt', 'invalid'), rule('removed', 'equals', 'x', 'two')]);
    expect(html).toContain('role="alert"');
    expect(html).toContain('aria-invalid="true"');
    expect(html).toContain('请输入有效数值');
    expect(html).toContain('并且');
    expect(html).toContain('removed（字段已移除）');
    expect(html).toContain('请选择当前表中的有效字段');
  });
  it('explains that removing all rules restores all records', () => {
    expect(render([])).toContain('没有筛选条件，将显示全部记录');
  });
});
