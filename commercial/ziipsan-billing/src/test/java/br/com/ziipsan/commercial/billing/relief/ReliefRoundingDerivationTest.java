package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.block;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correccao R-06 (REVISAO-PA-02) — o defeito da precisao DERIVA da tabela
 * de origem (escala dos precos da versao de origem); 4 casas e so o
 * recurso quando a origem e indeterminavel.
 */
class ReliefRoundingDerivationTest {

    private static TariffVersion sourceWithScale3() {
        return new TariffVersion(id(300), LocalDate.of(2026, 1, 1), null,
                List.of(res(10, "30.000", block(20, "4.000"), block(50, "6.000"),
                        block(null, "9.000"))), null);
    }

    @Test
    @DisplayName("defeito deriva da origem: origem a 3 casas -> politica de 3; origem T1 (2 casas) -> 2")
    void defaultDerivesFromSourceScale() {
        assertThat(ReliefRoundingPolicy.tenantDefaultFor(sourceWithScale3()).scale()).isEqualTo(3);
        assertThat(ReliefRoundingPolicy.tenantDefaultFor(t1()).scale()).isEqualTo(2);
        // Recurso (origem indeterminavel): 4.
        assertThat(ReliefRoundingPolicy.tenantDefaultFor(null).scale()).isEqualTo(4);
        assertThat(ReliefRoundingPolicy.tenantDefault().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("programa sem politica declarada gera na precisao DA ORIGEM (rotulo e precos a 3 casas)")
    void generationWithoutDeclaredPolicyUsesSourcePrecision() {
        ReliefProgram program = new ReliefProgram(UUID.randomUUID(), "DERIVA", "fic", null, "ato",
                ReliefFixtures.CUT, ReliefProgramStatus.ACTIVE, null, null, null, null,
                "Reguladora ficticia", "Reserva ficticia", null, null);
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        var result = generator.generateTariffVersion(program, sourceWithScale3(),
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 3);
        assertThat(result.link().roundingPolicy()).isEqualTo("HALF_UP@3");
        assertThat(result.relievedVersion().rules().get(0).minCharge())
                .isEqualByComparingTo("27.000");
        assertThat(result.relievedVersion().rules().get(0).blocks().get(0).pricePerM3())
                .isEqualByComparingTo("3.600");
    }
}
