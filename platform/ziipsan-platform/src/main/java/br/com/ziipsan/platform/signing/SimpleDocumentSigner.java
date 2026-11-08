package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Assinatura SIMPLES (Lei 14.063/2020, art. 4o, I): aceite com evidencias —
 * hash do texto, data, IP e autenticacao — SOBRE o
 * {@code platform.document_acceptance} da P1.24 (core V9_5), cuja propria
 * migration declara: "e ele [content_hash] que a P3.34 usa como evidencia da
 * assinatura simples, junto de accepted_at, ip_address e auth_method".
 *
 * <p>Nao ha infraestrutura paralela de aceite: esta classe le a evidencia da
 * P1.24 pela porta e recusa quando ela nao existe, esta incompleta (sem IP ou
 * sem metodo de autenticacao) ou nao e o aceite DESTA chave e DESTE texto —
 * aceite de outro documento ou de outro texto nao assina este.
 *
 * <p><b>Chave real do aceite (correcao D6 da revisao cruzada):</b> a V9_5
 * identifica o aceite por {@code (tenant_id, user_id, document_code,
 * document_version)} — UNIQUE da tabela — e NAO por uma string livre de
 * signatario. A porta abaixo recebe exatamente essa chave (mais o hash do
 * texto), vinda do {@link SignContext}: quem chama informa o
 * {@code user_id} do signatario e o {@code document_code}/{@code document_version}
 * do aceite que esta invocando. Este pacote nao inventa conversao entre
 * {@link SignableDocumentType} e {@code document_code}: os codigos do
 * catalogo da P1.24 sao do dominio dela, e amarra-los aos tipos da P3.34 e
 * decisao da integracao/consumidor, nao deste signer.
 */
public final class SimpleDocumentSigner implements DocumentSigner {

    /** Evidencia de aceite da P1.24 (linha de platform.document_acceptance, V9_5). */
    public record AcceptanceEvidence(UUID userId, String documentCode, String documentVersion,
                                     String contentHash, Instant acceptedAt,
                                     String ipAddress, String authMethod) {
    }

    /**
     * Porta de leitura do document_acceptance da P1.24 PELA CHAVE REAL da
     * tabela (V9_5): tenant + {@code user_id} + {@code document_code} +
     * {@code document_version}, com o hash do texto apresentado. O adaptador
     * de integracao e um SELECT sobre {@code platform.document_acceptance}
     * com esses cinco criterios — nenhum mapeamento implicito alem disso.
     */
    public interface AcceptanceEvidencePort {
        Optional<AcceptanceEvidence> findAcceptance(UUID tenantId, UUID userId, String documentCode,
                                                    String documentVersion, String contentHash);
    }

    private final AcceptanceEvidencePort evidencePort;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final Supplier<String> verificationCodes;

    public SimpleDocumentSigner(AcceptanceEvidencePort evidencePort, Clock clock,
                                Supplier<UUID> ids, Supplier<String> verificationCodes) {
        this.evidencePort = Objects.requireNonNull(evidencePort, "evidencePort e obrigatoria");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio");
        this.ids = Objects.requireNonNull(ids, "ids e obrigatorio");
        this.verificationCodes = Objects.requireNonNull(verificationCodes, "verificationCodes e obrigatorio");
    }

    @Override
    public SignatureLevel level() {
        return SignatureLevel.SIMPLE;
    }

    @Override
    public SignatureRecord sign(SignContext context) {
        if (context.signerUserId() == null || context.acceptanceDocumentCode() == null
                || context.acceptanceDocumentCode().isBlank()
                || context.acceptanceDocumentVersion() == null
                || context.acceptanceDocumentVersion().isBlank()) {
            throw new ApiException.Validation(
                    "Assinatura simples exige a chave do aceite da P1.24 no contexto: user_id do "
                            + "signatario, document_code e document_version do aceite (V9_5) — "
                            + "sem ela nao ha aceite real para conferir.",
                    java.util.List.of(), java.util.List.of());
        }
        String contentHash = SignatureHashes.sha256Hex(context.content());
        AcceptanceEvidence evidence = evidencePort
                .findAcceptance(context.tenantId(), context.signerUserId(),
                        context.acceptanceDocumentCode(), context.acceptanceDocumentVersion(), contentHash)
                .orElseThrow(() -> new ApiException.Validation(
                        "Assinatura simples exige aceite registrado no document_acceptance da P1.24 "
                                + "para este usuario, este documento (codigo/versao) e este texto "
                                + "(hash do texto): aceite nao encontrado.",
                        java.util.List.of(), java.util.List.of()));
        if (!context.signerUserId().equals(evidence.userId())
                || !context.acceptanceDocumentCode().equals(evidence.documentCode())
                || !context.acceptanceDocumentVersion().equals(evidence.documentVersion())
                || !contentHash.equals(evidence.contentHash())) {
            throw new ApiException.Validation(
                    "Aceite da P1.24 devolvido nao e o da chave pedida ou nao cobre este texto "
                            + "(usuario, documento ou hash divergente): o aceite nao cobre este documento.",
                    java.util.List.of(), java.util.List.of());
        }
        if (evidence.acceptedAt() == null || evidence.ipAddress() == null || evidence.ipAddress().isBlank()
                || evidence.authMethod() == null || evidence.authMethod().isBlank()) {
            throw new ApiException.Validation(
                    "Evidencia de aceite incompleta (data, IP e autenticacao sao obrigatorios na assinatura simples).",
                    java.util.List.of(), java.util.List.of());
        }
        Instant now = SignatureVerification.canonical(clock.instant());
        String code = VerificationCodes.requireShape(verificationCodes.get());
        Map<String, Object> evidenceJson = new LinkedHashMap<>();
        evidenceJson.put("source", "platform.document_acceptance (P1.24)");
        evidenceJson.put("userId", evidence.userId().toString());
        evidenceJson.put("documentCode", evidence.documentCode());
        evidenceJson.put("documentVersion", evidence.documentVersion());
        evidenceJson.put("contentHash", evidence.contentHash());
        evidenceJson.put("acceptedAt", evidence.acceptedAt().toString());
        evidenceJson.put("ipAddress", evidence.ipAddress());
        evidenceJson.put("authMethod", evidence.authMethod());
        return new SignatureRecord(ids.get(), context.tenantId(), context.documentType(),
                context.documentReference(), SignatureLevel.SIMPLE, contentHash,
                context.signerSubject(), now, code,
                SignatureVerification.digestOf(contentHash, code, now),
                null, null, null, null, null, Json.write(evidenceJson));
    }
}
