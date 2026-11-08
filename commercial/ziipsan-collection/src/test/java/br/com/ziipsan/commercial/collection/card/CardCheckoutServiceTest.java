package br.com.ziipsan.commercial.collection.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Abertura da sessao de link/checkout para a parcela do parcelamento. */
class CardCheckoutServiceTest {

    private final CardTestSupport.FakeAcquirer acquirer = new CardTestSupport.FakeAcquirer();
    private final CardTestSupport.FakeStore store = new CardTestSupport.FakeStore();
    private final CardTestSupport.CapturingLog log = new CardTestSupport.CapturingLog();

    private CardCheckoutService service(CardParameters params) {
        return new CardCheckoutService(params, acquirer, store, log);
    }

    private CardCheckoutService.CreateCommand command(boolean active, boolean open,
                                                      String expected, String requested) {
        return new CardCheckoutService.CreateCommand(CardTestSupport.TENANT, CardTestSupport.AGREEMENT,
                3, active, open, new BigDecimal(expected), new BigDecimal(requested),
                "https://portal.example.invalid/retorno",
                Instant.parse("2026-11-05T23:59:00Z"), Instant.parse("2026-11-05T10:00:00Z"));
    }

    @Test
    @DisplayName("cria a sessao: URL hospedada da credenciadora, referencia propria, situacao CREATED")
    void createsSession() {
        var session = service(CardTestSupport.params(true)).create(command(true, true, "106.61", "106.61"));
        assertThat(session.status()).isEqualTo(CardEnums.SessionStatus.CREATED);
        assertThat(session.checkoutUrl()).contains("checkout.example.invalid");
        assertThat(session.acquirerCode()).isEqualTo(CardTestSupport.ACQUIRER);
        assertThat(session.mode()).isEqualTo(CardEnums.CheckoutMode.PAYMENT_LINK);
        assertThat(store.sessions).containsKey(session.reference());
        assertThat(acquirer.lastRequest.reference()).isEqualTo(session.reference());
        assertThat(acquirer.lastRequest.amount()).isEqualByComparingTo("106.61");
    }

    @Test
    @DisplayName("recurso desligado recusa sem chamar a credenciadora")
    void disabledRefuses() {
        assertThatThrownBy(() -> service(CardTestSupport.params(false))
                .create(command(true, true, "106.61", "106.61")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(acquirer.createCalls).isZero();
    }

    @Test
    @DisplayName("acordo nao ativo ou parcela fechada recusam")
    void inactiveOrClosedRefuses() {
        var svc = service(CardTestSupport.params(true));
        assertThatThrownBy(() -> svc.create(command(false, true, "106.61", "106.61")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> svc.create(command(true, false, "106.61", "106.61")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(acquirer.createCalls).isZero();
    }

    @Test
    @DisplayName("valor diferente do valor da parcela na agenda recusa (baixa automatica exige igualdade)")
    void divergentAmountRefuses() {
        assertThatThrownBy(() -> service(CardTestSupport.params(true))
                .create(command(true, true, "106.61", "100.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(acquirer.createCalls).isZero();
    }

    @Test
    @DisplayName("entrada do acordo e a parcela 0 e tambem pode ser paga por cartao")
    void downPaymentIsInstallmentZero() {
        var cmd = new CardCheckoutService.CreateCommand(CardTestSupport.TENANT, CardTestSupport.AGREEMENT,
                0, true, true, new BigDecimal("220.00"), new BigDecimal("220.00"),
                "https://portal.example.invalid/retorno",
                Instant.parse("2026-11-05T23:59:00Z"), Instant.parse("2026-11-05T10:00:00Z"));
        var session = service(CardTestSupport.params(true)).create(cmd);
        assertThat(session.installmentNumber()).isZero();
    }
}
