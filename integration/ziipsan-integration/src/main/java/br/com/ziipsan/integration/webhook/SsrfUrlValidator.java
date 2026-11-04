package br.com.ziipsan.integration.webhook;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Proteção contra SSRF das entregas de webhook (P2.26).
 *
 * <p>Regras, nesta ordem: só HTTPS; sem userinfo; host obrigatório; IP
 * literal precisa ser público; nome precisa resolver (no envio) só para
 * IPs públicos — endereço privado, loopback, link-local, multicast ou
 * reservado é recusado. Redirecionamento nunca é seguido: o remetente
 * usa um cliente sem follow-redirect e trata 3xx como falha da tentativa,
 * nunca como novo destino (o Location de um 3xx não é validado porque
 * nunca é buscado).
 *
 * <p>A resolução de DNS é feita no envio, imediatamente antes da
 * requisição, e o resultado é o que o teste cobre (nomes que resolvem
 * para privado são recusados). Limitação declarada: entre a validação e
 * o connect, um DNS malicioso poderia trocar a resposta (TOCTOU); a
 * fixação do IP resolvido na conexão TLS (pinning com SNI) é um controle
 * de borda a ser acrescentado na P3.37, junto do hub — o validador aqui
 * é a barreira da aplicação, não a única barreira.
 */
public final class SsrfUrlValidator {

    /** Resolução de DNS injetável (o teste usa um resolvedor fixo). */
    public interface DnsResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    public static final DnsResolver SYSTEM = host -> List.of(InetAddress.getAllByName(host));

    private SsrfUrlValidator() {
    }

    /** Valida a URL de um endpoint. Lança {@link IllegalArgumentException} com o motivo. */
    public static URI validate(String url, DnsResolver resolver) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A URL do endpoint é obrigatória.");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("A URL do endpoint é inválida.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("O endpoint precisa usar HTTPS.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("A URL do endpoint não pode ter credencial embutida.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("A URL do endpoint precisa de um host.");
        }
        String asciiHost = IDN.toASCII(host);
        List<InetAddress> addresses;
        try {
            if (isIpLiteral(asciiHost)) {
                addresses = List.of(InetAddress.getByName(asciiHost));
            } else {
                addresses = resolver.resolve(asciiHost);
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("O host do endpoint não resolve.");
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("O host do endpoint não resolve.");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IllegalArgumentException(
                        "O host do endpoint resolve para um endereço privado ou reservado.");
            }
        }
        return uri;
    }

    /** Verdadeiro se o endereço pode ser destino de uma entrega (público, roteável). */
    public static boolean isPublic(InetAddress address) {
        return !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isMulticastAddress()
                && !address.isSiteLocalAddress()
                && !address.isAnyLocalAddress();
    }

    private static boolean isIpLiteral(String host) {
        // IPv4 literal ou [IPv6] (o URI já remove os colchetes em getHost()).
        return host.chars().allMatch(c -> Character.isDigit(c) || c == '.')
                || host.contains(":");
    }
}
