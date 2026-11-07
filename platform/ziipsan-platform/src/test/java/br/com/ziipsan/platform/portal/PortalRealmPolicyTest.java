package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - isolamento de realms: token do portal nao entra no backoffice e vice-versa. */
class PortalRealmPolicyTest {

    private static final String PORTAL_ISSUER = "https://sso.example/realms/ziipsan-portal";
    private static final String BACKOFFICE_ISSUER = "https://sso.example/realms/ziipsan";

    @Test
    void tokenDoPortalERecusadoNoBackoffice() {
        assertThatThrownBy(() -> PortalRealmPolicy.requireBackofficeToken(PORTAL_ISSUER))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void tokenDoBackofficeERecusadoNoPortal() {
        assertThatThrownBy(() -> PortalRealmPolicy.requirePortalToken(BACKOFFICE_ISSUER))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void emissorSemRealmERecusadoNoBackoffice() {
        assertThatThrownBy(() -> PortalRealmPolicy.requireBackofficeToken("https://sso.example/sem-realm"))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> PortalRealmPolicy.requireBackofficeToken(null))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void tokensNosSeusRealmsPassam() {
        assertThatCode(() -> PortalRealmPolicy.requirePortalToken(PORTAL_ISSUER)).doesNotThrowAnyException();
        assertThatCode(() -> PortalRealmPolicy.requireBackofficeToken(BACKOFFICE_ISSUER)).doesNotThrowAnyException();
        assertThat(PortalRealmPolicy.isPortalToken(PORTAL_ISSUER)).isTrue();
        assertThat(PortalRealmPolicy.isPortalToken(BACKOFFICE_ISSUER)).isFalse();
        assertThat(PortalRealmPolicy.isPortalToken(null)).isFalse();
    }
}
