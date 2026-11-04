package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.domain.HostResolver.Resolution;

import java.util.Optional;
import java.util.Set;

/**
 * Mapeamento por host das superfícies de segurança do login (P2.27).
 *
 * <p>O que cada item significa, sem inventar comportamento do
 * navegador ou do IdP:
 * <ul>
 *   <li>{@code Origin}: só o próprio host resolvido (https) é origem
 *       confiável do tenant — um host não resolvido nunca vira origem
 *       permitida, e http nunca é confiável para login (o cookie
 *       {@code __Host-} exige Secure).</li>
 *   <li>{@code redirect_uri} do OIDC: construído a partir do host da
 *       requisição resolvida, nunca de uma URL fixa do produto — sem
 *       isso, o retorno do IdP para um domínio próprio cairia no
 *       domínio do produto. O caminho precisa ser absoluto e sem
 *       {@code //} inicial (proteção contra open redirect).</li>
 *   <li>Cookie {@code __Host-}: por definição do prefixo, NÃO leva
 *       atributo {@code Domain} — o mapeamento por host é exatamente
 *       este: o cookie emitido no host resolvido só vale para aquele
 *       host exato, sem compartilhamento com subdomínios ou com o
 *       domínio do produto. {@link #sessionCookieHost} devolve o host
 *       ao qual o cookie fica preso (ou vazio, quando o host não
 *       resolve e nenhuma sessão deve ser emitida ali).</li>
 *   <li>CSP de documento: o frontend/BFF deve usar o host resolvido
 *       como origem própria ({@code 'self'} + o host) em
 *       {@code frame-ancestors} e {@code form-action}; esta classe
 *       devolve o conjunto de origens confiáveis para o host, e a
 *       emissão do cabeçalho continua no BFF (a API mantém o CSP
 *       estrito {@code default-src 'none'} da P0.16 — respostas da API
 *       não são documentos).</li>
 * </ul>
 */
public final class HostSecurityPolicy {

    private HostSecurityPolicy() {
    }

    /** Origem https do host resolvido, ou vazio quando o host não resolve. */
    public static Optional<String> trustedOrigin(String hostHeader, Resolution resolution) {
        Optional<String> host = TenantDomainService.normalizeHost(hostHeader);
        if (host.isEmpty() || resolution == null || resolution.kind() == HostResolver.Kind.NONE
                || resolution.tenantId() == null) {
            return Optional.empty();
        }
        return Optional.of("https://" + host.get());
    }

    /** Origens confiáveis para o host (para Origin e CSP de documento): só o próprio host. */
    public static Set<String> trustedOrigins(String hostHeader, Resolution resolution) {
        return trustedOrigin(hostHeader, resolution).map(Set::of).orElseGet(Set::of);
    }

    /**
     * {@code redirect_uri} do OIDC no host resolvido, ou vazio quando o
     * host não resolve ou o caminho é inválido (não absoluto, ou com
     * {@code //} inicial).
     */
    public static Optional<String> loginRedirectUri(String hostHeader, Resolution resolution, String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            return Optional.empty();
        }
        return trustedOrigin(hostHeader, resolution).map(origin -> origin + path);
    }

    /**
     * Host ao qual o cookie de sessão {@code __Host-} fica preso: o
     * próprio host resolvido, sem atributo Domain. Vazio = nenhuma
     * sessão deve ser emitida neste host.
     */
    public static Optional<String> sessionCookieHost(String hostHeader, Resolution resolution) {
        if (resolution == null || resolution.kind() == HostResolver.Kind.NONE
                || resolution.tenantId() == null) {
            return Optional.empty();
        }
        return TenantDomainService.normalizeHost(hostHeader);
    }
}
