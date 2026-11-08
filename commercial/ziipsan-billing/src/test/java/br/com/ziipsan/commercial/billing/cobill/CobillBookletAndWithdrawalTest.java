package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-25: carne mensal/anual, identificador unico e retirada a pedido - teste por regra. */
class CobillBookletAndWithdrawalTest {

    private static final UUID UNIT = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    @DisplayName("identificador unico: formato com o UUID COMPLETO da unidade/competencia/seq; duplicado identico no lote e recusado")
    void documentIdentifier() {
        assertEquals("COBILL-123e4567-e89b-12d3-a456-426614174000-202604-001",
                CobillBookletPlan.documentIdentifier(UNIT, YearMonth.of(2026, 4), 1));
        var slip = new CobillBookletPlan.Slip("COBILL-123e4567-e89b-12d3-a456-426614174000-202604-001", UNIT,
                YearMonth.of(2026, 4), LocalDate.of(2026, 4, 10), new BigDecimal("35.00"));
        assertThrows(IllegalArgumentException.class,
                () -> CobillBookletPlan.requireUnique(List.of(slip, slip)));
    }

    @Test
    @DisplayName("unicidade ENTRE DOCUMENTOS DISTINTOS (correcao D1): unidades que compartilham os 8 primeiros hex do UUID NAO colidem")
    void distinctUnitsDoNotCollide() {
        // Sonda da revisao cruzada: com o prefixo de 8 hex, estas duas
        // unidades geravam o MESMO identificador (COBILL-123e4567-...).
        UUID unitA = UUID.fromString("123e4567-0000-0000-0000-000000000001");
        UUID unitB = UUID.fromString("123e4567-ffff-ffff-ffff-ffffffffffff");
        YearMonth competence = YearMonth.of(2026, 4);
        String idA = CobillBookletPlan.documentIdentifier(unitA, competence, 1);
        String idB = CobillBookletPlan.documentIdentifier(unitB, competence, 1);
        assertNotEquals(idA, idB);
        // E o lote com documentos das DUAS unidades passa na unicidade:
        var slipA = new CobillBookletPlan.Slip(idA, unitA, competence,
                LocalDate.of(2026, 4, 10), new BigDecimal("35.00"));
        var slipB = new CobillBookletPlan.Slip(idB, unitB, competence,
                LocalDate.of(2026, 4, 10), new BigDecimal("35.00"));
        CobillBookletPlan.requireUnique(List.of(slipA, slipB));
        // Carnes completos das duas unidades, na mesma competencia e com a
        // mesma sequencia (1), tambem nao colidem entre si:
        var merged = new java.util.ArrayList<CobillBookletPlan.Slip>();
        merged.addAll(CobillBookletPlan.monthlyBooklet(unitA, 2026, new BigDecimal("35.00"), 10));
        merged.addAll(CobillBookletPlan.monthlyBooklet(unitB, 2026, new BigDecimal("35.00"), 10));
        assertEquals(24, merged.stream().map(CobillBookletPlan.Slip::documentIdentifier).distinct().count());
        CobillBookletPlan.requireUnique(merged);
    }

    @Test
    @DisplayName("carne MENSAL: 12 documentos no valor mensal cheio, vencimento no dia pedido")
    void monthlyBooklet() {
        var slips = CobillBookletPlan.monthlyBooklet(UNIT, 2026, new BigDecimal("35.00"), 10);
        assertEquals(12, slips.size());
        assertTrue(slips.stream().allMatch(s -> s.amount().compareTo(new BigDecimal("35.00")) == 0));
        assertEquals(LocalDate.of(2026, 1, 10), slips.get(0).dueDate());
        assertEquals(YearMonth.of(2026, 12), slips.get(11).competence());
    }

    @Test
    @DisplayName("carne ANUAL: 12 parcelas cuja soma e EXATAMENTE o valor anual (residuo na ultima)")
    void annualBooklet() {
        var slips = CobillBookletPlan.annualBooklet(UNIT, 2026, new BigDecimal("100.00"), 10);
        assertEquals(12, slips.size());
        BigDecimal sum = slips.stream().map(CobillBookletPlan.Slip::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100.00").compareTo(sum));
        assertEquals(0, new BigDecimal("8.33").compareTo(slips.get(0).amount()));
        assertEquals(0, new BigDecimal("8.37").compareTo(slips.get(11).amount()));
    }

    @Test
    @DisplayName("retirada a pedido: exige protocolo, nao retroage, vale da competencia efetiva em diante")
    void withdrawal() {
        assertThrows(IllegalArgumentException.class, () -> new CobillWithdrawalRule.WithdrawalRequest(
                UNIT, " ", LocalDate.of(2026, 4, 5), YearMonth.of(2026, 5)));
        assertThrows(IllegalArgumentException.class, () -> new CobillWithdrawalRule.WithdrawalRequest(
                UNIT, "ATD-2026-001", LocalDate.of(2026, 4, 5), YearMonth.of(2026, 3)));
        var request = new CobillWithdrawalRule.WithdrawalRequest(
                UNIT, "ATD-2026-001", LocalDate.of(2026, 4, 5), YearMonth.of(2026, 5));
        assertFalse(CobillWithdrawalRule.withdrawnIn(request, YearMonth.of(2026, 4)));
        assertTrue(CobillWithdrawalRule.withdrawnIn(request, YearMonth.of(2026, 5)));
        assertTrue(CobillWithdrawalRule.withdrawnIn(request, YearMonth.of(2026, 6)));
        assertFalse(CobillWithdrawalRule.withdrawnIn(null, YearMonth.of(2026, 5)));
    }

    @Test
    @DisplayName("retirada: efetivacao NO MES DO PEDIDO e legitima (correcao D2 - o dominio compara competencia, nao a data cheia)")
    void withdrawalEffectiveInRequestMonth() {
        // Pedido em 05/04 com efetivacao na competencia 04: a regra aceita;
        // o SQL pos-correcao compara date_trunc('month', ...) e persiste.
        var request = new CobillWithdrawalRule.WithdrawalRequest(
                UNIT, "ATD-2026-002", LocalDate.of(2026, 4, 5), YearMonth.of(2026, 4));
        assertFalse(CobillWithdrawalRule.withdrawnIn(request, YearMonth.of(2026, 3)));
        assertTrue(CobillWithdrawalRule.withdrawnIn(request, YearMonth.of(2026, 4)));
        assertTrue(CobillWithdrawalRule.withdrawnIn(request, YearMonth.of(2026, 5)));
    }
}
