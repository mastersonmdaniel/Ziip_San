package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.portal.PortalParameters;
import br.com.ziipsan.platform.portal.verify.web.PublicDocumentVerifyController;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correcao D5 da revisao cruzada da P3.35: {@code reportFraud} sem validacao
 * devolvia 500 (IllegalArgumentException/NullPointerException soltos) para
 * erro do cliente. Estes testes provam que corpo nulo, canal nulo/invalido
 * e campos invalidos viram {@link ApiException.BadRequest} (status 400),
 * que o caminho valido segue funcionando e que o 429 NAO e rebaixado a 400.
 */
class PublicDocumentVerifyControllerFraudValidationTest {

    private static final UUID TENANT = UUID.randomUUID();

    private static final class FakeReports implements FraudReportService.FraudReportPort {
        int byOrigin;
        final List<FraudReportService.FraudReport> saved = new ArrayList<>();
        @Override public int reportsByOrigin(UUID t, String origin) { return byOrigin; }
        @Override public void saveReport(FraudReportService.FraudReport r) { saved.add(r); }
    }

    private final FakeReports reports = new FakeReports();

    private PublicDocumentVerifyController controller() {
        var fraudReports = new FraudReportService(reports, alert -> { }, 5);
        var verification = new PublicBillVerificationService(
                new PublicBillVerificationService.VerificationPort() {
                    @Override public java.util.Optional<PublicBillVerificationService.VerifiableBill>
                    findByCodeHash(UUID t, String hash) { return java.util.Optional.empty(); }
                    @Override public int queriesByOrigin(UUID t, String origin) { return 0; }
                    @Override public int queriesByCode(UUID t, String hash) { return 0; }
                    @Override public void recordQuery(
                            PublicBillVerificationService.VerificationQueryAudit a) { }
                },
                (tenantId, code) -> "hash");
        return new PublicDocumentVerifyController(verification, fraudReports,
                FraudProtectionParameters.fromValues(Map.of()),
                PortalParameters.fromValues(Map.of()),
                new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15),
                tenantId -> null);
    }

    private static HttpServletRequest http() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                PublicDocumentVerifyControllerFraudValidationTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getRemoteAddr")) {
                        return "203.0.113.9";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void corpoNuloCanalNuloOuInvalidoRespondem400() {
        var controller = controller();
        assertThatThrownBy(() -> controller.reportFraud(null, http()))
                .isInstanceOf(ApiException.BadRequest.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(400));
        assertThatThrownBy(() -> controller.reportFraud(
                new PublicDocumentVerifyController.FraudReportRequest(null, "valor", "descricao", null), http()))
                .isInstanceOf(ApiException.BadRequest.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(400));
        assertThatThrownBy(() -> controller.reportFraud(
                new PublicDocumentVerifyController.FraudReportRequest("CANAL_QUE_NAO_EXISTE", "valor", "descricao", null), http()))
                .isInstanceOf(ApiException.BadRequest.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(400));
        assertThat(reports.saved).isEmpty();
    }

    @Test
    void valorOuDescricaoInvalidosRespondem400ENao500() {
        var controller = controller();
        assertThatThrownBy(() -> controller.reportFraud(
                new PublicDocumentVerifyController.FraudReportRequest("LINK", " ", "descricao", null), http()))
                .isInstanceOf(ApiException.BadRequest.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(400));
        assertThatThrownBy(() -> controller.reportFraud(
                new PublicDocumentVerifyController.FraudReportRequest("LINK", "valor", null, null), http()))
                .isInstanceOf(ApiException.BadRequest.class);
        assertThat(reports.saved).isEmpty();
    }

    @Test
    void denunciaValidaSegueORumoNormal() {
        var controller = controller();
        String body = controller.reportFraud(
                new PublicDocumentVerifyController.FraudReportRequest(
                        "LINK", "https://fraude.exemplo/pagar", "Recebi link por mensagem", null),
                http());
        assertThat(body).contains("\"received\":true");
        assertThat(reports.saved).hasSize(1);
    }

    @Test
    void estouroDeDenunciasContinua429ENaoVira400() {
        reports.byOrigin = 5;
        var controller = controller();
        assertThatThrownBy(() -> controller.reportFraud(
                new PublicDocumentVerifyController.FraudReportRequest("LINK", "valor", "descricao", null), http()))
                .isInstanceOf(ApiException.TooManyRequests.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(429));
    }
}
