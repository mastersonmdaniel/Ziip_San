package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.FieldOsPorts;
import br.com.ziipsan.core.fieldos.GpsPanel;
import br.com.ziipsan.core.fieldos.Tracking;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Painel de acompanhamento da trilha (ED-20): monta o DADO do painel que a
 * chefia consulta — trilha persistida + resumo (deslocamento, tempo,
 * paradas) por trabalhador — a partir da {@code TrackStore}.
 *
 * <p>O gate de acesso e o do PROPRIO nucleo, sem segundo gate inventado:
 * {@link Tracking#requireSupervisorAccess} corre antes de qualquer leitura
 * (papel que nao e chefia nao le trilha nenhuma) e {@code GpsPanel.build}
 * volta a exigi-lo ao montar o painel. Os limiares do resumo (raio/duracao
 * de parada) vem do contexto de cada trabalhador
 * ({@link FieldTrackContext}) — nenhum limiar e presumido aqui.
 *
 * <p>Esta frente entrega o MECANISMO e o DADO; a tela de acompanhamento ao
 * vivo e a pendencia P-MOB-GPS-06 (portao da decisao 57 do dono).
 */
@Service
public class FieldTrackPanelService {

    private final FieldTrackContextProvider contexts;
    private final FieldOsPorts.TrackStore trackStore;

    public FieldTrackPanelService(FieldTrackContextProvider contexts,
                                  FieldOsPorts.TrackStore trackStore) {
        this.contexts = contexts;
        this.trackStore = trackStore;
    }

    public GpsPanel.Panel panel(UUID tenantId, Instant from, Instant to,
                                FieldTrackPanelContext panelContext) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(from, "inicio da janela do painel e obrigatorio");
        Objects.requireNonNull(to, "fim da janela do painel e obrigatorio");
        Objects.requireNonNull(panelContext, "contexto da consulta ao painel e obrigatorio");
        Tracking.requireSupervisorAccess(panelContext.requesterRole());

        List<GpsPanel.PanelEntry> entries = new ArrayList<>();
        for (UUID workerId : panelContext.workerIds()) {
            List<Tracking.TrackPoint> track = trackStore.points(tenantId, workerId, from, to);
            FieldTrackContext context = contexts.contextFor(tenantId, workerId);
            Tracking.Summary summary = Tracking.summarize(track,
                    context.stopRadiusMeters(), context.stopMinDuration());
            UUID teamIdOrNull = track.isEmpty() ? null : track.get(0).teamIdOrNull();
            entries.add(new GpsPanel.PanelEntry(workerId, teamIdOrNull, track, summary));
        }
        return GpsPanel.build(tenantId, from, to, panelContext.requesterRole(), entries);
    }
}
