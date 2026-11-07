package br.com.ziipsan.commercial.billing.social;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.social.SocialEligibility.Basis.BPC;
import static br.com.ziipsan.commercial.billing.social.SocialEligibility.Basis.CADUNICO;
import static br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy.LegalNature.AUTARCHY;
import static br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy.LegalNature.PRIVATE;
import static br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy.LegalNature.PUBLIC_COMPANY;
import static br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy.ListSource.CONECTA_API;
import static br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy.ListSource.MUNICIPAL_BASE;
import static br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy.ListSource.REGULATOR_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-57: conferencia e vigencia do beneficio e origem da lista (PLT-36),
 * sempre delegando a regra da P1.5. Datas e hashes sao sinteticos; a data
 * normativa 11/12/2026 e a da P1.5 no plano (Lei 14.898/2024; NR 13,
 * art. 26, paragrafo 1o).
 */
class SocialBenefitConferenceTest {

    private static final LocalDate REF = LocalDate.of(2027, 1, 15);
    private static final String FAMILY = "hash-familia-sintetica-57";
    private static final String PERSON = "hash-documento-sintetico-57";

    private static SocialClassification.BaseRecord record(
            SocialEligibility.Basis basis, LocalDate cadunicoUpdatedOn) {
        return new SocialClassification.BaseRecord(FAMILY, PERSON, "HOLDER", basis, cadunicoUpdatedOn);
    }

    private static SocialBenefitConference.ListMetadata list(
            SocialProgramListPolicy.ListSource source, String legalBasis) {
        return new SocialBenefitConference.ListMetadata(source, legalBasis, "v1", LocalDate.of(2027, 1, 10));
    }

    // --- Origem da lista x natureza juridica (PLT-36) --------------------------------

