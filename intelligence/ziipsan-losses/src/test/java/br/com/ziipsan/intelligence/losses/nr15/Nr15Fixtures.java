package br.com.ziipsan.intelligence.losses.nr15;

import br.com.ziipsan.intelligence.losses.dmc.IncidentLossInputs;
import br.com.ziipsan.intelligence.losses.dmc.MicroPeriodAdjustment;
import br.com.ziipsan.intelligence.losses.dmc.WaterBalanceCalculator;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Massa SINTETICA dos testes da ED-78: balancos mensais calculados
 * pela ED-85 REAL (WaterBalanceCalculator) para 2 DMCs, convertidos
 * em fotos pela ponte da ED-85. Nenhum numero e resultado esperado
 * de norma - sao entradas declaradas do teste.
 *
 * DMC-A (1000 ligacoes): entrada 10000, exportado 0, faturado
 * micromedido 6000, nao micromedido 0, autorizado nao faturado 500,
 * aparentes 500 -> liquida 10000, autorizado 6500, perdas 3500,
 * reais 3000, ANF 4000. ILI com rede 12 km, pressao 30, ramais 5 km.
 * DMC-B (500 ligacoes): entrada 5000, exportado 500, faturado
 * micromedido 3000, nao micromedido 100, autorizado nao faturado
 * 100, aparentes 200 -> liquida 4500, autorizado 3200, perdas 1300,
 * reais 1100, ANF 1400. ILI com rede 6 km, pressao 30, ramais 2,5 km.
 */
final class Nr15Fixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID DMC_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID DMC_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final int YEAR = 2026;
    /** Entrada anual somada dos 2 DMCs: (10000 + 5000) x 12. */
    static final double PRODUCED = 180_000.0;

    private Nr15Fixtures() {
    }

    static WaterBalanceCalculator.Inputs inputsA(YearMonth period, Double apparentOrNull, boolean withIli) {
        return new WaterBalanceCalculator.Inputs(DMC_A, period, period.lengthOfMonth(), 1000,
                10000.0, 0.0, 6000.0, 0.0, 500.0, apparentOrNull,
                MicroPeriodAdjustment.Method.NONE, 0.95, null,
                withIli ? 12.0 : null, withIli ? 30.0 : null, withIli ? 5.0 : null,
                IncidentLossInputs.none());
    }

    static WaterBalanceCalculator.Inputs inputsB(YearMonth period, Double apparentOrNull, boolean withIli) {
        return new WaterBalanceCalculator.Inputs(DMC_B, period, period.lengthOfMonth(), 500,
                5000.0, 500.0, 3000.0, 100.0, 100.0, apparentOrNull,
                MicroPeriodAdjustment.Method.NONE, 0.95, null,
                withIli ? 6.0 : null, withIli ? 30.0 : null, withIli ? 2.5 : null,
                IncidentLossInputs.none());
    }

    static DmcBalanceSnapshot snapshot(WaterBalanceCalculator.Inputs inputs) {
        return DmcBalanceSnapshot.fromBalance(inputs, WaterBalanceCalculator.calculate(inputs));
    }

    /** 24 fotos do ano (2 DMCs x 12 meses), aparentes conhecidas, com ILI. */
    static List<DmcBalanceSnapshot> yearSnapshots() {
        return yearSnapshots(true, true, true);
    }

    static List<DmcBalanceSnapshot> yearSnapshots(boolean apparentA, boolean apparentB, boolean withIli) {
        List<DmcBalanceSnapshot> out = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth period = YearMonth.of(YEAR, month);
            out.add(snapshot(inputsA(period, apparentA ? 500.0 : null, withIli)));
            out.add(snapshot(inputsB(period, apparentB ? 200.0 : null, withIli)));
        }
        return out;
    }

    static RegulatoryProfilePort profilePort(Map<UUID, RegulatoryProfilePort.ProfileView> byTenant) {
        return tenantId -> Optional.ofNullable(byTenant.get(tenantId));
    }

    static RegulatoryProfilePort applicablePort() {
        return profilePort(Map.of(TENANT, new RegulatoryProfilePort.ProfileView("AGENCIA-TESTE",
                Map.of("NR15", new RegulatoryProfilePort.Adoption("ADOPTED", List.of("ATO-TESTE-01"))))));
    }

    static ProducedVolumePort producedPort(double value) {
        return (tenantId, baseYear) -> Optional.of(value);
    }

    static Nr15DeclarationService declarationService(RegulatoryProfilePort profiles,
                                                     List<DmcBalanceSnapshot> snapshots,
                                                     ProducedVolumePort produced) {
        return new Nr15DeclarationService(new Nr15Applicability(profiles),
                (tenantId, baseYear) -> snapshots, produced, null);
    }
}
