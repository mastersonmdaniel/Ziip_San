package br.com.ziipsan.commercial.billing.social;

import br.com.ziipsan.commercial.billing.domain.SocialTariffEnrollmentEntity;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billing.tariff.TariffEngine;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static br.com.ziipsan.commercial.billing.social.SocialEligibility.Basis.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Um teste por caso da P1.5 (plano v3.0, linha 630). Todos os valores de tarifa sao os
 * ficticios de {@code FictitiousTariffs}; nenhum dado real ou pessoal aparece aqui (os
 * hashes sao literais sinteticos).
 */
class SocialEnrollmentTest {

    private static final LocalDate REF = LocalDate.of(2026, 11, 3);
    private static final String FAMILY = "hash-familia-sintetica-01";
    private static final String HOLDER = "hash-documento-titular-01";
    private static final String MEMBER = "hash-documento-membro-01";

    // --- Elegibilidade: CadUnico de 25 meses inelegivel, de 24 elegivel ---------------

    @Test
    @DisplayName("CadUnico atualizado ha 25 meses e inelegivel (limite 24)")
    void cadunico25MonthsIsIneligible() {
        assertThat(SocialEligibility.isEligible(CADUNICO, REF.minusMonths(25), REF, 24)).isFalse();
    }

    @Test
    @DisplayName("CadUnico atualizado ha exatamente 24 meses ainda e elegivel")
    void cadunico24MonthsIsEligible() {
        assertThat(SocialEligibility.isEligible(CADUNICO, REF.minusMonths(24), REF, 24)).isTrue();
    }

    @Test
    @DisplayName("BPC da direito sem data de CadUnico")
    void bpcNeedsNoCadunicoDate() {
        assertThat(SocialEligibility.isEligible(BPC, null, REF, 24)).isTrue();
    }

    // --- Piso federal: beneficio abaixo do piso recusado ------------------------------

    @Test
    @DisplayName("piso federal 50% ate 15 m3: banda 0..15 a 50% passa")
    void floorPassesAt50UpTo15() {
        SocialBenefitFloor.validate(social("0", band(0, 15, "50")));
    }

    @Test
    @DisplayName("beneficio abaixo do piso federal recusado: 25% ate 15 m3")
    void belowFloorIsRefused() {
        assertThatThrownBy(() -> SocialBenefitFloor.validate(social("0", band(0, 15, "25"))))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("piso federal");
    }

    @Test
    @DisplayName("beneficio abaixo do piso federal recusado: lacuna antes de 15 m3")
    void gapBefore15IsRefused() {
        assertThatThrownBy(() -> SocialBenefitFloor.validate(social("0", band(0, 10, "50"), band(12, 20, "50"))))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("piso federal");
    }

    @Test
    @DisplayName("banda sem limite a 50% cobre o piso")
    void openBandAt50CoversFloor() {
        SocialBenefitFloor.validate(social("0", band(0, null, "50")));
    }

    // --- Ciclo de vida: aviso de 3 meses e graca GRACE_3M ------------------------------

    @Test
    @DisplayName("perda so depois de aviso na fatura por no minimo 3 meses")
    void lossRequiresThreeMonthsOfNotice() {
        LocalDate notice = LocalDate.of(2026, 8, 1);
        assertThat(SocialEnrollmentLifecycle.lossNoticePeriodSatisfied(notice, LocalDate.of(2026, 10, 31), 3)).isFalse();
        assertThat(SocialEnrollmentLifecycle.lossNoticePeriodSatisfied(notice, LocalDate.of(2026, 11, 1), 3)).isTrue();
    }

