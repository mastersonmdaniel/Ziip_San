package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Assinatura AVANCADA (Lei 14.063/2020, art. 4o, II): produzida por provedor
 * atras da porta {@link AdvancedSignatureProvider} — associacao univoca ao
 * signatario, controle exclusivo dos elementos de criacao e deteccao de
 * alteracao posterior sao garantias DO PROVEDOR, registradas como evidencia
 * (identidade vinculada e selo) nesta assinatura.
 *
 * <p>A assinatura avancada do GOV.BR NAO e caminho (spec da P3.34): ela e
 * restrita a gestor publico e a servico em dominio gov.br. Se o provedor se
 * identificar como gov.br, esta classe RECUSA — nao e um provedor valido
 * para consumidor/tenant privado neste servico. A recusa e por PADRAO sobre
 * o identificador normalizado (correcao D4): qualquer identificador cuja
 * forma sem separadores CONTENHA {@code govbr} e recusado
 * ({@code gov.br}, {@code govbr-sso}, {@code govbr2},
 * {@code assinatura-govbr}, {@code govbrassinatura}...). Identificadores que
 * nao contem a sequencia contigua {@code govbr} — {@code govtech},
 * {@code brgov}, {@code provedor-x} — nao sao atingidos: o delimitador e a
 * marca gov.br, como a spec a nomeia.
 *
 * <p>O provedor da avancada vive SO na evidencia (correcao D5): a coluna
 * {@code psc_provider} da V1_14 e, pela semantica da propria migration, o
 * PSC da assinatura QUALIFICADA — grava-la com o provedor da avancada
 * mentiria o registro e o CHECK da V1_14 agora veda {@code psc_provider}
 * fora da qualificada.
 */
public final class AdvancedDocumentSigner implements DocumentSigner {

    /** Selo devolvido pelo provedor de assinatura avancada. */
    public record AdvancedSeal(String providerId, String identityBindingRef, String sealValue) {

        public AdvancedSeal {
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalArgumentException("provedor da assinatura avancada e obrigatorio");
            }
            if (identityBindingRef == null || identityBindingRef.isBlank()) {
                throw new IllegalArgumentException("vinculo univoco de identidade do provedor e obrigatorio");
            }
            if (sealValue == null || sealValue.isBlank()) {
                throw new IllegalArgumentException("selo do provedor e obrigatorio");
            }
        }
    }

    /** Porta do provedor de assinatura avancada (integracao externa). */
    public interface AdvancedSignatureProvider {

        /**
         * Identidade declarada do provedor, quando conhecida ANTES do selo:
         * permite vedar o gov.br sem sequer invocar o provedor (correcao D4).
         * {@code null} = identidade so conhecida no selo (a veda se repete
         * sobre o selo devolvido, como defesa em profundidade).
         */
        default String providerId() {
            return null;
        }

        AdvancedSeal seal(SignContext context, String contentHash);
    }

    private final AdvancedSignatureProvider provider;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final Supplier<String> verificationCodes;

    public AdvancedDocumentSigner(AdvancedSignatureProvider provider, Clock clock,
                                  Supplier<UUID> ids, Supplier<String> verificationCodes) {
        this.provider = Objects.requireNonNull(provider, "provider e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio");
        this.ids = Objects.requireNonNull(ids, "ids e obrigatorio");
        this.verificationCodes = Objects.requireNonNull(verificationCodes, "verificationCodes e obrigatorio");
    }

    /**
     * A assinatura avancada do gov.br nao e caminho neste servico (spec).
     * Normaliza (minusculas, sem separadores) e recusa qualquer identificador
     * que contenha a sequencia contigua {@code govbr} (correcao D4).
     */
    static boolean isGovBr(String providerId) {
        if (providerId == null) {
            return false;
        }
        String normalized = providerId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.contains("govbr");
    }

    private static void refuseGovBr() {
        throw new ApiException.Validation(
                "Assinatura avancada do gov.br nao e caminho neste servico: ela e restrita a "
                        + "gestor publico e a servico em dominio gov.br (spec da P3.34).",
                java.util.List.of(), java.util.List.of());
    }

    @Override
    public SignatureLevel level() {
        return SignatureLevel.ADVANCED;
    }

    @Override
    public SignatureRecord sign(SignContext context) {
        // Veda ANTES de invocar o provedor, quando a identidade ja e conhecida (D4).
        if (isGovBr(provider.providerId())) {
            refuseGovBr();
        }
        String contentHash = SignatureHashes.sha256Hex(context.content());
        AdvancedSeal seal = Objects.requireNonNull(provider.seal(context, contentHash),
                "provedor de assinatura avancada nao devolveu selo");
        // E de novo sobre o selo: a identidade declarada na porta pode divergir.
        if (isGovBr(seal.providerId())) {
            refuseGovBr();
        }
        Instant now = SignatureVerification.canonical(clock.instant());
        String code = VerificationCodes.requireShape(verificationCodes.get());
        Map<String, Object> evidenceJson = new LinkedHashMap<>();
        evidenceJson.put("providerId", seal.providerId());
        evidenceJson.put("identityBindingRef", seal.identityBindingRef());
        evidenceJson.put("sealHash", SignatureHashes.sha256Hex(seal.sealValue()));
        return new SignatureRecord(ids.get(), context.tenantId(), context.documentType(),
                context.documentReference(), SignatureLevel.ADVANCED, contentHash,
                context.signerSubject(), now, code,
                SignatureVerification.digestOf(contentHash, code, now),
                // pscProvider = null de proposito (D5): PSC e so da qualificada (V1_14).
                null, null, null, null, null, Json.write(evidenceJson));
    }
}
