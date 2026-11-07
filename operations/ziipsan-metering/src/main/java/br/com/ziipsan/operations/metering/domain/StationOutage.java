package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
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
 * Parada de estação (ED-17): o registro de que uma estação (ETA e demais
 * estações do supervisório) ficou parada, com início, fim e motivo.
 *
 * <p><b>Fronteira declarada do aceite:</b> o diário de operação de estações
 * é da ED-87. Desta entrega é o registro que nasce da ingestão do
 * supervisório e o <b>registro manual</b> de paradas da ETA que o aceite
 * pede aqui ("registro manual de paradas da ETA"); a frase final da linha
 * da ED-17 no plano remete "o registro manual de paradas e o diário" à
 * ED-87 — o que fica aqui, sem duplicar a ED-87, é esta tabela mínima de
 * parada (início, fim, motivo, origem e quem registrou), que a ED-87
 * consome no diário.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "station_outages")
@Getter
@Setter
public class StationOutage extends TenantAwareEntity {

    public enum Source { MANUAL, SUPERVISORY }

    @Column(name = "station_code", nullable = false, length = 40)
    private String stationCode;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 15)
    private Source source;

    @Column(name = "registered_by")
    private UUID registeredBy;
}