    @Test
    @DisplayName("GRACE_3M: graca de 3 meses apos deixar de ser elegivel")
    void graceLastsThreeMonths() {
        LocalDate graceStart = LocalDate.of(2026, 9, 15);
        assertThat(SocialEnrollmentLifecycle.graceDeadline(graceStart, 3)).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(SocialEnrollmentLifecycle.gracePeriodSatisfied(graceStart, LocalDate.of(2026, 12, 14), 3)).isFalse();
        assertThat(SocialEnrollmentLifecycle.gracePeriodSatisfied(graceStart, LocalDate.of(2026, 12, 15), 3)).isTrue();
    }

    // --- Desempate: uma economia por familia ------------------------------------------

    @Test
    @DisplayName("desempate: responsavel no CadUnico vence titular da familia")
    void cadunicoResponsibleBeatsFamilyHead() {
        UUID responsible = UUID.randomUUID();
        UUID head = UUID.randomUUID();
        var winner = SocialFamilyTiebreak.pick(List.of(
                new SocialFamilyTiebreak.Candidate(head, false, true, true, LocalDate.of(2026, 1, 1)),
                new SocialFamilyTiebreak.Candidate(responsible, true, false, false, LocalDate.of(2020, 1, 1))));
        assertThat(winner).isPresent();
        assertThat(winner.get().unitId()).isEqualTo(responsible);
    }

    @Test
    @DisplayName("desempate: mesmo endereco vence titular; ligacao mais recente vence o resto")
    void addressThenRecency() {
        UUID sameAddress = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        UUID old = UUID.randomUUID();
        var winner = SocialFamilyTiebreak.pick(List.of(
                new SocialFamilyTiebreak.Candidate(old, false, false, false, LocalDate.of(2020, 1, 1)),
                new SocialFamilyTiebreak.Candidate(recent, false, false, false, LocalDate.of(2026, 1, 1)),
                new SocialFamilyTiebreak.Candidate(sameAddress, false, true, false, LocalDate.of(2019, 1, 1))));
        assertThat(winner.get().unitId()).isEqualTo(sameAddress);
        var recentWins = SocialFamilyTiebreak.pick(List.of(
                new SocialFamilyTiebreak.Candidate(old, false, false, false, LocalDate.of(2020, 1, 1)),
                new SocialFamilyTiebreak.Candidate(recent, false, false, false, LocalDate.of(2026, 1, 1))));
        assertThat(recentWins.get().unitId()).isEqualTo(recent);
    }

    // --- Classificacao automatica pela lista oficial -----------------------------------

    @Test
    @DisplayName("lista oficial: cruzamento pelo CPF do titular gera uma inscricao")
    void officialListMatchCreatesOneEnrollment() {
        UUID unit = UUID.randomUUID();
        var result = SocialClassification.classify(
                List.of(new SocialClassification.BaseRecord(FAMILY, HOLDER, "HOLDER", CADUNICO, REF.minusMonths(3))),
                List.of(new SocialClassification.UnitInfo(unit, Set.of(HOLDER), true, true, true, REF)),
                REF, 24);
        assertThat(result.enrollments()).containsExactly(
                new SocialClassification.EnrollmentCandidate(unit, FAMILY, CADUNICO));
        assertThat(result.unmatchedForActiveSearch()).isEmpty();
        assertThat(result.ineligible()).isEmpty();
    }

    @Test
    @DisplayName("lista oficial: cruzamento por membro da familia tambem casa")
    void memberMatchCounts() {
        UUID unit = UUID.randomUUID();
        var result = SocialClassification.classify(
                List.of(new SocialClassification.BaseRecord(FAMILY, MEMBER, "MEMBER", CADUNICO, REF.minusMonths(1))),
                List.of(new SocialClassification.UnitInfo(unit, Set.of(HOLDER, MEMBER), false, true, false, REF)),
                REF, 24);
        assertThat(result.enrollments()).hasSize(1);
        assertThat(result.enrollments().get(0).unitId()).isEqualTo(unit);
    }

