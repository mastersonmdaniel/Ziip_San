package br.com.ziipsan.platform.brand;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolução do slug do tenant a partir do host da requisição (P1.19,
 * MARCA-E-TEMAS §3.3: o BFF resolve o tenant pelo host e mostra a marca
 * antes do login).
 *
 * <p>Regra, sem base de subdomínio configurada (o pacote de marca ainda não
 * traz {@code domains.tenantSubdomainBase} — nenhum domínio real foi
 * inventado aqui): o primeiro rótulo do host é o slug candidato. O
 * fallback protege a regra: slug desconhecido devolve a marca do produto
 * com o mesmo status, então um host sem subdomínio (o ápice) apenas não
 * casa com nenhum tenant. Rótulos reservados nunca são slugs:
 * {@code app} (mostra só a marca do produto, §3.3) e {@code www}.
 * Localhost, IP e host de um só rótulo não têm slug.
 *
 * <p>O controller lê o cabeçalho {@code Host} (não {@code getServerName}),
 * para que o proxy só precise encaminhá-lo e os testes possam fixá-lo.
 * Quando a base de subdomínio for configurada (P2.27, domínio próprio),
 * esta classe passa a recortar a base antes de extrair o rótulo — o
 * contrato (primeiro rótulo restante) não muda.
 */
public final class SlugResolver {

    private static final Set<String> RESERVED = Set.of("app", "www");
    private static final Pattern IPV4 = Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$");
    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    private SlugResolver() {
    }

    /**
     * Slug candidato do host, ou vazio quando o host não carrega slug
     * (localhost, IP, rótulo único, rótulo reservado ou formato inválido).
     * A porta é descartada; o host é normalizado para minúsculas e sem o
     * ponto final da raiz.
     */
    public static Optional<String> extractSlug(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return Optional.empty();
        }
        String host = hostHeader.strip().toLowerCase();
        // IPv6 literal ([::1] ou [::1]:porta): nunca tem slug.
        if (host.startsWith("[")) {
            return Optional.empty();
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isBlank() || IPV4.matcher(host).matches() || host.equals("localhost")) {
            return Optional.empty();
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return Optional.empty();
        }
        String slug = labels[0];
        if (RESERVED.contains(slug) || !LABEL.matcher(slug).matches()) {
            return Optional.empty();
        }
        return Optional.of(slug);
    }
}
