package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seccao 5.2 ponto 5 do desenho, na leitura que fecha a RR-02 da
 * RE-REVISAO-PA-02 (correccao 2): (1) o MAIS RECENTE cujo effective_from
 * cubra a emissao vence SEMPRE, tambem entre escopos; (2) empate na mesma
 * data entre escopos diferentes resolve-se pela especificidade (precedente
 * da secao 3.3), sem erro; (3) empate na mesma data e no mesmo escopo =
 * erro de cadastro fechado, nunca mascarado por programa de outro escopo.
 *
 * <p>Nota de actualizacao (correccao 2, RR-02): os 3 testes originais foram
 * mantidos sem apagar assercoes — o cenario do 3.o (municipal x empresa na
 * MESMA data) coincide com o ramo 2 correcto e continua valido como
 * desempate; o que a ordem antiga tinha de errado (especificidade antes da
 * recencia, em qualquer data) nao estava coberto por nenhum deles — e por
 * isso a sonda do re-revisor o apanhou. Os dois testes novos cobrem
 * exactamente os ramos em falta, incluindo a sonda refeita.
 */
class ReliefProgramSelectorTest {

    private static ReliefProgram program(String code, LocalDate effectiveFrom,
                                         ReliefProgramStatus status, String municipality) {
        return new ReliefProgram(UUID.randomUUID(), code, code, municipality, "ato ficticio",
                effectiveFrom, status, null, ReliefRoundingPolicy.tenantDefault(), null, null,
                "Reguladora ficticia", "Reserva ficticia", null, null);
    }

    @Test
    @DisplayName("mais recente aplicavel vence; emissao anterior ao corte novo cai no programa anterior")
    void mostRecentCoveringProgramWins() {
        var old = program("ANTIGO", LocalDate.of(2026, 1, 1), ReliefProgramStatus.ACTIVE, null);
        var recent = program("RECENTE", LocalDate.of(2027, 1, 1), ReliefProgramStatus.ACTIVE, null);
        assertThat(ReliefProgramSelector.selectApplicable(List.of(old, recent), null,
                LocalDate.of(2027, 2, 1))).contains(recent);
        assertThat(ReliefProgramSelector.selectApplicable(List.of(old, recent), null,
                LocalDate.of(2026, 7, 1))).contains(old);
        assertThat(ReliefProgramSelector.selectApplicable(List.of(old, recent), null,
                LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    @DisplayName("dois activos com o mesmo effective_from e o mesmo escopo: erro de cadastro fechado")
    void sameEffectiveFromSameScopeIsRegistrationError() {
        var a = program("A", LocalDate.of(2027, 1, 1), ReliefProgramStatus.ACTIVE, null);
        var b = program("B", LocalDate.of(2027, 1, 1), ReliefProgramStatus.PUBLISHED, null);
        assertThatThrownBy(() -> ReliefProgramSelector.selectApplicable(List.of(a, b), null,
                LocalDate.of(2027, 1, 10)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("Erro de cadastro");
    }

    @Test
    @DisplayName("empate na mesma data entre escopos diferentes: o mais especifico vence, sem erro; DRAFT nao participa")
    void municipalScopeBeatsCompanyScopeAndDraftIgnored() {
        var company = program("EMPRESA", LocalDate.of(2027, 1, 1), ReliefProgramStatus.ACTIVE, null);
        var municipal = program("MUNICIPAL", LocalDate.of(2027, 1, 1), ReliefProgramStatus.ACTIVE,
                "4205407");
        var draft = program("RASCUNHO", LocalDate.of(2027, 6, 1), ReliefProgramStatus.DRAFT, null);
        assertThat(ReliefProgramSelector.selectApplicable(List.of(company, municipal, draft),
                "4205407", LocalDate.of(2027, 2, 1))).contains(municipal);
        // Unidade de outro municipio: so o programa de empresa cobre.
        assertThat(ReliefProgramSelector.selectApplicable(List.of(company, municipal), "3550308",
                LocalDate.of(2027, 2, 1))).contains(company);
    }

    @Test
    @DisplayName("RR-02 sonda refeita: recencia vence especificidade entre escopos — municipal 2026 x empresa 2027, vence 2027")
    void recencyBeatsSpecificityAcrossScopes() {
        var municipalOld = program("MUN-ANTIGO", LocalDate.of(2026, 1, 1),
                ReliefProgramStatus.ACTIVE, "4205407");
        var companyNew = program("EMPRESA-2027", LocalDate.of(2027, 1, 1),
                ReliefProgramStatus.ACTIVE, null);
        // Unidade do municipio com programa municipal: o de empresa, mais
        // recente, vence — a especificidade NAO se aplica antes da recencia.
        assertThat(ReliefProgramSelector.selectApplicable(List.of(municipalOld, companyNew),
                "4205407", LocalDate.of(2027, 2, 1))).contains(companyNew);
        // Simetrico: municipal mais recente vence empresa antiga (recencia,
        // nao especificidade, decide entre datas diferentes).
        var companyOld = program("EMPRESA-2026", LocalDate.of(2026, 1, 1),
                ReliefProgramStatus.ACTIVE, null);
        var municipalNew = program("MUN-2027", LocalDate.of(2027, 1, 1),
                ReliefProgramStatus.ACTIVE, "4205407");
        assertThat(ReliefProgramSelector.selectApplicable(List.of(companyOld, municipalNew),
                "4205407", LocalDate.of(2027, 2, 1))).contains(municipalNew);
    }

    @Test
    @DisplayName("RR-02 sonda refeita: ambiguidade de empresa na mesma data NAO e mascarada por programa municipal presente")
    void companyAmbiguityNotMaskedByMunicipalPresent() {
        var companyA = program("EMPRESA-A", LocalDate.of(2027, 1, 1),
                ReliefProgramStatus.ACTIVE, null);
        var companyB = program("EMPRESA-B", LocalDate.of(2027, 1, 1),
                ReliefProgramStatus.ACTIVE, null);
        var municipalSameDate = program("MUN", LocalDate.of(2027, 1, 1),
                ReliefProgramStatus.ACTIVE, "4205407");
        assertThatThrownBy(() -> ReliefProgramSelector.selectApplicable(
                List.of(companyA, companyB, municipalSameDate), "4205407",
                LocalDate.of(2027, 2, 1)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("Erro de cadastro");
        // Variante da sonda: municipal mais antigo presente — o erro da data
        // mais recente continua a disparar.
        var municipalOld = program("MUN-ANTIGO", LocalDate.of(2026, 1, 1),
                ReliefProgramStatus.ACTIVE, "4205407");
        assertThatThrownBy(() -> ReliefProgramSelector.selectApplicable(
                List.of(companyA, companyB, municipalOld), "4205407",
                LocalDate.of(2027, 2, 1)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("Erro de cadastro");
    }
}
