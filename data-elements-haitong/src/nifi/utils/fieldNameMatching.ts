export interface NamedField {
  name: string;
  path: string;
}

export interface ExactFieldNameMatch<TSource extends NamedField, TTarget extends NamedField> {
  source: TSource;
  target: TTarget;
  differsOnlyByCase: boolean;
}

/**
 * Database identifiers are conventionally case-insensitive for matching. The
 * original spelling is deliberately retained in the returned fields so an
 * actual source or target identifier is never rewritten.
 */
export function identifierMatchKey(value: string): string {
  return (value ?? '').trim().toLowerCase();
}

/**
 * Match only unambiguous, same-name fields. A key such as ID/id appearing more
 * than once on either side is intentionally left for the user to choose.
 */
export function findUniqueCaseInsensitiveFieldMatches<
  TSource extends NamedField,
  TTarget extends NamedField,
>(sources: TSource[], targets: TTarget[]): Array<ExactFieldNameMatch<TSource, TTarget>> {
  const sourcesByName = new Map<string, TSource[]>();
  const targetsByName = new Map<string, TTarget[]>();
  sources.forEach((source) => {
    const key = identifierMatchKey(source.name);
    if (!key) return;
    sourcesByName.set(key, [...(sourcesByName.get(key) ?? []), source]);
  });
  targets.forEach((target) => {
    const key = identifierMatchKey(target.name);
    if (!key) return;
    targetsByName.set(key, [...(targetsByName.get(key) ?? []), target]);
  });

  const matches: Array<ExactFieldNameMatch<TSource, TTarget>> = [];
  targetsByName.forEach((sameNameTargets, key) => {
    const sameNameSources = sourcesByName.get(key) ?? [];
    if (sameNameSources.length !== 1 || sameNameTargets.length !== 1) return;
    const source = sameNameSources[0];
    const target = sameNameTargets[0];
    matches.push({ source, target, differsOnlyByCase: source.name !== target.name });
  });
  return matches;
}
