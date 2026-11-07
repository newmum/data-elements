import { describe, expect, it } from 'vitest'
import { resolveSelectedTableReference, type SqlTableCatalog } from '../src/sqlTableReference'

const catalog: readonly SqlTableCatalog[] = [
  { database: 'commerce_db', tables: ['Orders', 'users', 'order items', 'a`b', 'a"b', 'a]b', '订单'] },
  { database: 'analytics', tables: ['Orders', 'reports'] },
]
// Selection markers make exact double-click and drag-selection boundaries visible.
function resolve(marked: string, entries = catalog, current = 'commerce_db') {
  const from = marked.indexOf('«')
  const to = marked.indexOf('»') - 1
  return resolveSelectedTableReference(marked.replace('«', '').replace('»', ''), [{ from, to }], entries, current)
}
const orders = { database: 'commerce_db', table: 'Orders' }

describe('resolveSelectedTableReference', () => {
  it.each([
    'SELECT * FROM «orders»', 'select * from «ORDERS» o',
    'SELECT orders FROM «orders» AS users',
    'SELECT * FROM users u JOIN «orders» o ON o.id = u.id',
    'SELECT * FROM users u LEFT JOIN «orders» ON true',
    'SELECT * FROM users, «orders»',
    'SELECT * FROM (SELECT * FROM «orders») q',
    'SELECT * FROM users WHERE EXISTS (SELECT 1 FROM «orders»)',
    'SELECT * FROM /* comment */ «orders»',
    'SELECT * FROM -- comment\n«orders»',
    'SELECT * FROM `«Orders»`', 'SELECT * FROM «`Orders`»',
    'SELECT * FROM "«Orders»"', 'SELECT * FROM [«Orders»]',
    'SELECT * FROM commerce_db.«orders»',
    'SELECT * FROM «commerce_db.orders»',
    'SELECT * FROM «`commerce_db`.`Orders`»',
    'SELECT * FROM `commerce_db` /* a */ . /* b */ `«Orders»`',
    'UPDATE «orders» SET id = 1', 'INSERT INTO «orders» VALUES (1)',
    'INSERT INTO «orders» (id) VALUES (1)',
    'REPLACE INTO «orders» VALUES (1)', 'DELETE FROM «orders» WHERE id = 1',
    'TRUNCATE «orders»', 'TRUNCATE TABLE «orders»',
    'ALTER TABLE «orders» ADD id INT', 'DROP TABLE IF EXISTS «orders»',
    'CREATE TABLE IF NOT EXISTS «orders» (id INT)',
    'DESC «orders»', 'DESCRIBE «orders»', 'SHOW CREATE TABLE «orders»',
    "SELECT '; FROM orders'; SELECT * FROM «orders»; SELECT 1",
    'WITH orders AS (SELECT 1) SELECT * FROM orders; SELECT * FROM «orders»',
  ])('recognizes a selected relation: %s', sql => expect(resolve(sql)).toEqual(orders))

  it.each([
    'UPDATE «orders» SET id = 1 FROM users AS orders',
    'DELETE FROM «orders» USING users AS orders',
    'SELECT «orders» FROM users', 'SELECT orders.id FROM users «orders»',
    'SELECT * FROM orders AS «orders»', 'SELECT * FROM users WHERE «orders» = 1',
    'SELECT * FROM «ord»ers', 'SELECT * FROM ord«ers»',
    'SELECT * FROM « orders»', 'SELECT * FROM «orders »',
    'SELECT * FROM «commerce_db».orders', 'SELECT * FROM commerce_db.orders.«orders»',
    'SELECT * FROM «unknown»', 'SELECT * FROM unknown.«orders»',
    'SELECT * FROM «reports»', 'SELECT * FROM «orders»()',
    "SELECT 'FROM «orders»'", "SELECT * FROM users WHERE x = 'JOIN «orders»'",
    'SELECT * FROM users -- JOIN «orders»', 'SELECT * FROM users # JOIN «orders»',
    'SELECT * FROM users /* JOIN «orders» */',
    'SELECT * FROM users /* outer /* JOIN «orders» */ end */',
    'SELECT * FROM «orders» /* unclosed', 'SELECT * FROM `«orders»',
    'SELECT * FROM «orders» WHERE x = $$FROM orders$$',
    'SELECT * FROM «orders» /*! WHERE false */',
    'WITH orders AS (SELECT 1) SELECT * FROM «orders»',
    'WITH q AS (SELECT * FROM «orders») SELECT * FROM q',
    'WITH RECURSIVE orders AS (SELECT 1) SELECT * FROM «orders»',
    'SELECT * FROM (SELECT 1) «orders»',
    'SELECT EXTRACT(day FROM «orders») FROM users',
    'SELECT * FROM users JOIN users u ON u.id = 1, «orders»',
    'SELECT * FROM users ORDER BY id, «orders»',
    'SELECT * FROM (SELECT * FROM «orders»',
    'SELECT * FROM «orders»)', 'SELECT * FROM «orders; SELECT * FROM users»',
  ])('rejects non-relations or uncertain SQL: %s', sql => expect(resolve(sql)).toBeNull())

  it('respects explicit database and returns canonical names', () => {
    expect(resolve('SELECT * FROM ANALYTICS.«orders»')).toEqual({ database: 'analytics', table: 'Orders' })
    expect(resolve('SELECT * FROM «reports»', catalog, 'analytics')).toEqual({ database: 'analytics', table: 'reports' })
    expect(resolve('SELECT * FROM «orders»', catalog, 'missing')).toBeNull()
    expect(resolve('SELECT * FROM commerce_db.«orders»', catalog, 'missing')).toEqual(orders)
  })
  it.each([
    ['SELECT * FROM «`order items`»', 'order items'],
    ['SELECT * FROM `«order items»`', 'order items'],
    ['SELECT * FROM «`a``b`»', 'a`b'], ['SELECT * FROM `«a``b»`', 'a`b'],
    ['SELECT * FROM «"a""b"»', 'a"b'], ['SELECT * FROM «[a]]b]»', 'a]b'],
    ['SELECT * FROM «订单»', '订单'],
  ])('decodes complete identifiers: %s', (sql, table) => expect(resolve(sql)).toEqual({ database: 'commerce_db', table }))

  it('rejects ambiguous catalog databases and tables, even with exact casing', () => {
    expect(resolve('SELECT * FROM «Orders»', [...catalog, catalog[0]])).toBeNull()
    expect(resolve('SELECT * FROM «Orders»', [{ database: 'commerce_db', tables: ['Orders', 'orders'] }])).toBeNull()
    expect(resolve('SELECT * FROM «Orders»', [{ database: 'commerce_db', tables: ['Orders', 'Orders'] }])).toBeNull()
    expect(resolve('SELECT * FROM «Orders»', [])).toBeNull()
  })
  it('requires exactly one nonempty bounded selection', () => {
    const sql = 'SELECT * FROM orders'
    for (const ranges of [[], [{ from: 14, to: 14 }], [{ from: -1, to: 20 }], [{ from: 14, to: 21 }], [{ from: 14.1, to: 20 }], [{ from: 14, to: 20 }, { from: 14, to: 20 }]]) {
      expect(resolveSelectedTableReference(sql, ranges, catalog, 'commerce_db')).toBeNull()
    }
  })
  it('bounds input size and nesting', () => {
    expect(resolve(' '.repeat(200_001) + 'SELECT * FROM «orders»')).toBeNull()
    expect(resolve('SELECT '.repeat(20_001) + '* FROM «orders»')).toBeNull()
    expect(resolve('SELECT * FROM ' + '('.repeat(128) + 'SELECT * FROM «orders»' + ')'.repeat(128))).toBeNull()
  })
})
