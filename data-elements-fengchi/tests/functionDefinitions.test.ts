import { describe, expect, it } from 'vitest';
import { functionSql, initialFunctions, nextFunctionName, parseFunctionDefinitions, validateFunction } from '../src/functionDefinitions';

describe('local function definitions', () => {
  it('starts with three editable definitions and preserves an intentionally empty list', () => {
    expect(parseFunctionDefinitions(null)).toHaveLength(3);
    expect(parseFunctionDefinitions('[]')).toEqual([]);
  });
  it('rejects corrupt persisted definitions instead of replacing them', () => {
    expect(() => parseFunctionDefinitions('{bad')).toThrow();
    expect(() => parseFunctionDefinitions('[{"name":"bad"}]')).toThrow();
    expect(() => parseFunctionDefinitions(JSON.stringify([initialFunctions[0], initialFunctions[0]]))).toThrow();
  });
  it('validates names, duplicates, return types and bodies', () => {
    expect(validateFunction(initialFunctions[0], initialFunctions)).toBeNull();
    for (const changes of [{ name: 'bad-name' }, { name: initialFunctions[1].name.toUpperCase() }, { returnType: ' ' }, { body: '' }]) {
      expect(validateFunction({ ...initialFunctions[0], ...changes }, initialFunctions)).toBeTruthy();
    }
  });
  it('makes collision-free names within MySQL identifier length', () => {
    expect(nextFunctionName('calculate_total', initialFunctions)).toBe('calculate_total_1');
    const long = { ...initialFunctions[0], name: 'a'.repeat(64) };
    expect(nextFunctionName(long.name, [long])).toHaveLength(64);
    expect(nextFunctionName(long.name, [long])).toMatch(/_1$/);
  });
  it('generates a readable SQL definition and quotes comments and identifiers', () => {
    const ddl = functionSql({ ...initialFunctions[0], name: 'odd`name', comment: "customer's total" });
    expect(ddl).toContain('`odd``name`');
    expect(ddl).toContain("COMMENT 'customer''s total'");
    expect(ddl).toContain('RETURNS DECIMAL(12,2)');
    expect(ddl).toContain('RETURN ROUND');
  });
});
