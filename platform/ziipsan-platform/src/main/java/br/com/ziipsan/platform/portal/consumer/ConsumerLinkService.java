package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.platform.portal.LinkingCode;
import br.com.ziipsan.security.crypto.FieldProtector;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Vinculo da unidade a conta do portal (P3.20): codigo de vinculacao da
 * conta (o da fatura, P3.3/{@link LinkingCode}) MAIS o CPF ou CNPJ do
 * titular, comparado pelo INDICE CEGO — o documento em claro nunca e
 * consultado nem gravado.
 *
 * <p>Anti-enumeracao: matricula inexistente, codigo errado e documento
 * errado recebem EXATAMENTE a mesma resposta ({@link #UNIFORM_FAILURE}).
 * Toda tentativa (sucesso ou falha) e registrada pela porta, e o limite
 * por unidade/IP/tenant e aplicado ANTES de qualquer conferencia
 * ({@link LinkAttemptLimiter}).
 *
 * <p>Canais laterais fechados (correcao D2 da revisao independente):
 * (a) TEMPO — os dois fatores (documento e codigo) sao SEMPRE computados
 * pelos indices cegos, sem curto-circuito entre eles e mesmo quando a
 * matricula nao existe (contra uma unidade ficticia de indices nulos) ou
 * quando a tentativa ja esta bloqueada: o custo de conferencia nao
 * distingue os caminhos de falha. (b) LIMITADOR — o 429 so sai das
 * dimensoes do SOLICITANTE (IP/tenant), cujas contagens nao dependem da
 * existencia da matricula; o bloqueio so pela dimensao da unidade e
 * absorvido na mesma resposta uniforme de falha, para nao virar oraculo
 * de existencia apos martelamento dirigido a uma unidade real.
 *
 * <p>Uma conta do portal pode vincular VARIAS unidades; o vinculo e por
 * unidade e morre na troca de titularidade (ver {@link HolderChangeService}).
 */
public final class ConsumerLinkService {

    public static final String UNIFORM_FAILURE =
            "Nao foi possivel concluir o vinculo. Confira o codigo da fatura e o documento do titular.";

    /** Unidade localizada pela matricula: indice cego do documento do titular e do codigo vigente. */
    public record UnitForLink(UUID unitId, String holderDocumentBlindIndex, String linkingCodeBlindIndex) { }

    public interface UnitLookupPort {
        Optional<UnitForLink> findByRegistration(UUID tenantId, String registration);
    }

    public interface LinkStorePort {
        boolean isAlreadyLinked(UUID tenantId, String consumerSubject, UUID unitId);
        /**
         * Grava o vinculo COM os indices cegos conferidos (correcao D3b):
         * {@code holderDocumentIndex} e {@code linkingCodeIndex} sao as
         * colunas {@code holder_document_index}/{@code linking_code_index}
         * de {@code consumer_portal_links} — sem eles a linha nasceria com
         * o indice do documento faltando (NOT NULL) e o do codigo orfao.
         */
        void saveLink(UUID tenantId, String consumerSubject, UUID unitId,
                      String holderDocumentIndex, String linkingCodeIndex, Instant linkedAt);
    }

    /** Registro de TODA tentativa + contagens da janela (ADR-0008). */
    public interface AttemptPort {
        int attemptsByUnit(UUID tenantId, UUID unitId);
        int attemptsByIp(UUID tenantId, String sourceIp);
        int attemptsByTenant(UUID tenantId);
        void recordAttempt(UUID tenantId, UUID unitId, String sourceIp, boolean success, boolean volumeAlert);
    }

    private final FieldProtector protector;
    private final UnitLookupPort units;
    private final LinkStorePort links;
    private final AttemptPort attempts;
    private final LinkAttemptLimiter.Limits limits;

    public ConsumerLinkService(FieldProtector protector, UnitLookupPort units, LinkStorePort links,
                               AttemptPort attempts, LinkAttemptLimiter.Limits limits) {
        if (protector == null || units == null || links == null || attempts == null || limits == null) {
            throw new IllegalArgumentException("dependencias do vinculo sao obrigatorias");
        }
        this.protector = protector;
        this.units = units;
        this.links = links;
        this.attempts = attempts;
        this.limits = limits;
    }

    /**
     * @return a unidade vinculada
     * @throws ApiException com {@link #UNIFORM_FAILURE} para matricula
     *         inexistente, codigo errado OU documento errado (indistintos)
     */
    public UUID link(UUID tenantId, String consumerSubject, String registration,
                     String linkingCode, String holderDocument, String sourceIp) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank()
                || registration == null || registration.isBlank() || sourceIp == null || sourceIp.isBlank()) {
            throw new IllegalArgumentException("tenant, consumidor, matricula e origem sao obrigatorios");
        }
        Optional<UnitForLink> found = units.findByRegistration(tenantId, registration.trim());
        UUID unitId = found.map(UnitForLink::unitId).orElse(null);

        int unitAttempts = unitId == null ? 0 : attempts.attemptsByUnit(tenantId, unitId);
        int ipAttempts = attempts.attemptsByIp(tenantId, sourceIp);
        int tenantAttempts = attempts.attemptsByTenant(tenantId);
        var verdict = LinkAttemptLimiter.evaluate(unitAttempts, ipAttempts, tenantAttempts, limits);

        // D2(a): os DOIS fatores sao sempre computados, sem curto-circuito
        // entre eles — contra a unidade real ou contra a ficticia (indices
        // nulos) quando a matricula nao existe, e mesmo com a tentativa ja
        // bloqueada. O resultado usa '&' (sem curto-circuito) de proposito.
        UnitForLink target = found.orElse(DUMMY_UNIT);
        boolean documentOk = documentMatches(tenantId, target, holderDocument);
        boolean codeOk = codeMatches(tenantId, target, linkingCode);
        boolean ok = found.isPresent() & documentOk & codeOk;

        if (verdict.decision() == LinkAttemptLimiter.Decision.BLOCKED) {
            attempts.recordAttempt(tenantId, unitId, sourceIp, false, true);
            // D2(b): 429 so quando o bloqueio vem das dimensoes do
            // solicitante (IP/tenant), iguais para matricula existente ou
            // nao. Bloqueio so pela unidade e absorvido na falha uniforme.
            var requesterVerdict = LinkAttemptLimiter.evaluate(0, ipAttempts, tenantAttempts, limits);
            if (requesterVerdict.decision() == LinkAttemptLimiter.Decision.BLOCKED) {
                LinkAttemptLimiter.enforce(verdict);
            }
            throw new ApiException.BadRequest("link-failed", UNIFORM_FAILURE);
        }

        attempts.recordAttempt(tenantId, unitId, sourceIp, ok, verdict.volumeAlert());
        if (!ok) {
            throw new ApiException.BadRequest("link-failed", UNIFORM_FAILURE);
        }
        if (!links.isAlreadyLinked(tenantId, consumerSubject, unitId)) {
            links.saveLink(tenantId, consumerSubject, unitId,
                    target.holderDocumentBlindIndex(), target.linkingCodeBlindIndex(), Instant.now());
        }
        return unitId;
    }

    /** Unidade ficticia dos caminhos sem matricula: indices nulos nunca casam, mas a computacao roda. */
    private static final UnitForLink DUMMY_UNIT = new UnitForLink(null, null, null);
    /** Candidato ficticio de documento (invalido como CPF/CNPJ): so alimenta o indice cego. */
    private static final String DUMMY_DOCUMENT = "00000000000";
    /** Candidato ficticio de codigo (fora do formato aceito): so alimenta o indice cego. */
    private static final String DUMMY_CODE = "dummy-code-000000000";

    private boolean documentMatches(UUID tenantId, UnitForLink unit, String holderDocument) {
        boolean valid = holderDocument != null
                && DocumentNumber.kind(holderDocument) != DocumentNumber.Kind.INVALID;
        // O indice cego e computado SEMPRE (D2a): documento invalido/ausente
        // entra com o candidato ficticio, nunca com retorno antecipado.
        String index = protector.indexOf(tenantId, HolderDocumentContext.CONTEXT,
                valid ? DocumentNumber.normalize(holderDocument) : DUMMY_DOCUMENT);
        return valid & constantTimeEquals(unit.holderDocumentBlindIndex(), index);
    }

    private boolean codeMatches(UUID tenantId, UnitForLink unit, String linkingCode) {
        boolean shaped = LinkingCode.hasCodeShape(linkingCode);
        // O indice cego e computado SEMPRE (D2a), como no documento.
        String index = protector.indexOf(tenantId, LinkingCode.CONTEXT,
                shaped ? linkingCode : DUMMY_CODE);
        return shaped & constantTimeEquals(unit.linkingCodeBlindIndex(), index);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
