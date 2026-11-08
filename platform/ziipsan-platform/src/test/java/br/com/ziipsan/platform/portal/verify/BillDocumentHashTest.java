package br.com.ziipsan.platform.portal.verify;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillDocumentHashTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();

    private String hash(BigDecimal amount, LocalDate due, String receiver) {
        return BillDocumentHash.compute(TENANT, BILL, 1, amount, due, YearMonth.of(2026, 11),
                receiver, "12.345.678/0001-90");
    }

    @Test
    void hashDeterministicoEmHexMinusculoDe64() {
        String a = hash(new BigDecimal("123.45"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO");
        String b = hash(new BigDecimal("123.450"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO");
        assertThat(a).isEqualTo(b);
        assertThat(a).matches("[0-9a-f]{64}");
    }

    @Test
    void qualquerCampoAlteradoMudaOHash() {
        String base = hash(new BigDecimal("123.45"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO");
        assertThat(hash(new BigDecimal("123.46"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO")).isNotEqualTo(base);
        assertThat(hash(new BigDecimal("123.45"), LocalDate.of(2026, 11, 21), "SAAE EXEMPLO")).isNotEqualTo(base);
        assertThat(hash(new BigDecimal("123.45"), LocalDate.of(2026, 11, 20), "OUTRO RECEBEDOR")).isNotEqualTo(base);
    }

    @Test
    void valorComMaisDeDuasCasasERecusado() {
        assertThatThrownBy(() -> hash(new BigDecimal("1.234"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchesExigeIgualdadeExata() {
        String h = hash(new BigDecimal("10.00"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO");
        assertThat(BillDocumentHash.matches(h, h)).isTrue();
        assertThat(BillDocumentHash.matches(h, null)).isFalse();
        assertThat(BillDocumentHash.matches(null, h)).isFalse();
        // Correcao D9 da revisao cruzada: o caso de hashes DIFERENTES nao era
        // exercitado no unitario (so igual e nulos) — divergencia tem de ser falsa.
        String different = hash(new BigDecimal("10.01"), LocalDate.of(2026, 11, 20), "SAAE EXEMPLO");
        assertThat(different).isNotEqualTo(h);
        assertThat(BillDocumentHash.matches(h, different)).isFalse();
        assertThat(BillDocumentHash.matches(different, h)).isFalse();
    }
}
