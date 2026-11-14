package br.com.ziipsan.commercial.fiscal.relief;

import br.com.ziipsan.commercial.fiscal.port.CertificateVault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grupos 12 e F-05 — certificado (limiares, job idempotente, guarda) e destaque CBS/IBS por fora. */
class CertificateAndHighlightTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 11, 14, 10, 0, 0, 0, ZoneOffset.of("-03:00"));

    private static CertificateVault.CertificateRef cert(OffsetDateTime notAfter) {
        return new CertificateVault.CertificateRef(UUID.randomUUID(), UUID.randomUUID(),
                "thumbprint-ficticio", "local-vault:ficticio", "A1", notAfter);
    }

    @Test
    @DisplayName("classificacao nos limiares 30/15/7 e vencido (F-21)")
    void thresholds() {
        assertThat(CertificateAlertClassifier.classify(cert(NOW.plusDays(45)), NOW))
                .isEqualTo(CertificateAlertClassifier.Classification.OK);
        assertThat(CertificateAlertClassifier.classify(cert(NOW.plusDays(30)), NOW))
                .isEqualTo(CertificateAlertClassifier.Classification.ALERT_30);
        assertThat(CertificateAlertClassifier.classify(cert(NOW.plusDays(15)), NOW))
                .isEqualTo(CertificateAlertClassifier.Classification.ALERT_15);
        assertThat(CertificateAlertClassifier.classify(cert(NOW.plusDays(7)), NOW))
                .isEqualTo(CertificateAlertClassifier.Classification.ALERT_7);
        assertThat(CertificateAlertClassifier.classify(cert(NOW.minusDays(1)), NOW))
                .isEqualTo(CertificateAlertClassifier.Classification.EXPIRED);
    }

    @Test
    @DisplayName("job diario idempotente: mesma chave (certificado, limiar, dia) nao duplica alerta")
    void plannerIsIdempotent() {
        var planner = new CertificateAlertPlanner();
        var ref = cert(NOW.plusDays(5));
        var entry = new CertificateAlertPlanner.TenantCertificate(UUID.randomUUID(), ref);
        var first = planner.plan(List.of(entry), NOW, Set.of());
        assertThat(first).hasSize(1);
        var key = new CertificateAlertPlanner.AlertKey(ref.certificateId(), "D7", NOW.toLocalDate());
        var second = planner.plan(List.of(entry), NOW, Set.of(key));
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("guarda de emissao: certificado vencido ou ausente recusa; valido nao e afectado (sem excepcao de contingencia)")
    void emissionGuard() {
        assertThatThrownBy(() -> CertificateEmissionGuard.requireValidForEmission(
                Optional.of(cert(NOW.minusDays(2))), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CertificateEmissionGuard.requireValidForEmission(
                Optional.empty(), NOW))
                .isInstanceOf(IllegalStateException.class);
        var valid = cert(NOW.plusDays(90));
        assertThat(CertificateEmissionGuard.requireValidForEmission(Optional.of(valid), NOW))
                .isEqualTo(valid);
    }

    @Test
    @DisplayName("F-05 corrigido: CBS/IBS DESTACADOS por fora — total da conta inalterado, a pagar soma o destaque")
    void cbsIbsAreHighlightedNotInformative() {
        var lines = ReliefTaxHighlight.cbsIbs(new BigDecimal("181.44"),
                new BigDecimal("0.10"), new BigDecimal("0.02"));
        assertThat(lines).extracting(ReliefTaxHighlight.HighlightedTax::informative)
                .containsOnly(false);
        assertThat(lines.get(0).amount()).isEqualByComparingTo("18.14");
        assertThat(lines.get(1).amount()).isEqualByComparingTo("3.63");
        BigDecimal billTotal = new BigDecimal("181.44");
        assertThat(ReliefTaxHighlight.payableTotal(billTotal, lines))
                .isEqualByComparingTo("203.21");
        assertThat(billTotal).isEqualByComparingTo("181.44"); // o total da conta nao absorve o destaque
    }

    @Test
    @DisplayName("R-04: fonte unica do destaque — 2026 informativo (P2.4), 2027+ destaque por fora; sem dupla contagem")
    void highlightSingleSourceByCompetence() {
        // Ano de teste (P2.4): informativo, NAO soma ao valor a pagar.
        var y2026 = ReliefTaxHighlight.cbsIbsForCompetence(new BigDecimal("181.44"),
                new BigDecimal("0.10"), new BigDecimal("0.02"), java.time.LocalDate.of(2026, 6, 1));
        assertThat(y2026).extracting(ReliefTaxHighlight.HighlightedTax::informative)
                .containsOnly(true);
        assertThat(ReliefTaxHighlight.payableTotal(new BigDecimal("181.44"), y2026))
                .isEqualByComparingTo("181.44");
        // Fronteira exacta do flip da P2.4: 31/12/2026 informativo; 01/01/2027 destaque.
        assertThat(ReliefTaxHighlight.cbsIbsForCompetence(new BigDecimal("100.00"),
                new BigDecimal("0.10"), new BigDecimal("0.02"), java.time.LocalDate.of(2026, 12, 31)))
                .extracting(ReliefTaxHighlight.HighlightedTax::informative).containsOnly(true);
        var y2027 = ReliefTaxHighlight.cbsIbsForCompetence(new BigDecimal("181.44"),
                new BigDecimal("0.10"), new BigDecimal("0.02"), java.time.LocalDate.of(2027, 1, 1));
        assertThat(y2027).extracting(ReliefTaxHighlight.HighlightedTax::informative)
                .containsOnly(false);
        // Sem dupla contagem: exactamente 2 linhas (CBS, IBS), cada montante
        // entra UMA vez no valor a pagar; o total da conta nao se move.
        assertThat(y2027).hasSize(2)
                .extracting(ReliefTaxHighlight.HighlightedTax::taxCode)
                .containsExactly("CBS", "IBS");
        BigDecimal billTotal = new BigDecimal("181.44");
        BigDecimal payable = ReliefTaxHighlight.payableTotal(billTotal, y2027);
        assertThat(payable.subtract(billTotal)).isEqualByComparingTo("21.77"); // 18,14 + 3,63, uma vez
        assertThat(billTotal).isEqualByComparingTo("181.44");
    }

    @Test
    @DisplayName("grupo 13 (fiscal): M2/M3 declaram tabelas, registo RLS, sem seed e sem timeout de contingencia")
    void fiscalMigrationsShape() throws Exception {
        String m2 = readResource("/db/migration/tenant/V116__fiscal_relief_registries.sql");
        for (String table : List.of("fiscal_cfop_codes", "fiscal_ncm_codes",
                "fiscal_nfage_substitution_types", "fiscal_substitution_links",
                "fiscal_contingency_types")) {
            assertThat(m2).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(m2).contains("register_tenant_table('" + table + "')");
        }
        assertThat(m2).doesNotContain("INSERT INTO");
        // V-31: nenhuma COLUNA de timeout (o comentario da migration menciona a
        // ausencia deliberada; coluna, nao ha).
        assertThat(m2).doesNotContainPattern("(?m)^\\s+timeout\\s");
        String m3 = readResource("/db/migration/tenant/V117__fiscal_certificate_alert_log.sql");
        assertThat(m3).contains("CREATE TABLE IF NOT EXISTS fiscal_certificate_alert_log");
        assertThat(m3).contains("register_tenant_table('fiscal_certificate_alert_log')");
        assertThat(m3).contains("threshold, alerted_on");
    }

    private String readResource(String path) throws Exception {
        try (var in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("recurso " + path).isNotNull();
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
