package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.portal.PortalParameters;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.35/PLT-11 — verificacao publica: conteudo permitido da resposta, forma
 * unica de falha (anti-oraculo), hash adulterado, parametro desligado,
 * limite de tentativas e registro de cada consulta.
 */
class PublicBillVerificationServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final PortalParameters.AccountThrottle THROTTLE =
            new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15);
    private static final String RECEIVER = "SAAE EXEMPLO";
    private static final String RECEIVER_TAX = "12.345.678/0001-90";

    private static String goodHash() {
        return BillDocumentHash.compute(TENANT, BILL, 1, new BigDecimal("123.45"),
                LocalDate.of(2026, 11, 20), YearMonth.of(2026, 11), RECEIVER, RECEIVER_TAX);
    }

    private static final class FakePort implements PublicBillVerificationService.VerificationPort {
        final Map<String, PublicBillVerificationService.VerifiableBill> byHash = new HashMap<>();
        int byOrigin;
        int byCode;
        final List<PublicBillVerificationService.VerificationQueryAudit> audits = new ArrayList<>();

        @Override public Optional<PublicBillVerificationService.VerifiableBill> findByCodeHash(UUID t, String hash) {
            return Optional.ofNullable(byHash.get(hash));
        }
        @Override public int queriesByOrigin(UUID t, String origin) { return byOrigin; }
        @Override public int queriesByCode(UUID t, String hash) { return byCode; }
        @Override public void recordQuery(PublicBillVerificationService.VerificationQueryAudit a) { audits.add(a); }
    }

    private PublicBillVerificationService service(FakePort port) {
        var protector = VerifyTestSupport.protector();
        return new PublicBillVerificationService(port,
                (tenantId, code) -> VerificationCode.storageHash(protector, tenantId, code));
    }

    private String registerBill(FakePort port, String hash, boolean codeActive, boolean cancelled) {
        var protector = VerifyTestSupport.protector();
        String code = VerificationCode.generate();
        port.byHash.put(VerificationCode.storageHash(protector, TENANT, code),
                new PublicBillVerificationService.VerifiableBill(BILL, 1, new BigDecimal("123.45"),
                        LocalDate.of(2026, 11, 20), YearMonth.of(2026, 11), RECEIVER, RECEIVER_TAX,
                        hash, codeActive, cancelled));
        return code;
    }

    private FraudProtectionParameters enabled() {
        return FraudProtectionParameters.fromValues(Map.of());
    }

    @Test
    void faturaAutenticaConfereValorVencimentoRecebedorEHash() {
        var port = new FakePort();
        String code = registerBill(port, goodHash(), true, false);
        var result = service(port).verify(TENANT, "203.0.113.9", code, enabled(), THROTTLE, NOW);
        assertThat(result.authentic()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("123.45");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(result.referenceMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(result.receiverName()).isEqualTo(RECEIVER);
        assertThat(result.receiverTaxId()).isEqualTo(RECEIVER_TAX);
        assertThat(result.documentHash()).isEqualTo(goodHash());
        assertThat(port.audits).hasSize(1);
        assertThat(port.audits.get(0).result()).isEqualTo("AUTHENTIC");
    }

    @Test
    void respostaNaoRevelaTitularEnderecoDocumentoMatriculaNemConsumo() {
        var port = new FakePort();
        String code = registerBill(port, goodHash(), true, false);
        var result = service(port).verify(TENANT, "ip", code, enabled(), THROTTLE, NOW);
        String json = Json.write(result);
        JsonNode tree = Json.readTree(json);
        // Jackson 3: fieldNames() foi removido; a API real e propertyNames().
        Set<String> fields = new HashSet<>(tree.propertyNames());
        assertThat(fields).containsExactlyInAnyOrder("authentic", "amount", "dueDate", "referenceMonth",
                "receiverName", "receiverTaxId", "documentHash");
        String lower = json.toLowerCase(java.util.Locale.ROOT);
        assertThat(lower).doesNotContain("titular").doesNotContain("holder")
                .doesNotContain("endereco").doesNotContain("address")
                .doesNotContain("matricula").doesNotContain("registration")
                .doesNotContain("consumo").doesNotContain("consumption")
                .doesNotContain("unidade").doesNotContain("unit");
        // receiverName/receiverTaxId sao do TENANT (fornecedor publico), nunca do titular:
        // o record nem tem componente capaz de carregar dado do titular.
        assertThat(PublicBillVerificationService.VerificationResult.class.getRecordComponents()).hasSize(7);
    }

    @Test
    void falhasSaoIndistintasCodigoInexistenteRevogadoCanceladoEHashDivergente() {
        var svcPort = new FakePort();
        var svc = service(svcPort);
        var inexistente = svc.verify(TENANT, "ip", VerificationCode.generate(), enabled(), THROTTLE, NOW);
        String codeRevogado = registerBill(svcPort, goodHash(), false, false);
        var revogado = svc.verify(TENANT, "ip", codeRevogado, enabled(), THROTTLE, NOW);
        String codeCancelado = registerBill(svcPort, goodHash(), true, true);
        var cancelado = svc.verify(TENANT, "ip", codeCancelado, enabled(), THROTTLE, NOW);
        String codeAdulterado = registerBill(svcPort, "0".repeat(64), true, false);
        var adulterado = svc.verify(TENANT, "ip", codeAdulterado, enabled(), THROTTLE, NOW);
        var malformado = svc.verify(TENANT, "ip", "123", enabled(), THROTTLE, NOW);
        assertThat(inexistente).isEqualTo(PublicBillVerificationService.VerificationResult.notAuthentic());
        assertThat(revogado).isEqualTo(inexistente);
        assertThat(cancelado).isEqualTo(inexistente);
        assertThat(adulterado).isEqualTo(inexistente);
        assertThat(malformado).isEqualTo(inexistente);
        assertThat(svcPort.audits).hasSize(5).allSatisfy(a ->
                assertThat(a.result()).isEqualTo("NOT_AUTHENTIC"));
    }

    @Test
    void verificacaoDesligadaRespondeComoNaoAutenticaSemRevelarOParametro() {
        var port = new FakePort();
        String code = registerBill(port, goodHash(), true, false);
        var params = FraudProtectionParameters.fromValues(
                Map.of(FraudProtectionParameters.VERIFY_ENABLED, "false"));
        var result = service(port).verify(TENANT, "ip", code, params, THROTTLE, NOW);
        assertThat(result).isEqualTo(PublicBillVerificationService.VerificationResult.notAuthentic());
    }

    @Test
    void limitePorOrigemEPorCodigoComoNoConvidadoDaP33() {
        var port = new FakePort();
        String code = registerBill(port, goodHash(), true, false);
        port.byOrigin = THROTTLE.maxGuestQueriesPerOrigin();
        assertThatThrownBy(() -> service(port).verify(TENANT, "ip", code, enabled(), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
        assertThat(port.audits).hasSize(1);
        assertThat(port.audits.get(0).result()).isEqualTo("RATE_LIMITED");

        var port2 = new FakePort();
        String code2 = registerBill(port2, goodHash(), true, false);
        port2.byCode = THROTTLE.maxGuestQueriesPerCode();
        assertThatThrownBy(() -> service(port2).verify(TENANT, "ip", code2, enabled(), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);

        // Codigo malformado tambem conta na origem (nao foge do limite).
        var port3 = new FakePort();
        port3.byOrigin = THROTTLE.maxGuestQueriesPerOrigin();
        assertThatThrownBy(() -> service(port3).verify(TENANT, "ip", "lixo", enabled(), THROTTLE, NOW))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    @Test
    void auditoriaNaoCarregaCodigoEmClaroNemDadoDoTitular() {
        var port = new FakePort();
        String code = registerBill(port, goodHash(), true, false);
        service(port).verify(TENANT, "203.0.113.9", code, enabled(), THROTTLE, NOW);
        var audit = port.audits.get(0);
        assertThat(audit.codeHash()).isNotEqualTo(code);
        assertThat(audit.codeHash()).doesNotContain(VerificationCode.normalize(code));
    }
}
