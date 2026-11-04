package br.com.ziipsan.platform.domain;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.domain.DnsResolvers.CnameResolver;
import br.com.ziipsan.platform.domain.DnsResolvers.TxtResolver;
import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;
import br.com.ziipsan.platform.domain.TenantDomainStore.ResolvedDomain;
import br.com.ziipsan.platform.domain.CertificateIssuer.IssuedCert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Domínio próprio do tenant (P2.27, PLT-05). Sem anotações Spring de
 * propósito, como o {@code TenantBrandService} da P1.19: o controller
 * constrói o serviço explicitamente com o store e as dependências do
 * ambiente.
 *
 * <p>Fluxo (um domínio por linha em {@code platform.tenant_domain}):
 * <ol>
 *   <li>{@link #requestDomain}: o admin pede um hostname. O serviço
 *       devolve o token em claro UMA vez, com o nome e o valor do TXT
 *       a publicar ({@code _ziipsan-verify.<host>} =
 *       {@code ziipsan-verify=<token>}) e o CNAME esperado (a borda).
 *       No banco fica só o SHA-256 do token.</li>
 *   <li>{@link #verifyDomain}: confere o TXT (prova de posse) e o CNAME
 *       (apontado para a borda). Com os dois ok, emite o certificado
 *       e marca VERIFIED + ISSUED, espelhando
 *       {@code brand.custom_domain}. TXT errado = 422
 *       {@code verification-failed}; CNAME errado = 422
 *       {@code cname-mismatch} (o domínio fica VERIFIED, sem
 *       certificado, e a verificação pode ser repetida).</li>
 *   <li>{@link #renewDomain}: dentro da janela (30 dias antes do
 *       vencimento, ou vencido) renova o certificado; fora dela, 409.
 *       Falha do emissor conta em {@code renewalFailures} e não perde
 *       o certificado vigente.</li>
 *   <li>{@link #revokeDomain}: revoga o certificado e o domínio (não
 *       resolve mais, não renova). {@link #releaseDomain} é a remoção
 *       do apontamento no encerramento do contrato (ou pelo admin):
 *       status REMOVED, certificado revogado, parâmetro limpo — e o
 *       hostname liberado, mas SÓ reativável com nova prova TXT.</li>
 * </ol>
 *
 * <p>Proteção contra tomada de subdomínio, em três camadas: (1) o
 * índice UNIQUE parcial da V10_2_1 recusa um segundo pedido ativo do
 * mesmo hostname (409 {@code data-conflict}); (2) apontar um CNAME
 * sozinho nunca ativa nada — sem o TXT com o token, o domínio fica
 * PENDING e não resolve; (3) depois de REMOVED, o nome só volta com
 * nova prova TXT. O que o serviço NÃO faz: derrubar o mapeamento na
 * borda — a borda (LC.2) deve observar o status REMOVED; aqui o
 * status é o sinal.
 *
 * <p>Códigos de erro: nenhum código novo de problem+json. Validações
 * usam {@code ApiException.Validation} (códigos de campo, como a
 * P1.19) e disputas de estado usam o {@code data-conflict} já
 * explicado no catálogo de erros — por isso nenhum YAML novo foi
 * criado em {@code content/help/errors/}.
 */
public final class TenantDomainService {

    private static final Pattern FQDN =
            Pattern.compile("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$");
    private static final Pattern IPV4 = Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$");
    private static final Set<String> REVOKE_REASONS =
            Set.of("CONTRACT_ENDED", "KEY_COMPROMISE", "ADMIN_REMOVED", "ADMIN");
    private static final Duration RENEWAL_WINDOW = Duration.ofDays(30);

    /** Dependências do ambiente (DNS, emissor, relógio, borda). */
    public record Dependencies(String edgeHost, TxtResolver txtResolver, CnameResolver cnameResolver,
                               CertificateIssuer issuer, Clock clock) {
    }

    /** Pedido criado: a visão + o que o admin precisa publicar no DNS (token em claro, uma vez). */
    public record CreateResult(DomainView domain, String token, String txtName, String txtValue,
                               String cnameTarget) {
    }

    private final TenantDomainStore store;
    private final Dependencies deps;
    private final SecureRandom random = new SecureRandom();

    public TenantDomainService(TenantDomainStore store, Dependencies deps) {
        if (store == null || deps == null || deps.txtResolver() == null
                || deps.cnameResolver() == null || deps.issuer() == null) {
            throw new IllegalArgumentException("store e dependências são obrigatórios");
        }
        // edgeHost pode ser null (borda ainda não configurada — caso da LC.2):
        // leituras funcionam; pedido e verificação falham com mensagem clara.
        String edge = deps.edgeHost() == null ? null : normalizeHost(deps.edgeHost())
                .orElseThrow(() -> new IllegalArgumentException("edgeHost inválido: " + deps.edgeHost()));
        this.store = store;
        this.deps = new Dependencies(edge, deps.txtResolver(), deps.cnameResolver(), deps.issuer(),
                deps.clock() == null ? Clock.systemUTC() : deps.clock());
    }

    /** Dependências locais: DNS por JNDI, emissor de certificado de teste, relógio do sistema. */
    public static Dependencies local(String edgeHost) {
        return new Dependencies(edgeHost, JndiDns.txtResolver(), JndiDns.cnameResolver(),
                new TestCertificateIssuer(Clock.systemUTC()), Clock.systemUTC());
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    /** Tenant do domínio próprio VERIFIED do host, ou vazio (host inválido, PENDING, REVOKED, REMOVED). */
    public Optional<ResolvedDomain> resolveByHost(String hostHeader) {
        return normalizeHost(hostHeader)
                .flatMap(store::resolveByHost)
                .filter(d -> "VERIFIED".equals(d.status()));
    }

    /** Domínios do tenant, do mais recente para o mais antigo. */
    public List<DomainView> listDomains(UUID tenantId) {
        requireTenant(tenantId);
        return store.listOwn(tenantId);
    }

    /** Domínio do próprio tenant, ou vazio. */
    public Optional<DomainView> findDomain(UUID tenantId, UUID domainId) {
        requireTenant(tenantId);
        if (domainId == null) {
            return Optional.empty();
        }
        return store.findOwn(tenantId, domainId);
    }

    // ------------------------------------------------------------------
    // Pedido
    // ------------------------------------------------------------------

    public CreateResult requestDomain(UUID tenantId, UUID actorId, String hostname) {
        requireTenant(tenantId);
        requireEdgeHost();
        String host = normalizeHost(hostname)
                .orElseThrow(() -> validation("hostname", "invalid-hostname",
                        "O domínio precisa ser um nome DNS válido (ex.: app.cliente.example)."));
        String token = generateToken();
        String tokenHash = sha256Hex(token);
        DomainView created = store.insert(tenantId, actorId, host, tokenHash, deps.edgeHost());
        return new CreateResult(created, token, "_ziipsan-verify." + host, "ziipsan-verify=" + token,
                deps.edgeHost());
    }

    // ------------------------------------------------------------------
    // Verificação (TXT + CNAME + certificado)
    // ------------------------------------------------------------------

    public DomainView verifyDomain(UUID tenantId, UUID actorId, UUID domainId) {
        DomainView domain = ownOrNotFound(tenantId, domainId);
        requireEdgeHost();
        boolean retryAfterCname = "VERIFIED".equals(domain.status())
                && ("NONE".equals(domain.certStatus()) || "PENDING".equals(domain.certStatus()));
        if (!"PENDING_VERIFICATION".equals(domain.status()) && !retryAfterCname) {
            throw new ApiException.Conflict("data-conflict",
                    "Só um domínio pendente (ou verificado sem certificado) pode ser verificado.");
        }
        String tokenHash = store.verificationTokenHash(tenantId, domainId)
                .orElseThrow(() -> new ApiException.NotFound("Domínio não encontrado."));
        if (!txtProvesOwnership(domain.hostname(), tokenHash)) {
            throw validation("hostname", "verification-failed",
                    "O registro TXT de verificação não foi encontrado. Publique o TXT indicado e tente de novo.");
        }
        Instant now = deps.clock().instant();
        Draft draft = new Draft(domain)
                .status("VERIFIED").verifiedAt(domain.verifiedAt() == null ? now : domain.verifiedAt())
                .lastCnameCheckAt(now);
        Optional<String> cname = deps.cnameResolver().cnameTarget(domain.hostname());
        if (cname.isEmpty() || !normalizeHost(cname.get()).map(deps.edgeHost()::equals).orElse(false)) {
            draft.cnameOk(false);
            store.update(tenantId, actorId, draft.toView(), "VERIFY");
            throw validation("hostname", "cname-mismatch",
                    "O domínio está verificado, mas o CNAME não aponta para a borda do Ziip ("
                            + deps.edgeHost() + "). Ajuste o CNAME e verifique de novo.");
        }
        draft.cnameOk(true);
        IssuedCert cert;
        try {
            cert = deps.issuer().issue(domain.hostname());
        } catch (RuntimeException e) {
            draft.certStatus("PENDING");
            store.update(tenantId, actorId, draft.toView(), "VERIFY");
            throw new IllegalStateException("Falha ao emitir o certificado do domínio.", e);
        }
        draft.certStatus("ISSUED").certProvider(cert.provider())
                .certIssuedAt(cert.issuedAt()).certNotAfter(cert.notAfter())
                .certRenewAfter(TestCertificateIssuer.renewAfter(cert.notAfter()));
        DomainView saved = store.update(tenantId, actorId, draft.toView(), "VERIFY");
        store.syncBrandParameter(tenantId, actorId, domain.hostname());
        return saved;
    }

    // ------------------------------------------------------------------
    // Renovação
    // ------------------------------------------------------------------

    public DomainView renewDomain(UUID tenantId, UUID actorId, UUID domainId) {
        DomainView domain = ownOrNotFound(tenantId, domainId);
        if (!"VERIFIED".equals(domain.status())) {
            throw new ApiException.Conflict("data-conflict",
                    "Só um domínio verificado pode ter o certificado renovado.");
        }
        if (!"ISSUED".equals(domain.certStatus()) || domain.certNotAfter() == null) {
            throw new ApiException.Conflict("data-conflict",
                    "Não há certificado vigente para renovar neste domínio.");
        }
        Instant now = deps.clock().instant();
        Instant renewAfter = domain.certRenewAfter() != null
                ? domain.certRenewAfter() : TestCertificateIssuer.renewAfter(domain.certNotAfter());
        if (now.isBefore(renewAfter) && now.isBefore(domain.certNotAfter())) {
            throw new ApiException.Conflict("data-conflict",
                    "O certificado ainda não está na janela de renovação (30 dias antes do vencimento).");
        }
        IssuedCert cert;
        try {
            cert = deps.issuer().renew(domain);
        } catch (RuntimeException e) {
            Draft failed = new Draft(domain).renewalFailures(domain.renewalFailures() + 1);
            store.update(tenantId, actorId, failed.toView(), "RENEW_FAILED");
            throw new IllegalStateException("Falha ao renovar o certificado do domínio.", e);
        }
        Draft draft = new Draft(domain)
                .certStatus("ISSUED").certProvider(cert.provider())
                .certIssuedAt(cert.issuedAt()).certNotAfter(cert.notAfter())
                .certRenewAfter(TestCertificateIssuer.renewAfter(cert.notAfter()))
                .lastRenewalAt(now).renewalFailures(0);
        return store.update(tenantId, actorId, draft.toView(), "RENEW");
    }

    // ------------------------------------------------------------------
    // Revogação e remoção do apontamento
    // ------------------------------------------------------------------

    public DomainView revokeDomain(UUID tenantId, UUID actorId, UUID domainId, String reason) {
        DomainView domain = ownOrNotFound(tenantId, domainId);
        if (!"VERIFIED".equals(domain.status())) {
            throw new ApiException.Conflict("data-conflict",
                    "Só um domínio verificado pode ser revogado.");
        }
        if (reason == null || !REVOKE_REASONS.contains(reason)) {
            throw validation("reason", "invalid-reason",
                    "O motivo da revogação precisa ser CONTRACT_ENDED, KEY_COMPROMISE, ADMIN_REMOVED ou ADMIN.");
        }
        if ("ISSUED".equals(domain.certStatus())) {
            try {
                deps.issuer().revoke(domain);
            } catch (RuntimeException ignored) {
                // Best-effort: a revogação local (status) não depende do emissor;
                // a borda (LC.2) observa o status REVOKED e derruba o certificado.
            }
        }
        Instant now = deps.clock().instant();
        Draft draft = new Draft(domain)
                .status("REVOKED").certStatus("REVOKED")
                .revokedAt(now).revokedReason(reason);
        DomainView saved = store.update(tenantId, actorId, draft.toView(), "REVOKE");
        clearBrandParameterIfEquals(tenantId, actorId, domain.hostname());
        return saved;
    }

    /**
     * Remoção do apontamento (encerramento do contrato ou remoção pelo
     * admin): o domínio deixa de resolver, o certificado é revogado e o
     * hostname fica livre — mas só volta com nova prova TXT. O chamador
     * do ciclo de vida (P2.12–P2.14) deve chamar este método no
     * encerramento; a borda (LC.2) observa o REMOVED e derruba o
     * mapeamento.
     */
    public DomainView releaseDomain(UUID tenantId, UUID actorId, UUID domainId, String reason) {
        DomainView domain = ownOrNotFound(tenantId, domainId);
        if ("REMOVED".equals(domain.status())) {
            throw new ApiException.Conflict("data-conflict", "O domínio já foi removido.");
        }
        if (reason == null || !REVOKE_REASONS.contains(reason)) {
            throw validation("reason", "invalid-reason",
                    "O motivo da remoção precisa ser CONTRACT_ENDED, KEY_COMPROMISE, ADMIN_REMOVED ou ADMIN.");
        }
        if ("ISSUED".equals(domain.certStatus())) {
            try {
                deps.issuer().revoke(domain);
            } catch (RuntimeException ignored) {
                // Best-effort, como em revokeDomain.
            }
        }
        Instant now = deps.clock().instant();
        Draft draft = new Draft(domain)
                .status("REMOVED").removedAt(now);
        if (domain.revokedAt() == null) {
            draft.revokedAt(now).revokedReason(reason);
        }
        if ("ISSUED".equals(domain.certStatus()) || "PENDING".equals(domain.certStatus())
                || "RENEWING".equals(domain.certStatus())) {
            draft.certStatus("REVOKED");
        }
        DomainView saved = store.update(tenantId, actorId, draft.toView(), "RELEASE");
        clearBrandParameterIfEquals(tenantId, actorId, domain.hostname());
        return saved;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void requireEdgeHost() {
        if (deps.edgeHost() == null) {
            throw new IllegalStateException(
                    "A borda do Ziip (ziipsan.domains.edge-host) não está configurada; o domínio próprio só pode ser pedido depois que a LC.2 definir a borda.");
        }
    }

    private void clearBrandParameterIfEquals(UUID tenantId, UUID actorId, String hostname) {
        if (store.brandCustomDomain(tenantId).map(hostname::equals).orElse(false)) {
            store.syncBrandParameter(tenantId, actorId, null);
        }
    }

    private DomainView ownOrNotFound(UUID tenantId, UUID domainId) {
        requireTenant(tenantId);
        if (domainId == null) {
            throw new ApiException.NotFound("Domínio não encontrado.");
        }
        return store.findOwn(tenantId, domainId)
                .orElseThrow(() -> new ApiException.NotFound("Domínio não encontrado."));
    }

    /** Prova TXT: algum registro `_ziipsan-verify.<host>` com o token cujo SHA-256 é o guardado. */
    private boolean txtProvesOwnership(String hostname, String tokenHash) {
        List<String> records = deps.txtResolver().txtRecords("_ziipsan-verify." + hostname);
        for (String record : records) {
            String value = JndiDns.unquote(record.strip());
            if (value.startsWith("ziipsan-verify=")) {
                String candidate = value.substring("ziipsan-verify=".length()).strip();
                if (!candidate.isEmpty()
                        && MessageDigest.isEqual(sha256Hex(candidate).getBytes(StandardCharsets.UTF_8),
                                tokenHash.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Normaliza um host: minúsculas, sem porta, sem ponto final da raiz.
     * Vazio quando não é um FQDN válido (IP, localhost, rótulo único,
     * curinga, rótulo inválido). A mesma normalização vive no SQL da
     * V10_2_1 (a função re-normaliza sem prejuízo).
     */
    public static Optional<String> normalizeHost(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String host = raw.strip().toLowerCase();
        if (host.startsWith("[")) {
            return Optional.empty();
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isBlank() || host.equals("localhost") || IPV4.matcher(host).matches()
                || host.contains("*") || host.length() > 253 || !FQDN.matcher(host).matches()) {
            return Optional.empty();
        }
        return Optional.of(host);
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta operação exige um token de tenant.");
        }
    }

    private static ApiException.Validation validation(String field, String code, String message) {
        return ApiException.Validation.of(new ApiException.FieldError(field, code, message));
    }

    /** Rascunho mutável da visão, para as transições não repetirem os 20 campos. */
    private static final class Draft {
        private final DomainView base;
        private String status;
        private boolean cnameOk;
        private Instant lastCnameCheckAt;
        private String certStatus;
        private String certProvider;
        private Instant certIssuedAt;
        private Instant certNotAfter;
        private Instant certRenewAfter;
        private Instant lastRenewalAt;
        private int renewalFailures;
        private Instant verifiedAt;
        private Instant revokedAt;
        private String revokedReason;
        private Instant removedAt;

        Draft(DomainView base) {
            this.base = base;
            this.status = base.status();
            this.cnameOk = base.cnameOk();
            this.lastCnameCheckAt = base.lastCnameCheckAt();
            this.certStatus = base.certStatus();
            this.certProvider = base.certProvider();
            this.certIssuedAt = base.certIssuedAt();
            this.certNotAfter = base.certNotAfter();
            this.certRenewAfter = base.certRenewAfter();
            this.lastRenewalAt = base.lastRenewalAt();
            this.renewalFailures = base.renewalFailures();
            this.verifiedAt = base.verifiedAt();
            this.revokedAt = base.revokedAt();
            this.revokedReason = base.revokedReason();
            this.removedAt = base.removedAt();
        }

        Draft status(String v) { status = v; return this; }
        Draft cnameOk(boolean v) { cnameOk = v; return this; }
        Draft lastCnameCheckAt(Instant v) { lastCnameCheckAt = v; return this; }
        Draft certStatus(String v) { certStatus = v; return this; }
        Draft certProvider(String v) { certProvider = v; return this; }
        Draft certIssuedAt(Instant v) { certIssuedAt = v; return this; }
        Draft certNotAfter(Instant v) { certNotAfter = v; return this; }
        Draft certRenewAfter(Instant v) { certRenewAfter = v; return this; }
        Draft lastRenewalAt(Instant v) { lastRenewalAt = v; return this; }
        Draft renewalFailures(int v) { renewalFailures = v; return this; }
        Draft verifiedAt(Instant v) { verifiedAt = v; return this; }
        Draft revokedAt(Instant v) { revokedAt = v; return this; }
        Draft revokedReason(String v) { revokedReason = v; return this; }
        Draft removedAt(Instant v) { removedAt = v; return this; }

        DomainView toView() {
            return new DomainView(base.id(), base.tenantId(), base.hostname(), status,
                    base.cnameTarget(), cnameOk, lastCnameCheckAt, certStatus, certProvider,
                    certIssuedAt, certNotAfter, certRenewAfter, lastRenewalAt, renewalFailures,
                    verifiedAt, revokedAt, revokedReason, removedAt, base.createdAt(), base.updatedAt());
        }
    }
}
