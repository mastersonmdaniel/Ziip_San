package br.com.ziipsan.platform.field;

import br.com.ziipsan.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aparelho do app de campo (core V11, {@code platform.app_device}; ED-18, ADR-0030).
 * Sem dado pessoal alem do {@code sub}: ver o cabecalho da migration.
 *
 * <p>Nao usar {@code @Builder} (o Lombok descarta os campos de {@link BaseEntity}).
 * A tabela vive no schema {@code platform} do banco core, nao no schema do tenant:
 * por isso esta entidade NAO estende {@code TenantAwareEntity} — {@code tenant_id}
 * aqui e FK para {@code public.tenant_catalog}, nao a coluna de RLS do tenant.
 */
@Entity
@Table(schema = "platform", name = "app_device")
@Getter
@Setter
public class AppDevice extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "user_sub", nullable = false, length = 255)
    private String userSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppDeviceStatus status;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "attestation_verdict", length = 25)
    private String attestationVerdict;

    @Column(name = "attested_at")
    private OffsetDateTime attestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_changed_by_area", length = 10)
    private DeviceArea statusChangedByArea;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    @Column(name = "status_changed_at")
    private OffsetDateTime statusChangedAt;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "last_contact_at")
    private OffsetDateTime lastContactAt;

    @Column(name = "app_version", length = 50)
    private String appVersion;
}
