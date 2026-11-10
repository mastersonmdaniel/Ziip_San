package br.com.ziipsan.commercial.collection.cession;

import br.com.ziipsan.commercial.collection.cession.ReceivablesPortfolioSource.PortfolioLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ED-103 — FIXTURES DE PROVA declaradas: linhas de carteira construidas
 * para os testes, sobre o contrato da porta (E1). Nao sao dados reais de
 * nenhum tenant; as chaves de devedor sao pseudonimos de fixture.
 */
final class CessionFixtures {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000001030");
    static final LocalDate REF = LocalDate.of(2026, 11, 10);

    static PortfolioLine line(String id, String debtor, String face, String paid, LocalDate due) {
        return new PortfolioLine(id, debtor, new BigDecimal(face), new BigDecimal(paid), due, "FATURA");
    }

    /**
     * Carteira de prova (valores calculados a mao nos testes):
     * R1 a vencer (vence 2026-12-10): face 1000, pago 250, devido 750.
     * R2 vencido ha 20 dias (2026-10-21): face 500, pago 0, devido 500.
     * R3 vencido ha 45 dias (2026-09-26): face 800, pago 800, devido 0.
     * R4 vencido ha 400 dias (2025-10-06): face 300, pago 100, devido 200.
     */
    static List<PortfolioLine> portfolio() {
        return List.of(
                line("R1", "dev-hash-001", "1000.00", "250.00", LocalDate.of(2026, 12, 10)),
                line("R2", "dev-hash-002", "500.00", "0.00", LocalDate.of(2026, 10, 21)),
                line("R3", "dev-hash-001", "800.00", "800.00", LocalDate.of(2026, 9, 26)),
                line("R4", "dev-hash-003", "300.00", "100.00", LocalDate.of(2025, 10, 6)));
    }

    static ReceivablesPortfolioSource source(List<PortfolioLine> lines) {
        return tenantId -> lines;
    }

    private CessionFixtures() {}
}
