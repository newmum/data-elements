import { describe, expect, it } from 'vitest';
import { captureSqlTableContext, currentSqlTableReference } from '../src/sqlEditorContext';

const sql = 'SELECT * FROM commerce_db.users;';
const ranges = [{ from: sql.indexOf('users'), to: sql.indexOf('users') + 5 }];
const catalog = [{ database: 'commerce_db', tables: ['users', 'orders'] }];
const capture = () => captureSqlTableContext('query-one', sql, ranges, catalog, 'commerce_db');

describe('SQL table menu context', () => {
  it('captures the exact current query and canonical table', () => {
    expect(capture()).toEqual({ queryKey: 'query-one', sql, ranges, reference: { database: 'commerce_db', table: 'users' } });
    expect(currentSqlTableReference(capture(), 'query-one', sql, catalog, 'commerce_db')).toEqual({ database: 'commerce_db', table: 'users' });
  });

  it('copies selection ranges instead of retaining mutable caller objects', () => {
    const source = [{ ...ranges[0] }];
    const context = captureSqlTableContext('query-one', sql, source, catalog, 'commerce_db');
    source[0].to = source[0].from;
    expect(context?.ranges).toEqual(ranges);
  });

  it('does not create a table item for an empty or unknown selection', () => {
    expect(captureSqlTableContext('query-one', sql, [{ from: 0, to: 0 }], catalog, 'commerce_db')).toBeNull();
    expect(captureSqlTableContext('query-one', sql, ranges, [], 'commerce_db')).toBeNull();
  });

  it('rejects a menu after switching to a different query with identical SQL', () => {
    expect(currentSqlTableReference(capture(), 'query-two', sql, catalog, 'commerce_db')).toBeNull();
    expect(currentSqlTableReference(capture(), undefined, sql, catalog, 'commerce_db')).toBeNull();
  });

  it('rejects stale document offsets after editing the SQL', () => {
    expect(currentSqlTableReference(capture(), 'query-one', sql.replace('users', 'orders'), catalog, 'commerce_db')).toBeNull();
  });

  it('rechecks existence and ambiguity before navigation', () => {
    expect(currentSqlTableReference(capture(), 'query-one', sql, [{ database: 'commerce_db', tables: ['orders'] }], 'commerce_db')).toBeNull();
    expect(currentSqlTableReference(capture(), 'query-one', sql, [{ database: 'commerce_db', tables: ['users', 'USERS'] }], 'commerce_db')).toBeNull();
  });

  it('does not turn an old current-database reference into a new database target', () => {
    const unqualified = 'SELECT * FROM users';
    const selection = [{ from: 14, to: 19 }];
    const databases = [...catalog, { database: 'other_db', tables: ['users'] }];
    const context = captureSqlTableContext('query-one', unqualified, selection, databases, 'commerce_db');
    expect(currentSqlTableReference(context, 'query-one', unqualified, databases, 'other_db')).toBeNull();
  });
});
