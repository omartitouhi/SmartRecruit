package com.smartrecruit.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Request seen by the proxy: client-supplied identity headers are hidden and replaced by the
 * ones computed by the gateway, so a client can never spoof another user's identity.
 */
class IdentityRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    IdentityRequestWrapper(HttpServletRequest request, Map<String, String> identityHeaders) {
        super(request);
        overrides.putAll(identityHeaders);
    }

    private static boolean isIdentityHeader(String name) {
        return InternalIdentityHeaders.ALL.stream().anyMatch(header -> header.equalsIgnoreCase(name));
    }

    @Override
    public String getHeader(String name) {
        if (isIdentityHeader(name)) {
            return overrides.get(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (isIdentityHeader(name)) {
            String value = overrides.get(name);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Collections.list(super.getHeaderNames()).stream()
                .filter(name -> !isIdentityHeader(name))
                .forEach(names::add);
        names.addAll(overrides.keySet());
        return Collections.enumeration(names);
    }
}
