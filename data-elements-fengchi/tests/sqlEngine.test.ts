import { describe, expect, it } from 'vitest';
import { initialTables } from '../src/data';
import { executeSQL } from '../src/sqlEngine';
const run = (sql: string) => executeSQL(sql, initialTables);
describe('read-only SQL engine', () => {
  it('selects all columns with a limit', () => { const r = run('SELECT * FROM users LIMIT 5;'); expect(r.rows).toHaveLength(5); expect(r.columns).toEqual(initialTables.users.columns.map(c => c.name)); });
  it('filters AND comparisons and sorts before limiting', () => { expect(run("SELECT id, name FROM users WHERE id >= 2 AND id < 5 ORDER BY id DESC LIMIT 2").rows.map(r => r.id)).toEqual([4, 3]); });
  it('supports strings and escaped quotes', () => { expect(run("SELECT id FROM users WHERE role = 'admin'").rows).toEqual([{ id: 1 }]); const t = structuredClone(initialTables); t.users.rows[0].name = "O'Brien"; expect(executeSQL("SELECT id FROM users WHERE name = 'O''Brien'", t).rows).toEqual([{ id: 1 }]); });
  it('counts matching rows and respects aggregate LIMIT 0', () => { expect(run('SELECT COUNT(*) AS total FROM users WHERE id <= 3').rows).toEqual([{ total: 3 }]); expect(run('SELECT COUNT(*) FROM users LIMIT 0').rows).toEqual([]); });
  it('lists tables and describes column metadata', () => { expect(run('SHOW TABLES').rows).toHaveLength(5); expect(run('DESCRIBE users').rows[0]).toMatchObject({ Field: 'id', Key: 'PRI', Null: 'NO' }); });
  it('handles casing, comments, and backtick names', () => { expect(run('-- query\n select `id` from USERS order by id asc limit 1').rows).toEqual([{ id: 1 }]); });
  it('does not match NULL equality and never mutates input', () => { const before = JSON.stringify(initialTables); expect(run('SELECT * FROM categories WHERE description = NULL').rows).toEqual([]); run('SELECT * FROM users ORDER BY id DESC'); expect(JSON.stringify(initialTables)).toBe(before); });
  it.each(['UPDATE users SET name = \'x\'', 'DELETE FROM users', 'SELECT * FROM users JOIN orders ON users.id = orders.user_id', 'SELECT role FROM users GROUP BY role', 'SELECT * FROM users UNION SELECT * FROM users'])('rejects unsupported statement %s', sql => { expect(() => run(sql)).toThrow(); });
  it.each(['SELECT * FROM absent', 'SELECT missing FROM users', 'SELECT * FROM users WHERE missing = 1', 'SELECT * FROM users ORDER BY missing', 'SELECT * FROM users LIMIT -1', 'SELECT * FROM users LIMIT 1.5', 'SELECT * FROM users; SELECT * FROM orders', 'SELECT * FROM users WHERE id = 1 OR id = 2', 'SELECT * FROM users nonsense', 'SELECT COUNT(*) FROM users ORDER BY id', 'SELECT id, id FROM users', '', "SELECT * FROM users WHERE id = '1'"])('rejects invalid or unimplemented input %s', sql => { expect(() => run(sql)).toThrow(); });
});
