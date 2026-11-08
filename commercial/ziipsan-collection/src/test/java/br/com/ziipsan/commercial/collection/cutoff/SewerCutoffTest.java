package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.cutoff.CutoffEnums;
import br.com.ziipsan.core.cutoff.ReconnectionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Corte e religacao de esgoto (SG-503/SG-504) sobre os bloqueios da P3.4. */
class SewerCutoffTest {

    private static ZonedDateTime monday() {
        return ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, CutoffExtTestSupport.TZ);
    }

    @Test
    @DisplayName("corte de esgoto sem cut.sewer_allowed: recusado pelo portao da P3.4 (padrao nao)")
    void sewerCutRefusedByDefault() {
        assertThatThrownBy(() -> SewerCutoff.assertSewerCutAllowed(CutoffEnums.Reason.REQUEST,
                monday(), CutoffExtTestSupport.scCalendar(), false, null, null, Set.of(), null,
                CutoffExtTestSupport.cutoffParams(false)))
                .isInstanceOf(ApiException.Conflict.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("cutoff-sewer-not-allowed");
    }

    @Test
    @DisplayName("corte de esgoto com cut.sewer_allowed aceito e fora das vedacoes: permitido")
    void sewerCutAllowedWhenParamAllows() {
        assertThatCode(() -> SewerCutoff.assertSewerCutAllowed(CutoffEnums.Reason.REQUEST,
                monday(), CutoffExtTestSupport.scCalendar(), false, null, null, Set.of(), null,
                CutoffExtTestSupport.cutoffParams(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("D1: corte de esgoto por inadimplencia (DEFAULT) COM aviso e sewer_allowed: PERMITIDO pelo portao da P3.4")
    void sewerCutDefaultWithNoticeAllowed() {
        UUID unit = UUID.randomUUID();
        var notice = CutoffExtTestSupport.deliveredNotice(unit);
        assertThatCode(() -> SewerCutoff.assertSewerCutAllowed(CutoffEnums.Reason.DEFAULT,
                monday(), CutoffExtTestSupport.scCalendar(), false, null, null, Set.of(), notice,
                CutoffExtTestSupport.cutoffParams(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("D1: corte de esgoto por inadimplencia (DEFAULT) SEM aviso: recusado pelo portao da P3.4 (noticeId REQUIRED)")
    void sewerCutDefaultWithoutNoticeRefused() {
        assertThatThrownBy(() -> SewerCutoff.assertSewerCutAllowed(CutoffEnums.Reason.DEFAULT,
                monday(), CutoffExtTestSupport.scCalendar(), false, null, null, Set.of(), null,
                CutoffExtTestSupport.cutoffParams(true)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(((ApiException.Validation) e).errors())
                        .anySatisfy(err -> {
                            assertThat(err.field()).isEqualTo("noticeId");
                            assertThat(err.code()).isEqualTo("REQUIRED");
                        }));
    }

    @Test
    @DisplayName("D4: religacao de esgoto sem carta e com parametros nulos: erro explicito, nao decisao com condicao nula")
    void sewerReconnectionNullParamsExplicitError() {
        var facts = new ReconnectionRule.PaymentFacts(true, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.DELIVERED);
        var now = OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
        assertThatThrownBy(() -> SewerCutoff.evaluateSewerReconnection(null, facts, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("religacao de esgoto sem a carta de liberacao: nao gera, com motivo registrado")
    void sewerReconnectionNeedsLetter() {
        var facts = new ReconnectionRule.PaymentFacts(true, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.DELIVERED);
        var decision = SewerCutoff.evaluateSewerReconnection(null, facts,
                CutoffExtTestSupport.cutoffParams(true),
                OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(decision.createOrder()).isFalse();
        assertThat(decision.blockedReason()).isEqualTo("SEWER_RELEASE_LETTER_REQUIRED");
    }

    @Test
    @DisplayName("religacao de esgoto com carta e condicao da P3.4: gera, com taxa e SLA da regra da P3.4")
    void sewerReconnectionWithLetter() {
        var templates = new CutoffExtTestSupport.FakeTemplates();
        UUID unit = UUID.randomUUID();
        var letter = SewerCutoff.issueReleaseLetter(UUID.randomUUID(), unit, Map.of("unitRef", "UC-9"),
                templates, CutoffExtTestSupport.extParams());
        assertThat(letter.templateCode()).isEqualTo("LIBERACAO_ESGOTO");
        assertThat(letter.templateVersion()).isEqualTo("v7");
        var paymentAt = OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
        var facts = new ReconnectionRule.PaymentFacts(true, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.DELIVERED);
        var decision = SewerCutoff.evaluateSewerReconnection(letter, facts,
                CutoffExtTestSupport.cutoffParams(true), paymentAt);
        assertThat(decision.createOrder()).isTrue();
        assertThat(decision.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.CHARGED);
        assertThat(decision.slaDueAt()).isEqualTo(paymentAt.plusHours(48));
        // Sem prova do aviso: a religacao e SEM taxa, pela regra da P3.4 (Lei 13.460/2017).
        var noProof = new ReconnectionRule.PaymentFacts(true, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.NOT_DELIVERED);
        var waived = SewerCutoff.evaluateSewerReconnection(letter, noProof,
                CutoffExtTestSupport.cutoffParams(true), paymentAt);
        assertThat(waived.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.WAIVED_NO_PROOF);
    }
}
