package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffEngine;
import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grupo 3 — Corte por emissao (a prova central; NT 6.7.2/6.7.3 confirma F-08
 * integralmente): emissao em 2027 sobre consumo encerrado em 2026 usa a
 * desonerada para a TOTALIDADE, sem rateio; emissao em 2026 usa a antiga.
 */
class EmissionDateCutTest {

    private final LocalDate from = LocalDate.of(2026, 12, 15);
    private final LocalDate to = LocalDate.of(2027, 1, 14);

    private record Setup(ReliefProgram program, TariffVersion source, TariffVersion relieved,
                         ReliefLink link) {
    }

    private Setup setup() {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        ReliefProgram program = ReliefFixtures.program(false);
        TariffVersion source = t1();
        var result = generator.generateTariffVersion(program, source,
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4);
        return new Setup(program, source, result.relievedVersion(), result.link());
    }

    @Test
    @DisplayName("consumo encerrado em 2026 + emissao em 2027: desonerada integral, sem rateio")
    void postCutEmissionUsesRelievedForWholePeriod() {
        Setup s = setup();
        var resolution = EmissionDateResolver.resolveForEmission(t1Schedule(), from, to,
                LocalDate.of(2027, 1, 2), s.program(), List.of(s.link()),
                Map.of(s.relieved().id(), s.relieved()));

        assertThat(resolution.relievedApplied()).isTrue();
        assertThat(resolution.schedule().slices(from, to)).hasSize(1);
        // O motor, sobre a resolucao, cobra a desonerada pelo periodo inteiro:
        // 27 m3 RES = 27,00 + 10x3,60 + 7x5,40 = 100,80 (vs. 112,00 da antiga).
        var charge = TariffEngine.calculate(resolution.schedule(), from, to, List.of(res(1)), 27);
        assertThat(charge.net()).isEqualByComparingTo("100.80");
    }

    @Test
    @DisplayName("emissao em 2026: versao antiga, mesmo com periodo a cavalo do corte")
    void preCutEmissionKeepsOriginalSchedule() {
        Setup s = setup();
        TariffSchedule original = t1Schedule();
        var resolution = EmissionDateResolver.resolveForEmission(original, from, to,
                LocalDate.of(2026, 12, 30), s.program(), List.of(s.link()),
                Map.of(s.relieved().id(), s.relieved()));
        assertThat(resolution.relievedApplied()).isFalse();
        assertThat(resolution.schedule()).isSameAs(original);
    }

    @Test
    @DisplayName("sem programa activo: comportamento vigente intacto (a resolucao devolve o schedule dado)")
    void noProgramKeepsCurrentBehaviour() {
        TariffSchedule original = t1Schedule();
        var resolution = EmissionDateResolver.resolveForEmission(original, from, to,
                LocalDate.of(2027, 1, 2), null, List.of(), Map.of());
        assertThat(resolution.relievedApplied()).isFalse();
        assertThat(resolution.schedule()).isSameAs(original);
    }
}
