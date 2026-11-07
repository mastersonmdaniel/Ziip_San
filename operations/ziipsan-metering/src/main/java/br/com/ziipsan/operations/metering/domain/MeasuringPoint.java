package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Ponto de medição (ED-17): onde a telemetria mede — a instalação de
 * hidrômetro de uma unidade, identificada por um código estável do tenant
 * (o código que os arquivos AMR e os dispositivos usam).
 *
 * <p>O ponto carrega o <b>relógio e o fuso</b> da medição ({@code timezoneId},
 * o {@code ZoneId} em que os instantes do dispositivo são lidos) e a
 * <b>base legal</b> do tratamento do consumo horário ({@code legalBasis}):
 * a curva horária é dado pessoal, e nenhum ponto recebe amostra horária sem
 * a base legal registrada — a ingestão recusa ponto sem ela.
 *
 * <p>{@code largeConsumer} marca o ponto de grande consumidor: os alarmes
 * dele são persistidos com a marca (aceite da ED-17) para a analítica e os
 * contratos de grandes usuários que outras tarefas consomem (ED-22, fora
 * desta entrega).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "measuring_points")
@Getter
@Setter
public class MeasuringPoint extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "installation_id", nullable = false)
    private UUID installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private MeasurementKind kind;

    /** O fuso do ponto (um {@code ZoneId}, ex.: {@code America/Sao_Paulo}): o relógio do dispositivo é lido nele. */
    @Column(name = "timezone_id", nullable = false, length = 50)
    private String timezoneId;

    /** Base legal registrada do tratamento do consumo horário deste ponto (dado pessoal). */
    @Column(name = "legal_basis", length = 80)
    private String legalBasis;

    @Column(name = "large_consumer", nullable = false)
    private boolean largeConsumer;

    @Column(name = "active", nullable = false)
    private boolean active;
}
