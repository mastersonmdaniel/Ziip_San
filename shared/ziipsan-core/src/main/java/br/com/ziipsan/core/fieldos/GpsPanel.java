package br.com.ziipsan.core.fieldos;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Painel de acompanhamento de campo (ED-20) sobre o mapa do nucleo GIS da
 * P3.25: esta entrega produz o DADO do painel (trilha + resumo de
 * deslocamento, tempo e paradas, por leiturista/equipe) no formato que a
 * camada de publicacao da P3.25 consome — a tela/mapa em si e da P3.25 e
 * nao e duplicada aqui. O acesso ao painel passa pelo mesmo portao da
 * trilha: so chefia ({@link Tracking#requireSupervisorAccess}).
 */
public final class GpsPanel {

    public record PanelEntry(UUID workerId, UUID teamIdOrNull, List<Tracking.TrackPoint> track,
                             Tracking.Summary summary) {
        public PanelEntry {
            Objects.requireNonNull(workerId, "trabalhador e obrigatorio");
            track = track == null ? List.of() : List.copyOf(track);
            Objects.requireNonNull(summary, "resumo e obrigatorio");
        }
    }

    public record Panel(UUID tenantId, Instant from, Instant to, List<PanelEntry> entries) {
        public Panel { entries = entries == null ? List.of() : List.copyOf(entries); }
    }

    private GpsPanel() {}

    public static Panel build(UUID tenantId, Instant from, Instant to, Tracking.AccessRole requesterRole,
                              List<PanelEntry> entries) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(from, "inicio da janela do painel e obrigatorio");
        Objects.requireNonNull(to, "fim da janela do painel e obrigatorio");
        Tracking.requireSupervisorAccess(requesterRole);
        return new Panel(tenantId, from, to, entries);
    }
}
