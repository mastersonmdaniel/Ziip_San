package br.com.ziipsan.integration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ciclo de vida e versionamento da API pública (P2.26).
 *
 * <p>Versão corrente: 1, sob {@code /api/v1/public-api/v1}. Quando uma
 * versão for descontinuada, ela entra em {@code deprecated} com a data
 * de desligamento: enquanto existir, as respostas dela carregam
 * {@code Deprecation: true} e {@code Sunset: <data RFC 7231>}; depois
 * da data, a versão some do roteamento (404). Hoje nenhuma versão está
 * descontinuada — o mecanismo existe e é testado, a decisão de
 * descontinuar uma versão é do dono, nunca automática.
 */
public final class PublicApiVersions {

    public static final int CURRENT = 1;

    private final Map<Integer, String> deprecatedSunsets;

    public PublicApiVersions(Map<Integer, String> deprecatedSunsets) {
        this.deprecatedSunsets = Map.copyOf(deprecatedSunsets);
    }

    /** Versões descontinuadas hoje (vazio na v1: nenhuma versão descontinuada). */
    public static PublicApiVersions current() {
        return new PublicApiVersions(Map.of());
    }

    public boolean isKnown(int version) {
        return version == CURRENT || deprecatedSunsets.containsKey(version);
    }

    public boolean isDeprecated(int version) {
        return deprecatedSunsets.containsKey(version);
    }

    /** Data de desligamento (Sunset) da versão, se descontinuada. */
    public Optional<String> sunsetFor(int version) {
        return Optional.ofNullable(deprecatedSunsets.get(version));
    }

    public Set<Integer> knownVersions() {
        var known = new java.util.HashSet<Integer>(deprecatedSunsets.keySet());
        known.add(CURRENT);
        return Set.copyOf(known);
    }
}
