package com.linewell.dataelement.mapping.core;

import java.util.*;

/** Exact names win; unambiguous Java/SQL naming variants remain subject to the case policy. */
public final class SourceFieldNames {
    private final Set<String> names;
    private final boolean caseSensitive;
    private final Map<String,String> cache = new HashMap<>();
    private Map<String,List<String>> folded, conventional;

    public SourceFieldNames(Collection<String> names, boolean caseSensitive) {
        this.names = names instanceof Set<String> set ? set : new LinkedHashSet<>(names);
        this.caseSensitive = caseSensitive;
    }

    public String resolve(String name) {
        if (names.contains(name)) return name;
        if (cache.containsKey(name)) return cache.get(name);
        String result;
        if (!caseSensitive) {
            if (folded == null) { folded = new HashMap<>(); conventional = new HashMap<>();
                for (String candidate : names) {
                    folded.computeIfAbsent(candidate.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(candidate);
                    if (identifier(candidate)) conventional.computeIfAbsent(normalize(candidate), key -> new ArrayList<>()).add(candidate);
                }
            }
            result = unique(folded.get(name.toLowerCase(Locale.ROOT)));
            if (result == null && identifier(name)) result = unique(conventional.get(normalize(name)));
        } else {
            Set<String> candidates = new LinkedHashSet<>();
            if (identifier(name)) {
                String camel = camel(name), snake = snake(name);
                if (!camel.equals(name) && names.contains(camel)) candidates.add(camel);
                if (!name.contains("_") && snake.contains("_") && names.contains(snake)) candidates.add(snake);
            }
            result = unique(candidates);
        }
        cache.put(name, result);
        return result;
    }

    private static String unique(Collection<String> matches) {
        if (matches == null || matches.isEmpty()) return null;
        if (matches.size() != 1) throw new MappingException("AMBIGUOUS_FIELD", "来源字段的命名或大小写匹配存在歧义，请显式指定来源字段");
        return matches.iterator().next();
    }
    private static boolean identifier(String value) { return value.matches("[A-Za-z][A-Za-z0-9_]*"); }
    private static String normalize(String value) { return value.replace("_", "").toLowerCase(Locale.ROOT); }
    private static String camel(String name) {
        StringBuilder result = new StringBuilder(); boolean upper = false;
        for (char c : name.toCharArray()) { if (c == '_') upper = true; else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
        return result.toString();
    }
    private static String snake(String name) {
        StringBuilder result = new StringBuilder();
        for (int index=0;index<name.length();index++) {
            char c=name.charAt(index);
            if (Character.isUpperCase(c) && index>0 && name.charAt(index-1)!='_' &&
                    (Character.isLowerCase(name.charAt(index-1)) || Character.isDigit(name.charAt(index-1)) ||
                     index+1<name.length() && Character.isLowerCase(name.charAt(index+1)))) result.append('_');
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }
}
