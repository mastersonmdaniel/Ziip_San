package br.com.ziipsan.commercial.collection.ampliada;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-26 - VAN configuravel e o portao do caixa condicional (desligado por padrao). */
class VanConfigAndCashierAccessTest {

    @Test
    void vanGeraNomeDeRemessaECabecalhoDaConfiguracao() {
        var van = new VanConfig("237", "1234", "56789-0", "CONV-01", "CNAB240-v1", "CX-77");
        assertThat(van.remittanceFileName(LocalDate.of(2026, 1, 15), 3))
                .isEqualTo("REM237202601150003.REM");
        assertThat(van.headerLine()).isEqualTo("237|1234|56789-0|CONV-01|CNAB240-v1|CX-77");
    }

    @Test
    void vanIncompletaEConfiguracaoInvalidaSaoRecusadas() {
        assertThatThrownBy(() -> new VanConfig("237", " ", "56789-0", "C", "L", "CX"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VanConfig("23", "1234", "56789-0", "C", "L", "CX"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caixaDesligadoEscondeMenuPermissoesERotasERecusaOperacao() {
        assertThat(CashierAccess.menu(false)).isEmpty();
        assertThat(CashierAccess.permissions(false)).isEmpty();
        assertThat(CashierAccess.routes(false)).isEmpty();
        assertThatThrownBy(() -> CashierAccess.requireEnabled(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pay.own_cashier_enabled");
    }

    @Test
    void caixaLigadoExpoeMenuPermissoesERotas() {
        assertThat(CashierAccess.menu(true)).containsExactly("Caixa do prestador");
        assertThat(CashierAccess.permissions(true)).contains("cashier:open", "cashier:receive",
                "cashier:bleed", "cashier:close", "cashier:check");
        assertThat(CashierAccess.routes(true)).contains("/caixa/abertura", "/caixa/fechamento");
        CashierAccess.requireEnabled(true);
    }
}
