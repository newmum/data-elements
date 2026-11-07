export interface SqlSelectionRange { from: number; to: number }
export interface SqlTableCatalog { database: string; tables: readonly string[] }
export interface SqlTableReference { database: string; table: string }

interface Token {
  kind: 'identifier' | 'symbol' | 'literal'
  value: string
  from: number
  to: number
  quoted?: boolean
}

// This is deliberately a bounded relation recognizer, not a SQL validator. Unknown
// dialect constructs fail closed. Quoted identifiers support ``, "" and [];
// single quotes are always literals. CTE statements are excluded altogether,
// including physical references inside them, rather than guessing CTE scope.
function tokenize(sql: string): Token[] | null {
  const tokens: Token[] = []
  let i = 0
  while (i < sql.length) {
    if (tokens.length >= 20_000) return null
    const from = i
    const c = sql[i]
    if (/\s/u.test(c)) { i++; continue }
    if (sql.startsWith('--', i) || c === '#') {
      while (i < sql.length && sql[i] !== '\n' && sql[i] !== '\r') i++
      continue
    }
    if (sql.startsWith('/*', i)) {
      if (sql.startsWith('/*!', i) || sql.startsWith('/*+', i)) return null
      i += 2
      let nesting = 1
      while (i < sql.length && nesting) {
        if (sql.startsWith('/*', i)) { nesting++; i += 2 }
        else if (sql.startsWith('*/', i)) { nesting--; i += 2 }
        else i++
      }
      if (nesting) return null
      continue
    }
    if (c === "'" || c === '`' || c === '"' || c === '[') {
      const end = c === '[' ? ']' : c
      const literal = c === "'"
      let value = ''
      let closed = false
      i++
      while (i < sql.length) {
        if (sql[i] === '\\') {
          // Backslash quoting depends on session SQL mode; do not guess.
          return null
        }
        if (sql[i] === end) {
          if (sql[i + 1] === end) { value += end; i += 2; continue }
          i++; closed = true; break
        }
        value += sql[i++]
      }
      if (!closed) return null
      tokens.push({ kind: literal ? 'literal' : 'identifier', value, from, to: i, quoted: !literal })
    } else if (/[\p{L}_]/u.test(c)) {
      i++
      while (i < sql.length && /[\p{L}\p{N}_$]/u.test(sql[i])) i++
      tokens.push({ kind: 'identifier', value: sql.slice(from, i), from, to: i })
    } else {
      // Dollar-quoted strings, variables and nonstandard quoting are unsupported.
      if (c === '$' || c === '\\') return null
      tokens.push({ kind: 'symbol', value: c, from, to: ++i })
    }
  }
  return tokens
}

const word = (token: Token | undefined) => token?.kind === 'identifier' && !token.quoted ? token.value.toUpperCase() : ''
const stopFrom = new Set(['WHERE', 'GROUP', 'ORDER', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'EXCEPT', 'INTERSECT', 'RETURNING', 'SET', 'WINDOW', 'QUALIFY', 'FETCH', 'FOR'])
const reserved = new Set(['SELECT', 'FROM', 'JOIN', 'ON', 'AS', 'WITH', 'LATERAL', 'ONLY', 'TABLE', 'VALUES', ...stopFrom])

/** Resolve one complete selected relation against only the supplied data-source catalog.
 * Bare names use currentDatabase; two-part names use their explicit database.
 * Case-insensitive matching must be unique, and returns catalog spelling. No
 * fallback to another database, aliases, CTEs, three-part names or table functions.
 */
