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
 * Assinatura QUALIFICADA (Lei 14.063/2020, art. 4o, III; MP 2.200-2/2001,
 * ICP-Brasil): certificado em nuvem de PSC credenciado
 * ({@code sign.psc_provider}, classe d), formato PAdES, com carimbo do tempo
 * de ACT credenciada pelo protocolo da RFC 3161
 * ({@code sign.timestamp_authority}, classe d).
 *
 * <p>HONESTIDADE DE ESCOPO: PSC e ACT sao servicos EXTERNOS credenciados.
 * Esta classe e o aparato (portas + recusas + registro da politica e do
 * carimbo); ela NAO chama PSC/ACT real — a homologacao com o provedor
 * escolhido pelo tenant e passo externo declarado no relatorio da tarefa.
 * A politica de assinatura aplicavel e a devolvida pelo PSC conforme a
 * tabela do DOC-ICP-15.03: o identificador da politica e REGISTRADO e a
 * assinatura sem politica declarada e recusada; nenhum OID de politica e
 * fixado neste codigo, porque a tabela do DOC-ICP-15.03 nao esta nas fontes
 * desta frente — fixa-lo seria inventar a politica.
 *
 * <p><b>Amarracoes do retorno ao pedido (correcao D9 da revisao cruzada)</b> —
 * o que ESTE pacote confere sobre o que as portas devolvem:
 * <ul>
 *   <li>o titular do certificado ({@code certificateSubject}) tem de ser o
 *       signatario do contexto — certificado de outro nao assina por ele;</li>
 *   <li>o PSC declara o hash do conteudo que efetivamente assinou
 *       ({@code signedContentHash}) e ele tem de ser o hash do conteudo
 *       apresentado — a conferencia criptografica de que o PAdES retornado
 *       incorpora esse conteudo e do adaptador homologado (pendencia 1 do
 *       relatorio), porque exige analisar o documento PAdES;</li>
 *   <li>o carimbo declara o hash carimbado ({@code stampedHash}, o
 *       messageImprint da RFC 3161) e ele tem de ser o hash do conteudo;</li>
 *   <li>a ACT do token tem de ser a configurada e o {@code generationTime}
 *       nao pode estar no futuro em relacao ao instante da assinatura.</li>
 * </ul>
 */
public final class QualifiedDocumentSigner implements DocumentSigner {

    /** Formato da assinatura qualificada exigido pela spec. */
    public static final String FORMAT = "PAdES";

    /**
     * Assinatura produzida pelo PSC (certificado em nuvem).
     * {@code signedContentHash} e o hash do conteudo que o PSC declara ter
     * assinado (D9): o adaptador real o extrai/confere no documento PAdES
     * na homologacao; aqui ele e confrontado com o hash do pedido.
     */
    public record PscSignature(String signaturePolicyId, String certificateSubject,
                               String signedContentHash, byte[] signedDocument) {

        public PscSignature {
            if (signaturePolicyId == null || signaturePolicyId.isBlank()) {
                throw new IllegalArgumentException(
                        "politica de assinatura (DOC-ICP-15.03) declarada pelo PSC e obrigatoria");
            }
            if (certificateSubject == null || certificateSubject.isBlank()) {
                throw new IllegalArgumentException("titular do certificado do PSC e obrigatorio");
            }
            if (!SignatureHashes.isSha256Hex(signedContentHash)) {
                throw new IllegalArgumentException(
                        "hash do conteudo assinado declarado pelo PSC deve ser SHA-256 hexadecimal");
            }
            if (signedDocument == null || signedDocument.length == 0) {
                throw new IllegalArgumentException("documento assinado (PAdES) do PSC e obrigatorio");
            }
            signedDocument = signedDocument.clone();
        }

        @Override
        public byte[] signedDocument() {
            return signedDocument.clone();
        }
    }

    /** Porta do PSC credenciado (certificado em nuvem) — integracao externa. */
    public interface PscCloudSigner {
        PscSignature sign(SignContext context, String contentHash, String pscProvider);
    }

    /**
     * Carimbo do tempo devolvido pela ACT (RFC 3161). {@code stampedHash} e o
     * messageImprint do token: o hash sobre o qual o carimbo foi emitido (D9).
     */
    public record TimestampToken(String authority, Instant generationTime,
                                 String stampedHash, String tokenHash) {

        public TimestampToken {
            if (authority == null || authority.isBlank()) {
                throw new IllegalArgumentException("ACT do carimbo do tempo e obrigatoria");
            }
            if (generationTime == null) {
                throw new IllegalArgumentException("instante do carimbo do tempo e obrigatorio");
            }
            if (!SignatureHashes.isSha256Hex(stampedHash)) {
                throw new IllegalArgumentException(
                        "hash carimbado (messageImprint RFC 3161) deve ser SHA-256 hexadecimal");
            }
            if (!SignatureHashes.isSha256Hex(tokenHash)) {
                throw new IllegalArgumentException("hash do token RFC 3161 deve ser SHA-256 hexadecimal");
            }
        }
    }

