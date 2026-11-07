package br.com.ziipsan.platform.regulatory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Troca de perfil na data de vigência do ato (P2.31), com relógio
 * simulado: o CISAM Meio-Oeste passa da RN 01/2017 para a RN 05/2026 em
 * 20/05/2027 (vigência do art. 163 da RN 05/2026; mapa de ERIs, apêndice B).
 */
class RegulatoryProfileSwitchTest {

    private static RegulatoryProfileDef versionOnClock(String agencyCode, Clock clock) {
        return ScRegulatoryProfiles.versionOn(agencyCode, LocalDate.now(clock));
    }

    private static Clock clockAt(int year, int month, int day) {
        return Clock.fixed(LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC);
    }

    @Test
    @DisplayName("CISAM-MO: em 19/05/2027 vale a RN 01/2017; em 20/05/2027, a RN 05/2026")
    void cisamMeioOesteSwitchesOn2027_05_20() {
        assertThat(versionOnClock("CISAM_MO", clockAt(2027, 5, 19)).profileKey())
                .isEqualTo("CISAM_MO_RN01_2017");
        assertThat(versionOnClock("CISAM_MO", clockAt(2027, 5, 20)).profileKey())
                .isEqualTo("CISAM_MO_RN05_2026");
        assertThat(versionOnClock("CISAM_MO", clockAt(2027, 5, 21)).profileKey())
                .isEqualTo("CISAM_MO_RN05_2026");
    }

    @Test
    @DisplayName("CISAM-MO: antes do vigor da RN 01/2017 (21/12/2017) não há perfil; no dia do vigor, há")
    void cisamMeioOesteBeforeFirstActHasNoProfile() {
        assertThat(ScRegulatoryProfiles.versionOn("CISAM_MO", LocalDate.of(2017, 12, 20)))
                .isNull();
        assertThat(ScRegulatoryProfiles.versionOn("CISAM_MO", LocalDate.of(2017, 12, 21))
                .profileKey()).isEqualTo("CISAM_MO_RN01_2017");
    }

    @Test
    @DisplayName("A troca muda a regra viva: contagem indefinida e sem adoção (2017) viram dias úteis e NR 11 adotada (2026)")
    void switchChangesLiveRules() {
        RegulatoryProfileDef before = versionOnClock("CISAM_MO", clockAt(2027, 5, 19));
        RegulatoryProfileDef after = versionOnClock("CISAM_MO", clockAt(2027, 5, 20));
        assertThat(before.value("sac.deadline_counting_rule").confirmation())
                .isEqualTo(RegulatoryProfileDef.Confirmation.REQUIRES_TENANT_VALUE);
        assertThat(after.value("sac.deadline_counting_rule").valueJson())
                .isEqualTo("\"BUSINESS\"");
        assertThat(before.value("reg.ana_nr_adoption")).isNull();
        assertThat(after.value("reg.ana_nr_adoption").valueJson()).contains("RN 05/2026");
    }

    @Test
    @DisplayName("Agência de ato único resolve em qualquer data da vigência; agência desconhecida, nenhuma")
    void singleActAgenciesResolve() {
        assertThat(ScRegulatoryProfiles.versionOn("ARIS", LocalDate.of(2026, 11, 7)).profileKey())
                .isEqualTo("ARIS_RN19_2019");
        assertThat(ScRegulatoryProfiles.versionOn("ARESC", LocalDate.of(2020, 1, 1)).profileKey())
                .isEqualTo("ARESC_RES046_2016");
        assertThat(ScRegulatoryProfiles.versionOn("AGENCIA_INEXISTENTE", LocalDate.of(2026, 1, 1)))
                .isNull();
    }
}
