package br.com.ziipsan.commercial.collection.pix;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.2 - cobranca hibrida (Res. BCB 587/2026, vigente 01/02/2027):
 * um teste por caso, inclusive as recusas. Valores calculados a mao:
 * documento de R$ 100,00 = 10000 centavos, vencimento 10/03/2027.
 */
class HybridChargeRuleTest {

    private static HybridChargeRule.HybridRequest valid() {
        return new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.COMUM, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
    }

    private static void assertRefused(HybridChargeRule.HybridRequest req,
                                      HybridChargeRule.Refusal expected) {
        assertThatThrownBy(() -> HybridChargeRule.requireEligible(req))
                .isInstanceOfSatisfying(HybridChargeRule.HybridRefusedException.class,
                        ex -> assertThat(ex.refusal()).isEqualTo(expected));
    }

    @Test
    void hibridoValidoComumEAceito() {
        assertThat(HybridChargeRule.requireEligible(valid())).isEqualTo(valid());
    }

    @Test
    void hibridoValidoDinamicoEAceito() {
        var dinamico = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.DINAMICO, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertThat(HybridChargeRule.requireEligible(dinamico)).isEqualTo(dinamico);
    }

    @Test
    void antesDaVigenciaERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 1, 31), true,
                HybridChargeRule.SlipKind.COMUM, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.RULE_NOT_YET_EFFECTIVE);
    }

    @Test
    void pixSemVencimentoERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), false,
                HybridChargeRule.SlipKind.COMUM, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.PIX_WITHOUT_DUE_DATE);
    }

    @Test
    void boletoDePropostaERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.PROPOSTA, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.SLIP_KIND_FORBIDDEN);
    }

    @Test
    void boletoDeDepositoERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.DEPOSITO, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.SLIP_KIND_FORBIDDEN);
    }

    @Test
    void boletoVinculadoAAtivoFinanceiroERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.DINAMICO, true, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.SLIP_LINKED_TO_FINANCIAL_ASSET);
    }

    @Test
    void emissorDiferenteDoPspDoRecebedorERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.COMUM, false, "OUTRO", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.SLIP_ISSUER_NOT_RECEIVER_PSP);
    }

    @Test
    void tenantSemOfertaNaoTemHibrido() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.COMUM, false, "PSP_X", "PSP_X", false,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.TENANT_DOES_NOT_OFFER);
    }

    @Test
    void valorDivergenteERecusado() {
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.COMUM, false, "PSP_X", "PSP_X", true,
                10_000, 9_999, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 10));
        assertRefused(req, HybridChargeRule.Refusal.AMOUNT_OR_DUE_DATE_DIVERGE);
    }

    @Test
    void vencimentoDivergenteERecusado() {
        // Mesmo valor (10.000 = 10.000); so o vencimento diverge: 10/03 x 11/03/2027.
        var req = new HybridChargeRule.HybridRequest(LocalDate.of(2027, 3, 1), true,
                HybridChargeRule.SlipKind.COMUM, false, "PSP_X", "PSP_X", true,
                10_000, 10_000, LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 11));
        assertRefused(req, HybridChargeRule.Refusal.AMOUNT_OR_DUE_DATE_DIVERGE);
    }
}
