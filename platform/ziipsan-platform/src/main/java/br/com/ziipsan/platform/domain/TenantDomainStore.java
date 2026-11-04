package br.com.ziipsan.platform.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do domínio próprio do tenant (P2.27). Mesma separação do
 * pipeline de marca da P1.19 ({@code TenantBrandStore} /
 * {@code JdbcTenantBrandStore}): o serviço orquestra validação, DNS e
 * certificado; o store só fala com o banco. Assim o serviço é testável
 * sem PostgreSQL, com um falso em memória.
 *
 * <p>Leituras pré-login ({@link #resolveByHost}) não usam contexto de
 * tenant: um único SELECT na função SECURITY DEFINER da V10_2_1, em
 * autocommit — a função só devolve tenant_id/hostname/status, nunca o
 * hash do token nem dados do certificado. As demais operações rodam
 * numa transação própria com {@code app.current_tenant} local
 * (precedente do {@code TenantBrandStore}), porque
 * {@code platform.tenant_domain} tem RLS ativada.
 *
 * <p>O hash do token de verificação NUNCA sai do store como parte da
 * visão: ele só volta por {@link #verificationTokenHash}, para o
 * serviço comparar com o hash do valor lido no DNS.
 */
public interface TenantDomainStore {

    /** Domínio como o admin o vê (sem hash do token, sem segredo). */
    record DomainView(UUID id, UUID tenantId, String hostname, String status,
                      String cnameTarget, boolean cnameOk, Instant lastCnameCheckAt,
                      String certStatus, String certProvider,
                      Instant certIssuedAt, Instant certNotAfter, Instant certRenewAfter,
                      Instant lastRenewalAt, int renewalFailures,
                      Instant verifiedAt, Instant revokedAt, String revokedReason,
                      Instant removedAt, Instant createdAt, Instant updatedAt) {
    }

    /** Resolução pré-login: só tenant, hostname e status. */
    record ResolvedDomain(UUID tenantId, String hostname, String status) {
    }

    /** Tenant do domínio próprio VERIFIED do host, ou vazio. */
    Optional<ResolvedDomain> resolveByHost(String hostname);

    /** Domínio do próprio tenant, ou vazio (inclusive quando é de outro tenant). */
    Optional<DomainView> findOwn(UUID tenantId, UUID domainId);

    /** Domínios do tenant, do mais recente para o mais antigo. */
    List<DomainView> listOwn(UUID tenantId);

    /** Hash SHA-256 hex do token de verificação do domínio do próprio tenant. */
    Optional<String> verificationTokenHash(UUID tenantId, UUID domainId);

    /**
     * Insere o pedido PENDING_VERIFICATION. A unicidade parcial do
     * hostname da V10_2_1 vira 409 {@code data-conflict} (código já
     * explicado no catálogo de erros): outro tenant (ou o mesmo, com
     * outro pedido ativo) já reivindica este hostname.
     */
    DomainView insert(UUID tenantId, UUID actorId, String hostname, String tokenHash, String cnameTarget);

    /**
     * Persiste a visão inteira (o serviço calcula o novo estado) e
     * audita a transição (só hostname, estados e motivo — nunca o hash
     * do token).
     */
    DomainView update(UUID tenantId, UUID actorId, DomainView view, String auditAction);

    /**
     * Espelha o domínio próprio VERIFIED no parâmetro
     * {@code brand.custom_domain} (classe d): hostname como string JSON
     * quando há domínio verificado, ou apaga a chave quando
     * {@code hostname} é null. Não apaga quando o valor corrente é de
     * outro domínio (o apagamento é condicional, feito pelo chamador
     * via {@link #brandCustomDomain}).
     */
    void syncBrandParameter(UUID tenantId, UUID actorId, String hostname);

    /** Valor corrente do parâmetro {@code brand.custom_domain}, ou vazio. */
    Optional<String> brandCustomDomain(UUID tenantId);
}