    @Test
    @DisplayName("cessao direta do CadUnico (CONECTA_API) so para ente de direito publico")
    void conectaApiOnlyForPublicLawBody() {
        assertThat(SocialProgramListPolicy.isPublicLawBody(AUTARCHY)).isTrue();
        assertThat(SocialProgramListPolicy.isPublicLawBody(PRIVATE)).isFalse();
        assertThat(SocialProgramListPolicy.isPublicLawBody(PUBLIC_COMPANY)).isFalse();
        assertThat(SocialProgramListPolicy.isAllowed(CONECTA_API, AUTARCHY)).isTrue();
        assertThat(SocialProgramListPolicy.isAllowed(CONECTA_API, PRIVATE)).isFalse();
        assertThatThrownBy(() -> SocialProgramListPolicy.requireAllowed(CONECTA_API, PRIVATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decreto 11.016/2022");
    }

    @Test
    @DisplayName("lista da agencia e base municipal nao sao cessao direta: sem trava de natureza")
    void otherSourcesHaveNoNatureLock() {
        assertThat(SocialProgramListPolicy.isAllowed(REGULATOR_LIST, PRIVATE)).isTrue();
        assertThat(SocialProgramListPolicy.isAllowed(MUNICIPAL_BASE, PRIVATE)).isTrue();
        SocialProgramListPolicy.requireAllowed(REGULATOR_LIST, PRIVATE);
    }

    @Test
    @DisplayName("regra legal aplicavel a partir de 11/12/2026 (data normativa da P1.5)")
    void legalRuleAppliesFrom2026_12_11() {
        assertThat(SocialProgramListPolicy.legalRuleAppliesOn(LocalDate.of(2026, 12, 10))).isFalse();
        assertThat(SocialProgramListPolicy.legalRuleAppliesOn(LocalDate.of(2026, 12, 11))).isTrue();
        assertThat(SocialProgramListPolicy.legalRuleAppliesOn(REF)).isTrue();
    }

    // --- Conferencia: elegibilidade delegada a P1.5 -----------------------------------

    @Test
    @DisplayName("conferencia: CadUnico de 25 meses e inelegivel, como na P1.5; o de 24, elegivel")
    void conferenceDelegatesCadunicoAgeToP15() {
        var validity = new SocialBenefitConference.BenefitValidity(LocalDate.of(2026, 1, 1), null);
        var stale = SocialBenefitConference.check(record(CADUNICO, REF.minusMonths(25)), validity, REF, 24);
        assertThat(stale.eligible()).isFalse();
        assertThat(stale.applicable()).isFalse();
        var fresh = SocialBenefitConference.check(record(CADUNICO, REF.minusMonths(24)), validity, REF, 24);
        assertThat(fresh.eligible()).isTrue();
        assertThat(fresh.applicable()).isTrue();
    }

    @Test
    @DisplayName("conferencia: beneficio fora da vigencia declarada nao e aplicavel, mesmo elegivel")
    void conferenceChecksDeclaredValidity() {
        var ended = new SocialBenefitConference.BenefitValidity(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        var result = SocialBenefitConference.check(record(BPC, null), ended, REF, 24);
        assertThat(result.eligible()).isTrue();
        assertThat(result.withinValidity()).isFalse();
        assertThat(result.applicable()).isFalse();
        // Fronteiras contam como cobertas.
        assertThat(ended.covers(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(ended.covers(LocalDate.of(2026, 12, 31))).isTrue();
        assertThatThrownBy(() -> new SocialBenefitConference.BenefitValidity(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("conferencia antes de 11/12/2026: elegivel e vigente, mas a regra legal ainda nao se aplica")
    void conferenceBeforeLegalRuleDate() {
        var validity = new SocialBenefitConference.BenefitValidity(LocalDate.of(2026, 1, 1), null);
        var result = SocialBenefitConference.check(record(BPC, null), validity,
                LocalDate.of(2026, 12, 10), 24);
        assertThat(result.eligible()).isTrue();
        assertThat(result.withinValidity()).isTrue();
        assertThat(result.legalRuleApplicable()).isFalse();
        assertThat(result.applicable()).isFalse();
    }

    @Test
    @DisplayName("fim do beneficio respeita o aviso de perda da P1.5 (delegacao, sem recalculo)")
    void endRespectsLossNoticeByDelegation() {
        LocalDate notice = LocalDate.of(2026, 8, 1);
        assertThat(SocialBenefitConference.endRespectsLossNotice(notice, LocalDate.of(2026, 10, 31), 3))
                .isFalse();
        assertThat(SocialBenefitConference.endRespectsLossNotice(notice, LocalDate.of(2026, 11, 1), 3))
                .isTrue();
    }

    // --- Rascunho de inscricao ----------------------------------------------------------

    @Test
    @DisplayName("rascunho: candidato da classificacao vira inscricao OFFICIAL_LIST com a base legal da lista")
    void draftFromClassificationCandidate() {
        UUID unit = UUID.randomUUID();
        UUID holder = UUID.randomUUID();
        var candidate = new SocialClassification.EnrollmentCandidate(unit, FAMILY, CADUNICO);
        var draft = SocialBenefitConference.draftEnrollment(candidate,
                record(CADUNICO, REF.minusMonths(3)), holder,
                list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE,
                new SocialBenefitConference.BenefitValidity(LocalDate.of(2027, 1, 1), null),
                1, REF, 24);
        assertThat(draft.unitId()).isEqualTo(unit);
        assertThat(draft.holderCustomerId()).isEqualTo(holder);
        assertThat(draft.familyCodeHash()).isEqualTo(FAMILY);
        assertThat(draft.eligibilityBasis()).isEqualTo(CADUNICO);
        assertThat(draft.enrollmentSource()).isEqualTo("OFFICIAL_LIST");
        assertThat(draft.status()).isEqualTo("ACTIVE");
        assertThat(draft.legalBasis()).isEqualTo("Lei 14.898/2024");
        assertThat(draft.validFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(draft.validTo()).isNull();
        assertThat(draft.socialEconomies()).isEqualTo(1);
    }

    @Test
    @DisplayName("rascunho recusa: origem travada, inelegivel, fora da vigencia, sem base legal, familia divergente")
    void draftRefusals() {
        UUID unit = UUID.randomUUID();
        var candidate = new SocialClassification.EnrollmentCandidate(unit, FAMILY, CADUNICO);
        var validity = new SocialBenefitConference.BenefitValidity(LocalDate.of(2027, 1, 1), null);

        assertThatThrownBy(() -> SocialBenefitConference.draftEnrollment(candidate,
                record(CADUNICO, REF.minusMonths(3)), UUID.randomUUID(),
                list(CONECTA_API, "Lei 14.898/2024"), PRIVATE, validity, 1, REF, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decreto 11.016/2022");

        assertThatThrownBy(() -> SocialBenefitConference.draftEnrollment(candidate,
                record(CADUNICO, REF.minusMonths(25)), UUID.randomUUID(),
                list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE, validity, 1, REF, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inelegivel");

        assertThatThrownBy(() -> SocialBenefitConference.draftEnrollment(candidate,
                record(CADUNICO, REF.minusMonths(3)), UUID.randomUUID(),
                list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE,
                new SocialBenefitConference.BenefitValidity(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), 1, REF, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vigencia");

        assertThatThrownBy(() -> list(REGULATOR_LIST, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base legal");

        var otherFamily = new SocialClassification.BaseRecord("outra-familia", PERSON, "HOLDER",
                CADUNICO, REF.minusMonths(3));
        assertThatThrownBy(() -> SocialBenefitConference.draftEnrollment(candidate, otherFamily,
                UUID.randomUUID(), list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE, validity, 1, REF, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("familia");
    }

    @Test
    @DisplayName("A2: rascunho antes de 11/12/2026 e recusado - a regra legal ainda nao vigora, mesmo elegivel e vigente")
    void draftBeforeLegalRuleDateRefused() {
        var candidate = new SocialClassification.EnrollmentCandidate(UUID.randomUUID(), FAMILY, BPC);
        var validity = new SocialBenefitConference.BenefitValidity(LocalDate.of(2026, 1, 1), null);
        assertThatThrownBy(() -> SocialBenefitConference.draftEnrollment(candidate,
                record(BPC, null), UUID.randomUUID(),
                list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE, validity, 1,
                LocalDate.of(2026, 12, 10), 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11/12/2026");
    }

    @Test
    @DisplayName("A2: rascunho exatamente em 11/12/2026 e aceito - a regra legal vigora a partir da data normativa")
    void draftOnLegalRuleDateAccepted() {
        UUID unit = UUID.randomUUID();
        var candidate = new SocialClassification.EnrollmentCandidate(unit, FAMILY, BPC);
        var draft = SocialBenefitConference.draftEnrollment(candidate,
                record(BPC, null), UUID.randomUUID(),
                list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE,
                new SocialBenefitConference.BenefitValidity(LocalDate.of(2026, 1, 1), null),
                1, LocalDate.of(2026, 12, 11), 24);
        assertThat(draft.unitId()).isEqualTo(unit);
        assertThat(draft.eligibilityBasis()).isEqualTo(BPC);
        assertThat(draft.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("A4: rascunho recusa candidato cuja base diverge da base do registro conferido")
    void draftRefusesDivergingBasis() {
        var candidate = new SocialClassification.EnrollmentCandidate(UUID.randomUUID(), FAMILY, CADUNICO);
        var validity = new SocialBenefitConference.BenefitValidity(LocalDate.of(2027, 1, 1), null);
        assertThatThrownBy(() -> SocialBenefitConference.draftEnrollment(candidate,
                record(BPC, null), UUID.randomUUID(),
                list(REGULATOR_LIST, "Lei 14.898/2024"), PRIVATE, validity, 1, REF, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base de elegibilidade");
    }
}
