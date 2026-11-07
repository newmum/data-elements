import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import ColumnHeader, {FieldTypeIcon, getFieldKind} from '../src/ColumnHeader';
import RecordFormView from '../src/RecordFormView';
import {initialTables} from '../src/data';

// Vitest's default CSS transform returns an empty module, even with ?raw.
// Read the authored tokens without enabling browser CSS processing globally.
const fileSystemModule = 'node:fs';
const {readFileSync} = await import(/* @vite-ignore */ fileSystemModule) as {
  readFileSync: (path: URL, encoding: 'utf8') => string;
};
const iconStyles = readFileSync(new URL('../src/fieldTypeIcons.css', import.meta.url), 'utf8');
const themeStyles = readFileSync(new URL('../src/themes.css', import.meta.url), 'utf8');

const typeGroups = [
  ['text', ['VARCHAR(50)', 'TEXT', 'CHAR(1)']],
  ['number', ['INT UNSIGNED', 'DECIMAL(10,2)', 'BIGINT', 'DOUBLE']],
  ['temporal', ['DATE', 'TIME', 'DATETIME', 'TIMESTAMP', 'YEAR']],
  ['boolean', ['BOOLEAN', 'BOOL', 'BIT']],
  ['document', ['JSON', 'XML']],
  ['binary', ['BLOB', 'VARBINARY(16)', 'BYTEA']],
] as const;

describe('shared field type icons', () => {
  it.each(typeGroups)('gives the %s group one consistent font-independent SVG', (kind, types) => {
    const rendered = types.map(type => {
      expect(getFieldKind(type)).toBe(kind);
      const html = renderToStaticMarkup(createElement(FieldTypeIcon, {type}));
      expect(html.match(/<svg\b/g)).toHaveLength(1);
      expect(html).toContain(`class="field-type-icon field-kind-${kind}"`);
      expect(html).toContain('width="14" height="14" viewBox="0 0 20 20"');
      expect(html).toContain('stroke="currentColor" stroke-width="1.5"');
      expect(html).toContain('aria-hidden="true" focusable="false"');
      expect(html).not.toMatch(/<text\b|<image\b|<foreignObject\b|\p{Extended_Pictographic}/u);
      return html;
    });
    expect(new Set(rendered).size).toBe(1);
  });

  it('distinguishes every type group by its geometry, not only its color', () => {
    const shapes = typeGroups.map(([, [type]]) => renderToStaticMarkup(createElement(FieldTypeIcon, {type})).replace(/<svg[^>]*>/, ''));
    expect(new Set(shapes).size).toBe(typeGroups.length);
  });

  it('renders one icon per field in both grid headers and record forms, retaining accessible type text', () => {
    const table = initialTables.users;
    const form = renderToStaticMarkup(createElement(RecordFormView, {table, rows:table.rows, selectedId:1, onSelect:()=>{}, onChange:()=>{}}));
    expect(form.match(/class="field-type-icon /g)).toHaveLength(table.columns.length);
    for (const column of table.columns) {
      const grid = renderToStaticMarkup(createElement(ColumnHeader, {column}));
      expect(grid.match(/class="field-type-icon /g)).toHaveLength(1);
      expect(grid).toContain(`<span>${column.type.toUpperCase()}</span>`);
      expect(form).toContain(`<span>${column.type}${column.primary ? ' · 主键' : ''}</span>`);
    }
    expect(form).toMatch(/aria-describedby="[^"]+-type/);
    expect(form).toContain('主键');
  });
});

const tokens = (styles: string) => Object.fromEntries([...styles.matchAll(/--([\w-]+):\s*(#[\da-f]{6});/gi)].map(([, name, value]) => [name, value]));
function luminance(hex: string) {
  const channels = [1, 3, 5].map(offset => parseInt(hex.slice(offset, offset + 2), 16) / 255);
  const [r, g, b] = channels.map(channel => channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4);
  return .2126 * r + .7152 * g + .0722 * b;
}
function contrast(first: string, second: string) {
  const [dark, light] = [luminance(first), luminance(second)].sort((a, b) => a - b);
  return (light + .05) / (dark + .05);
}

describe('field type color accessibility', () => {
  it.each(['light', 'dark'])('keeps all %s type accents readable on header and form surfaces', theme => {
    const themeBlock = new RegExp(`:root\\[data-theme='${theme}'\\]\\s*\\{([\\s\\S]*?)\\}`);
    const palette = tokens(iconStyles.match(themeBlock)![1]);
    const surfaces = tokens(themeStyles.match(themeBlock)![1]);
    expect(new Set(Object.values(palette)).size).toBe(typeGroups.length);
    for (const [kind] of typeGroups) {
      for (const surface of ['surface', 'surface-inset', 'chrome', 'chrome-soft']) {
        expect(contrast(palette[`field-type-${kind}`], surfaces[surface]), `${kind} on ${surface}`).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('overrides legacy monochrome styling and respects forced colors without hiding type shapes', () => {
    expect(iconStyles).toContain(':root .field-type-icon {');
    for (const [kind] of typeGroups.filter(([kind]) => kind !== 'text')) {
      expect(iconStyles).toContain(`:root .field-type-icon.field-kind-${kind} { color: var(--field-type-${kind}); }`);
    }
    expect(iconStyles).toContain('@media (forced-colors: active)');
    expect(iconStyles).toContain(".field-type-icon[class*='field-kind-'] { color: CanvasText; }");
    expect(iconStyles).not.toContain('forced-color-adjust: none');
  });
});
