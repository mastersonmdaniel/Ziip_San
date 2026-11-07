package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ensaio em bancada própria de uma AMOSTRA de lote (ED-14): resultado por vazão e curva de
 * erro vivem em {@code meter_bench_test_results} (uma linha por hidrômetro × vazão);
 * {@code lotApproved} só é gravado pela regra {@code BenchTestRule.lotApproved}.
 */
@Entity @Table(name = "meter_bench_tests") @Getter @Setter
public class MeterBenchTest extends TenantAwareEntity {
    @Column(name = "lot_id", nullable = false) private UUID lotId;
    @Column(name = "performed_on", nullable = false) private LocalDate performedOn;
    @Enumerated(EnumType.STRING) @Column(name = "performed_by", nullable = false, length = 20)
    private HydrometryEnums.PerformedBy performedBy;
    @Column(name = "sample_size", nullable = false) private int sampleSize;
    @Column(name = "lot_approved") private Boolean lotApproved;
}