export function resolveSelectedTableReference(
  sql: string,
  ranges: readonly SqlSelectionRange[],
  catalog: readonly SqlTableCatalog[],
  currentDatabase: string,
): SqlTableReference | null {
  if (sql.length > 200_000 || ranges.length !== 1) return null
  const { from, to } = ranges[0]
  if (!Number.isInteger(from) || !Number.isInteger(to) || from < 0 || to <= from || to > sql.length) return null
  const all = tokenize(sql)
  if (!all) return null
  // Isolate the selected statement, so a preceding CTE does not suppress later SQL.
  let before = -1
  all.forEach((t, index) => { if (t.value === ';' && t.kind === 'symbol' && t.to <= from) before = index })
  const after = all.findIndex(t => t.value === ';' && t.kind === 'symbol' && t.from >= to)
  const tokens = all.slice(before + 1, after < 0 ? undefined : after)
  if (tokens.some(t => word(t) === 'WITH' || (t.kind === 'symbol' && t.value === ';'))) return null
  const sameRange = (start: number, end: number) => from === start && to === end
  const matchesToken = (t: Token) => sameRange(t.from, t.to) || (t.quoted && sameRange(t.from + 1, t.to - 1))
  const candidates: { database?: string; table: string }[] = []
  function relation(index: number, allowColumnList = false) {
    const first = tokens[index]
    if (!first || first.kind !== 'identifier' || reserved.has(word(first))) return
    let last = first
    let end = index
    let database: string | undefined
    if (tokens[end + 1]?.value === '.') {
      database = first.value
      last = tokens[end + 2]
      if (!last || last.kind !== 'identifier' || reserved.has(word(last))) return
      end += 2
    }
    if (tokens[end + 1]?.value === '.' || (!allowColumnList && tokens[end + 1]?.value === '(')) return
    if (sameRange(first.from, last.to) || matchesToken(last)) candidates.push({ database, table: last.value })
  }
  interface Scope { select: boolean; fromList: boolean; join: boolean }
  const scopes: Scope[] = [{ select: false, fromList: false, join: false }]
  const command = word(tokens[0])
  // Multi-table DML can target an alias rather than the identically named table.
  if (command === 'UPDATE' && tokens.some(t => ['FROM', 'JOIN'].includes(word(t)))) return null
  if (command === 'DELETE' && tokens.some(t => ['USING', 'JOIN'].includes(word(t)))) return null
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]
    if (token.kind === 'symbol' && token.value === '(') {
      if (scopes.length >= 128) return null
      scopes.push({ select: false, fromList: false, join: false }); continue
    }
    if (token.kind === 'symbol' && token.value === ')') {
      if (scopes.length === 1) return null
      scopes.pop(); continue
    }
    const scope = scopes[scopes.length - 1]
    const key = word(token)
    if (key === 'SELECT') scope.select = true
    if (stopFrom.has(key)) { scope.fromList = false; scope.join = false }
    if (key === 'ON' || key === 'USING') scope.fromList = false
    if (key === 'FROM' && (scope.select || (scopes.length === 1 && command === 'DELETE'))) {
      relation(i + 1); scope.fromList = true; scope.join = true
    } else if (key === 'JOIN' && scope.join) {
      relation(i + 1); scope.fromList = true
    } else if (token.kind === 'symbol' && token.value === ',' && scope.fromList) relation(i + 1)
  }
  // Unterminated parenthesized SQL is uncertain, even if a token looked valid.
  if (scopes.length !== 1) return null
  if (command === 'UPDATE' || command === 'DESC' || command === 'DESCRIBE') relation(1)
  if ((command === 'INSERT' || command === 'REPLACE') && word(tokens[1]) === 'INTO') relation(2, true)
  if (command === 'TRUNCATE') relation(word(tokens[1]) === 'TABLE' ? 2 : 1)
  if (['CREATE', 'ALTER', 'DROP'].includes(command) && word(tokens[1]) === 'TABLE') {
    let index = 2
    if (word(tokens[index]) === 'IF') {
      index++
      if (word(tokens[index]) === 'NOT') index++
      if (word(tokens[index]) !== 'EXISTS') return null
      index++
    }
    relation(index, command === 'CREATE')
  }
  if (command === 'SHOW' && word(tokens[1]) === 'CREATE' && word(tokens[2]) === 'TABLE') relation(3)
  if (candidates.length !== 1) return null
  const candidate = candidates[0]
  const database = candidate.database ?? currentDatabase
  const databases = catalog.filter(entry => entry.database.toLowerCase() === database.toLowerCase())
  if (databases.length !== 1) return null
  const tables = databases[0].tables.filter(table => table.toLowerCase() === candidate.table.toLowerCase())
  return tables.length === 1 ? { database: databases[0].database, table: tables[0] } : null
}
