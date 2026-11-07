package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.telemetry.AlarmAction;
import br.com.ziipsan.operations.metering.telemetry.MeterAlarmKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alarme do medidor persistido (ED-17): todo alarme detectado vira linha —
 * inclusive o de grande consumidor, persistido com a marca
 * {@code largeConsumer} — tenha ele aberto OS, avisado o usuário, os dois
 * ou nenhum (sem ação configurada em {@code ami.alert_thresholds}, a ação
 * fica nula e a ausência é visível, nunca presumida).
 *
 * <p>{@code serviceOrderId} é o identificador da OS aberta pela porta de OS,
 * sem FK: o motor de OS é de outro módulo (o mesmo formato de vínculo por
 * identificador das tarefas da P2.7 com o ciclo).
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "meter_alarms")
@Getter
@Setter
public class MeterAlarm extends TenantAwareEntity {

    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }

    @Column(name = "measuring_point_id", nullable = false)
    private UUID measuringPointId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private MeterAlarmKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;

    /** Detalhe da detecção em JSON (limiar, valores), pela classe {@code Json} do core. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb")
    private String detail;

    /** A ação aplicada, de {@code ami.alert_thresholds}; nula quando o tenant não configurou ação para o tipo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20)
    private AlarmAction action;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    @Column(name = "large_consumer", nullable = false)
    private boolean largeConsumer;
}