    @Test
    @DisplayName("lista oficial: elegivel sem unidade vai para a busca ativa, nao some")
    void eligibleWithoutUnitGoesToActiveSearch() {
        var result = SocialClassification.classify(
                List.of(new SocialClassification.BaseRecord(FAMILY, HOLDER, "HOLDER", CADUNICO, REF.minusMonths(2))),
                List.of(), REF, 24);
        assertThat(result.enrollments()).isEmpty();
        assertThat(result.unmatchedForActiveSearch()).hasSize(1);
    }

    @Test
    @DisplayName("lista oficial: CadUnico de 25 meses vai para inelegiveis, nao gera inscricao")
    void staleCadunicoGoesToIneligible() {
        UUID unit = UUID.randomUUID();
        var result = SocialClassification.classify(
                List.of(new SocialClassification.BaseRecord(FAMILY, HOLDER, "HOLDER", CADUNICO, REF.minusMonths(25))),
                List.of(new SocialClassification.UnitInfo(unit, Set.of(HOLDER), true, true, true, REF)),
                REF, 24);
        assertThat(result.enrollments()).isEmpty();
        assertThat(result.ineligible()).hasSize(1);
    }

    @Test
    @DisplayName("lista oficial: familia com duas unidades gera uma unica inscricao, na vencedora")
    void oneEnrollmentPerFamilyAcrossUnits() {
        UUID loser = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        var result = SocialClassification.classify(
                List.of(new SocialClassification.BaseRecord(FAMILY, HOLDER, "HOLDER", CADUNICO, REF.minusMonths(1))),
                List.of(
                        new SocialClassification.UnitInfo(loser, Set.of(HOLDER), false, false, false, LocalDate.of(2020, 1, 1)),
                        new SocialClassification.UnitInfo(winner, Set.of(HOLDER), true, false, false, LocalDate.of(2020, 1, 1))),
                REF, 24);
        assertThat(result.enrollments()).hasSize(1);
        assertThat(result.enrollments().get(0).unitId()).isEqualTo(winner);
    }

    // --- T-03 ligado a inscricao: 102,00 e 87,00 ----------------------------------------

    @Test
    @DisplayName("T-03 com inscricao ativa: banda 0..15 a 50% da 102,00; com 50% na fixa da 87,00")
    void t03WithActiveEnrollmentGives102And87() {
        var withoutFixed = TariffEngine.categoriesFor(t1WithSocial(social("0", band(0, 15, "50"))),
                List.of(socialRes(1, 1)), 27).get(0);
        assertThat(withoutFixed.gross()).isEqualByComparingTo("112.00");
        assertThat(withoutFixed.net()).isEqualByComparingTo("102.00");

        var withFixed = TariffEngine.categoriesFor(t1WithSocial(social("50", band(0, 15, "50"))),
                List.of(socialRes(1, 1)), 27).get(0);
        assertThat(withFixed.net()).isEqualByComparingTo("87.00");
    }

    @Test
    @DisplayName("T-03 sem inscricao (0 economias sociais) fica em 112,00")
    void t03WithoutEnrollmentStays112() {
        var charge = TariffEngine.categoriesFor(t1WithSocial(social("0", band(0, 15, "50"))),
                List.of(res(1)), 27).get(0);
        assertThat(charge.net()).isEqualByComparingTo("112.00");
    }

    // --- Sem campo de adimplencia (classe a) --------------------------------------------

    @Test
    @DisplayName("a inscricao nao tem nenhum campo de adimplencia")
    void enrollmentHasNoPaymentStandingField() {
        Set<String> fields = java.util.Arrays.stream(SocialTariffEnrollmentEntity.class.getDeclaredFields())
                .map(f -> {
                    Column c = f.getAnnotation(Column.class);
                    return c == null ? f.getName() : c.name();
                })
                .collect(Collectors.toSet());
        assertThat(fields).noneMatch(name ->
                name.contains("payment") || name.contains("adimpl") || name.contains("paid")
                        || name.contains("delinquen") || name.contains("default"));
    }
}
