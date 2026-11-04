package br.com.ziipsan.platform.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras puras da cobranca do SaaS por contrato (P2.12, ADR-0019), sem
 * banco: matriz de restricao, vencimento, cotas, empenho, margem,
 * reconciliacao, fotografia com hash e reajuste so com apostilamento.
 */
class SaaSContractRulesTest {

    @Test
    @DisplayName("restricao: so OWNER ou FINANCE, e so por inadimplencia contratual")
    void restrictionMatrix() {
        assertThat(SaaSContractRules.canRestrict("PLATFORM_OWNER", "CONTRACT_DEFAULT")).isTrue();
        assertThat(SaaSContractRules.canRestrict("PLATFORM_FINANCE", "CONTRACT_DEFAULT")).isTrue();
        assertThat(SaaSContractRules.canRestrict("PLATFORM_OPS", "CONTRACT_DEFAULT")).isFalse();
        assertThat(SaaSContractRules.canRestrict("PLATFORM_SUPPORT", "CONTRACT_DEFAULT")).isFalse();
        assertThat(SaaSContractRules.canRestrict("PLATFORM_OWNER", "OTHER")).isFalse();
        assertThat(SaaSContractRules.canRestrict("PLATFORM_FINANCE", "OTHER")).isFalse();
        assertThat(SaaSContractRules.canRestrict("ADMIN", "CONTRACT_DEFAULT")).isFalse();
        assertThat(SaaSContractRules.canRestrict(null, "CONTRACT_DEFAULT")).isFalse();
    }

