package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

/** Resultado por vazão do ensaio em bancada (ED-14, correção M1 — a entidade faltava). */
@Entity @Table(name = "meter_bench_test_results") @Getter @Setter
public class MeterBenchTestResult extends TenantAwareEntity {
    @Column(name = "bench_test_id", nullable = false) private UUID benchTestId;
    @Column(name = "meter_id", nullable = false) private UUID meterId;
    @Enumerated(EnumType.STRING) @Column(name = "flow_point", nullable = false, length = 5)
    private HydrometryEnums.BenchFlowPoint flowPoint;
    @Column(name = "reference_volume", nullable = false, precision = 12, scale = 4) private BigDecimal referenceVolume;
    @Column(name = "indicated_volume", nullable = false, precision = 12, scale = 4) private BigDecimal indicatedVolume;
    @Column(name = "error_pct", nullable = false, precision = 8, scale = 4) private BigDecimal errorPct;
}
