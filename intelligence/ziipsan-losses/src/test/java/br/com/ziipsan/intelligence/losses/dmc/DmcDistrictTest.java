package br.com.ziipsan.intelligence.losses.dmc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class DmcDistrictTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID ZONE = UUID.fromString("00000000-0000-0000-0000-0000000000z1".replace('z','1'));

    DmcDistrict dmc() {
        return new DmcDistrict(UUID.randomUUID(), TEN, UUID.randomUUID(), ZONE,
                "DMC-01", "SETOR-NORTE", 1000, 12.0);
    }

    /** Duble honesto declarado da ED-17: so existem os codigos listados. */
    static MeasuringPointPort knownPoints(String... codes) {
        Set<String> known = Set.of(codes);
        return (tenantId, code) -> TEN.equals(tenantId) && known.contains(code);
    }

    @Test @DisplayName("DMC sem zona DMA nao existe (fonte unica); setor e codigo obrigatorios")
    void singleSourceGuards() {
        assertThatThrownBy(() -> new DmcDistrict(UUID.randomUUID(), TEN, UUID.randomUUID(), null,
                "DMC-01", "S1", 10, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DmcDistrict(UUID.randomUUID(), TEN, UUID.randomUUID(), ZONE,
                " ", "S1", 10, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DmcDistrict(UUID.randomUUID(), TEN, null, ZONE,
                "DMC-01", "S1", 10, null)).isInstanceOf(NullPointerException.class);
    }

    @Test @DisplayName("macromedidores ED-17: entrada/saida por codigo; duplicado recusa; inativo nao recebe")
    void meterLinks() {
        MeasuringPointPort points = knownPoints("MACRO-E1", "MACRO-S1", "MACRO-E2");
        DmcDistrict d = dmc();
        assertThat(d.hasInletMeter()).isFalse();
        d.linkMacroMeter(new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "MACRO-E1"), points);
        d.linkMacroMeter(new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.OUTLET, "MACRO-S1"), points);
        assertThat(d.hasInletMeter()).isTrue();
        assertThat(d.inletMeters()).hasSize(1);
        assertThat(d.outletMeters()).hasSize(1);
        assertThatThrownBy(() -> d.linkMacroMeter(
                new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "MACRO-E1"), points))
                .isInstanceOf(IllegalArgumentException.class);
        d.deactivate();
        assertThatThrownBy(() -> d.linkMacroMeter(
                new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "MACRO-E2"), points))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("R1: macromedidor com codigo INEXISTENTE na ED-17 recusa o vinculo (INLET e OUTLET, fail-closed)")
    void macroMeterUnknownCodeRefused() {
        MeasuringPointPort points = knownPoints("MACRO-E1");
        DmcDistrict d = dmc();
        assertThatThrownBy(() -> d.linkMacroMeter(
                new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "CODIGO-QUE-NAO-EXISTE-NA-ED17"), points))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.linkMacroMeter(
                new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.OUTLET, "CODIGO-QUE-NAO-EXISTE-NA-ED17"), points))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.linkMacroMeter(
                new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "MACRO-E1"), null))
                .isInstanceOf(NullPointerException.class);
        assertThat(d.meterLinks()).isEmpty();
        assertThat(d.hasInletMeter()).isFalse();
    }

    @Test @DisplayName("R1: macromedidor com codigo EXISTENTE na ED-17 aceita o vinculo INLET")
    void macroMeterKnownInletAccepted() {
        DmcDistrict d = dmc();
        d.linkMacroMeter(new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "MACRO-E1"),
                knownPoints("MACRO-E1"));
        assertThat(d.inletMeters()).hasSize(1);
        assertThat(d.inletMeters().get(0).measuringPointCode()).isEqualTo("MACRO-E1");
        assertThat(d.hasInletMeter()).isTrue();
    }

    @Test @DisplayName("R1: macromedidor com codigo EXISTENTE na ED-17 aceita o vinculo OUTLET")
    void macroMeterKnownOutletAccepted() {
        DmcDistrict d = dmc();
        d.linkMacroMeter(new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.OUTLET, "MACRO-S1"),
                knownPoints("MACRO-S1"));
        assertThat(d.outletMeters()).hasSize(1);
        assertThat(d.outletMeters().get(0).measuringPointCode()).isEqualTo("MACRO-S1");
    }

    @Test @DisplayName("servico: zona inexistente, zona nao-DMA, codigo e zona duplicados recusam")
    void serviceRules() {
        DmcSectorService okSvc = new DmcSectorService(
                (t, z) -> Optional.of("DMA"),
                new DmcSectorService.DmcRegistryPort() {
                    public boolean codeExists(UUID t, String c) { return false; }
                    public boolean zoneAlreadyHasDmc(UUID t, UUID z) { return false; }
                });
        DmcDistrict d = okSvc.createDmc(UUID.randomUUID(), TEN, UUID.randomUUID(), ZONE,
                "DMC-01", "SETOR-NORTE", 1000, 12.0);
        assertThat(d.code()).isEqualTo("DMC-01");

        DmcSectorService noZone = new DmcSectorService((t, z) -> Optional.empty(),
                okSvcRegistry());
        assertThatThrownBy(() -> noZone.createDmc(UUID.randomUUID(), TEN, UUID.randomUUID(), ZONE,
                "DMC-02", "S1", 1, null)).isInstanceOf(IllegalArgumentException.class);

        DmcSectorService wrongType = new DmcSectorService((t, z) -> Optional.of("PRESSURE_ZONE"),
                okSvcRegistry());
        assertThatThrownBy(() -> wrongType.createDmc(UUID.randomUUID(), TEN, UUID.randomUUID(), ZONE,
                "DMC-02", "S1", 1, null)).isInstanceOf(IllegalArgumentException.class);

        DmcSectorService dupZone = new DmcSectorService((t, z) -> Optional.of("DMA"),
                new DmcSectorService.DmcRegistryPort() {
                    public boolean codeExists(UUID t, String c) { return false; }
                    public boolean zoneAlreadyHasDmc(UUID t, UUID z) { return true; }
                });
        assertThatThrownBy(() -> dupZone.createDmc(UUID.randomUUID(), TEN, UUID.randomUUID(), ZONE,
                "DMC-02", "S1", 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    static DmcSectorService.DmcRegistryPort okSvcRegistry() {
        return new DmcSectorService.DmcRegistryPort() {
            public boolean codeExists(UUID t, String c) { return false; }
            public boolean zoneAlreadyHasDmc(UUID t, UUID z) { return false; }
        };
    }

    @Test @DisplayName("balanco exige DMC ativo, com entrada macromedida e ligacoes")
    void balanceReady() {
        DmcDistrict d = dmc();
        assertThatThrownBy(() -> DmcSectorService.requireBalanceReady(d))
                .isInstanceOf(IllegalStateException.class);
        d.linkMacroMeter(new DmcDistrict.MacroMeterLink(DmcDistrict.Direction.INLET, "MACRO-E1"),
                knownPoints("MACRO-E1"));
        DmcSectorService.requireBalanceReady(d);
    }
}
