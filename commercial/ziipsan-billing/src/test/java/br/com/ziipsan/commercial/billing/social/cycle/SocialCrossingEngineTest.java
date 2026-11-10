package br.com.ziipsan.commercial.billing.social.cycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.social.cycle.SocialCycleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * II-3/E3 - cruzamento por chave documental (aceite SS4.1 da
 * estimativa): correspondencias exactas, sem-correspondencia e
 * inelegiveis, conjuntos exactos, desempate da P1.5 respeitado.
 */
class SocialCrossingEngineTest {

    static final UUID U1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID U2 = UUID.fromString("22222222-2222-4222-8222-222222222222");
    static final UUID U3 = UUID.fromString("33333333-3333-4333-8333-333333333333");
    static final UUID U4 = UUID.fromString("44444444-4444-4444-8444-444444444444");
    static final UUID C1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID C3 = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    static List<SocialListImportPort.ImportedBeneficiaryRow> importedRows() {
        SocialListImportPort.ImportOutcome outcome = new DeclaredCsvSocialListImporter().importBatch(csv(
                row(F1, P1, "HOLDER", "CADUNICO", "2025-06-01", "2026-01-01", "", "MUN-A"),
                row(F1, P2, "MEMBER", "CADUNICO", "2025-06-01", "2026-01-01", "", "MUN-A"),
                row(F2, P9, "HOLDER", "BPC", "", "2026-01-01", "", "MUN-A"),
                row(F3, P5, "HOLDER", "BPC", "", "2026-01-01", "", ""),
                row(F4, P7, "HOLDER", "CADUNICO", "2024-12-10", "2026-01-01", "", "")),
                DeclaredSocialListFormat.proofFormat());
        assertThat(outcome.rejections()).isEmpty();
        return outcome.accepted();
    }

    static List<SocialCrossingEngine.UnitView> units() {
        return List.of(
                new SocialCrossingEngine.UnitView(U1, C1, Set.of(P1, P2), true, true, true,
                        LocalDate.of(2020, 1, 1), "MUN-A", "R-01"),
                new SocialCrossingEngine.UnitView(U2, C1, Set.of(P1), false, false, false,
                        LocalDate.of(2023, 1, 1), "MUN-A", "R-01"),
                new SocialCrossingEngine.UnitView(U3, C3, Set.of(P5), false, true, true,
                        LocalDate.of(2021, 5, 1), "MUN-B", "R-02"),
                new SocialCrossingEngine.UnitView(U4, C3, Set.of(P7), false, true, true,
                        LocalDate.of(2022, 1, 1), "MUN-B", "R-02"));
    }

    private SocialCrossingEngine.CrossingResult cross() {
        return SocialCrossingEngine.cross(importedRows(), units(), REF, SocialTariffCycleParameters.proofParameters());
    }

    @Test
    @DisplayName("correspondencias exactas: F1 e F3 casam, uma inscricao por familia")
    void exactMatches() {
        SocialCrossingEngine.CrossingResult result = cross();
        assertThat(result.matched()).extracting(SocialCrossingEngine.CrossedFamily::familyCodeHash)
                .containsExactlyInAnyOrder(F1, F3);
    }

    @Test
    @DisplayName("desempate da P1.5: F1 fica com U1 (responsavel CadUnico), nao com U2")
    void tiebreakWinnerIsRespected() {
        SocialCrossingEngine.CrossedFamily f1 = cross().matched().stream()
                .filter(f -> f.familyCodeHash().equals(F1)).findFirst().orElseThrow();
        assertThat(f1.unitId()).isEqualTo(U1);
        assertThat(f1.holderCustomerId()).isEqualTo(C1);
        assertThat(f1.municipalityCode()).isEqualTo("MUN-A");
        assertThat(f1.routeCode()).isEqualTo("R-01");
        assertThat(f1.personCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("membro da familia tambem casa: unidade so com o hash do membro corresponde")
    void memberHashAlsoMatches() {
        List<SocialCrossingEngine.UnitView> onlyMemberUnit = List.of(
                new SocialCrossingEngine.UnitView(U2, C1, Set.of(P2), false, true, false,
                        LocalDate.of(2023, 1, 1), "MUN-A", "R-01"));
        SocialCrossingEngine.CrossingResult result = SocialCrossingEngine.cross(
                importedRows(), onlyMemberUnit, REF, SocialTariffCycleParameters.proofParameters());
        assertThat(result.matched()).extracting(SocialCrossingEngine.CrossedFamily::familyCodeHash)
                .containsExactly(F1);
    }

    @Test
    @DisplayName("sem-correspondencia exacta: F2 elegivel sem unidade")
    void exactUnmatched() {
        SocialCrossingEngine.CrossingResult result = cross();
        assertThat(result.unmatched()).singleElement().satisfies(f -> {
            assertThat(f.familyCodeHash()).isEqualTo(F2);
            assertThat(f.municipalityCode()).isEqualTo("MUN-A");
            assertThat(f.personCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("inelegivel exacto: F4 com CadUnico de 24 meses e 1 dia nao casa mesmo com unidade")
    void exactIneligible() {
        SocialCrossingEngine.CrossingResult result = cross();
        assertThat(result.ineligible()).extracting(SocialCrossingEngine.IneligibleFamily::familyCodeHash)
                .containsExactly(F4);
        assertThat(result.matched()).extracting(SocialCrossingEngine.CrossedFamily::familyCodeHash)
                .doesNotContain(F4);
    }

    @Test
    @DisplayName("registo de divergencias exacto: uma por familia nao casada ou inelegivel")
    void exactDivergenceRegister() {
        SocialCrossingEngine.CrossingResult result = cross();
        assertThat(result.divergences()).extracting(d -> d.familyCodeHash() + ":" + d.kind())
                .containsExactlyInAnyOrder(F2 + ":NO_UNIT_MATCH", F4 + ":INELIGIBLE");
    }
}
