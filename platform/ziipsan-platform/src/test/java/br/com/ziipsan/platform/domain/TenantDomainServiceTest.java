package br.com.ziipsan.platform.domain;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.domain.CertificateIssuer.IssuedCert;
import br.com.ziipsan.platform.domain.DnsResolvers.CnameResolver;
import br.com.ziipsan.platform.domain.DnsResolvers.TxtResolver;
import br.com.ziipsan.platform.domain.TenantDomainService.CreateResult;
import br.com.ziipsan.platform.domain.TenantDomainService.Dependencies;
import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;
import br.com.ziipsan.platform.domain.TenantDomainStore.ResolvedDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.27 — serviço do domínio próprio com store falso em memória e
 * DNS/emissor falsos (sem PostgreSQL, sem DNS real).
 *
 * <p>O falso reproduz a semântica da V10_2_1/JDBC que importa para o
 * serviço: unicidade parcial do hostname (só linhas não REMOVED
 * disputam o nome), resolução pré-login só de VERIFIED, e parâmetro
 * {@code brand.custom_domain} espelhado. É esse o contrato que a IT
 * de migração prova no banco de verdade.
 */
class TenantDomainServiceTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final String EDGE = "edge.ziip.example";
    private static final String HOST = "app.cliente.example";

    // ------------------------------------------------------------------
    // Falsos
    // ------------------------------------------------------------------

    private static final class FakeStore implements TenantDomainStore {
        final Map<UUID, DomainView> rows = new LinkedHashMap<>();
        final Map<UUID, String> tokenHashes = new HashMap<>();
        final Map<UUID, String> brandParams = new HashMap<>();

        @Override
        public Optional<ResolvedDomain> resolveByHost(String hostname) {
            return rows.values().stream()
                    .filter(v -> v.hostname().equals(hostname) && "VERIFIED".equals(v.status()))
                    .findFirst()
                    .map(v -> new ResolvedDomain(v.tenantId(), v.hostname(), v.status()));
        }

        @Override
        public Optional<DomainView> findOwn(UUID tenantId, UUID domainId) {
            DomainView v = rows.get(domainId);
            return v != null && v.tenantId().equals(tenantId) ? Optional.of(v) : Optional.empty();
        }

        @Override
        public List<DomainView> listOwn(UUID tenantId) {
            List<DomainView> out = new ArrayList<>();
            rows.values().stream().filter(v -> v.tenantId().equals(tenantId))
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).forEach(out::add);
            return out;
        }

        @Override
        public Optional<String> verificationTokenHash(UUID tenantId, UUID domainId) {
            return findOwn(tenantId, domainId).flatMap(v -> Optional.ofNullable(tokenHashes.get(domainId)));
        }

        @Override
        public DomainView insert(UUID tenantId, UUID actorId, String hostname, String tokenHash,
                                 String cnameTarget) {
            boolean claimed = rows.values().stream()
                    .anyMatch(v -> v.hostname().equals(hostname) && !"REMOVED".equals(v.status()));
            if (claimed) {
                throw new ApiException.Conflict("data-conflict", "O domínio já está em uso por outro tenant.");
            }
            Instant now = Instant.now();
            DomainView v = new DomainView(UUID.randomUUID(), tenantId, hostname, "PENDING_VERIFICATION",
                    cnameTarget, false, null, "NONE", null, null, null, null, null, 0,
                    null, null, null, null, now, now);
            rows.put(v.id(), v);
            tokenHashes.put(v.id(), tokenHash);
            return v;
        }

        @Override
        public DomainView update(UUID tenantId, UUID actorId, DomainView view, String auditAction) {
            DomainView current = rows.get(view.id());
            if (current == null || !current.tenantId().equals(tenantId)) {
                throw new ApiException.NotFound("Domínio não encontrado.");
            }
            rows.put(view.id(), view);
            return view;
        }

        @Override
        public void syncBrandParameter(UUID tenantId, UUID actorId, String hostname) {
            if (hostname == null) {
                brandParams.remove(tenantId);
            } else {
                brandParams.put(tenantId, hostname);
            }
        }

        @Override
        public Optional<String> brandCustomDomain(UUID tenantId) {
            return Optional.ofNullable(brandParams.get(tenantId));
        }
    }

    private static final class FakeDns implements TxtResolver, CnameResolver {
        final Map<String, List<String>> txt = new HashMap<>();
        final Map<String, String> cname = new HashMap<>();

        @Override
        public List<String> txtRecords(String name) {
            return txt.getOrDefault(name, List.of());
        }

        @Override
        public Optional<String> cnameTarget(String hostname) {
            return Optional.ofNullable(cname.get(hostname));
        }
    }

    private static final class FailingIssuer implements CertificateIssuer {
        @Override
        public IssuedCert issue(String hostname) {
            throw new IllegalStateException("emissor fora do ar");
        }

        @Override
        public IssuedCert renew(DomainView domain) {
            throw new IllegalStateException("emissor fora do ar");
        }

        @Override
        public void revoke(DomainView domain) {
        }
    }

    private record Fixture(FakeStore store, FakeDns dns, TenantDomainService service) {
    }

    private Fixture fixture() {
        return fixture(new TestCertificateIssuer(Clock.systemUTC()));
    }

    private Fixture fixture(CertificateIssuer issuer) {
        FakeStore store = new FakeStore();
        FakeDns dns = new FakeDns();
        TenantDomainService service = new TenantDomainService(store,
                new Dependencies(EDGE, dns, dns, issuer, Clock.systemUTC()));
        return new Fixture(store, dns, service);
    }

    /** Domínio VERIFIED + ISSUED, com TXT e CNAME certos no DNS falso. */
    private DomainView verified(Fixture f, UUID tenant, String host) {
        CreateResult created = f.service().requestDomain(tenant, ACTOR, host);
        f.dns().txt.put("_ziipsan-verify." + host, List.of("ziipsan-verify=" + created.token()));
        f.dns().cname.put(host, EDGE);
        return f.service().verifyDomain(tenant, ACTOR, created.domain().id());
    }

    // ------------------------------------------------------------------
    // Pedido
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pedido normaliza o host e devolve o TXT e o CNAME a publicar, sem expor o hash")
    void requestNormalizesAndReturnsDnsInstructions() {
        Fixture f = fixture();
        CreateResult result = f.service().requestDomain(TENANT_A, ACTOR, "APP.Cliente.EXAMPLE:8443.");
        assertThat(result.domain().hostname()).isEqualTo(HOST);
        assertThat(result.domain().status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(result.domain().certStatus()).isEqualTo("NONE");
        assertThat(result.txtName()).isEqualTo("_ziipsan-verify." + HOST);
        assertThat(result.txtValue()).isEqualTo("ziipsan-verify=" + result.token());
        assertThat(result.cnameTarget()).isEqualTo(EDGE);
        assertThat(result.token()).isNotBlank();
        // O hash guardado é o SHA-256 do token devolvido — e o token não está na visão.
        assertThat(f.store().verificationTokenHash(TENANT_A, result.domain().id()))
                .contains(TenantDomainService.sha256Hex(result.token()));
    }

    @Test
    @DisplayName("host inválido (IP, rótulo único, localhost, curinga) é recusado")
    void invalidHostnamesAreRejected() {
        Fixture f = fixture();
        for (String bad : List.of("127.0.0.1", "localhost", "ziipsan", "*.cliente.example",
                "-bad.cliente.example", "cliente", "")) {
            assertThatThrownBy(() -> f.service().requestDomain(TENANT_A, ACTOR, bad))
                    .as("host %s", bad)
                    .isInstanceOf(ApiException.Validation.class);
        }
    }

    @Test
    @DisplayName("sem tenant e sem borda configurada, o pedido falha com mensagem clara")
    void missingTenantAndEdgeFailClearly() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.service().requestDomain(null, ACTOR, HOST))
                .isInstanceOf(ApiException.Forbidden.class);
        TenantDomainService noEdge = new TenantDomainService(new FakeStore(),
                new Dependencies(null, f.dns(), f.dns(), new TestCertificateIssuer(Clock.systemUTC()),
                        Clock.systemUTC()));
        assertThatThrownBy(() -> noEdge.requestDomain(TENANT_A, ACTOR, HOST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borda");
    }

    // ------------------------------------------------------------------
    // Verificação
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TXT + CNAME ok: VERIFIED + ISSUED, certificado de teste e parâmetro espelhado")
    void verifyHappyPath() {
        Fixture f = fixture();
        DomainView verified = verified(f, TENANT_A, HOST);
        assertThat(verified.status()).isEqualTo("VERIFIED");
        assertThat(verified.certStatus()).isEqualTo("ISSUED");
        assertThat(verified.certProvider()).isEqualTo("TEST");
        assertThat(verified.cnameOk()).isTrue();
        assertThat(verified.verifiedAt()).isNotNull();
        assertThat(verified.certNotAfter()).isAfter(verified.certIssuedAt());
        assertThat(verified.certRenewAfter()).isEqualTo(verified.certNotAfter().minus(Duration.ofDays(30)));
        assertThat(f.store().brandCustomDomain(TENANT_A)).contains(HOST);
        assertThat(f.service().resolveByHost(HOST)).isPresent();
    }

    @Test
    @DisplayName("sem TXT, a verificação falha e o domínio continua PENDING (CNAME sozinho não ativa)")
    void verifyWithoutTxtFailsAndStaysPending() {
        Fixture f = fixture();
        CreateResult created = f.service().requestDomain(TENANT_A, ACTOR, HOST);
        f.dns().cname.put(HOST, EDGE); // CNAME certo, TXT ausente: não basta.
        assertThatThrownBy(() -> f.service().verifyDomain(TENANT_A, ACTOR, created.domain().id()))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(((ApiException.Validation) e).errors().get(0).code())
                        .isEqualTo("verification-failed"));
        assertThat(f.store().findOwn(TENANT_A, created.domain().id()).orElseThrow().status())
                .isEqualTo("PENDING_VERIFICATION");
        assertThat(f.service().resolveByHost(HOST)).isEmpty();
    }

    @Test
    @DisplayName("TXT ok mas CNAME errado: VERIFIED sem certificado; corrigido o CNAME, a nova verificação emite")
    void cnameMismatchThenRetry() {
        Fixture f = fixture();
        CreateResult created = f.service().requestDomain(TENANT_A, ACTOR, HOST);
        f.dns().txt.put("_ziipsan-verify." + HOST, List.of("ziipsan-verify=" + created.token()));
        f.dns().cname.put(HOST, "outra-borda.example");
        assertThatThrownBy(() -> f.service().verifyDomain(TENANT_A, ACTOR, created.domain().id()))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(((ApiException.Validation) e).errors().get(0).code())
                        .isEqualTo("cname-mismatch"));
        DomainView after = f.store().findOwn(TENANT_A, created.domain().id()).orElseThrow();
        assertThat(after.status()).isEqualTo("VERIFIED");
        assertThat(after.certStatus()).isEqualTo("NONE");
        assertThat(after.cnameOk()).isFalse();
        f.dns().cname.put(HOST, EDGE);
        DomainView verified = f.service().verifyDomain(TENANT_A, ACTOR, created.domain().id());
        assertThat(verified.certStatus()).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("TXT com aspas do DNS e token errado não provam posse")
    void txtQuotingAndWrongToken() {
        Fixture f = fixture();
        CreateResult created = f.service().requestDomain(TENANT_A, ACTOR, HOST);
        f.dns().cname.put(HOST, EDGE);
        f.dns().txt.put("_ziipsan-verify." + HOST, List.of("\"ziipsan-verify=token-errado\""));
        assertThatThrownBy(() -> f.service().verifyDomain(TENANT_A, ACTOR, created.domain().id()))
                .isInstanceOf(ApiException.Validation.class);
        // Com aspas, o token certo prova (o DNS devolve o valor entre aspas).
        f.dns().txt.put("_ziipsan-verify." + HOST, List.of("\"ziipsan-verify=" + created.token() + "\""));
        assertThat(f.service().verifyDomain(TENANT_A, ACTOR, created.domain().id()).certStatus())
                .isEqualTo("ISSUED");
    }

    // ------------------------------------------------------------------
    // Tomada de subdomínio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("hostname VERIFIED (ou PENDING) de outro tenant não pode ser reivindicado")
    void takeoverOfActiveHostnameIsRefused() {
        Fixture f = fixture();
        verified(f, TENANT_A, HOST);
        assertThatThrownBy(() -> f.service().requestDomain(TENANT_B, ACTOR, HOST))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("data-conflict"));
        assertThat(f.service().resolveByHost(HOST).orElseThrow().tenantId()).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("após a remoção, o nome só volta com nova prova TXT — e o tenant original deixa de resolver")
    void afterReleaseHostnameRequiresFreshProof() {
        Fixture f = fixture();
        DomainView verified = verified(f, TENANT_A, HOST);
        f.service().releaseDomain(TENANT_A, ACTOR, verified.id(), "CONTRACT_ENDED");
        assertThat(f.service().resolveByHost(HOST)).isEmpty();
        assertThat(f.store().brandCustomDomain(TENANT_A)).isEmpty();
        // Outro tenant pode pedir, mas sem TXT não ativa (CNAME sozinho não basta).
        CreateResult claimed = f.service().requestDomain(TENANT_B, ACTOR, HOST);
        f.dns().cname.put(HOST, EDGE);
        assertThatThrownBy(() -> f.service().verifyDomain(TENANT_B, ACTOR, claimed.domain().id()))
                .isInstanceOf(ApiException.Validation.class);
        // Com a nova prova TXT, ativa para o novo tenant.
        f.dns().txt.put("_ziipsan-verify." + HOST, List.of("ziipsan-verify=" + claimed.token()));
        assertThat(f.service().verifyDomain(TENANT_B, ACTOR, claimed.domain().id()).status())
                .isEqualTo("VERIFIED");
        assertThat(f.service().resolveByHost(HOST).orElseThrow().tenantId()).isEqualTo(TENANT_B);
    }

    // ------------------------------------------------------------------
    // Renovação e revogação
    // ------------------------------------------------------------------

    @Test
    @DisplayName("renovação fora da janela é recusada; vencido, renova com novas datas e zera falhas")
    void renewalWindow() {
        Fixture f = fixture();
        DomainView verified = verified(f, TENANT_A, HOST);
        assertThatThrownBy(() -> f.service().renewDomain(TENANT_A, ACTOR, verified.id()))
                .isInstanceOf(ApiException.Conflict.class);
        // Vencido: o falso não recalcula a janela — simula o vencimento
        // persistindo uma visão com notAfter no passado via update.
        Instant now = Instant.now();
        DomainView expired = new DomainView(verified.id(), TENANT_A, HOST, "VERIFIED", EDGE, true, now,
                "ISSUED", "TEST", now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(10)),
                now.minus(Duration.ofDays(40)), null, 2, verified.verifiedAt(), null, null, null,
                verified.createdAt(), verified.updatedAt());
        f.store().update(TENANT_A, ACTOR, expired, "TEST");
        DomainView renewed = f.service().renewDomain(TENANT_A, ACTOR, verified.id());
        assertThat(renewed.certNotAfter()).isAfter(now);
        assertThat(renewed.lastRenewalAt()).isNotNull();
        assertThat(renewed.renewalFailures()).isZero();
    }

    @Test
    @DisplayName("falha do emissor na renovação conta a falha e preserva o certificado vigente")
    void renewalFailureCountsAndKeepsCert() {
        Fixture f = fixture(new FailingIssuer());
        // Verificação com emissor falho: VERIFIED + PENDING, sem certificado.
        CreateResult created = f.service().requestDomain(TENANT_A, ACTOR, HOST);
        f.dns().txt.put("_ziipsan-verify." + HOST, List.of("ziipsan-verify=" + created.token()));
        f.dns().cname.put(HOST, EDGE);
        assertThatThrownBy(() -> f.service().verifyDomain(TENANT_A, ACTOR, created.domain().id()))
                .isInstanceOf(IllegalStateException.class);
        DomainView pending = f.store().findOwn(TENANT_A, created.domain().id()).orElseThrow();
        assertThat(pending.status()).isEqualTo("VERIFIED");
        assertThat(pending.certStatus()).isEqualTo("PENDING");
        // Troca o emissor por um que funciona via update manual + renew de ISSUED com falha.
        DomainView issued = new DomainView(pending.id(), TENANT_A, HOST, "VERIFIED", EDGE, true, Instant.now(),
                "ISSUED", "TEST", Instant.now().minus(Duration.ofDays(100)), Instant.now().minus(Duration.ofDays(10)),
                Instant.now().minus(Duration.ofDays(40)), null, 0, pending.verifiedAt(), null, null, null,
                pending.createdAt(), pending.updatedAt());
        f.store().update(TENANT_A, ACTOR, issued, "TEST");
        assertThatThrownBy(() -> f.service().renewDomain(TENANT_A, ACTOR, issued.id()))
                .isInstanceOf(IllegalStateException.class);
        DomainView after = f.store().findOwn(TENANT_A, issued.id()).orElseThrow();
        assertThat(after.renewalFailures()).isEqualTo(1);
        assertThat(after.certStatus()).isEqualTo("ISSUED");
        assertThat(after.certNotAfter()).isEqualTo(issued.certNotAfter());
    }

    @Test
    @DisplayName("revogação: REVOKED, certificado revogado, sem resolução, sem renovação, parâmetro limpo")
    void revocation() {
        Fixture f = fixture();
        DomainView verified = verified(f, TENANT_A, HOST);
        DomainView revoked = f.service().revokeDomain(TENANT_A, ACTOR, verified.id(), "KEY_COMPROMISE");
        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThat(revoked.certStatus()).isEqualTo("REVOKED");
        assertThat(revoked.revokedReason()).isEqualTo("KEY_COMPROMISE");
        assertThat(f.service().resolveByHost(HOST)).isEmpty();
        assertThat(f.store().brandCustomDomain(TENANT_A)).isEmpty();
        assertThatThrownBy(() -> f.service().renewDomain(TENANT_A, ACTOR, verified.id()))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> f.service().revokeDomain(TENANT_A, ACTOR, verified.id(), "ADMIN"))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("liberação: REMOVED; segunda liberação é conflito; outro tenant não vê o domínio alheio")
    void releaseAndIsolation() {
        Fixture f = fixture();
        DomainView verified = verified(f, TENANT_A, HOST);
        DomainView released = f.service().releaseDomain(TENANT_A, ACTOR, verified.id(), "CONTRACT_ENDED");
        assertThat(released.status()).isEqualTo("REMOVED");
        assertThat(released.certStatus()).isEqualTo("REVOKED");
        assertThatThrownBy(() -> f.service().releaseDomain(TENANT_A, ACTOR, verified.id(), "ADMIN_REMOVED"))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> f.service().verifyDomain(TENANT_B, ACTOR, verified.id()))
                .isInstanceOf(ApiException.NotFound.class);
        assertThat(f.service().listDomains(TENANT_B)).isEmpty();
        assertThat(f.service().listDomains(TENANT_A)).hasSize(1);
    }
}
