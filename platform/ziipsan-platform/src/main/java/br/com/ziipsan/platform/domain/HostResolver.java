package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.brand.SlugResolver;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolução do tenant pelo host antes do login (P2.27). Ordem, que é
 * o ponto da tarefa: o domínio próprio VERIFIED vence o slug — sem
 * isso, um host como {@code portal.cliente.example} cairia no slug
 * {@code portal} de outro tenant. Só então tenta o slug (P1.19) e,
 * por fim, nenhum tenant (o chamador responde a marca do produto com
 * o mesmo status, sem revelar existência).
 *
 * <p>Puro (sem banco, sem Spring): recebe as duas buscas como funções,
 * para que o controller injete o store e os testes injetem mapas.
 */
public final class HostResolver {

    /** Origem da resolução. */
    public enum Kind {
        /** Host é um domínio próprio VERIFIED do tenant. */
        CUSTOM_DOMAIN,
        /** Host é um subdomínio na base do produto (slug da P1.19). */
        SUBDOMAIN,
        /** Nenhum tenant resolve por este host. */
        NONE
    }

    /** Resultado: a origem, o tenant e o identificador que casou (hostname ou slug). */
    public record Resolution(Kind kind, UUID tenantId, String identifier) {
        public static Resolution none() {
            return new Resolution(Kind.NONE, null, null);
        }
    }

    private HostResolver() {
    }

    /**
     * Resolve o host: domínio próprio primeiro, slug depois.
     *
     * @param hostHeader cabeçalho Host (com ou sem porta, qualquer caixa)
     * @param byCustomDomain busca por hostname normalizado (domínio próprio VERIFIED)
     * @param bySlug busca por slug (subdomínio da P1.19)
     */
    public static Resolution resolve(String hostHeader,
                                     Function<String, Optional<UUID>> byCustomDomain,
                                     Function<String, Optional<UUID>> bySlug) {
        Optional<String> host = TenantDomainService.normalizeHost(hostHeader);
        if (host.isEmpty()) {
            return Resolution.none();
        }
        Optional<UUID> custom = byCustomDomain.apply(host.get());
        if (custom.isPresent()) {
            return new Resolution(Kind.CUSTOM_DOMAIN, custom.get(), host.get());
        }
        Optional<String> slug = SlugResolver.extractSlug(hostHeader);
        if (slug.isPresent()) {
            Optional<UUID> tenant = bySlug.apply(slug.get());
            if (tenant.isPresent()) {
                return new Resolution(Kind.SUBDOMAIN, tenant.get(), slug.get());
            }
        }
        return Resolution.none();
    }
}
