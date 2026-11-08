package br.com.ziipsan.reporting.bi;

import java.net.URI;
import java.util.Objects;

/**
 * ED-47: configuração do SSO do Superset com o Keycloak (forma da
 * exceção do ADR-0010: "SSO pelo Keycloak").
 *
 * <p>O que é fato do projeto e entra como padrão: o realm é
 * {@code ziipsan} (login ponta a ponta nesse realm desde a P0.33). O
 * que NÃO é fato e fica como parâmetro obrigatório de ambiente: a URL
 * do emissor (depende do ambiente — local, homologação, produção) e o
 * {@code clientId} do cliente OIDC a criar no realm para o Superset
 * (criação é passo de implantação, registrado no runbook de operação;
 * nenhum client é presumido como já existente).
 *
 * <p>O segredo do cliente NUNCA entra neste record: ele vive no cofre e
 * é injetado no contêiner do Superset por variável de ambiente na
 * fiação (ver {@code infra/superset/superset_config.py}).
 *
 * <p>Emissor {@code http} só é aceito em loopback (ambiente local da
 * P0.27/P0.29); fora dele, emissor sem TLS é recusado na construção.
 */
public record SupersetSsoConfig(
        URI issuer,
        String realm,
        String clientId,
        URI redirectUri) {

    public static final String DEFAULT_REALM = "ziipsan";

    public SupersetSsoConfig {
        Objects.requireNonNull(issuer, "issuer do Keycloak é obrigatório (parâmetro de ambiente)");
        if (realm == null || realm.isBlank()) {
            throw new IllegalArgumentException("Realm do Keycloak é obrigatório");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "clientId do Superset no Keycloak é obrigatório (criado na implantação; não é presumido)");
        }
        Objects.requireNonNull(redirectUri, "redirectUri do Superset é obrigatório");
        boolean loopback = "localhost".equals(issuer.getHost()) || "127.0.0.1".equals(issuer.getHost());
        if (!"https".equals(issuer.getScheme()) && !("http".equals(issuer.getScheme()) && loopback)) {
            throw new IllegalArgumentException("Emissor OIDC deve ser https (http só em loopback local)");
        }
    }

    /** URL de descoberta OIDC do realm ({@code <issuer>/realms/<realm>}). */
    public String realmIssuer() {
        String base = issuer.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/realms/" + realm;
    }

    public String discoveryUrl() {
        return realmIssuer() + "/.well-known/openid-configuration";
    }
}
