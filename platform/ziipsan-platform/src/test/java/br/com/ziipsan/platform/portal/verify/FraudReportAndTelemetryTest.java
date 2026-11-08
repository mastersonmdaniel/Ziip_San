package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudReportAndTelemetryTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static final class FakeReports implements FraudReportService.FraudReportPort {
        int byOrigin;
        final List<FraudReportService.FraudReport> saved = new ArrayList<>();
        @Override public int reportsByOrigin(UUID t, String origin) { return byOrigin; }
        @Override public void saveReport(FraudReportService.FraudReport r) { saved.add(r); }
    }

    @Test
    void denunciaERegistradaEAlimentaAlertaNaCentral() {
        var reports = new FakeReports();
        var alerts = new ArrayList<FraudReportService.CentralFraudAlert>();
        var service = new FraudReportService(reports, alerts::add, 5);
        var report = service.report(TENANT, "203.0.113.9", FraudReportService.ReportedChannel.LINK,
                "https://fraude.exemplo/pagar", "Recebi link de cobranca por mensagem", null, NOW);
        assertThat(reports.saved).containsExactly(report);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).tenantId()).isEqualTo(TENANT);
        assertThat(alerts.get(0).channel()).isEqualTo(FraudReportService.ReportedChannel.LINK);
        // A denuncia nao carrega dado de titular: os componentes sao so o declarado.
        assertThat(FraudReportService.FraudReport.class.getRecordComponents()).hasSize(7);
    }

    @Test
    void denunciaExigeValorEDescricaoETemLimitePorOrigem() {
        var reports = new FakeReports();
        var service = new FraudReportService(reports, a -> { }, 2);
        assertThatThrownBy(() -> service.report(TENANT, "ip", FraudReportService.ReportedChannel.EMAIL,
                " ", "descricao", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        reports.byOrigin = 2;
        assertThatThrownBy(() -> service.report(TENANT, "ip", FraudReportService.ReportedChannel.EMAIL,
                "remetente@fraude.exemplo", "descricao", null, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
        assertThat(reports.saved).isEmpty();
    }

    @Test
    void telemetriaAgregaConsultasEDenunciasParaACentral() {
        var view = VerificationTelemetry.build(TENANT,
                (t, from, to) -> new VerificationTelemetry.WindowCounts(10, 3, 2, 4),
                NOW.minusSeconds(3600), NOW);
        assertThat(view.totalQueries()).isEqualTo(15);
        assertThat(view.fraudReports()).isEqualTo(4);
        String json = VerificationTelemetry.toJson(view);
        assertThat(Json.readTree(json).get("notAuthentic").intValue()).isEqualTo(3);
        assertThat(json).doesNotContain("titular").doesNotContain("holder");
        assertThatThrownBy(() -> VerificationTelemetry.build(TENANT,
                (t, from, to) -> new VerificationTelemetry.WindowCounts(0, 0, 0, 0), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void politicaDeRemetenteExigeDmarcRejectRemetenteFixoEDominioProprio() {
        var ok = EmailSenderPolicy.assess("tenant.exemplo", EmailSenderPolicy.DmarcPolicy.REJECT,
                "fatura@tenant.exemplo", "fatura@tenant.exemplo");
        assertThat(ok.conforming()).isTrue();
        var frouxo = EmailSenderPolicy.assess("tenant.exemplo", EmailSenderPolicy.DmarcPolicy.QUARANTINE,
                "fatura@tenant.exemplo", "fatura@tenant.exemplo");
        assertThat(frouxo.conforming()).isFalse();
        assertThat(frouxo.dmarcStrict()).isFalse();
        var outroRemetente = EmailSenderPolicy.assess("tenant.exemplo", EmailSenderPolicy.DmarcPolicy.REJECT,
                "fatura@tenant.exemplo", "cobranca@tenant.exemplo");
        assertThat(outroRemetente.fixedSender()).isFalse();
        var dominioAlheio = EmailSenderPolicy.assess("tenant.exemplo", EmailSenderPolicy.DmarcPolicy.REJECT,
                "fatura@plataforma.exemplo", "fatura@plataforma.exemplo");
        assertThat(dominioAlheio.ownDomain()).isFalse();
    }
}
