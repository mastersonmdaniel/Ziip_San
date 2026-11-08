package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.core.cutoff.CutoffCalendar;
import br.com.ziipsan.core.cutoff.CutoffEnums;
import br.com.ziipsan.core.cutoff.CutoffExecutionRule;
import br.com.ziipsan.core.cutoff.CutoffNoticeRule;
import br.com.ziipsan.core.cutoff.CutoffParameters;
import br.com.ziipsan.core.cutoff.ReconnectionRule;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Corte e religacao de ESGOTO (SG-503 e SG-504, 18e-E9; 18f-ENT-04),
 * sobre as regras da P3.4:
 *
 * <ul>
 *   <li><b>Corte de esgoto:</b> so com {@code cut.sewer_allowed} aceito
 *       para o tenant (padrao de fabrica da P3.4: NAO — Lei 11.445/2007,
 *       art. 40, V). A recusa e a do proprio portao da P3.4
 *       ({@code cutoff-sewer-not-allowed}), chamado com
 *       {@code sewer = true}: nenhuma regra nova e criada aqui.</li>
 *   <li><b>Religacao de esgoto:</b> exige a CARTA DE LIBERACAO DE
 *       ESGOTO emitida pelo motor de modelos da P2.30 (comunicado de
 *       esgoto de texto parametrizado, TR-581/SG-558; codigo do tenant
 *       em {@code cut.sewer_release_template}) E a condicao de
 *       liberacao da P3.4 ({@link ReconnectionRule}: condicao
 *       {@code rec.release_condition}, taxa — sem taxa quando o aviso
 *       nao teve prova ou o corte foi indevido — e SLA de
 *       {@code rec.sla_hours}). Sem carta, nao ha OS de religacao de
 *       esgoto.</li>
 * </ul>
 */
public final class SewerCutoff {

    private SewerCutoff() {
    }

    /**
     * Portao do corte de esgoto: delega ao portao de execucao da P3.4
     * com {@code sewer = true} (vedacoes temporais, classes protegidas
     * e {@code cut.sewer_allowed} sao os da P3.4). O AVISO da P3.4 e
     * recebido e propagado sem alteracao ao {@code GateInput} — no
     * motivo {@code DEFAULT} (inadimplencia) ele e obrigatorio e o
     * portao da P3.4 recusa a sua ausencia; a decisao continua 100%
     * do {@link CutoffExecutionRule} da P3.4, nenhuma regra e
     * reimplementada aqui (correcao D1 da revisao cruzada da ED-29).
     */
    public static void assertSewerCutAllowed(CutoffEnums.Reason reason, ZonedDateTime when,
                                             CutoffCalendar.CalendarData calendar, boolean judicialOrder,
                                             String emergencyJustification, UUID emergencyPhotoDocumentId,
                                             Set<CutoffEnums.ProtectedClass> unitProtectedClasses,
                                             CutoffNoticeRule.Notice notice,
                                             CutoffParameters.Values cutoffParams) {
        CutoffExecutionRule.assertAllowed(new CutoffExecutionRule.GateInput(
                reason, true, when, calendar, judicialOrder, emergencyJustification,
                emergencyPhotoDocumentId, unitProtectedClasses, notice, cutoffParams));
    }

    /** Carta de liberacao de esgoto emitida (modelo/versao e referencia do documento da P2.30). */
    public record SewerReleaseLetter(UUID unitId, String templateCode, String templateVersion,
                                     String documentRef) {
        public SewerReleaseLetter {
            if (unitId == null || templateCode == null || templateVersion == null || documentRef == null) {
                throw new IllegalArgumentException(
                        "carta de liberacao de esgoto exige unidade, modelo/versao e referencia do documento");
            }
        }
    }

    /** Emite a carta de liberacao de esgoto pelo motor de modelos da P2.30. */
    public static SewerReleaseLetter issueReleaseLetter(UUID tenantId, UUID unitId,
                                                        Map<String, Object> variables,
                                                        DunningPorts.TemplatePort templates,
                                                        CutoffExtParameters extParams) {
        if (tenantId == null || unitId == null || templates == null || extParams == null) {
            throw new IllegalArgumentException("tenant, unidade, motor de modelos e parametros sao obrigatorios");
        }
        var rendered = templates.render(tenantId, extParams.sewerReleaseTemplate(),
                variables == null ? Map.of() : variables);
        return new SewerReleaseLetter(unitId, rendered.templateCode(), rendered.templateVersion(),
                rendered.documentRef());
    }

    /**
     * Decisao da religacao de esgoto: carta de liberacao presente E
     * condicao de liberacao da P3.4 satisfeita. A decisao de taxa e o
     * SLA sao os de {@link ReconnectionRule#evaluate} — esta classe nao
     * duplica a regra.
     */
    public static ReconnectionRule.Decision evaluateSewerReconnection(
            SewerReleaseLetter releaseLetter, ReconnectionRule.PaymentFacts facts,
            CutoffParameters.Values cutoffParams, OffsetDateTime paymentAt) {
        if (cutoffParams == null) {
            // Erro explicito de integracao (correcao D4 da revisao cruzada):
            // sem os parametros da P3.4 nao ha decisao honesta nem mesmo
            // para o bloqueio por falta de carta — a condicao aplicada
            // sairia nula e esconderia o erro do chamador.
            throw new IllegalArgumentException("parametros da P3.4 sao obrigatorios para avaliar a religacao de esgoto");
        }
        if (releaseLetter == null) {
            return new ReconnectionRule.Decision(false, "SEWER_RELEASE_LETTER_REQUIRED",
                    cutoffParams.releaseCondition(), null, null);
        }
        return ReconnectionRule.evaluate(facts, cutoffParams, paymentAt);
    }
}
