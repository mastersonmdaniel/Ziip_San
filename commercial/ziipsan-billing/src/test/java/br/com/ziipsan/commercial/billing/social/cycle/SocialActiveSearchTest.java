package br.com.ziipsan.commercial.billing.social.cycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.ziipsan.commercial.billing.social.cycle.SocialCycleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * II-3/E5 - busca activa (aceite SS4.4 da estimativa): a lista e
 * exactamente elegiveis menos ja inscritos, agrupada por municipio/rota.
 */
class SocialActiveSearchTest {

    private SocialCrossingEngine.CrossingResult crossing() {
        return SocialCrossingEngine.cross(SocialCrossingEngineTest.importedRows(),
                SocialCrossingEngineTest.units(), REF, SocialTariffCycleParameters.proofParameters());
    }

    @Test
    @DisplayName("sem inscritos: entram os casados nao inscritos e a sem unidade, sai a inelegivel")
    void nobodyEnrolled() {
        List<SocialActiveSearch.ActiveSearchEntry> entries = SocialActiveSearch.compute(crossing(), Set.of());
        assertThat(entries).extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash)
                .containsExactlyInAnyOrder(F1, F2, F3);
        assertThat(entries).filteredOn(e -> e.familyCodeHash().equals(F2)).singleElement()
                .satisfies(e -> {
                    assertThat(e.reason()).isEqualTo(SocialActiveSearch.Reason.NO_UNIT_MATCH);
                    assertThat(e.municipalityCode()).isEqualTo("MUN-A");
                    assertThat(e.routeCode()).isEqualTo(SocialActiveSearch.SEM_ROTA);
                });
        assertThat(entries).filteredOn(e -> e.familyCodeHash().equals(F3)).singleElement()
                .satisfies(e -> {
                    assertThat(e.reason()).isEqualTo(SocialActiveSearch.Reason.MATCHED_NOT_ENROLLED);
                    assertThat(e.municipalityCode()).isEqualTo("MUN-B");
                    assertThat(e.routeCode()).isEqualTo("R-02");
                });
    }

    @Test
    @DisplayName("exacto: elegiveis menos inscritos - F1 inscrita sai, F3 e F2 ficam")
    void eligibleMinusEnrolledExactly() {
        List<SocialActiveSearch.ActiveSearchEntry> entries = SocialActiveSearch.compute(crossing(), Set.of(F1));
        assertThat(entries).extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash)
                .containsExactlyInAnyOrder(F2, F3);
    }

    @Test
    @DisplayName("todos inscritos: a lista fica vazia")
    void allEnrolledEmptiesList() {
        assertThat(SocialActiveSearch.compute(crossing(), Set.of(F1, F2, F3))).isEmpty();
    }

    @Test
    @DisplayName("inelegivel nunca entra, nem quando nao esta inscrita")
    void ineligibleNeverEnters() {
        assertThat(SocialActiveSearch.compute(crossing(), Set.of()))
                .extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash)
                .doesNotContain(F4);
    }

    @Test
    @DisplayName("agrupamento por municipio e rota exacto")
    void groupingByMunicipalityAndRoute() {
        Map<SocialActiveSearch.GroupKey, List<SocialActiveSearch.ActiveSearchEntry>> grouped =
                SocialActiveSearch.groupByMunicipalityAndRoute(SocialActiveSearch.compute(crossing(), Set.of(F1)));
        assertThat(grouped).hasSize(2);
        assertThat(grouped.get(new SocialActiveSearch.GroupKey("MUN-A", SocialActiveSearch.SEM_ROTA)))
                .extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash).containsExactly(F2);
        assertThat(grouped.get(new SocialActiveSearch.GroupKey("MUN-B", "R-02")))
                .extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash).containsExactly(F3);
    }
}
