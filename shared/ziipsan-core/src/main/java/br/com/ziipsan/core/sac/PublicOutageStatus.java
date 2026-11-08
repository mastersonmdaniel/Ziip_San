package br.com.ziipsan.core.sac;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta publica, SEM LOGIN, da situacao do abastecimento (ED-35), por
 * endereco/bairro (texto da area) ou inscricao (unidade consumidora), com
 * interrupcoes ativas e programadas e a previsao de retorno vigente.
 * A visibilidade vem de {@code portal.outage_status_visibility}:
 * HIDDEN nao expoe nada; PUBLIC_SUMMARY expoe area/janela/previsao sem o
 * motivo; PUBLIC_FULL expoe tambem o motivo. A ED-38 expoe no portal.
 * A consulta filtra pelo PERIODO pedido e distingue interrupcao ATIVA de
 * PROGRAMADA; interrupcao ja encerrada nao aparece (correcao
 * pos-reprovacao, M2).
 */
public final class PublicOutageStatus {

    /** Fase da interrupcao em relacao ao instante da consulta. */
    public enum Phase { ACTIVE, SCHEDULED }

    public record StatusEntry(String locality, String sector, String route,
                              LocalDateTime start, LocalDateTime expectedReturn,
                              String reasonOrNull, Phase phase) {}

    private PublicOutageStatus() {}

    /**
     * @param periodStart inicio do periodo consultado (inclusivo)
     * @param periodEnd   fim do periodo consultado (exclusivo)
     * @param now         instante da consulta: define a fase e exclui encerradas
     */
    public static List<StatusEntry> query(List<PlannedOutage> outages,
                                          SacParameters.OutageVisibility visibility,
                                          String searchKey,
                                          LocalDateTime periodStart, LocalDateTime periodEnd,
                                          LocalDateTime now) {
        if (visibility == SacParameters.OutageVisibility.HIDDEN) return List.of();
        if (periodStart == null || periodEnd == null || now == null)
            throw new IllegalArgumentException("periodo e instante da consulta obrigatorios");
        if (!periodEnd.isAfter(periodStart))
            throw new IllegalArgumentException("fim do periodo deve ser posterior ao inicio");
        String key = searchKey == null ? "" : searchKey.trim().toLowerCase();
        return outages.stream()
                .filter(o -> !o.finishedOn(now))
                .filter(o -> o.scheduledStart().isBefore(periodEnd) && o.expectedReturn().isAfter(periodStart))
                .filter(o -> key.isEmpty() || matches(o, key))
                .map(o -> new StatusEntry(o.area().locality(), o.area().sector(), o.area().route(),
                        o.scheduledStart(), o.expectedReturn(),
                        visibility == SacParameters.OutageVisibility.PUBLIC_FULL ? o.reason() : null,
                        o.activeOn(now) ? Phase.ACTIVE : Phase.SCHEDULED))
                .toList();
    }

    private static boolean matches(PlannedOutage o, String key) {
        if (o.affectedUnitIds().stream().anyMatch(id -> id.toString().equalsIgnoreCase(key))) return true;
        String area = (o.area().locality() + " " + o.area().sector() + " "
                + o.area().route()).toLowerCase();
        return area.contains(key);
    }
}
