package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - limite por identificador e IP: atraso crescente, 429, sem lockout da vitima. */
class LoginThrottleTest {

    private static final PortalParameters.AccountThrottle LIMITS =
            new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15);

    @Test
    void semFalhasPermiteSemAtraso() {
        var v = LoginThrottle.evaluate(0, 0, LIMITS);
        assertThat(v.decision()).isEqualTo(LoginThrottle.Decision.ALLOW);
        assertThat(v.delayMillis()).isZero();
    }

    @Test
    void atrasoCresceComAsFalhas() {
        long d1 = LoginThrottle.evaluate(1, 0, LIMITS).delayMillis();
        long d3 = LoginThrottle.evaluate(3, 0, LIMITS).delayMillis();
        assertThat(d3).isGreaterThan(d1);
        assertThat(LoginThrottle.evaluate(1, 0, LIMITS).decision()).isEqualTo(LoginThrottle.Decision.DELAY);
    }

    @Test
    void estouroPorIdentificadorOuPorIpVira429ComAlerta() {
        var byIdentifier = LoginThrottle.evaluate(5, 0, LIMITS);
        var byIp = LoginThrottle.evaluate(0, 20, LIMITS);
        assertThat(byIdentifier.decision()).isEqualTo(LoginThrottle.Decision.TOO_MANY_REQUESTS);
        assertThat(byIp.decision()).isEqualTo(LoginThrottle.Decision.TOO_MANY_REQUESTS);
        assertThat(byIdentifier.centralAlert()).isTrue();
        assertThatThrownBy(() -> LoginThrottle.enforce(byIdentifier))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    @Test
    void nuncaBloqueiaAContaSoAtrasoOu429Temporario() {
        // Nao existe estado de bloqueio de conta no veredito: ou permite, ou atrasa, ou 429 da tentativa.
        for (int failures = 0; failures <= 25; failures++) {
            assertThat(LoginThrottle.evaluate(failures, 0, LIMITS).decision())
                    .isIn(LoginThrottle.Decision.ALLOW, LoginThrottle.Decision.DELAY,
                            LoginThrottle.Decision.TOO_MANY_REQUESTS);
        }
    }
}
