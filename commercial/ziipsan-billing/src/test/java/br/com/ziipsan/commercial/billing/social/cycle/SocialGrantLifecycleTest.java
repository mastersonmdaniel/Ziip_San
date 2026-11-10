package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialBenefitConference;
import br.com.ziipsan.commercial.billing.social.SocialEligibility;
import br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static br.com.ziipsan.commercial.billing.social.cycle.SocialCycleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * II-3/E4 - concessao sem pedido e ciclo de vida com trilha (aceite
 * SS4.3 da estimativa): elegivel vira concedido com trilha; nao
 * elegivel nunca e concedido; cessacao respeita aviso e graca da P1.5.
 */
class SocialGrantLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-12-11T12:00:00Z");
    private static final SocialTariffCycleParameters PARAMS = SocialTariffCycleParameters.proofParameters();

    private static SocialBenefitConference.ListMetadata list(SocialProgramListPolicy.ListSource source) {
        return new SocialBenefitConference.ListMetadata(source, "Lei 14.898/2024", "1", LocalDate.of(2026, 12, 1));
    }

    private static SocialGrantLifecycle.Grant grantedBpc() {
        SocialCrossingEngine.CrossedFamily family = new SocialCrossingEngine.CrossedFamily(
                F2, SocialEligibility.Basis.BPC, null, LocalDate.of(2026, 1, 1), null,
                SocialCrossingEngineTest.U3, SocialCrossingEngineTest.C3, "MUN-B", "R-02", 1);
        return SocialGrantLifecycle.grantWithoutRequest(family, list(SocialProgramListPolicy.ListSource.REGULATOR_LIST),
                SocialProgramListPolicy.LegalNature.PRIVATE, REF, PARAMS, T0, "cycle-test");
    }

    @Test
    @DisplayName("elegivel vira concedido sem pedido, com a primeira entrada da trilha")
    void eligibleBecomesGrantedWithTrail() {
        SocialGrantLifecycle.Grant grant = grantedBpc();
        assertThat(grant.status()).isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
        assertThat(grant.trail()).singleElement().satisfies(e -> {
            assertThat(e.sequence()).isEqualTo(1);
            assertThat(e.fromStatus()).isEqualTo(SocialGrantLifecycle.GrantStatus.ELIGIBLE);
            assertThat(e.toStatus()).isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
            assertThat(e.reasonCode()).isEqualTo("GRANT_WITHOUT_REQUEST");
            assertThat(e.occurredAt()).isEqualTo(T0);
        });
        assertThat(grant.legalBasis()).isEqualTo("Lei 14.898/2024");
        assertThat(grant.familyCodeHash()).isEqualTo(F2);
    }

    @Test
    @DisplayName("origem CONECTA_API com prestador privado recusa; com autarquia concede (PLT-36 da ED-57)")
    void listSourceLockDelegated() {
        SocialCrossingEngine.CrossedFamily family = new SocialCrossingEngine.CrossedFamily(
                F2, SocialEligibility.Basis.BPC, null, LocalDate.of(2026, 1, 1), null,
                SocialCrossingEngineTest.U3, SocialCrossingEngineTest.C3, "MUN-B", "R-02", 1);
        assertThatThrownBy(() -> SocialGrantLifecycle.grantWithoutRequest(family,
                list(SocialProgramListPolicy.ListSource.CONECTA_API),
                SocialProgramListPolicy.LegalNature.PRIVATE, REF, PARAMS, T0, "cycle-test"))
                .isInstanceOf(SocialGrantLifecycle.SocialGrantException.class)
                .extracting(e -> ((SocialGrantLifecycle.SocialGrantException) e).reason())
                .isEqualTo(SocialGrantLifecycle.RefusalReason.LIST_SOURCE_NOT_ALLOWED);
        assertThat(SocialGrantLifecycle.grantWithoutRequest(family,
                list(SocialProgramListPolicy.ListSource.CONECTA_API),
                SocialProgramListPolicy.LegalNature.AUTARCHY, REF, PARAMS, T0, "cycle-test").status())
                .isEqualTo(SocialGrantLifecycle.GrantStatus.GRANTED);
    }

    @Test
    @DisplayName("aviso de perda: fim antes de 3 meses recusa, em 3 meses cessa, trilha sequencial")
    void lossNoticePath() {
        SocialGrantLifecycle.Grant grant = SocialGrantLifecycle.beginLossNotice(
                grantedBpc(), LocalDate.of(2027, 1, 5), T0.plusSeconds(60), "cycle-test");
        assertThat(grant.status()).isEqualTo(SocialGrantLifecycle.GrantStatus.UNDER_REVIEW);
        assertThatThrownBy(() -> SocialGrantLifecycle.end(grant, LocalDate.of(2027, 4, 4),
                "CADUNICO_EXPIRED", PARAMS, T0.plusSeconds(120), "cycle-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aviso de perda");
        SocialGrantLifecycle.Grant ended = SocialGrantLifecycle.end(grant, LocalDate.of(2027, 4, 5),
                "CADUNICO_EXPIRED", PARAMS, T0.plusSeconds(120), "cycle-test");
        assertThat(ended.status()).isEqualTo(SocialGrantLifecycle.GrantStatus.ENDED);
        assertThat(ended.endReasonCode()).isEqualTo("CADUNICO_EXPIRED");
        assertThat(ended.trail()).extracting(SocialGrantLifecycle.TrailEntry::sequence).containsExactly(1L, 2L, 3L);
        assertThat(ended.trail()).extracting(SocialGrantLifecycle.TrailEntry::toStatus)
                .containsExactly(SocialGrantLifecycle.GrantStatus.GRANTED,
                        SocialGrantLifecycle.GrantStatus.UNDER_REVIEW, SocialGrantLifecycle.GrantStatus.ENDED);
    }

    @Test
    @DisplayName("graca: fim antes de 3 meses recusa, no fim da graca cessa")
    void gracePath() {
        SocialGrantLifecycle.Grant grant = SocialGrantLifecycle.beginGrace(
                grantedBpc(), LocalDate.of(2027, 2, 1), T0.plusSeconds(60), "cycle-test");
        assertThat(grant.status()).isEqualTo(SocialGrantLifecycle.GrantStatus.GRACE);
        assertThatThrownBy(() -> SocialGrantLifecycle.end(grant, LocalDate.of(2027, 4, 30),
                "NO_LONGER_ELIGIBLE", PARAMS, T0.plusSeconds(120), "cycle-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("graca");
        assertThat(SocialGrantLifecycle.end(grant, LocalDate.of(2027, 5, 1),
                "NO_LONGER_ELIGIBLE", PARAMS, T0.plusSeconds(120), "cycle-test").status())
                .isEqualTo(SocialGrantLifecycle.GrantStatus.ENDED);
    }

    @Test
    @DisplayName("cessar directamente de GRANTED recusa: exige aviso ou graca")
    void directEndFromGrantedRefuses() {
        assertThatThrownBy(() -> SocialGrantLifecycle.end(grantedBpc(), LocalDate.of(2027, 1, 1),
                "ANY", PARAMS, T0.plusSeconds(60), "cycle-test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fim sem motivo recusa (catalogo de motivos da V4_2)")
    void endWithoutReasonRefuses() {
        SocialGrantLifecycle.Grant grant = SocialGrantLifecycle.beginGrace(
                grantedBpc(), LocalDate.of(2027, 2, 1), T0.plusSeconds(60), "cycle-test");
        assertThatThrownBy(() -> SocialGrantLifecycle.end(grant, LocalDate.of(2027, 5, 1),
                " ", PARAMS, T0.plusSeconds(120), "cycle-test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recertificacao: sem intervalo definido [A DEFINIR] devolve vazio; com intervalo, soma meses")
    void recertificationDue() {
        SocialGrantLifecycle.Grant grant = grantedBpc();
        assertThat(SocialGrantLifecycle.nextRecertificationDue(grant, PARAMS)).isEmpty();
        SocialTariffCycleParameters withInterval = new SocialTariffCycleParameters(24, 3, 3,
                java.util.OptionalInt.of(12));
        assertThat(SocialGrantLifecycle.nextRecertificationDue(grant, withInterval))
                .isEqualTo(Optional.of(LocalDate.of(2027, 1, 1)));
    }
}
