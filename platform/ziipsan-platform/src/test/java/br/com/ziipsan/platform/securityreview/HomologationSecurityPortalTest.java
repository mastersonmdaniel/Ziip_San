package br.com.ziipsan.platform.securityreview;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.platform.portal.BlindDocumentLogin;
import br.com.ziipsan.platform.portal.BotDefensePolicy;
import br.com.ziipsan.platform.portal.GuestPaymentService;
import br.com.ziipsan.platform.portal.LinkingCode;
import br.com.ziipsan.platform.portal.LoginThrottle;
import br.com.ziipsan.platform.portal.PortalAuthenticationFlow;
import br.com.ziipsan.platform.portal.PortalParameters;
import br.com.ziipsan.platform.portal.PortalRealmPolicy;
import br.com.ziipsan.platform.portal.SecondCopyService;
import br.com.ziipsan.platform.portal.consumer.ConsumerLinkService;
import br.com.ziipsan.platform.portal.consumer.HolderDocumentContext;
import br.com.ziipsan.platform.portal.consumer.LinkAttemptLimiter;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.8 — casos do pentest local contra as regras REAIS da P3.3 (mesmo
 * modulo) e do vinculo da P3.20 ({@code ConsumerLinkService}, pacote
 * {@code consumer} do mesmo modulo — a integracao da P3.20 precede esta
 * suite, como a da P3.3): enumeracao, vinculo, convidado, realms,
 * download e defesa contra automacao. Chaves e documentos sao os
 * marcadores publicos de teste do projeto (mesmos da suite da P3.3);
 * nenhum dado real.
 *
 * <p>O que este nivel NAO prova esta declarado na matriz: tempo de
 * resposta medido na rede, RLS no PostgreSQL e a varredura DAST sao da
 * stack no CI.
 */
class HomologationSecurityPortalTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000380");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID ACCOUNT_CNPJ = UUID.randomUUID();
    private static final UUID ACCOUNT_EMAIL = UUID.randomUUID();
    private static final String CPF = "52998224725";
    private static final String CPF_INEXISTENTE = "39053344705";
    private static final String CNPJ = "11222333000181";
    private static final String CNPJ_INEXISTENTE = "11444777000161";
    private static final String EMAIL = "titular@example.com";
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final PortalParameters.AccountThrottle THROTTLE =
            new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15);

    private static FieldProtector protector() {
        byte[] enc = new byte[32];
        byte[] idx = new byte[32];
        Arrays.fill(enc, (byte) 7);
        Arrays.fill(idx, (byte) 9);
        TenantKeyProvider keys = new TenantKeyProvider() {
            @Override public byte[] encryptionKey(UUID tenantId) { return enc.clone(); }
            @Override public byte[] blindIndexKey(UUID tenantId) { return idx.clone(); }
        };
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }

    private BlindDocumentLogin loginWith(Map<String, UUID> byIndex) {
        return new BlindDocumentLogin(protector(),
                (tenantId, context, blindIndex) -> Optional.ofNullable(byIndex.get(context + "|" + blindIndex)));
    }

    private Map<String, UUID> knownAccount() {
        return knownAccounts();
    }

    /** Contas conhecidas nos TRES tipos de identificador do aceite: CPF, CNPJ e e-mail. */
    private Map<String, UUID> knownAccounts() {
        var p = protector();
        String cpfIndex = p.indexOf(TENANT, BlindDocumentLogin.DOCUMENT_CONTEXT,
                DocumentNumber.normalize(CPF));
        String cnpjIndex = p.indexOf(TENANT, BlindDocumentLogin.DOCUMENT_CONTEXT,
                DocumentNumber.normalize(CNPJ));
        String emailIndex = p.indexOf(TENANT, BlindDocumentLogin.EMAIL_CONTEXT, EMAIL);
        return Map.of(
                BlindDocumentLogin.DOCUMENT_CONTEXT + "|" + cpfIndex, ACCOUNT,
                BlindDocumentLogin.DOCUMENT_CONTEXT + "|" + cnpjIndex, ACCOUNT_CNPJ,
                BlindDocumentLogin.EMAIL_CONTEXT + "|" + emailIndex, ACCOUNT_EMAIL);
    }

    @Test
    @DisplayName("SEC login: CPF, CNPJ e e-mail — inexistente e senha errada indistintos, verificacao roda em TODOS os casos (D6)")
    void enumeracaoLogin() {
        // Contador de invocacoes do verificador: a equalizacao de trabalho
        // (a evidencia declarada do caso ENUMERACAO_LOGIN) agora e
        // ASSEVERADA, nao so afirmada — inclusive contra a conta ficticia.
        List<UUID> verified = new ArrayList<>();
        Set<UUID> known = Set.of(ACCOUNT, ACCOUNT_CNPJ, ACCOUNT_EMAIL);
        var flow = new PortalAuthenticationFlow(loginWith(knownAccounts()), (accountId, password) -> {
            verified.add(accountId);
            return known.contains(accountId) && "correta".equals(new String(password));
        });
        var wrongCpf = flow.authenticate(TENANT, CPF, "errada".toCharArray());
        var unknownCpf = flow.authenticate(TENANT, CPF_INEXISTENTE, "correta".toCharArray());
        var wrongCnpj = flow.authenticate(TENANT, CNPJ, "errada".toCharArray());
        var unknownCnpj = flow.authenticate(TENANT, CNPJ_INEXISTENTE, "correta".toCharArray());
        var wrongEmail = flow.authenticate(TENANT, EMAIL, "errada".toCharArray());
        var unknownEmail = flow.authenticate(TENANT, "ninguem@example.invalid", "correta".toCharArray());
        assertThat(wrongCpf.outcome()).isEqualTo(PortalAuthenticationFlow.Outcome.INVALID_CREDENTIALS);
        assertThat(unknownCpf).isEqualTo(wrongCpf);
        assertThat(wrongCnpj).isEqualTo(wrongCpf);
        assertThat(unknownCnpj).isEqualTo(wrongCpf);
        assertThat(wrongEmail).isEqualTo(wrongCpf);
        assertThat(unknownEmail).isEqualTo(wrongCpf);
        // A verificacao rodou nas SEIS tentativas: nas contas existentes com
        // a conta real de cada identificador (CPF, CNPJ e e-mail EXISTENTE
        // com senha errada inclusive), nas inexistentes com a ficticia.
        assertThat(verified).containsExactly(ACCOUNT, PortalAuthenticationFlow.DUMMY_ACCOUNT_ID,
                ACCOUNT_CNPJ, PortalAuthenticationFlow.DUMMY_ACCOUNT_ID,
                ACCOUNT_EMAIL, PortalAuthenticationFlow.DUMMY_ACCOUNT_ID);
        // Sanidade do dublê: com a senha certa, os tres identificadores autenticam.
        assertThat(flow.authenticate(TENANT, CPF, "correta".toCharArray()).outcome())
                .isEqualTo(PortalAuthenticationFlow.Outcome.SUCCESS);
        assertThat(flow.authenticate(TENANT, CNPJ, "correta".toCharArray()).accountId()).isEqualTo(ACCOUNT_CNPJ);
        assertThat(flow.authenticate(TENANT, EMAIL, "correta".toCharArray()).accountId()).isEqualTo(ACCOUNT_EMAIL);
    }

    @Test
    @DisplayName("SEC recuperacao: mesma mensagem e mesmo enfileiramento para conta existente e inexistente")
    void enumeracaoRecuperacao() {
        var flow = new PortalAuthenticationFlow(loginWith(knownAccount()), (id, pw) -> false);
        List<UUID> enqueued = new ArrayList<>();
        String known = flow.recover(TENANT, CPF, enqueued::add);
        String unknown = flow.recover(TENANT, CPF_INEXISTENTE, enqueued::add);
        assertThat(known).isEqualTo(unknown).isEqualTo(BlindDocumentLogin.UNIFORM_RECOVERY_MESSAGE);
        assertThat(enqueued).containsExactly(ACCOUNT, PortalAuthenticationFlow.DUMMY_ACCOUNT_ID);
        // Limite de tentativas protege a recuperacao sem bloquear a vitima (atraso + 429, sem lockout).
        var verdict = LoginThrottle.evaluate(5, 0, THROTTLE);
        assertThat(verdict.decision()).isEqualTo(LoginThrottle.Decision.TOO_MANY_REQUESTS);
        assertThatThrownBy(() -> LoginThrottle.enforce(verdict)).isInstanceOf(ApiException.TooManyRequests.class);
    }

    // ------------------------------------------------ convidado

    private static final class FakeGuestPort implements GuestPaymentService.GuestInvoicePort {
        final Set<String> knownHashes = new HashSet<>();
        int byOrigin;
        int byCode;
        final List<GuestPaymentService.GuestQueryAudit> audits = new ArrayList<>();
        @Override public Optional<GuestPaymentService.GuestInvoiceView> findOpenInvoice(UUID t, String hash) {
            return knownHashes.contains(hash) ? Optional.of(INVOICE) : Optional.empty();
        }
        @Override public boolean documentSuffixMatches(UUID t, String hash, String suffix) { return "4725".equals(suffix); }
        @Override public int queriesByOrigin(UUID t, String origin) { return byOrigin; }
        @Override public int queriesByCode(UUID t, String hash) { return byCode; }
        @Override public void recordQuery(GuestPaymentService.GuestQueryAudit audit) { audits.add(audit); }
    }

    private static final GuestPaymentService.GuestInvoiceView INVOICE =
            new GuestPaymentService.GuestInvoiceView(new BigDecimal("123.45"),
                    LocalDate.of(2026, 11, 20), YearMonth.of(2026, 11),
                    "23793.38128.60007.827136.95000.063305.9.12340000123456", "000201pix-copia-e-cola");

    private GuestPaymentService guestService(FakeGuestPort port) {
        var p = protector();
        return new GuestPaymentService(port, (tenantId, code) -> LinkingCode.storageHash(p, tenantId, code));
    }

    private PortalParameters guestParams(String mode) {
        return PortalParameters.fromValues(Map.of(PortalParameters.GUEST_PAYMENT, "\"" + mode + "\""));
    }

    @Test
    @DisplayName("SEC convidado (ADR-0043 item 4): inexistente, sufixo errado e formato invalido sao indistintos; toda consulta registrada")
    void enumeracaoConvidado() {
        var port = new FakeGuestPort();
        String code = LinkingCode.generate();
        port.knownHashes.add(LinkingCode.storageHash(protector(), TENANT, code));
        var service = guestService(port);
        String unknownCode = LinkingCode.generate();
        List<String> messages = new ArrayList<>();
        for (Runnable attempt : List.<Runnable>of(
                () -> service.query(TENANT, "203.0.113.9", unknownCode, null, guestParams("CODE_ONLY"), THROTTLE, NOW),
                () -> service.query(TENANT, "203.0.113.9", code, "0000", guestParams("CODE_AND_DOCUMENT_SUFFIX"), THROTTLE, NOW),
                () -> service.query(TENANT, "203.0.113.9", "123456", null, guestParams("CODE_ONLY"), THROTTLE, NOW))) {
            try {
                attempt.run();
                throw new AssertionError("consulta de convidado deveria ter sido recusada");
            } catch (ApiException.NotFound e) {
                messages.add(e.getMessage());
            }
        }
        assertThat(messages).hasSize(3);
        assertThat(new HashSet<>(messages)).hasSize(1);
        assertThat(port.audits).hasSize(3);
        assertThat(port.audits).allSatisfy(a -> assertThat(a.found()).isFalse());
    }

    @Test
    @DisplayName("SEC convidado: limite por origem devolve 429 mesmo com codigo malformado")
    void convidadoLimite() {
        var port = new FakeGuestPort();
        port.byOrigin = THROTTLE.maxGuestQueriesPerOrigin();
        var service = guestService(port);
        assertThatThrownBy(() -> service.query(TENANT, "203.0.113.9", "123456", null,
                guestParams("CODE_ONLY"), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    @Test
    @DisplayName("SEC convidado: limite POR CODIGO devolve 429 e a tentativa fica registrada como nao encontrada (D5)")
    void convidadoLimitePorCodigo() {
        var port = new FakeGuestPort();
        // So a dimensao por codigo estoura (byCode, que a revisao apontou
        // como nunca exercitado); a origem esta zerada.
        port.byCode = THROTTLE.maxGuestQueriesPerCode();
        var service = guestService(port);
        String code = LinkingCode.generate();
        assertThatThrownBy(() -> service.query(TENANT, "203.0.113.10", code, null,
                guestParams("CODE_ONLY"), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
        assertThat(port.audits).hasSize(1);
        assertThat(port.audits.get(0).found()).isFalse();
        assertThat(port.audits.get(0).codeHash())
                .isEqualTo(LinkingCode.storageHash(protector(), TENANT, code));
    }

    /**
     * Protetor com chave DERIVADA do tenant (o protector() comum usa a
     * chave fixa de teste para todos, como o PortalTestSupport da P3.3 —
     * com ele, a amarra ao tenant nao seria observavel). Aqui os 32 bytes
     * saem dos bits do UUID do tenant: tenants distintos, chaves distintas,
     * de forma deterministica.
     */
    private static FieldProtector tenantBoundProtector() {
        TenantKeyProvider keys = new TenantKeyProvider() {
            private byte[] derive(UUID tenantId, int seed) {
                byte[] raw = new byte[16];
                java.nio.ByteBuffer.wrap(raw)
                        .putLong(tenantId.getMostSignificantBits())
                        .putLong(tenantId.getLeastSignificantBits());
                byte[] key = new byte[32];
                for (int i = 0; i < key.length; i++) {
                    key[i] = (byte) (raw[i % raw.length] + seed + i);
                }
                return key;
            }
            @Override public byte[] encryptionKey(UUID tenantId) { return derive(tenantId, 1); }
            @Override public byte[] blindIndexKey(UUID tenantId) { return derive(tenantId, 2); }
        };
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }

    @Test
    @DisplayName("SEC codigo de vinculacao: hash cego deterministico, matches so para o codigo certo (D5)")
    void codigoVinculacaoHashCego() {
        var p = tenantBoundProtector();
        String code = LinkingCode.generate();
        String hash = LinkingCode.storageHash(p, TENANT, code);
        // Hash cego: 64 hex (HMAC-SHA-256 do indice), nunca o codigo em claro,
        // e deterministico para o mesmo tenant/codigo (e assim que o banco guarda).
        assertThat(hash).hasSize(64);
        assertThat(hash).isNotEqualTo(code);
        assertThat(LinkingCode.storageHash(p, TENANT, code)).isEqualTo(hash);
        // Conferencia em tempo constante sobre o indice: so o codigo certo casa.
        assertThat(LinkingCode.matches(p, TENANT, hash, code)).isTrue();
        assertThat(LinkingCode.matches(p, TENANT, hash, LinkingCode.generate())).isFalse();
        assertThat(LinkingCode.matches(p, TENANT, hash, "123456")).isFalse();
        assertThat(LinkingCode.matches(p, TENANT, null, code)).isFalse();
        // Outro tenant NAO confere o mesmo codigo (indice amarrado a chave do tenant).
        UUID otherTenant = UUID.fromString("ff000000-0000-0000-0000-000000000380");
        assertThat(LinkingCode.matches(p, otherTenant, hash, code)).isFalse();
        assertThat(LinkingCode.storageHash(p, otherTenant, code)).isNotEqualTo(hash);
        // Matricula (sem forma de codigo) nao vira hash: recusada na origem.
        assertThatThrownBy(() -> LinkingCode.storageHash(p, TENANT, "123456789"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SEC matricula/codigo: matricula numerica nao tem forma de codigo; codigo gerado tem alta entropia e nao e a matricula")
    void matriculaNaoECodigo() {
        assertThat(LinkingCode.hasCodeShape("123456789")).isFalse();
        assertThat(LinkingCode.hasCodeShape("000123")).isFalse();
        String code = LinkingCode.generate();
        assertThat(code).hasSize(20);
        assertThat(LinkingCode.hasCodeShape(code)).isTrue();
    }

    // ------------------------------------------------ vinculo (P3.20)

    private static final class FakeLinkPorts {
        final List<Boolean> attemptSuccess = new ArrayList<>();
        final List<String> savedLinks = new ArrayList<>();
        boolean unitExists = true;
        int ipAttempts;
        String code = LinkingCode.generate();
        UUID unit = UUID.randomUUID();

        ConsumerLinkService service(FieldProtector p) {
            var units = new ConsumerLinkService.UnitLookupPort() {
                @Override public Optional<ConsumerLinkService.UnitForLink> findByRegistration(UUID t, String reg) {
                    if (!unitExists) {
                        return Optional.empty();
                    }
                    return Optional.of(new ConsumerLinkService.UnitForLink(unit,
                            p.indexOf(t, HolderDocumentContext.CONTEXT, DocumentNumber.normalize(CPF)),
                            LinkingCode.storageHash(p, t, code)));
                }
            };
            var links = new ConsumerLinkService.LinkStorePort() {
                @Override public boolean isAlreadyLinked(UUID t, String s, UUID u) { return savedLinks.contains(s + "|" + u); }
                @Override public void saveLink(UUID t, String s, UUID u, Instant at) { savedLinks.add(s + "|" + u); }
                @Override public int countActiveLinks(UUID t, String s) {
                    return (int) savedLinks.stream().filter(x -> x.startsWith(s + "|")).count();
                }
            };
            var attempts = new ConsumerLinkService.AttemptPort() {
                @Override public int attemptsByUnit(UUID t, UUID u) { return 0; }
                @Override public int attemptsByIp(UUID t, String ip) { return ipAttempts; }
                @Override public int attemptsByTenant(UUID t) { return 0; }
                @Override public void recordAttempt(UUID t, UUID u, String ip, boolean ok, boolean alert) {
                    attemptSuccess.add(ok);
                }
            };
            return new ConsumerLinkService(p, units, links, attempts,
                    new LinkAttemptLimiter.Limits(5, 5, 100, 4));
        }
    }

    private static String linkFailure(Runnable attempt) {
        try {
            attempt.run();
        } catch (ApiException e) {
            return e.getMessage();
        }
        throw new AssertionError("vinculo deveria ter sido recusado");
    }

    @Test
    @DisplayName("SEC vinculo: matricula inexistente, codigo errado e documento errado tem EXATAMENTE a mesma resposta (D5)")
    void vinculoRespostaUniforme() {
        var p = protector();
        var ports = new FakeLinkPorts();
        var service = ports.service(p);
        // Sucesso: codigo da fatura + documento do titular (indice cego) vinculam.
        assertThat(service.link(TENANT, "consumidor-1", "MAT-1", ports.code, CPF, "203.0.113.20"))
                .isEqualTo(ports.unit);
        assertThat(ports.savedLinks).hasSize(1);

        var unknownReg = new FakeLinkPorts();
        unknownReg.unitExists = false;
        String m1 = linkFailure(() -> unknownReg.service(p)
                .link(TENANT, "consumidor-1", "MAT-X", unknownReg.code, CPF, "203.0.113.20"));
        var wrongCode = new FakeLinkPorts();
        String m2 = linkFailure(() -> wrongCode.service(p)
                .link(TENANT, "consumidor-1", "MAT-1", LinkingCode.generate(), CPF, "203.0.113.20"));
        var wrongDoc = new FakeLinkPorts();
        String m3 = linkFailure(() -> wrongDoc.service(p)
                .link(TENANT, "consumidor-1", "MAT-1", wrongDoc.code, "11144477735", "203.0.113.20"));
        assertThat(m1).isEqualTo(ConsumerLinkService.UNIFORM_FAILURE);
        assertThat(m2).isEqualTo(m1);
        assertThat(m3).isEqualTo(m1);
        // Toda tentativa registrada: sucesso = true; as tres falhas = false.
        assertThat(ports.attemptSuccess).containsExactly(true);
        assertThat(unknownReg.attemptSuccess).containsExactly(false);
        assertThat(wrongCode.attemptSuccess).containsExactly(false);
        assertThat(wrongDoc.attemptSuccess).containsExactly(false);
        assertThat(wrongCode.savedLinks).isEmpty();
        assertThat(wrongDoc.savedLinks).isEmpty();
    }

    @Test
    @DisplayName("SEC realms: token do portal nao acessa backoffice e token do backoffice nao acessa o portal")
    void trocaDeRealms() {
        String portalIssuer = "https://keycloak.local/realms/ziipsan-portal";
        String backofficeIssuer = "https://keycloak.local/realms/ziipsan";
        assertThatThrownBy(() -> PortalRealmPolicy.requireBackofficeToken(portalIssuer))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> PortalRealmPolicy.requirePortalToken(backofficeIssuer))
                .isInstanceOf(ApiException.Forbidden.class);
        PortalRealmPolicy.requirePortalToken(portalIssuer);
        PortalRealmPolicy.requireBackofficeToken(backofficeIssuer);
    }

    @Test
    @DisplayName("SEC download: 2a via so para unidade vinculada a conta do consumidor")
    void downloadPortal() {
        UUID unitLinked = UUID.randomUUID();
        UUID unitOther = UUID.randomUUID();
        UUID bill = UUID.randomUUID();
        var service = new SecondCopyService(new SecondCopyService.OpenBillPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String subject, UUID unit) { return unit.equals(unitLinked); }
            @Override public Optional<UUID> openBillId(UUID t, UUID unit) { return Optional.of(bill); }
            @Override public byte[] secondCopyPdf(UUID t, UUID billId) { return new byte[]{1, 2, 3}; }
            @Override public Optional<String> pixCopyPaste(UUID t, UUID billId) { return Optional.of("000201pix"); }
        });
        assertThatThrownBy(() -> service.secondCopy(TENANT, "consumidor-1", unitOther))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(service.secondCopy(TENANT, "consumidor-1", unitLinked).billId()).isEqualTo(bill);
    }

    @Test
    @DisplayName("SEC bot defense (PLT-22): nenhum desafio cognitivo existe; stuffing e detectado; senha vazada recusada")
    void defesaAutomacao() {
        assertThat(BotDefensePolicy.ALLOWED_KINDS)
                .containsExactlyInAnyOrder(BotDefensePolicy.ChallengeKind.NONE,
                        BotDefensePolicy.ChallengeKind.PROOF_OF_WORK,
                        BotDefensePolicy.ChallengeKind.INVISIBLE);
        var thresholds = new BotDefensePolicy.StuffingThresholds(10, 5);
        assertThat(BotDefensePolicy.detectStuffing(11, 0, thresholds).any()).isTrue();
        assertThat(BotDefensePolicy.detectStuffing(0, 6, thresholds).any()).isTrue();
        assertThat(BotDefensePolicy.detectStuffing(1, 1, thresholds).any()).isFalse();
        assertThat(BotDefensePolicy.requiredChallenge(PortalParameters.BotDefenseMode.CHALLENGE,
                BotDefensePolicy.detectStuffing(11, 0, thresholds)))
                .isEqualTo(BotDefensePolicy.ChallengeKind.PROOF_OF_WORK);
        assertThatThrownBy(() -> BotDefensePolicy.validateNewPassword("senha-vazada-conhecida", pw -> true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BotDefensePolicy.validateNewPassword("qualquer-senha", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
