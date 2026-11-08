package br.com.ziipsan.core.certification.quality9001;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Calibração de um instrumento de medição (ED-69): instrumento, data da
 * calibração, validade, referência do certificado e quem calibrou. A
 * régua central do aceite é o BLOQUEIO: instrumento com a validade
 * vencida na data de uso está bloqueado — {@link #requireUsableOn}
 * recusa o uso e {@link #blockedOn} expõe o estado para a tela e para
 * os serviços que medem (medição com instrumento vencido produziria
 * resultado sem rastreabilidade metrológica).
 *
 * <p>Validade é data-limite INCLUSIVA: no dia {@code validUntil} o
 * instrumento ainda está válido; no dia seguinte, bloqueado. Validade
 * anterior à data da calibração é incoerente e recusada no nascimento.
 *
 * <p>Só com a norma ligada: o nascimento passa pela porta da
 * {@link Iso9001Activation}. O histórico não é apagado ao desligar a
 * norma nem ao recalibrar: cada calibração é um registro novo (o SQL
 * pendente não tem DELETE nem UPDATE de validade).
 */
public record InstrumentCalibration(String instrumentCode, String instrumentDescription,
                                    LocalDate calibratedOn, LocalDate validUntil,
                                    String certificateRef, UUID calibratedBy) {

    /** Teto do código do instrumento no SQL pendente da ED-69 (VARCHAR(60)). */
    public static final int INSTRUMENT_CODE_MAX = 60;
    /** Teto da referência do certificado no SQL pendente da ED-69 (VARCHAR(200)). */
    public static final int CERTIFICATE_REF_MAX = 200;

    public InstrumentCalibration {
        if (instrumentCode == null || instrumentCode.isBlank()
                || instrumentCode.trim().length() > INSTRUMENT_CODE_MAX) {
            throw new IllegalArgumentException("instrumentCode é obrigatório e tem no máximo "
                    + INSTRUMENT_CODE_MAX + " caracteres");
        }
        instrumentCode = instrumentCode.trim();
        if (instrumentDescription == null || instrumentDescription.isBlank()) {
            throw new IllegalArgumentException("instrumentDescription é obrigatória "
                    + "(qual instrumento de medição é este)");
        }
        instrumentDescription = instrumentDescription.trim();
        Objects.requireNonNull(calibratedOn, "calibratedOn é obrigatório (quando calibrou)");
        Objects.requireNonNull(validUntil, "validUntil é obrigatório (validade da calibração)");
        if (validUntil.isBefore(calibratedOn)) {
            throw new IllegalArgumentException("validUntil não pode ser anterior a calibratedOn: "
                    + "calibração que nasce vencida não é registro, é erro de digitação");
        }
        if (certificateRef == null || certificateRef.isBlank()
                || certificateRef.trim().length() > CERTIFICATE_REF_MAX) {
            throw new IllegalArgumentException("certificateRef é obrigatória (referência do "
                    + "certificado de calibração, sem copiar o documento) e tem no máximo "
                    + CERTIFICATE_REF_MAX + " caracteres");
        }
        certificateRef = certificateRef.trim();
        Objects.requireNonNull(calibratedBy, "calibratedBy é obrigatório (quem calibrou)");
    }

    /** Nascimento do registro de calibração: só com a norma ligada (porta da ativação). */
    public static InstrumentCalibration register(Iso9001Activation activation,
                                                 String instrumentCode,
                                                 String instrumentDescription,
                                                 LocalDate calibratedOn, LocalDate validUntil,
                                                 String certificateRef, UUID calibratedBy) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new InstrumentCalibration(instrumentCode, instrumentDescription, calibratedOn,
                validUntil, certificateRef, calibratedBy);
    }

    /** Verdadeiro quando o instrumento está dentro da validade na data (limite inclusivo). */
    public boolean validOn(LocalDate date) {
        Objects.requireNonNull(date, "date é obrigatória");
        return !date.isBefore(calibratedOn) && !date.isAfter(validUntil);
    }

    /** Verdadeiro quando o uso está bloqueado na data: validade vencida (ou uso antes da calibração). */
    public boolean blockedOn(LocalDate date) {
        return !validOn(date);
    }

    /**
     * Porta de uso: qualquer medição com este instrumento na data é
     * recusada quando ele está bloqueado. É a trava que os serviços de
     * medição chamam antes de aceitar um resultado.
     */
    public void requireUsableOn(LocalDate date) {
        if (blockedOn(date)) {
            throw new IllegalStateException("instrumento " + instrumentCode + " bloqueado em "
                    + date + ": calibração válida de " + calibratedOn + " até " + validUntil
                    + " — recalibrar antes de medir");
        }
    }
}
