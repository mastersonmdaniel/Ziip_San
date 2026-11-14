package br.com.ziipsan.commercial.fiscal.relief;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Guarda do prazo de cancelamento da NFAg (modelo 75) — PA-02, item (a) da §9.
 *
 * <p><b>Fonte primaria (P-02-16 CONFIRMADA com correccao de fonte)</b>: MOC da
 * NFAg, ENCAT/SEFAZ Virtual RS, Visao Geral, item 6 — Evento de Cancelamento
 * (tipo de evento 110111, autor: o emissor), tabela "Validacao das Regras
 * Especificas do Evento", <b>regra L11</b> (rejeicao 220): "Verificar se a
 * NFAg foi autorizada ate 120 horas do mes anterior — Rejeicao: Vedado
 * cancelamento de NFAg com data/hora de autorizacao anterior ao prazo
 * permitido". Observacao da fonte: "Na comparacao dos horarios acima, aceitar
 * uma tolerancia de 5 minutos". O Fisco pode liberar o cancelamento fora de
 * prazo pelo evento de Manifestacao do Fisco "Liberacao do Prazo de
 * Cancelamento" — aqui modelado como excepcao EXPLICITA (referencia do
 * evento obrigatoria), nunca como bypass silencioso. Base delegante:
 * LC 214/2025, art. 60, §3º (forma/conteudo/prazos dos DF-e por ato conjunto
 * CGIBS+RFB).</p>
 *
 * <p><b>A citacao legislativa originalmente indicada pelo comparativo para
 * esta regra esta REFUTADA e foi removida de todo o pacote</b> — os artigos
 * citados nao contem "120 horas" nem "NFAg" (verificacao da sessao 35b3c739,
 * arquivada em {@code fontes/MOC-NFAg_cancelamento-L11_e_emissao-G32.txt}
 * e registada no ADENDO-PA01-AO-DESENHO).</p>
 *
 * <p><b>Formulacao implementada, estritamente como a fonte</b>: o prazo e de
 * 120 horas referenciado ao mes anterior — leitura pratica corroborada pela
 * familia DFe (NFCom, Ajuste SINIEF 07/22, clausula 15ª; NFGas, Ajuste SINIEF
 * 38/2025): <b>120 horas apos o ultimo dia do mes da autorizacao</b>. As
 * horas sao de relogio: o MOC NAO as qualifica (nem corridas nem uteis) e
 * esta guarda nao as qualifica. Exclusoes da mesma tabela de regras do
 * evento: NFAg ja cancelada (L05, rejeicao 218), NFAg ja substituida (L06,
 * rejeicao 224) e NFAg de substituicao (L07, rejeicao 524) NAO se cancelam.
 * Substituicao nao tem prazo proprio em horas no MOC (Anexo I, seccao 2.4,
 * usa a janela de cancelamento como referencia).</p>
 *
 * <p><b>Versoes</b>: regra lida na Visao Geral v1.00g (04/12/2025) e
 * RECONFIRMADA com texto identico na Visao Geral v1.00k (11/03/2026) e na
 * v1.00l (agosto/2026, versao mais recente localizada em 14/11/2026).</p>
 *
 * <p>Fora deste pacote (pendencia P-02-17): a regra G32 do Anexo I (emissao
 * com atraso &gt; 120h face a recepcao na SEFAZ, rejeicao 228, excepcao de
 * contingencia off-line cStat 150) — regra de emissao/recepcao, nao de
 * cancelamento, e nao confundir com timeout de contingencia (item (h):
 * nada entra).</p>
 */
public final class CancellationDeadlineGuard {

    public static final String PRIMARY_SOURCE =
            "MOC NFAg (modelo 75), ENCAT/SEFAZ Virtual RS, Visao Geral, Evento de Cancelamento"
                    + " (110111), regra L11 (rejeicao 220); base delegante LC 214/2025 art. 60, §3º";
    public static final String CONFIRMED_VERSIONS =
            "Visao Geral v1.00g (04/12/2025); reconfirmada identica em v1.00k (11/03/2026)"
                    + " e v1.00l (agosto/2026)";
    public static final int DEADLINE_HOURS = 120;
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    public enum Verdict {
        ALLOWED,
        /** Fora do prazo, liberado pelo evento de Manifestacao do Fisco. */
        ALLOWED_BY_FISCO_RELEASE,
        REJECTED_218_ALREADY_CANCELLED,
        REJECTED_220_OUT_OF_DEADLINE,
        REJECTED_224_ALREADY_SUBSTITUTED,
        REJECTED_524_SUBSTITUTION_DOCUMENT
    }

    /**
     * @param authorizationAt       data/hora de AUTORIZACAO da NFAg (marco da regra L11)
     * @param documentZone          fuso em que o mes da autorizacao se le (do documento/emitente)
     * @param cancellationAt        instante do pedido de cancelamento
     * @param alreadyCancelled      L05
     * @param alreadySubstituted    L06
     * @param substitutionDocument  L07 — a propria NFAg e de substituicao
     * @param fiscoReleaseEventRef  referencia do evento de Manifestacao do Fisco
     *                              "Liberacao do Prazo de Cancelamento" (null se nao houver)
     */
    public record CancellationRequest(
            Instant authorizationAt,
            ZoneId documentZone,
            Instant cancellationAt,
            boolean alreadyCancelled,
            boolean alreadySubstituted,
            boolean substitutionDocument,
            String fiscoReleaseEventRef) {
        public CancellationRequest {
            if (authorizationAt == null || documentZone == null || cancellationAt == null) {
                throw new IllegalArgumentException(
                        "autorizacao, fuso do documento e instante do cancelamento sao obrigatorios");
            }
        }
    }

    /** Limite L11: ultimo dia do mes da autorizacao (fim do dia, no fuso do documento) + 120h. */
    public Instant deadlineFor(Instant authorizationAt, ZoneId documentZone) {
        LocalDateTime authLocal = LocalDateTime.ofInstant(authorizationAt, documentZone);
        LocalDateTime endOfMonth = YearMonth.from(authLocal).atEndOfMonth()
                .atTime(23, 59, 59);
        return endOfMonth.atZone(documentZone).toInstant().plus(DEADLINE_HOURS, ChronoUnit.HOURS);
    }

    public Verdict evaluate(CancellationRequest request) {
        if (request.alreadyCancelled()) {
            return Verdict.REJECTED_218_ALREADY_CANCELLED;
        }
        if (request.alreadySubstituted()) {
            return Verdict.REJECTED_224_ALREADY_SUBSTITUTED;
        }
        if (request.substitutionDocument()) {
            return Verdict.REJECTED_524_SUBSTITUTION_DOCUMENT;
        }
        Instant limit = deadlineFor(request.authorizationAt(), request.documentZone())
                .plus(TOLERANCE); // tolerancia de 5 minutos da fonte
        if (!request.cancellationAt().isAfter(limit)) {
            return Verdict.ALLOWED;
        }
        // Fora do prazo: so com liberacao do Fisco, explicita e referenciada.
        if (request.fiscoReleaseEventRef() != null && !request.fiscoReleaseEventRef().isBlank()) {
            return Verdict.ALLOWED_BY_FISCO_RELEASE;
        }
        return Verdict.REJECTED_220_OUT_OF_DEADLINE;
    }
}
