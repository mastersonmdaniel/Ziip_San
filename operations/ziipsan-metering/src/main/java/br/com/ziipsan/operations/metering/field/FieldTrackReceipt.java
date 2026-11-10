package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.Tracking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Recibo de UM ponto da sincronizacao da trilha: aceite (ingerido por
 * {@code Tracking.ingest} e persistido pela {@code TrackStore}), ou recusado
 * com o motivo NOMEADO da recusa do proprio nucleo (sem aviso registado /
 * fora da jornada / trava {@code field.track_only_in_shift}) — o texto do
 * motivo e o da excecao do nucleo, sem reescrita nesta camada. Um recibo por
 * ponto, sempre: o lote nunca e tudo-ou-nada.
 *
 * @param refusalReason motivo nomeado da recusa; {@code null} se e so se o
 *                      ponto foi aceite
 */
public record FieldTrackReceipt(UUID workerId, Instant at, boolean accepted, String refusalReason) {

    public FieldTrackReceipt {
        Objects.requireNonNull(workerId, "trabalhador do ponto e obrigatorio");
        Objects.requireNonNull(at, "instante do ponto e obrigatorio");
        if (accepted && refusalReason != null) {
            throw new IllegalArgumentException("ponto aceite nao tem motivo de recusa");
        }
        if (!accepted && (refusalReason == null || refusalReason.isBlank())) {
            throw new IllegalArgumentException("ponto recusado exige o motivo nomeado da recusa");
        }
    }

    public static FieldTrackReceipt accepted(Tracking.TrackPoint point) {
        return new FieldTrackReceipt(point.workerId(), point.at(), true, null);
    }

    public static FieldTrackReceipt refused(Tracking.TrackPoint point, String refusalReason) {
        return new FieldTrackReceipt(point.workerId(), point.at(), false, refusalReason);
    }
}