    @Test
    @DisplayName("fatura vencida: so OPEN/PARTIALLY_PAID com vencimento passado")
    void overdue() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        assertThat(SaaSContractRules.isOverdue("OPEN", LocalDate.of(2026, 11, 3), today)).isTrue();
        assertThat(SaaSContractRules.isOverdue("PARTIALLY_PAID", LocalDate.of(2026, 11, 3), today)).isTrue();
        assertThat(SaaSContractRules.isOverdue("OPEN", LocalDate.of(2026, 11, 4), today)).isFalse();
        assertThat(SaaSContractRules.isOverdue("PAID", LocalDate.of(2026, 11, 3), today)).isFalse();
        assertThat(SaaSContractRules.isOverdue("CANCELLED", LocalDate.of(2026, 11, 3), today)).isFalse();
    }

    @Test
    @DisplayName("vencimento: WARNING a 180/90 dias, CRITICAL a 30, nada fora das janelas")
    void expirySeverity() {
        assertThat(SaaSContractRules.expirySeverity(180)).isEqualTo("WARNING");
        assertThat(SaaSContractRules.expirySeverity(90)).isEqualTo("WARNING");
        assertThat(SaaSContractRules.expirySeverity(31)).isEqualTo("WARNING");
        assertThat(SaaSContractRules.expirySeverity(30)).isEqualTo("CRITICAL");
        assertThat(SaaSContractRules.expirySeverity(0)).isEqualTo("CRITICAL");
        assertThat(SaaSContractRules.expirySeverity(181)).isNull();
        assertThat(SaaSContractRules.expirySeverity(-1)).isNull();
    }

    @Test
    @DisplayName("cota: 80% abre WARNING, 100% abre WARNING+CRITICAL, abaixo de 80% nada")
    void quotaLevels() {
        assertThat(SaaSContractRules.quotaAlertLevels(new BigDecimal("0.7999"))).isEmpty();
        assertThat(SaaSContractRules.quotaAlertLevels(new BigDecimal("0.8"))).containsExactly("80");
        assertThat(SaaSContractRules.quotaAlertLevels(new BigDecimal("0.9999"))).containsExactly("80");
        assertThat(SaaSContractRules.quotaAlertLevels(new BigDecimal("1"))).containsExactlyInAnyOrder("80", "100");
        assertThat(SaaSContractRules.quotaAlertLevels(new BigDecimal("1.5"))).containsExactlyInAnyOrder("80", "100");
        assertThat(SaaSContractRules.quotaRatio(new BigDecimal("80"), new BigDecimal("100")))
                .isEqualByComparingTo("0.8");
        assertThat(SaaSContractRules.quotaRatio(new BigDecimal("10"), BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("empenho: abaixo de duas faturas alerta; valor mensal zero nao divide por zero")
    void commitmentLow() {
        assertThat(SaaSContractRules.commitmentBalanceInInvoices(
                new BigDecimal("15000"), new BigDecimal("10000"))).isEqualByComparingTo("1.5");
        assertThat(SaaSContractRules.commitmentLow(new BigDecimal("1.5"))).isTrue();
        assertThat(SaaSContractRules.commitmentLow(new BigDecimal("2"))).isFalse();
        assertThat(SaaSContractRules.commitmentLow(new BigDecimal("2.0001"))).isFalse();
        assertThat(SaaSContractRules.commitmentLow(
                SaaSContractRules.commitmentBalanceInInvoices(new BigDecimal("100"), BigDecimal.ZERO)))
                .isFalse();
        assertThat(SaaSContractRules.commitmentLow(
                SaaSContractRules.commitmentBalanceInInvoices(BigDecimal.ZERO, BigDecimal.ZERO)))
                .isTrue();
    }

    @Test
    @DisplayName("margem: (faturado - custo) / faturado; nula sem faturamento")
    void margin() {
        assertThat(SaaSContractRules.margin(new BigDecimal("10000"), new BigDecimal("6000")))
                .isEqualByComparingTo("0.4");
        assertThat(SaaSContractRules.margin(BigDecimal.ZERO, new BigDecimal("100"))).isNull();
        assertThat(SaaSContractRules.margin(new BigDecimal("10000"), null)).isNull();
    }

    @Test
    @DisplayName("reconciliacao: pago cobre o total vira PAID, parcial vira PARTIALLY_PAID")
    void reconciledStatus() {
        assertThat(SaaSContractRules.reconciledStatus(new BigDecimal("100"), BigDecimal.ZERO)).isEqualTo("OPEN");
        assertThat(SaaSContractRules.reconciledStatus(new BigDecimal("100"), new BigDecimal("40")))
                .isEqualTo("PARTIALLY_PAID");
        assertThat(SaaSContractRules.reconciledStatus(new BigDecimal("100"), new BigDecimal("100")))
                .isEqualTo("PAID");
        assertThat(SaaSContractRules.reconciledStatus(new BigDecimal("100"), new BigDecimal("150")))
                .isEqualTo("PAID");
    }

    @Test
    @DisplayName("ledger: lista fechada mais messages.canal.categoria; resto recusado")
    void ledgerMetrics() {
        assertThat(SaaSContractRules.isLedgerMetric("active_connections")).isTrue();
        assertThat(SaaSContractRules.isLedgerMetric("llm_tokens")).isTrue();
        assertThat(SaaSContractRules.isLedgerMetric("messages.whatsapp.utility")).isTrue();
        assertThat(SaaSContractRules.isLedgerMetric("messages.sms.marketing")).isTrue();
        assertThat(SaaSContractRules.isLedgerMetric("customer_name")).isFalse();
        assertThat(SaaSContractRules.isLedgerMetric("messages.whatsapp")).isFalse();
        assertThat(SaaSContractRules.isLedgerMetric(null)).isFalse();
        assertThat(SaaSContractRules.quotaKeyFor("active_connections")).isEqualTo("quota.active_connections");
        assertThat(SaaSContractRules.monthOf("2026-11")).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("fotografia: hash confere o conteudo e falha quando a quantidade muda")
    void snapshotHash() {
        String tenant = "11111111-1111-1111-1111-111111111111";
        LocalDate month = LocalDate.of(2026, 11, 1);
        String hash = SaaSContractRules.snapshotHash(tenant, month, 5000);
        assertThat(SaaSContractRules.snapshotValid(tenant, month, 5000, hash)).isTrue();
        assertThat(SaaSContractRules.snapshotValid(tenant, month, 5001, hash)).isFalse();
        assertThat(SaaSContractRules.snapshotValid(tenant, LocalDate.of(2026, 12, 1), 5000, hash)).isFalse();
        assertThat(SaaSContractRules.snapshotValid(tenant, month, 5000, null)).isFalse();
    }

    @Test
    @DisplayName("reajuste so vale com apostilamento ou aditivo registrado")
    void readjustmentNeedsAddendum() {
        assertThat(SaaSContractRules.readjustmentMayApply(true)).isTrue();
        assertThat(SaaSContractRules.readjustmentMayApply(false)).isFalse();
    }
}
