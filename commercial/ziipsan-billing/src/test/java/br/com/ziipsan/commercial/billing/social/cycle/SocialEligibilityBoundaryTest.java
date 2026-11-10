package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialBenefitConference;
import br.com.ziipsan.commercial.billing.social.SocialEligibility;
import br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.social.cycle.SocialCycleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * II-3/E4 - elegibilidade parametrizavel: casos fronteira por parametro
 * (aceite SS4.2 da estimativa), sempre por delegacao a P1.5/ED-57.
 */
class SocialEligibilityBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-12-11T12:00:00Z");

    private static SocialCrossingEngine.CrossedFamily crossed(String family, SocialEligibility.Basis basis,
                                                              LocalDate cadunico, LocalDate from, LocalDate to) {
        return new SocialCrossingEngine.CrossedFamily(family, basis, cadunico, from, to,
                SocialCrossingEngineTest.U1, SocialCrossingEngineTest.C1, "MUN-A", "R-01", 1);
    }

    private static SocialBenefitConference.ListMetadata list(SocialProgramListPolicy.ListSource source) {
        return new SocialBenefitConference.ListMetadata(source, "Lei 14.898/2024", "1", LocalDate.of(2026, 12, 1));
    }

    private static SocialGrantLifecycle.Grant grant(SocialCrossingEngine.CrossedFamily family, LocalDate ref,
                                                    SocialTariffCycleParameters params) {
        return SocialGrantLifecycle.grantWithoutRequest(family, list(SocialProgramListPolicy.ListSource.REGULATOR_LIST),
                SocialProgramListPolicy.LegalNature.PRIVATE, ref, params, NOW, "cycle-test");
    }

    @Test
    @DisplayName("CadUnico com exactamente 24 meses na referencia e elegivel e concede")
    void cadunicoExactly24MonthsGrants() {
        SocialCrossingEngine.CrossedFamily family = crossed(F1, SocialEligibility.Basis.CADUNICO,
                REF.minusMonths(24), LocalDate.of(2026, 1, 1), null);
        assertThat(grant(family, REF, SocialTariffCycleParameters.proofParameters()).status())
                .isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
    }

    @Test
    @DisplayName("CadUnico com 24 meses e 1 dia na referencia e inelegivel e nunca concede")
    void cadunico24MonthsAndOneDayNeverGrants() {
        SocialCrossingEngine.CrossedFamily family = crossed(F1, SocialEligibility.Basis.CADUNICO,
                REF.minusMonths(24).minusDays(1), LocalDate.of(2026, 1, 1), null);
        assertThatThrownBy(() -> grant(family, REF, SocialTariffCycleParameters.proofParameters()))
                .isInstanceOf(SocialGrantLifecycle.SocialGrantException.class)
                .extracting(e -> ((SocialGrantLifecycle.SocialGrantException) e).reason())
                .isEqualTo(SocialGrantLifecycle.RefusalReason.INELIGIBLE);
    }

    @Test
    @DisplayName("parametro muda a fronteira: com maximo de 12 meses, 13 meses e inelegivel e 12 concede")
    void parameterMovesTheBoundary() {
        SocialTariffCycleParameters params12 = SocialTariffCycleParameters.of(12, 3, 3);
        SocialCrossingEngine.CrossedFamily stale = crossed(F1, SocialEligibility.Basis.CADUNICO,
                REF.minusMonths(13), LocalDate.of(2026, 1, 1), null);
        assertThatThrownBy(() -> grant(stale, REF, params12))
                .isInstanceOf(SocialGrantLifecycle.SocialGrantException.class);
        SocialCrossingEngine.CrossedFamily fresh = crossed(F1, SocialEligibility.Basis.CADUNICO,
                REF.minusMonths(12), LocalDate.of(2026, 1, 1), null);
        assertThat(grant(fresh, REF, params12).status()).isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
    }

    @Test
    @DisplayName("BPC concede sem data de CadUnico em qualquer parametro de idade")
    void bpcGrantsWithoutCadunicoDate() {
        SocialCrossingEngine.CrossedFamily family = crossed(F2, SocialEligibility.Basis.BPC,
                null, LocalDate.of(2026, 1, 1), null);
        assertThat(grant(family, REF, SocialTariffCycleParameters.of(0, 3, 3)).status())
                .isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
    }

    @Test
    @DisplayName("fronteiras da vigencia declarada: inicio e fim contam como cobertos")
    void validityBordersAreCovered() {
        SocialCrossingEngine.CrossedFamily family = crossed(F3, SocialEligibility.Basis.BPC,
                null, REF, REF);
        assertThat(grant(family, REF, SocialTariffCycleParameters.proofParameters()).status())
                .isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
    }

    @Test
    @DisplayName("fora da vigencia declarada: fim na vespera da referencia recusa por OUTSIDE_VALIDITY")
    void outsideValidityRefuses() {
        SocialCrossingEngine.CrossedFamily family = crossed(F3, SocialEligibility.Basis.BPC,
                null, LocalDate.of(2026, 1, 1), REF.minusDays(1));
        assertThatThrownBy(() -> grant(family, REF, SocialTariffCycleParameters.proofParameters()))
                .isInstanceOf(SocialGrantLifecycle.SocialGrantException.class)
                .extracting(e -> ((SocialGrantLifecycle.SocialGrantException) e).reason())
                .isEqualTo(SocialGrantLifecycle.RefusalReason.OUTSIDE_VALIDITY);
    }

    @Test
    @DisplayName("fronteira da regra legal: vespera recusa, 11/12/2026 concede (data da ED-57 reutilizada)")
    void legalRuleBorder() {
        SocialCrossingEngine.CrossedFamily family = crossed(F2, SocialEligibility.Basis.BPC,
                null, LocalDate.of(2026, 1, 1), null);
        assertThatThrownBy(() -> grant(family, REF_BEFORE_LEGAL, SocialTariffCycleParameters.proofParameters()))
                .isInstanceOf(SocialGrantLifecycle.SocialGrantException.class)
                .extracting(e -> ((SocialGrantLifecycle.SocialGrantException) e).reason())
                .isEqualTo(SocialGrantLifecycle.RefusalReason.LEGAL_RULE_NOT_YET_APPLICABLE);
        assertThat(grant(family, REF, SocialTariffCycleParameters.proofParameters()).status())
                .isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
        assertThat(SocialProgramListPolicy.LEGAL_RULE_APPLIES_FROM).isEqualTo(REF);
    }

    @Test
    @DisplayName("cruzamento com parametro de 12 meses poe a familia de 13 meses nos inelegiveis")
    void crossingHonoursParameter() {
        List<SocialListImportPort.ImportedBeneficiaryRow> rows = List.of(
                new SocialListImportPort.ImportedBeneficiaryRow(F1, P1, "HOLDER", SocialEligibility.Basis.CADUNICO,
                        REF.minusMonths(13), LocalDate.of(2026, 1, 1), null, null));
        SocialCrossingEngine.CrossingResult with24 = SocialCrossingEngine.cross(rows,
                SocialCrossingEngineTest.units(), REF, SocialTariffCycleParameters.proofParameters());
        assertThat(with24.matched()).hasSize(1);
        SocialCrossingEngine.CrossingResult with12 = SocialCrossingEngine.cross(rows,
                SocialCrossingEngineTest.units(), REF, SocialTariffCycleParameters.of(12, 3, 3));
        assertThat(with12.matched()).isEmpty();
        assertThat(with12.ineligible()).extracting(SocialCrossingEngine.IneligibleFamily::familyCodeHash)
                .containsExactly(F1);
    }

    @Test
    @DisplayName("unidades do fixture partilhado nao escondem fronteira: conjunto vazio nao cruza nada")
    void emptyUnitsCrossNothing() {
        SocialCrossingEngine.CrossingResult result = SocialCrossingEngine.cross(
                SocialCrossingEngineTest.importedRows(), List.of(), REF,
                SocialTariffCycleParameters.proofParameters());
        assertThat(result.matched()).isEmpty();
        assertThat(result.unmatched()).extracting(SocialCrossingEngine.UnmatchedFamily::familyCodeHash)
                .containsExactlyInAnyOrder(F1, F2, F3);
        assertThat(Set.of(F4)).isEqualTo(Set.of(result.ineligible().get(0).familyCodeHash()));
        assertThat(result.ineligible()).hasSize(1);
    }
}
