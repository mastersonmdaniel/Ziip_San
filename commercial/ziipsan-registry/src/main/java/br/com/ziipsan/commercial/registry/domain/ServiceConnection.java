package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligação de água ou de esgoto ({@code service_connections}, GIS-05,
 * P2.23, V3_14; GIS-CADASTRO-TECNICO §3.11; glossário do plano §8):
 * conexão física entre a rede e o imóvel (o lote).
 *
 * <p>O imóvel físico é ancorado em {@code propertyUnitId}: a
 * unidade-mãe quando a unidade é filha de condomínio
 * ({@code parent_relation_type = CONDOMINIUM_MACRO}), a própria unidade
 * nos demais casos. Há no máximo UMA ligação não encerrada por
 * (imóvel, tipo) — índice parcial no banco — e várias unidades
 * consumidoras por ligação (condomínio), ligadas por
 * {@code service_connection_units}.
 *
 * <p>A situação vigente mora aqui ({@code statusId} para o catálogo
 * único {@code connection_statuses}); o histórico é
 * {@code connection_status_history}. {@code connectedOn} (início do
 * serviço; NR 11, art. 22) é obrigatório, com a fonte em
 * {@code connectedOnSource} (DAD-10).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "service_connections")
@Getter
@Setter
public class ServiceConnection extends TenantAwareEntity {

    @Column(name = "property_unit_id", nullable = false)
    private UUID propertyUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private ConnectionStatus.Kind kind;

    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "status_id", nullable = false)
    private UUID statusId;

    @Column(name = "connected_on", nullable = false)
    private LocalDate connectedOn;

    /** LEGACY_STAGING, STATUS_HISTORY, UNIT_REGISTRATION, SERVICE_ORDER ou MANUAL. */
    @Column(name = "connected_on_source", nullable = false, length = 20)
    private String connectedOnSource;

    @Column(name = "disconnected_on")
    private LocalDate disconnectedOn;

    /** Encerramento da ligação: com {@code endedOn} preenchido, outra ligação do mesmo tipo pode nascer para o imóvel. */
    @Column(name = "ended_on")
    private LocalDate endedOn;
}
