package br.com.ziipsan.platform.notification.multichannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** As cinco bases da regra do PDF anexo (ADR-0028, l), uma por teste. */
class BillEmailRuleTest {

    @Test
    @DisplayName("opt-in do morador anexa, qualquer que seja o padrão do tenant")
    void residentOptInAttaches() {
        BillEmailRule.AttachmentDecision d = BillEmailRule.decide(Optional.of(true), false, false);
        assertThat(d.attachPdf()).isTrue();
        assertThat(d.basis()).isEqualTo(BillEmailRule.Basis.RESIDENT_OPT_IN);
    }

    @Test
    @DisplayName("opt-out do morador vence padrão do tenant com aceite formal")
    void residentOptOutBeatsTenantDefault() {
        BillEmailRule.AttachmentDecision d = BillEmailRule.decide(Optional.of(false), true, true);
        assertThat(d.attachPdf()).isFalse();
        assertThat(d.basis()).isEqualTo(BillEmailRule.Basis.LINK_ONLY_RESIDENT_OPT_OUT);
    }

    @Test
    @DisplayName("sem manifestação: padrão do tenant COM aceite formal anexa")
    void tenantDefaultWithFormalAcceptanceAttaches() {
        BillEmailRule.AttachmentDecision d = BillEmailRule.decide(Optional.empty(), true, true);
        assertThat(d.attachPdf()).isTrue();
        assertThat(d.basis()).isEqualTo(BillEmailRule.Basis.TENANT_DEFAULT_WITH_FORMAL_ACCEPTANCE);
    }

    @Test
    @DisplayName("padrão do tenant SEM aceite formal não anexa")
    void tenantDefaultWithoutFormalAcceptanceStaysLinkOnly() {
        BillEmailRule.AttachmentDecision d = BillEmailRule.decide(Optional.empty(), true, false);
        assertThat(d.attachPdf()).isFalse();
        assertThat(d.basis()).isEqualTo(BillEmailRule.Basis.LINK_ONLY_NO_FORMAL_ACCEPTANCE);
    }

    @Test
    @DisplayName("sem manifestação e sem padrão: só o link autenticado")
    void noOptInNoDefaultStaysLinkOnly() {
        BillEmailRule.AttachmentDecision d = BillEmailRule.decide(Optional.empty(), false, false);
        assertThat(d.attachPdf()).isFalse();
        assertThat(d.basis()).isEqualTo(BillEmailRule.Basis.LINK_ONLY_NO_OPT_IN);
    }
}
