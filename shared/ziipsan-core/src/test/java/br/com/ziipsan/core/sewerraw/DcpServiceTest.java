package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.effluent.BodEfficiency;
import br.com.ziipsan.core.effluent.EffluentStandard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DcpServiceTest {
    static final UUID ETE = UUID.randomUUID();
    static final LocalDate START = LocalDate.of(2026, 3, 1);
    static final LocalDate END = LocalDate.of(2026, 3, 31);

    private BodEfficiency.Result bod() {
        return BodEfficiency.assess(ETE, START, END, List.of(
                new BodEfficiency.BodPair(new BigDecimal("200"), new BigDecimal("20")),
                new BodEfficiency.BodPair(new BigDecimal("220"), new BigDecimal("22"))));
    }

    @Test void generatesDeclarationWithDimensionalLoadFromMonitoring() {
        var dcp = DcpService.generate(TestSupport.TENANT, ETE, START, END, TestSupport.catalog(),
                EffluentStandard.Jurisdiction.FEDERAL, "",
                List.of(new DcpService.MonitoringMean("PARAM_A", new BigDecimal("10"), "mg/L")),
                new BigDecimal("2000"), bod(), true, "RT-FIXTURE", END);
        // carga = 10 mg/L x 2000 m3 / 1000 = 20 kg
        assertEquals(0, new BigDecimal("20.000").compareTo(dcp.totalLoadKg()));
        assertEquals(0, new BigDecimal("90.00").compareTo(dcp.bodRemovalPercent()));
        assertEquals(List.of("NORMA-SINTETICA [FIXTURE]"), dcp.normativeBasisRefs());
        assertEquals("v-fixture-1", dcp.effluentCatalogVersion());
    }

    @Test void refusesWhileSuccessorNormBasisNotConfirmedPublished() {
        assertThrows(IllegalStateException.class, () -> DcpService.generate(TestSupport.TENANT, ETE, START, END,
                TestSupport.catalog(), EffluentStandard.Jurisdiction.FEDERAL, "",
                List.of(new DcpService.MonitoringMean("PARAM_A", new BigDecimal("10"), "mg/L")),
                new BigDecimal("2000"), bod(), false, "RT-FIXTURE", END));
    }

    @Test void refusesParameterWithoutReviewedStandardInCatalog() {
        assertThrows(IllegalStateException.class, () -> DcpService.generate(TestSupport.TENANT, ETE, START, END,
                TestSupport.catalog(), EffluentStandard.Jurisdiction.FEDERAL, "",
                List.of(new DcpService.MonitoringMean("PARAM_DESCONHECIDO", new BigDecimal("10"), "mg/L")),
                new BigDecimal("2000"), bod(), true, "RT-FIXTURE", END));
    }

    @Test void refusesNoMonitoringZeroVolumeAndForeignBod() {
        assertThrows(IllegalArgumentException.class, () -> DcpService.generate(TestSupport.TENANT, ETE, START, END,
                TestSupport.catalog(), EffluentStandard.Jurisdiction.FEDERAL, "",
                List.of(), new BigDecimal("2000"), bod(), true, "RT-FIXTURE", END));
        assertThrows(IllegalArgumentException.class, () -> DcpService.generate(TestSupport.TENANT, ETE, START, END,
                TestSupport.catalog(), EffluentStandard.Jurisdiction.FEDERAL, "",
                List.of(new DcpService.MonitoringMean("PARAM_A", new BigDecimal("10"), "mg/L")),
                BigDecimal.ZERO, bod(), true, "RT-FIXTURE", END));
        var foreign = BodEfficiency.assess(UUID.randomUUID(), START, END,
                List.of(new BodEfficiency.BodPair(new BigDecimal("100"), new BigDecimal("10"))));
        assertThrows(IllegalArgumentException.class, () -> DcpService.generate(TestSupport.TENANT, ETE, START, END,
                TestSupport.catalog(), EffluentStandard.Jurisdiction.FEDERAL, "",
                List.of(new DcpService.MonitoringMean("PARAM_A", new BigDecimal("10"), "mg/L")),
                new BigDecimal("2000"), foreign, true, "RT-FIXTURE", END));
    }
}
