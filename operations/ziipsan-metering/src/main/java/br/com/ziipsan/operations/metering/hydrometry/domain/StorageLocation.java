package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Local de armazenagem (catálogo): obrigatório p/ hidrômetro disponível/em manutenção. */
@Entity @Table(name = "meter_storage_locations") @Getter @Setter
public class StorageLocation extends TenantAwareEntity {
    @Column(name = "code", nullable = false, length = 30) private String code;
    @Column(name = "name", nullable = false, length = 120) private String name;
    @Column(name = "active", nullable = false) private boolean active;
}
