package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.IsolationEngine.IsolationResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.NoticeRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Área afetada da interrupção programada da ED-35 CALCULADA pelo
 * isolamento (GIS-11): a ED-35 cadastra a interrupção por área
 * textual (localidade/setor/rota — "SEM mapa; área calculada pelo
 * GIS é da ED-54", no javadoc do {@code PlannedOutage} dela); esta
 * classe devolve, para o trecho da interrupção, as unidades afetadas
 * e, dentre elas, as ESSENCIAIS da P1.3 — exatamente os dois
 * conjuntos que o construtor do {@code PlannedOutage} da ED-35
 * exige e valida (essencial fora da área = recusa lá e aqui, por
 * construção).
 *
 * <p>O aviso aos afetados sai pela ED-41: {@link #toNoticeRequest}
 * monta o mesmo {@link NoticeRequest} do plano de manobra — nenhum
 * segundo canal de aviso nasce nesta entrega.
 */
public final class PlannedOutageAreaService {

    /** Unidade essencial afetada, nos tipos da P1.3/ED-35. */
    public record EssentialAffected(UUID consumerUnitId, String essentialKind) {
    }

    public record AffectedArea(boolean calculable, String failure,
                               List<UUID> affectedUnitIds,
                               List<EssentialAffected> essentialUnits,
                               List<UUID> sensitiveUnitIds, int totalEconomies,
                               IsolationResult isolation) {
        public AffectedArea {
            affectedUnitIds = List.copyOf(affectedUnitIds);
            essentialUnits = List.copyOf(essentialUnits);
            sensitiveUnitIds = List.copyOf(sensitiveUnitIds);
        }
    }

    private PlannedOutageAreaService() {
    }

    public static AffectedArea compute(NetworkSnapshot snapshot, Target target) {
        IsolationResult iso = IsolationEngine.isolate(snapshot, target);
        if (!iso.isolable()) {
            return new AffectedArea(false, String.valueOf(iso.failure()),
                    List.of(), List.of(), List.of(), 0, iso);
        }
        List<EssentialAffected> essential = iso.essentialConnections().stream()
                .flatMap(c -> c.consumerUnitIds().stream()
                        .map(u -> new EssentialAffected(u, c.essentialKind())))
                .distinct().toList();
        List<UUID> sensitive = iso.affectedConnections().stream()
                .filter(c -> c.sensitive())
                .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList();
        return new AffectedArea(true, null, iso.affectedUnitIds(), essential, sensitive,
                iso.totalEconomies(), iso);
    }

    /** Aviso da interrupção programada pela ED-41, sobre a área calculada. */
    public static NoticeRequest toNoticeRequest(UUID tenantId, UUID outageId, AffectedArea area,
                                                LocalDateTime scheduledStart, LocalDateTime expectedReturn,
                                                String reason) {
        if (!area.calculable())
            throw new IllegalArgumentException("interrupcao sem area calculavel: " + area.failure());
        return new NoticeRequest(tenantId, outageId, ManeuverPlanService.NOTICE_EVENT_CODE,
                area.affectedUnitIds(),
                area.essentialUnits().stream().map(EssentialAffected::consumerUnitId).toList(),
                scheduledStart, expectedReturn, reason);
    }
}
