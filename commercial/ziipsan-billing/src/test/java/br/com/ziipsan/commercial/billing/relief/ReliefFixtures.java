package br.com.ziipsan.commercial.billing.relief;

import java.time.LocalDate;
import java.util.UUID;

/** Fixtures FICTICIAS do PA-02: nenhum valor aqui e de prestador real. */
final class ReliefFixtures {

    static final UUID PROGRAM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final LocalDate CUT = LocalDate.of(2027, 1, 1);

    private ReliefFixtures() {
    }

    static ReliefProgram program(boolean provisionalDeclared) {
        return new ReliefProgram(PROGRAM_ID, "DESON-2027", "Desoneracao ficticia de teste", null,
                "Ato ficticio de teste (cadastro do tenant)", CUT, ReliefProgramStatus.ACTIVE,
                provisionalDeclared, ReliefRoundingPolicy.tenantDefault(),
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 2),
                "Entidade reguladora ficticia", "Reserva de Fase II ficticia (NT 4.7.3)",
                LocalDate.of(2026, 11, 15), "Ato ficticio 1/2026");
    }

    static ReliefRate tenantRate(String pct) {
        return new ReliefRate(UUID.randomUUID(), PROGRAM_ID, ReliefRate.ScopeKind.TENANT, null,
                new java.math.BigDecimal(pct), "Apuracao contabilistica ficticia do tenant",
                LocalDate.of(2026, 1, 1), null);
    }
}
