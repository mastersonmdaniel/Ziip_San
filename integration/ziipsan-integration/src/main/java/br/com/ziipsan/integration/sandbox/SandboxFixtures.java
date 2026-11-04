package br.com.ziipsan.integration.sandbox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Massa sintética do ambiente de teste (P2.26).
 *
 * <p>Cliente com {@code environment = SANDBOX} nunca toca nas tabelas
 * reais: as leituras devolvem estas fixtures e a escrita de OS devolve
 * um pedido sintético, sem persistir. Nada aqui é dado pessoal real:
 * matrículas, valores e identificadores são inventados e estáveis, para
 * o integrador testar sem credencial de produção e sem sujar o tenant.
 * Os identificadores são UUIDs fixos (não aleatórios) para o teste de
 * contrato por versão poder afirmar o corpo exato.
 */
public final class SandboxFixtures {

    public static final UUID PROPERTY_1 = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID PROPERTY_2 = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public static final UUID BILL_1 = UUID.fromString("20000000-0000-4000-8000-000000000001");
    public static final UUID PAYMENT_1 = UUID.fromString("30000000-0000-4000-8000-000000000001");
    public static final UUID SERVICE_ORDER_1 = UUID.fromString("40000000-0000-4000-8000-000000000001");

    private SandboxFixtures() {
    }

    public static List<Map<String, Object>> properties() {
        return List.of(
                Map.of("id", PROPERTY_1.toString(), "registrationNumber", "10001", "checkDigit", "0",
                        "status", "ACTIVE", "lot", 12, "sublot", 0, "builtAreaM2", 85.5, "dueDay", 10),
                Map.of("id", PROPERTY_2.toString(), "registrationNumber", "10002", "checkDigit", "5",
                        "status", "ACTIVE", "lot", 13, "sublot", 1, "builtAreaM2", 120.0, "dueDay", 15));
    }

    public static List<Map<String, Object>> bills() {
        return List.of(
                Map.of("id", BILL_1.toString(), "unitId", PROPERTY_1.toString(),
                        "referenceMonth", LocalDate.of(2026, 10, 1).toString(), "status", "NORMAL",
                        "totalValue", new BigDecimal("123.45"),
                        "dueDate", LocalDate.of(2026, 11, 10).toString()));
    }

    public static List<Map<String, Object>> payments() {
        return List.of(
                Map.of("id", PAYMENT_1.toString(), "unitId", PROPERTY_1.toString(),
                        "documentType", "BILL", "documentId", BILL_1.toString(),
                        "amount", new BigDecimal("123.45"),
                        "paidOn", LocalDate.of(2026, 11, 5).toString(), "status", "CLASSIFIED"));
    }

    public static List<Map<String, Object>> serviceOrders() {
        return List.of(
                Map.of("id", SERVICE_ORDER_1.toString(), "serviceTypeCode", "SANDBOX_TEST",
                        "status", "DRAFT", "origin", "INTERNAL", "executionMode", "INTERNAL"));
    }
}