    /** Porta da ACT credenciada — requisicao de carimbo pelo protocolo da RFC 3161. */
    public interface TimestampProvider {
        TimestampToken stamp(String contentHash, String authority);
    }

    private final SigningParameters parameters;
    private final PscCloudSigner psc;
    private final TimestampProvider timestamps;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final Supplier<String> verificationCodes;

    public QualifiedDocumentSigner(SigningParameters parameters, PscCloudSigner psc,
                                   TimestampProvider timestamps, Clock clock,
                                   Supplier<UUID> ids, Supplier<String> verificationCodes) {
        this.parameters = Objects.requireNonNull(parameters, "parameters e obrigatorio");
        this.psc = Objects.requireNonNull(psc, "psc e obrigatorio");
        this.timestamps = Objects.requireNonNull(timestamps, "timestamps e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio");
        this.ids = Objects.requireNonNull(ids, "ids e obrigatorio");
        this.verificationCodes = Objects.requireNonNull(verificationCodes, "verificationCodes e obrigatorio");
    }

    @Override
    public SignatureLevel level() {
        return SignatureLevel.QUALIFIED;
    }

    @Override
    public SignatureRecord sign(SignContext context) {
        String pscProvider = parameters.pscProvider(context.tenantId()).orElseThrow(() ->
                new ApiException.Validation("Assinatura qualificada exige o PSC credenciado do tenant "
                        + "(sign.psc_provider, classe d): parametro nao registrado.",
                        java.util.List.of(), java.util.List.of()));
        String authority = parameters.timestampAuthority(context.tenantId()).orElseThrow(() ->
                new ApiException.Validation("Assinatura qualificada exige a ACT credenciada do tenant "
                        + "(sign.timestamp_authority, classe d): parametro nao registrado.",
                        java.util.List.of(), java.util.List.of()));
        String contentHash = SignatureHashes.sha256Hex(context.content());
        Instant now = SignatureVerification.canonical(clock.instant());
        PscSignature pscSignature = Objects.requireNonNull(psc.sign(context, contentHash, pscProvider),
                "PSC nao devolveu assinatura");
        // Amarracoes D9: certificado do signatario e conteudo efetivamente assinado.
        if (!context.signerSubject().equals(pscSignature.certificateSubject())) {
            throw new ApiException.Validation("Certificado do PSC nao e do signatario do pedido "
                    + "(certificateSubject divergente): recusado.", java.util.List.of(), java.util.List.of());
        }
        if (!contentHash.equals(pscSignature.signedContentHash())) {
            throw new ApiException.Validation("Documento devolvido pelo PSC nao corresponde ao conteudo "
                    + "apresentado (hash do conteudo assinado divergente): recusado.",
                    java.util.List.of(), java.util.List.of());
        }
        TimestampToken token = Objects.requireNonNull(timestamps.stamp(contentHash, authority),
                "ACT nao devolveu carimbo do tempo");
        if (!authority.equals(token.authority())) {
            throw new ApiException.Validation("Carimbo do tempo de ACT diferente da configurada ("
                    + "sign.timestamp_authority): recusado.", java.util.List.of(), java.util.List.of());
        }
        if (!contentHash.equals(token.stampedHash())) {
            throw new ApiException.Validation("Carimbo do tempo nao amarra este conteudo "
                    + "(messageImprint divergente do hash apresentado): recusado.",
                    java.util.List.of(), java.util.List.of());
        }
        if (token.generationTime().isAfter(now)) {
            throw new ApiException.Validation("Carimbo do tempo com instante no futuro em relacao "
                    + "a assinatura: recusado.", java.util.List.of(), java.util.List.of());
        }
        String code = VerificationCodes.requireShape(verificationCodes.get());
        Map<String, Object> evidenceJson = new LinkedHashMap<>();
        evidenceJson.put("format", FORMAT);
        evidenceJson.put("pscProvider", pscProvider);
        evidenceJson.put("signaturePolicyId", pscSignature.signaturePolicyId());
        evidenceJson.put("certificateSubject", pscSignature.certificateSubject());
        evidenceJson.put("signedContentHash", pscSignature.signedContentHash());
        evidenceJson.put("signedDocumentHash", SignatureHashes.sha256Hex(pscSignature.signedDocument()));
        evidenceJson.put("timestampAuthority", token.authority());
        evidenceJson.put("timestampGenerationTime", token.generationTime().toString());
        evidenceJson.put("timestampStampedHash", token.stampedHash());
        evidenceJson.put("timestampTokenHash", token.tokenHash());
        return new SignatureRecord(ids.get(), context.tenantId(), context.documentType(),
                context.documentReference(), SignatureLevel.QUALIFIED, contentHash,
                context.signerSubject(), now, code,
                SignatureVerification.digestOf(contentHash, code, now),
                FORMAT, pscProvider, pscSignature.signaturePolicyId(),
                token.authority(), token.tokenHash(), Json.write(evidenceJson));
    }
}
