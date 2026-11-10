package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.FieldOsPorts;
import br.com.ziipsan.core.fieldos.Tracking;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sincronizacao da trilha GPS (ED-20 / P-MOB-GPS-01): o aparelho devolve os
 * pontos coletados na jornada e recebe UM RECIBO POR PONTO.
 *
 * <h2>Regras, na ordem em que rodam</h2>
 * <ol>
 *   <li><b>Portao do aparelho:</b> {@link FieldDeviceGate#assertCanSync}
 *       antes de qualquer ponto, como na sincronizacao de leituras
 *       ({@code FieldSyncService}) — aparelho nao autorizado nao ingere
 *       ponto nenhum.</li>
 *   <li><b>Ingestao ANTES de persistir, sem atalho (P-ED20-02):</b> cada
 *       ponto passa por {@code Tracking.ingest(ponto, jornada, trava,
 *       aviso)} com o contexto do trabalhador
 *       ({@link FieldTrackContextProvider}) e SO o ponto devolvido pela
 *       ingestao chega a {@code TrackStore.append} — nesta ordem, ponto a
 *       ponto. Nenhum ponto persiste sem ingestao.</li>
 *   <li><b>Lote nunca e tudo-ou-nada:</b> a recusa de um ponto (sem aviso
 *       registado, fora da jornada, trava desligada na configuracao) vira o
 *       recibo recusado DESSE ponto, com o motivo nomeado lancado pelo
 *       proprio nucleo, e nao derruba os vizinhos — nem o ponto bom cai
 *       pelo mau, nem o mau entra pelo bom.</li>
 * </ol>
 */
@Service
public class FieldTrackIngestionService {

    private final FieldDeviceGate devices;
    private final FieldTrackContextProvider contexts;
    private final FieldOsPorts.TrackStore trackStore;

    public FieldTrackIngestionService(FieldDeviceGate devices,
                                      FieldTrackContextProvider contexts,
                                      FieldOsPorts.TrackStore trackStore) {
        this.devices = devices;
        this.contexts = contexts;
        this.trackStore = trackStore;
    }

    public List<FieldTrackReceipt> ingestBatch(UUID tenantId, UUID deviceId,
                                               List<Tracking.TrackPoint> points,
                                               OffsetDateTime now) {
        devices.assertCanSync(tenantId, deviceId, now);
        Objects.requireNonNull(points, "lote de pontos da trilha e obrigatorio");
        Map<UUID, FieldTrackContext> contextByWorker = new HashMap<>();
        List<FieldTrackReceipt> receipts = new ArrayList<>();
        for (Tracking.TrackPoint point : points) {
            receipts.add(ingestOne(tenantId, point, contextByWorker));
        }
        return List.copyOf(receipts);
    }

    private FieldTrackReceipt ingestOne(UUID tenantId, Tracking.TrackPoint point,
                                        Map<UUID, FieldTrackContext> contextByWorker) {
        Objects.requireNonNull(point, "ponto da trilha e obrigatorio");
        try {
            FieldTrackContext context = contextByWorker.computeIfAbsent(point.workerId(),
                    workerId -> contexts.contextFor(tenantId, workerId));
            Tracking.TrackPoint ingested = Tracking.ingest(point, context.shift(),
                    context.trackOnlyInShiftConfigured(), context.employeeNoticeAcknowledged());
            trackStore.append(tenantId, ingested);
            return FieldTrackReceipt.accepted(ingested);
        } catch (RuntimeException refusal) {
            // A recusa nomeada do nucleo (aviso/jornada/trava) vira o recibo
            // deste ponto — nunca e reescrita nem escondida nesta camada.
            return FieldTrackReceipt.refused(point, refusal.getMessage());
        }
    }
}
