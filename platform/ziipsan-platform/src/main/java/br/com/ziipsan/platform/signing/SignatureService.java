package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.api.ApiException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestrador do servico de assinatura (P3.34): aplica, nesta ordem, o
 * portao dos documentos de entrega ao tenant (decisao do dono, com
 * proveniencia — D8), o nivel minimo do tenant para o tipo
 * ({@link MinLevelPolicy}) e so entao entrega o pedido ao
 * {@link DocumentSigner} do nivel pretendido. A assinatura produzida e
 * persistida pela porta {@link SignatureStore} (tabela
 * {@code document_signatures} da V1_14) e cada verificacao publica e
 * registrada (tabela {@code signature_verification_attempts}), COM LIMITE
 * DE TENTATIVAS por codigo e por origem (correcao D7).
 */
public final class SignatureService {

    /** Persistencia das assinaturas e das verificacoes (V1_14). */
    public interface SignatureStore {
        void save(DocumentSigner.SignatureRecord record);

        Optional<DocumentSigner.SignatureRecord> findByVerificationCode(String verificationCode);

        void recordVerification(String verificationCode, SignatureVerification.Outcome outcome, String sourceIp);

        /** Tentativas registradas para este codigo desde o instante (base do limite por codigo, D7). */
        int verificationAttemptsByCodeSince(String verificationCode, Instant since);

        /** Tentativas registradas desta origem desde o instante (base do limite por origem, D7). */
        int verificationAttemptsByOriginSince(String sourceIp, Instant since);
    }

    /** Porta que os controllers usam; a implementacao amarra store e signers. */
    public interface SignatureFacade {
        String signJson(SignCommand command);

        String publicValidationJson(String verificationCode, String sourceIp);
    }

    /** Comando de assinatura: nivel pretendido + documento + signatario. */
    public record SignCommand(SignatureLevel intendedLevel, DocumentSigner.SignContext context) {

        public SignCommand {
            Objects.requireNonNull(intendedLevel, "nivel pretendido e obrigatorio");
            Objects.requireNonNull(context, "contexto e obrigatorio");
        }
    }

    /**
     * Limites da verificacao publica (correcao D7), no padrao das tarefas
     * irmas do portal: os valores de fabrica sao os do
     * {@code portal.account_throttle} da P3.3 (30 por origem e 10 por codigo
     * em janela de 15 minutos — os mesmos que a P3.35 aplica a verificacao
     * publica dela, ADR-0043 item 4). Sao injetados para a integracao poder
     * apertar (nunca afrouxar alem da fabrica sem decisao registrada).
     */
    public record VerificationLimits(int maxPerCode, int maxPerOrigin, int windowMinutes) {

        public VerificationLimits {
            if (maxPerCode < 1 || maxPerOrigin < 1 || windowMinutes < 1) {
                throw new IllegalArgumentException("limites de verificacao devem ser >= 1");
            }
        }

        /** Fabrica das tarefas irmas do portal (P3.3/P3.35): 10/codigo, 30/origem, 15 min. */
        public static VerificationLimits siblingFactory() {
            return new VerificationLimits(10, 30, 15);
        }
    }

    private final SigningParameters parameters;
    private final MinLevelPolicy minLevelPolicy;
    private final Map<SignatureLevel, DocumentSigner> signers;
    private final SignatureStore store;
    private final Clock clock;
    private final VerificationLimits verificationLimits;

    public SignatureService(SigningParameters parameters, MinLevelPolicy minLevelPolicy,
                            List<DocumentSigner> signers, SignatureStore store,
                            Clock clock, VerificationLimits verificationLimits) {
        this.parameters = Objects.requireNonNull(parameters, "parameters e obrigatorio");
        this.minLevelPolicy = Objects.requireNonNull(minLevelPolicy, "minLevelPolicy e obrigatorio");
        this.store = Objects.requireNonNull(store, "store e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio");
        this.verificationLimits = Objects.requireNonNull(verificationLimits,
                "verificationLimits e obrigatorio");
        Objects.requireNonNull(signers, "signers e obrigatorio");
        Map<SignatureLevel, DocumentSigner> byLevel = new EnumMap<>(SignatureLevel.class);
        for (DocumentSigner signer : signers) {
            byLevel.put(signer.level(), signer);
        }
        this.signers = Map.copyOf(byLevel);
    }

    public DocumentSigner.SignatureRecord sign(SignCommand command) {
        Objects.requireNonNull(command, "command e obrigatorio");
        DocumentSigner.SignContext context = command.context();
        if (context.documentType().tenantDelivery()) {
            // Portao do dono COM proveniencia (D8): a decisao vem do registro
            // do dono da plataforma (ver SigningParameters), nao de flag solto.
            Optional<SigningParameters.TenantDeliveryDecision> decision =
                    parameters.tenantDeliveryDecision(context.tenantId());
            if (decision.isEmpty() || !decision.get().enabled()) {
                throw new ApiException.Forbidden(
                        "Documentos de entrega ao tenant (DPA, contrato) so usam o servico de assinatura "
                                + "por decisao do dono, e nenhuma decisao habilitadora foi registrada "
                                + "para este tenant.");
            }
        }
        minLevelPolicy.requireSatisfied(context.tenantId(), context.documentType(), command.intendedLevel());
        DocumentSigner signer = signers.get(command.intendedLevel());
        if (signer == null) {
            throw new IllegalStateException("nenhum DocumentSigner configurado para o nivel "
                    + command.intendedLevel());
        }
        DocumentSigner.SignatureRecord record = signer.sign(context);
        store.save(record);
        return record;
    }

    /**
     * Verificacao publica por codigo: aplica o limite de tentativas (D7),
     * registra a tentativa e devolve o desfecho. Estouro de limite registra a
     * tentativa como {@code RATE_LIMITED} e responde 429 — sem revelar o
     * registro. Origem nula/vazia nao participa do limite por origem (nao ha
     * chave confiavel), mas o limite por codigo continua valendo.
     */
    public SignatureVerification.Outcome verify(String verificationCode, String sourceIp) {
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new IllegalArgumentException("codigo de verificacao e obrigatorio");
        }
        Instant since = clock.instant().minus(Duration.ofMinutes(verificationLimits.windowMinutes()));
        boolean originLimited = sourceIp != null && !sourceIp.isBlank()
                && store.verificationAttemptsByOriginSince(sourceIp, since) >= verificationLimits.maxPerOrigin();
        boolean codeLimited = store.verificationAttemptsByCodeSince(verificationCode, since)
                >= verificationLimits.maxPerCode();
        if (originLimited || codeLimited) {
            store.recordVerification(verificationCode, SignatureVerification.Outcome.RATE_LIMITED, sourceIp);
            throw new ApiException.TooManyRequests("Muitas consultas de validacao. Aguarde e tente novamente.");
        }
        SignatureVerification.Outcome outcome = SignatureVerification.check(
                store.findByVerificationCode(verificationCode).orElse(null));
        store.recordVerification(verificationCode, outcome, sourceIp);
        return outcome;
    }

    public Optional<DocumentSigner.SignatureRecord> find(String verificationCode) {
        return store.findByVerificationCode(verificationCode);
    }
}
