package br.com.ziipsan.core.certification.lims17025;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Equipamento de laboratorio com CALIBRACAO, HISTORICO e BLOQUEIO
 * (aceite da ED-51): cada calibracao e um registro novo no historico
 * (nunca sobrescreve a anterior) e o equipamento esta BLOQUEADO para
 * uso em qualquer data fora da validade da calibracao vigente —
 * {@link #requireUsableOn} e a porta que o ensaio chama antes de
 * aceitar um resultado (resultado medido com equipamento vencido nao
 * tem rastreabilidade metrologica).
 *
 * <p>Validade e data-limite INCLUSIVA (padrao da calibracao da ED-69):
 * no dia {@code validUntil} o equipamento ainda esta valido; no dia
 * seguinte, bloqueado. Validade anterior a data da calibracao e
 * incoerente e recusada no registro. Uso ANTES da primeira calibracao
 * tambem esta bloqueado (nao ha calibracao vigente).
 *
 * <p>A vigencia numa data e a da calibracao mais recente com
 * {@code calibratedOn <= data}: se essa esta vencida na data, o
 * equipamento esta bloqueado — uma calibracao antiga valida nao
 * ressuscita validade depois de uma recalibracao vencida.
 *
 * <p>So com a norma ligada: o nascimento do equipamento e o registro
 * de calibracao passam pela porta da {@link Lims17025Activation}.
 */
public final class LabEquipment {

    /** Um registro de calibracao (historico: registros nunca se apagam nem se sobrescrevem). */
    public record CalibrationRecord(LocalDate calibratedOn, LocalDate validUntil,
                                    String certificateRef, String calibratedByRef) {
        public CalibrationRecord {
            Objects.requireNonNull(calibratedOn, "calibratedOn e obrigatorio (quando calibrou)");
            Objects.requireNonNull(validUntil, "validUntil e obrigatorio (validade da calibracao)");
            if (validUntil.isBefore(calibratedOn)) {
                throw new IllegalArgumentException("validUntil nao pode ser anterior a calibratedOn: "
                        + "calibracao que nasce vencida nao e registro, e erro de digitacao");
            }
            if (certificateRef == null || certificateRef.isBlank())
                throw new IllegalArgumentException("certificateRef e obrigatoria (referencia do "
                        + "certificado de calibracao, sem copiar o documento)");
            if (calibratedByRef == null || calibratedByRef.isBlank())
                throw new IllegalArgumentException("calibratedByRef e obrigatorio (quem calibrou)");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final String description;
    private final List<CalibrationRecord> history = new ArrayList<>();

    private LabEquipment(UUID id, UUID tenantId, String code, String description) {
        if (id == null) throw new IllegalArgumentException("id do equipamento e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId do equipamento e obrigatorio");
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("code do equipamento e obrigatorio");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description do equipamento e obrigatoria");
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.description = description;
    }

    /** Nascimento do equipamento com a primeira calibracao: so com a norma ligada. */
    public static LabEquipment register(Lims17025Activation activation, UUID id, UUID tenantId,
                                        String code, String description,
                                        CalibrationRecord firstCalibration) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        Objects.requireNonNull(firstCalibration, "a primeira calibracao e obrigatoria: "
                + "equipamento sem calibracao registrada nao entra em uso");
        LabEquipment eq = new LabEquipment(id, tenantId, code, description);
        eq.history.add(firstCalibration);
        return eq;
    }

    /**
     * Nova calibracao no historico: so com a norma ligada e em ordem
     * cronologica (nunca anterior a ultima registrada — historico nao
     * se reescreve para tras).
     */
    public void recordCalibration(Lims17025Activation activation, CalibrationRecord record) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        Objects.requireNonNull(record, "record de calibracao e obrigatorio");
        CalibrationRecord last = history.get(history.size() - 1);
        if (record.calibratedOn().isBefore(last.calibratedOn())) {
            throw new IllegalArgumentException("calibracao fora de ordem cronologica no equipamento "
                    + code + ": " + record.calibratedOn() + " anterior a ultima registrada ("
                    + last.calibratedOn() + ") — o historico nao se reescreve");
        }
        history.add(record);
    }

    /** Calibracao vigente numa data: a mais recente com calibratedOn <= data, se houver. */
    public CalibrationRecord effectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date e obrigatoria");
        CalibrationRecord effective = null;
        for (CalibrationRecord r : history) {
            if (!r.calibratedOn().isAfter(date)) effective = r;
        }
        return effective;
    }

    /** Verdadeiro quando o equipamento esta dentro da validade na data (limite inclusivo). */
    public boolean validOn(LocalDate date) {
        CalibrationRecord effective = effectiveOn(date);
        return effective != null && !date.isAfter(effective.validUntil());
    }

    /** Verdadeiro quando o uso esta bloqueado na data: sem calibracao vigente ou vencida. */
    public boolean blockedOn(LocalDate date) {
        return !validOn(date);
    }

    /**
     * Porta de uso: qualquer ensaio com este equipamento na data e
     * recusado quando ele esta bloqueado. E a trava que o ensaio
     * (e a fiacao) chama antes de aceitar um resultado.
     */
    public void requireUsableOn(LocalDate date) {
        if (blockedOn(date)) {
            CalibrationRecord effective = effectiveOn(date);
            String detail = effective == null
                    ? "nenhuma calibracao vigente (uso antes da primeira calibracao registrada)"
                    : "calibracao vigente de " + effective.calibratedOn() + " ate "
                            + effective.validUntil() + " vencida";
            throw new IllegalStateException("equipamento " + code + " BLOQUEADO em " + date
                    + ": " + detail + " — recalibrar antes de ensaiar");
        }
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public String description() { return description; }
    /** Historico completo de calibracoes, em ordem de registro (nunca truncado). */
    public List<CalibrationRecord> history() { return List.copyOf(history); }
    public CalibrationRecord latestCalibration() { return history.get(history.size() - 1); }
}
