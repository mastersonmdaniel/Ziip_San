package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.3/ADR-0043 - pagamento sem conta: conteudo da resposta do convidado,
 * um teste por opcao de portal.guest_payment, indistinguibilidade e
 * registro de cada consulta.
 */
class GuestPaymentServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final PortalParameters.AccountThrottle THROTTLE =
            new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15);
    private static final GuestPaymentService.GuestInvoiceView INVOICE =
            new GuestPaymentService.GuestInvoiceView(new BigDecimal("123.45"),
                    LocalDate.of(2026, 11, 20), YearMonth.of(2026, 11),
                    "23793.38128.60007.827136.95000.063305.9.12340000123456", "000201pix-copia-e-cola");

    private static final class FakePort implements GuestPaymentService.GuestInvoicePort {
        final Set<String> knownHashes = new HashSet<>();
        String expectedSuffix = "4725";
        int byOrigin;
        int byCode;
        final List<GuestPaymentService.GuestQueryAudit> audits = new ArrayList<>();

        @Override public Optional<GuestPaymentService.GuestInvoiceView> findOpenInvoice(UUID t, String hash) {
            return knownHashes.contains(hash) ? Optional.of(INVOICE) : Optional.empty();
        }
        @Override public boolean documentSuffixMatches(UUID t, String hash, String suffix) {
            return expectedSuffix.equals(suffix);
        }
        @Override public int queriesByOrigin(UUID t, String origin) { return byOrigin; }
        @Override public int queriesByCode(UUID t, String hash) { return byCode; }
        @Override public void recordQuery(GuestPaymentService.GuestQueryAudit audit) { audits.add(audit); }
    }

    private GuestPaymentService service(FakePort port) {
        var protector = PortalTestSupport.protector();
        return new GuestPaymentService(port, (tenantId, code) -> LinkingCode.storageHash(protector, tenantId, code));
    }

    private PortalParameters params(String mode) {
        return PortalParameters.fromValues(Map.of(PortalParameters.GUEST_PAYMENT, "\"" + mode + "\""));
    }

    private String knownCode(FakePort port) {
        var protector = PortalTestSupport.protector();
        String code = LinkingCode.generate();
        port.knownHashes.add(LinkingCode.storageHash(protector, TENANT, code));
        return code;
    }

    @Test
    void conteudoDaRespostaDoConvidadoSoTemOsCincoCamposPermitidos() {
        var port = new FakePort();
        String code = knownCode(port);
        var view = service(port).query(TENANT, "203.0.113.9", code, null, params("CODE_ONLY"), THROTTLE, NOW);
        String json = Json.write(view);
        JsonNode tree = Json.readTree(json);
        Set<String> fields = new HashSet<>();
        tree.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "amount", "dueDate", "referenceMonth", "digitableLine", "pixCopyPaste");
        String lower = json.toLowerCase(java.util.Locale.ROOT);
        assertThat(lower).doesNotContain("nome").doesNotContain("name")
                .doesNotContain("endereco").doesNotContain("address")
                .doesNotContain("documento").doesNotContain("document")
                .doesNotContain("consumo").doesNotContain("consumption")
                .doesNotContain("matricula").doesNotContain("titular").doesNotContain("holder");
        // Sem sessao: o record da resposta nao tem componente de sessao/token.
        assertThat(GuestPaymentService.GuestInvoiceView.class.getRecordComponents()).hasSize(5);
    }

    @Test
    void modoDesligadoRecusaOCaminho() {
        var port = new FakePort();
        String code = knownCode(port);
        assertThatThrownBy(() -> service(port).query(TENANT, "ip", code, null, params("OFF"), THROTTLE, NOW))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void modoSoCodigoEntregaSemSufixo() {
        var port = new FakePort();
        String code = knownCode(port);
        var view = service(port).query(TENANT, "ip", code, null, params("CODE_ONLY"), THROTTLE, NOW);
        assertThat(view.amount()).isEqualByComparingTo("123.45");
        assertThat(port.audits).hasSize(1);
        assertThat(port.audits.get(0).found()).isTrue();
    }

    @Test
    void modoCodigoMaisSufixoExigeSufixoCorreto() {
        var port = new FakePort();
        String code = knownCode(port);
        var service = service(port);
        assertThat(service.query(TENANT, "ip", code, "4725", params("CODE_AND_DOCUMENT_SUFFIX"), THROTTLE, NOW))
                .isEqualTo(INVOICE);
        assertThatThrownBy(() -> service.query(TENANT, "ip", code, "0000",
                params("CODE_AND_DOCUMENT_SUFFIX"), THROTTLE, NOW))
                .isInstanceOf(ApiException.NotFound.class);
        assertThatThrownBy(() -> service.query(TENANT, "ip", code, null,
                params("CODE_AND_DOCUMENT_SUFFIX"), THROTTLE, NOW))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    void codigoInexistenteTemAMesmaRespostaDeSufixoErrado() {
        var port = new FakePort();
        String code = knownCode(port);
        var service = service(port);
        var unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.query(TENANT, "ip", LinkingCode.generate(), null, params("CODE_ONLY"), THROTTLE, NOW));
        var wrongSuffix = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.query(TENANT, "ip", code, "9999", params("CODE_AND_DOCUMENT_SUFFIX"), THROTTLE, NOW));
        assertThat(unknown).isInstanceOf(ApiException.NotFound.class);
        assertThat(wrongSuffix).isInstanceOf(ApiException.NotFound.class);
        assertThat(unknown.getMessage()).isEqualTo(wrongSuffix.getMessage());
        // Cada consulta foi registrada, inclusive as que nao encontraram.
        assertThat(port.audits).hasSize(2);
        assertThat(port.audits).allMatch(a -> !a.found());
    }

    @Test
    void limitePorOrigemValeTambemParaCodigoMalformado() {
        var port = new FakePort();
        port.byOrigin = 30;
        assertThatThrownBy(() -> service(port).query(TENANT, "ip", "123", null, params("CODE_ONLY"), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
        assertThat(port.audits).hasSize(1);
        assertThat(port.audits.get(0).found()).isFalse();
    }

    @Test
    void limitePorOrigemEPorCodigoVira429() {
        var port = new FakePort();
        port.byOrigin = 30;
        String code = knownCode(port);
        assertThatThrownBy(() -> service(port).query(TENANT, "ip", code, null, params("CODE_ONLY"), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
        var port2 = new FakePort();
        port2.byCode = 10;
        String code2 = knownCode(port2);
        assertThatThrownBy(() -> service(port2).query(TENANT, "ip", code2, null, params("CODE_ONLY"), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }
}
