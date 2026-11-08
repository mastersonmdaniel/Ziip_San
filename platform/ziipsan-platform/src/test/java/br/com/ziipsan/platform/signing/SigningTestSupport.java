package br.com.ziipsan.platform.signing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Apoio dos testes da P3.34: relogio fixo, ids/codigos deterministicos e parametros em memoria. */
final class SigningTestSupport {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    static final byte[] CONTENT = "contrato de adesao - texto sintetico de teste".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    /** Codigo/versao do aceite da P1.24 que o chamador (fixture) diz invocar — sinteticos, sem mapeamento implicito. */
    static final String ACCEPTANCE_CODE = "contract";
    static final String ACCEPTANCE_VERSION = "2026-01";

    private SigningTestSupport() {
    }

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static java.util.function.Supplier<UUID> ids() {
        AtomicInteger seq = new AtomicInteger();
        return () -> UUID.fromString(String.format("22222222-2222-2222-2222-%012d", seq.incrementAndGet()));
    }

    /**
     * Codigos deterministicos MAS no formato de alta entropia (16 simbolos do
     * alfabeto, D7): os signers recusam codigo fora do formato, entao nem o
     * fixture pode usar "CODIGO-TESTE-N" sequencial textual.
     */
    static java.util.function.Supplier<String> codes() {
        AtomicInteger seq = new AtomicInteger();
        return () -> String.format("0000-0000-0000-%04d", seq.incrementAndGet());
    }

    static DocumentSigner.SignContext context(SignableDocumentType type) {
        return new DocumentSigner.SignContext(TENANT, type, "documento-1", CONTENT, "usuario-teste");
    }

    /** Contexto com a chave real do aceite da P1.24 (D6), como o consumidor da simples monta. */
    static DocumentSigner.SignContext contextForSimple(SignableDocumentType type) {
        return new DocumentSigner.SignContext(TENANT, type, "documento-1", CONTENT, "usuario-teste",
                USER_ID, ACCEPTANCE_CODE, ACCEPTANCE_VERSION);
    }

    /** Evidencia da P1.24 que cobre a chave de {@link #contextForSimple} e o hash dado. */
    static SimpleDocumentSigner.AcceptanceEvidence acceptance(String contentHash) {
        return new SimpleDocumentSigner.AcceptanceEvidence(USER_ID, ACCEPTANCE_CODE, ACCEPTANCE_VERSION,
                contentHash, NOW, "203.0.113.10", "PORTAL_PASSWORD_MFA");
    }

    /** Servico com relogio fixo e limites de fabrica das irmas (ou os dados). */
    static SignatureService service(SigningParameters params, List<DocumentSigner> signers,
                                    SignatureService.SignatureStore store) {
        return service(params, signers, store, SignatureService.VerificationLimits.siblingFactory());
    }

    static SignatureService service(SigningParameters params, List<DocumentSigner> signers,
                                    SignatureService.SignatureStore store,
                                    SignatureService.VerificationLimits limits) {
        return new SignatureService(params, new MinLevelPolicy(params), signers, store, clock(), limits);
    }

    /** Parametros sign.* em memoria: mapa chave -> valor, como o parameter_versions devolveria. */
    static final class MapParameters implements SigningParameters {
        final Map<String, String> values = new HashMap<>();
        Optional<TenantDeliveryDecision> tenantDeliveryDecision = Optional.empty();

        MapParameters withMinLevel(SignableDocumentType type, String level) {
            values.put(MinLevelPolicy.parameterKey(type), level);
            return this;
        }

        /** Decisao do dono HABILITADORA com proveniencia completa (D8), como o registro real traria. */
        MapParameters withTenantDeliveryDecision() {
            tenantDeliveryDecision = Optional.of(new TenantDeliveryDecision(
                    true, "dono-da-plataforma (fixture)", NOW, "registro-de-decisao-sintetico-de-teste"));
            return this;
        }

        @Override
        public Optional<String> minLevelRaw(UUID tenantId, SignableDocumentType documentType) {
            return Optional.ofNullable(values.get(MinLevelPolicy.parameterKey(documentType)));
        }

        @Override
        public Optional<String> pscProvider(UUID tenantId) {
            return Optional.ofNullable(values.get("sign.psc_provider"));
        }

        @Override
        public Optional<String> timestampAuthority(UUID tenantId) {
            return Optional.ofNullable(values.get("sign.timestamp_authority"));
        }

        @Override
        public Optional<TenantDeliveryDecision> tenantDeliveryDecision(UUID tenantId) {
            return tenantDeliveryDecision;
        }
    }

    /** Store em memoria, com o registro adulteravel para o teste de verificacao e o log de tentativas. */
    static final class MapStore implements SignatureService.SignatureStore {

        /** Uma tentativa registrada (o relogio dos testes e fixo: tudo esta "na janela"). */
        record Attempt(String code, SignatureVerification.Outcome outcome, String sourceIp) {
        }

        final Map<String, DocumentSigner.SignatureRecord> byCode = new HashMap<>();
        final Map<String, SignatureVerification.Outcome> attempts = new HashMap<>();
        final List<Attempt> attemptLog = new ArrayList<>();

        @Override
        public void save(DocumentSigner.SignatureRecord record) {
            byCode.put(record.verificationCode(), record);
        }

        @Override
        public Optional<DocumentSigner.SignatureRecord> findByVerificationCode(String verificationCode) {
            return Optional.ofNullable(byCode.get(verificationCode));
        }

        @Override
        public void recordVerification(String verificationCode, SignatureVerification.Outcome outcome, String sourceIp) {
            attempts.put(verificationCode, outcome);
            attemptLog.add(new Attempt(verificationCode, outcome, sourceIp));
        }

        @Override
        public int verificationAttemptsByCodeSince(String verificationCode, Instant since) {
            return Math.toIntExact(attemptLog.stream()
                    .filter(a -> a.code().equals(verificationCode)).count());
        }

        @Override
        public int verificationAttemptsByOriginSince(String sourceIp, Instant since) {
            return Math.toIntExact(attemptLog.stream()
                    .filter(a -> sourceIp.equals(a.sourceIp())).count());
        }
    }
}
