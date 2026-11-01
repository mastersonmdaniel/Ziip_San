package br.com.ziipsan.intelligence.quality.lab.audit;

import br.com.ziipsan.intelligence.quality.lab.LabInstrument;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que a trilha de auditoria guarda de um instrumento: identificação e situação de calibração.
 *
 * <p>Fica de fora {@code connectionConfig}, que pode conter usuário e senha do equipamento; antes a
 * entidade inteira ia para {@code audit_log}. Os records de auditoria deste pacote só têm campos
 * escalares, o que também evita serializar proxies JPA preguiçosos.
 */
public record LabInstrumentSnapshot(
        UUID id,
        String code,
        String name,
        LabInstrument.InstrumentType instrumentType,
        String serialNumber,
        String status,
        OffsetDateTime calibrationDue,
        Integer calibrationFrequencyDays
) {

    public static LabInstrumentSnapshot of(LabInstrument i) {
        return new LabInstrumentSnapshot(i.getId(), i.getCode(), i.getName(), i.getInstrumentType(),
                i.getSerialNumber(), i.getStatus(), i.getCalibrationDue(), i.getCalibrationFrequencyDays());
    }
}
