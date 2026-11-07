package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Verificação metrológica (ED-14): inicial, subsequente ou após reparo; em bancada própria
 * ou laboratório externo. Correção B3: {@code nextVerificationOn} NÃO tem setter — nasce no
 * construtor com o valor CALCULADO pelo {@code MeterVerificationService}
 * ({@code VerificationSchedule}); nenhum caminho de escrita aceita data digitada.
 */
@Entity @Table(name = "meter_verifications") @Getter
public class MeterVerification extends TenantAwareEntity {

    @Column(name = "meter_id", nullable = false) private UUID meterId;
    @Enumerated(EnumType.STRING) @Column(name = "verification_type", nullable = false, length = 20)
    private HydrometryEnums.VerificationType verificationType;
    @Column(name = "performed_on", nullable = false) private LocalDate performedOn;
    @Enumerated(EnumType.STRING) @Column(name = "performed_by", nullable = false, length = 20)
    private HydrometryEnums.PerformedBy performedBy;
    @Column(name = "external_lab_name", length = 160) private String externalLabName;
    @Column(name = "certificate_ref", length = 80) private String certificateRef;
    @Enumerated(EnumType.STRING) @Column(name = "result", nullable = false, length = 20)
    private HydrometryEnums.VerificationResult result;
    @Column(name = "next_verification_on", nullable = false) private LocalDate nextVerificationOn;

    protected MeterVerification() {}

    /** Único construtor de escrita: {@code calculatedNextVerificationOn} vem do cálculo, nunca de digitação. */
    public MeterVerification(UUID meterId, HydrometryEnums.VerificationType type, LocalDate performedOn,
                             HydrometryEnums.PerformedBy performedBy, String externalLabName, String certificateRef,
                             HydrometryEnums.VerificationResult result, LocalDate calculatedNextVerificationOn) {
        this.meterId = meterId; this.verificationType = type; this.performedOn = performedOn;
        this.performedBy = performedBy; this.externalLabName = externalLabName; this.certificateRef = certificateRef;
        this.result = result; this.nextVerificationOn = calculatedNextVerificationOn;
    }
}
