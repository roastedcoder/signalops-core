package com.signalops.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void convertsKeycloakRealmRolesToSpringRoleAuthorities() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of(
                        "roles", List.of("signalops-operator", "signalops-admin")
                )
        ));

        Set<String> authorities = authorityNames(jwt);

        assertTrue(authorities.contains("ROLE_signalops-operator"));
        assertTrue(authorities.contains("ROLE_signalops-admin"));
    }

    @Test
    void retainsScopeAuthoritiesAlongsideRealmRoles() {
        Jwt jwt = jwt(Map.of(
                "scope", "openid profile",
                "realm_access", Map.of("roles", List.of("signalops-operator"))
        ));

        Set<String> authorities = authorityNames(jwt);

        assertTrue(authorities.contains("SCOPE_openid"));
        assertTrue(authorities.contains("SCOPE_profile"));
        assertTrue(authorities.contains("ROLE_signalops-operator"));
    }

    @Test
    void returnsScopeAuthoritiesWhenRealmRolesAreMissing() {
        Jwt jwt = jwt(Map.of("scope", "openid"));

        assertEquals(Set.of("SCOPE_openid"), authorityNames(jwt));
    }

    @Test
    void ignoresMalformedAndBlankRealmRoleEntries() {
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("signalops-operator", "", 42))
        ));

        assertEquals(Set.of("ROLE_signalops-operator"), authorityNames(jwt));
    }

    private Set<String> authorityNames(Jwt jwt) {
        return converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(existing -> existing.putAll(claims))
                .build();
    }
}
