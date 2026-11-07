package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Marca do catálogo metrológico (ED-14): substitui o texto livre de {@code meters.brand}. */
@Entity @Table(name = "meter_brands") @Getter @Setter
public class MeterBrand extends TenantAwareEntity {
    @Column(name = "code", nullable = false, length = 30) private String code;
    @Column(name = "name", nullable = false, length = 120) private String name;
    @Column(name = "active", nullable = false) private boolean active;
}
