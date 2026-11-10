package br.com.ziipsan.operations.metering.field.web;

import br.com.ziipsan.core.fieldos.GpsPanel;
import br.com.ziipsan.core.fieldos.Tracking;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.operations.metering.field.FieldTrackContextProvider;
import br.com.ziipsan.operations.metering.field.FieldTrackIngestionService;
import br.com.ziipsan.operations.metering.field.FieldTrackPanelContext;
import br.com.ziipsan.operations.metering.field.FieldTrackPanelService;
import br.com.ziipsan.operations.metering.field.FieldTrackReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Trilha GPS de campo (ED-20 / P-MOB-GPS-01), sob /api/v1/field como a
 * sincronizacao de leituras ({@code FieldSyncController}). Terminologia do
 * dono: servidor -&gt; aparelho e ENVIO; aparelho -&gt; servidor e
 * SINCRONIZACAO — a trilha e uma sincronizacao de pontos.
 *
 * <ul>
 *   <li>POST /track: o aparelho sincroniza o lote de pontos coletados na
 *       jornada; identificacao pelo cabecalho X-Device-Id e portao do
 *       aparelho ({@code FieldDeviceGate}, dentro do servico de ingestao,
 *       como no /sync). Um recibo por ponto: aceite, ou recusado com o
 *       motivo nomeado do nucleo (sem aviso / fora da jornada / trava).</li>
 *   <li>GET /track/panel?from=&amp;to=: a chefia consulta o DADO do painel
 *       (trilha + resumo por trabalhador). O papel do chamador e o ambito
 *       de trabalhadores chegam por entrada tipada do contexto
 *       ({@code FieldTrackContextProvider#panelContextFor}) — nenhuma
 *       fonte de papeis e inventada aqui; o gate de chefia e o do nucleo.</li>
 * </ul>
 * Os DTOs abaixo sao o espelho honesto dos tipos do nucleo
 * ({@code Tracking.TrackPoint}, {@code Tracking.Summary},
 * {@code GpsPanel.Panel}): mesmos campos, com os {@code Duration} do resumo
 * em texto ISO-8601 para o transporte.
 */
@RestController
@RequestMapping("/api/v1/field")
public class FieldTrackController {

    /** Espelho de transporte de {@code Tracking.TrackPoint}. */
    public record TrackPointDto(@NotNull UUID workerId, UUID teamIdOrNull,
                                @NotNull Instant at, double latitude, double longitude) {

        public Tracking.TrackPoint toTrackPoint() {
            return new Tracking.TrackPoint(workerId, teamIdOrNull, at, latitude, longitude);
        }
    }

    public record TrackBatchRequest(@NotNull List<@Valid TrackPointDto> points) {
    }

    /** Espelho de transporte de um ponto persistido da trilha. */
    public record TrackPointView(UUID workerId, UUID teamIdOrNull, Instant at,
                                 double latitude, double longitude) {

        static TrackPointView of(Tracking.TrackPoint point) {
            return new TrackPointView(point.workerId(), point.teamIdOrNull(), point.at(),
                    point.latitude(), point.longitude());
        }
    }

    /** Espelho de transporte de {@code Tracking.Stop}; duracao em ISO-8601. */
    public record StopView(Instant from, Instant to, String duration) {

        static StopView of(Tracking.Stop stop) {
            return new StopView(stop.from(), stop.to(), stop.duration().toString());
        }
    }

    /** Espelho de transporte de {@code Tracking.Summary}; duracoes em ISO-8601. */
    public record SummaryView(double distanceMeters, String trackedTime, String movingTime,
                              List<StopView> stops) {

        static SummaryView of(Tracking.Summary summary) {
            return new SummaryView(summary.distanceMeters(), summary.trackedTime().toString(),
                    summary.movingTime().toString(),
                    summary.stops().stream().map(StopView::of).toList());
        }
    }

    /** Espelho de transporte de {@code GpsPanel.PanelEntry}. */
    public record PanelEntryView(UUID workerId, UUID teamIdOrNull,
                                 List<TrackPointView> track, SummaryView summary) {

        static PanelEntryView of(GpsPanel.PanelEntry entry) {
            return new PanelEntryView(entry.workerId(), entry.teamIdOrNull(),
                    entry.track().stream().map(TrackPointView::of).toList(),
                    SummaryView.of(entry.summary()));
        }
    }

    /** Espelho de transporte de {@code GpsPanel.Panel}. */
    public record PanelView(UUID tenantId, Instant from, Instant to,
                            List<PanelEntryView> entries) {

        static PanelView of(GpsPanel.Panel panel) {
            return new PanelView(panel.tenantId(), panel.from(), panel.to(),
                    panel.entries().stream().map(PanelEntryView::of).toList());
        }
    }

    private final FieldTrackIngestionService ingestionService;
    private final FieldTrackPanelService panelService;
    private final FieldTrackContextProvider contexts;

    public FieldTrackController(FieldTrackIngestionService ingestionService,
                                FieldTrackPanelService panelService,
                                FieldTrackContextProvider contexts) {
        this.ingestionService = ingestionService;
        this.panelService = panelService;
        this.contexts = contexts;
    }

    @PostMapping("/track")
    public List<FieldTrackReceipt> track(@RequestHeader("X-Device-Id") UUID deviceId,
                                         @RequestBody @Valid TrackBatchRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        List<Tracking.TrackPoint> points = request.points().stream()
                .map(TrackPointDto::toTrackPoint)
                .toList();
        return ingestionService.ingestBatch(tenantId, deviceId, points, OffsetDateTime.now());
    }

    @GetMapping("/track/panel")
    public PanelView panel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        UUID tenantId = TenantContextHolder.getTenantId();
        FieldTrackPanelContext panelContext = contexts.panelContextFor(tenantId);
        return PanelView.of(panelService.panel(tenantId, from, to, panelContext));
    }
}
