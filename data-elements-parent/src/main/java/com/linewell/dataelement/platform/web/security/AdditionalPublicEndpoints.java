package com.linewell.dataelement.platform.web.security;

import java.util.Set;

/** Exact public endpoint paths supplied by an installed integration. No wildcard expansion. */
public interface AdditionalPublicEndpoints {
    Set<String> paths();
}
