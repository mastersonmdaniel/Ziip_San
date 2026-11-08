package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.cutoff.CutoffCalendar;
import br.com.ziipsan.core.cutoff.CutoffEnums;
import br.com.ziipsan.core.cutoff.CutoffExecutionRule;
import br.com.ziipsan.core.cutoff.CutoffNoticeRule;
import br.com.ziipsan.core.cutoff.CutoffParameters;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Portao de GERACAO da OS de corte da ED-29 (TR-415/SG-398: dias e
 * horarios de geracao das ordens de 1o, 2o e 3o nivel SO dentro das
 * vedacoes da Lei 14.015/2020, travadas no produto).
 *
 * <p>Duas camadas, nesta ordem:
 * <ol>
 *   <li><b>Calendario do nivel</b> ({@code cut.level_schedule} do
 *       tenant): fora dos dias/faixa declarados para o nivel, a geracao
 *       recusa com {@code cutoff-generation-outside-level-window}.</li>
 *   <li><b>Regras e bloqueios da P3.4</b>, sem afrouxamento: o portao
 *       delega integralmente a {@link CutoffExecutionRule#assertAllowed}
 *       — evidencia de emergencia, classes protegidas, esgoto
 *       ({@code cut.sewer_allowed}), vedacoes temporais federais
 *       (Lei 14.015/2020) e de SC (Lei 11.959/2001 c/ 17.780/2019) e o
 *       portao do aviso (prova de entrega, prazo decorrido, validade)
 *       para o motivo de inadimplencia. Fora da janela legal, a recusa
 *       e o {@code cutoff-temporal-ban} da P3.4.</li>
 * </ol>
 */
public final class CutoffGenerationGate {

    private CutoffGenerationGate() {
    }

    /** Tudo o que o portao precisa; o aviso e o da P3.4 (obrigatorio no motivo DEFAULT). */
    public record GenerationInput(
            CutoffLevel level,
            CutoffEnums.Reason reason,
            boolean sewer,
            ZonedDateTime when,
            CutoffCalendar.CalendarData calendar,
            boolean judicialOrder,
            String emergencyJustification,
            UUID emergencyPhotoDocumentId,
            Set<CutoffEnums.ProtectedClass> unitProtectedClasses,
            CutoffNoticeRule.Notice notice,
            CutoffParameters.Values cutoffParams) {
        public GenerationInput {
            if (level == null || reason == null || when == null || calendar == null || cutoffParams == null) {
                throw new IllegalArgumentException(
                        "geracao exige nivel, motivo, instante, calendario e parametros da P3.4");
            }
            unitProtectedClasses = unitProtectedClasses == null ? Set.of() : Set.copyOf(unitProtectedClasses);
        }
    }

    /**
     * Recusa a geracao fora da janela do nivel ou por qualquer bloqueio
     * da P3.4; nao devolve nada quando a geracao e permitida.
     */
    public static void assertGenerationAllowed(GenerationInput input, CutoffExtParameters extParams) {
        if (input == null || extParams == null) {
            throw new IllegalArgumentException("geracao e parametros da ED-29 sao obrigatorios");
        }
        CutoffExtParameters.LevelSchedule schedule = extParams.levelSchedule(input.level());
        if (!schedule.contains(input.when().toLocalDateTime())) {
            throw new ApiException.Conflict("cutoff-generation-outside-level-window",
                    "Geracao de OS de corte do nivel " + input.level() + " fora dos dias/horarios"
                            + " declarados em " + CutoffExtParameters.LEVEL_SCHEDULE
                            + " (TR-415/SG-398).");
        }
        CutoffExecutionRule.assertAllowed(new CutoffExecutionRule.GateInput(
                input.reason(), input.sewer(), input.when(), input.calendar(), input.judicialOrder(),
                input.emergencyJustification(), input.emergencyPhotoDocumentId(),
                input.unitProtectedClasses(), input.notice(), input.cutoffParams()));
    }
}
