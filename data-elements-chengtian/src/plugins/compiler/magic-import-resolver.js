/**
 * Resolves imports used by persisted low-code components.
 *
 * Vite exposes some CommonJS packages (such as exceljs) as an ES module
 * namespace whose usable API is on `default`. Older compiled components use
 * `*` for both namespace and default imports, so keep that representation
 * backward compatible here.
 */
export function resolveMagicImport(library, name) {
  const resolvedLibrary = library?.default ?? library?.["module.exports"] ?? library;

  if (name === "*" || name === "default") {
    return resolvedLibrary;
  }

  return library?.[name] ?? resolvedLibrary?.[name];
}
