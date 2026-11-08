package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.signing.web.SigningController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Borda HTTP da assinatura (correcao D3): tenant e signatario vem do contexto
 * autenticado (TenantContextHolder + principal), nunca do corpo — e corpo
 * divergente do contexto e recusado.
 */
class SigningControllerTest {

    private final AtomicReference<SignatureService.SignCommand> captured = new AtomicReference<>();

    private SigningController controller() {
        SignatureService.SignatureFacade facade = new SignatureService.SignatureFacade() {
            @Override
            public String signJson(SignatureService.SignCommand command) {
                captured.set(command);
                return "{}";
            }

            @Override
            public String publicValidationJson(String verificationCode, String sourceIp) {
                return "{}";
            }
        };
        return new SigningController(facade);
    }

    private Map<String, String> body() {
        Map<String, String> body = new HashMap<>();
        body.put("documentType", "ADHESION_CONTRACT");
        body.put("level", "SIMPLE");
        body.put("documentReference", "documento-1");
        body.put("contentBase64", Base64.getEncoder().encodeToString(SigningTestSupport.CONTENT));
        body.put("acceptanceDocumentCode", SigningTestSupport.ACCEPTANCE_CODE);
        body.put("acceptanceDocumentVersion", SigningTestSupport.ACCEPTANCE_VERSION);
        return body;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("controller (D3): tenant vem do TenantContextHolder e signatario do principal autenticado")
    void identityComesFromAuthenticatedContext() {
        TenantContextHolder.setTenantId(SigningTestSupport.TENANT);
        String subject = SigningTestSupport.USER_ID.toString();
        controller().sign(subject, body());
        DocumentSigner.SignContext context = captured.get().context();
        assertThat(context.tenantId()).isEqualTo(SigningTestSupport.TENANT);
        assertThat(context.signerSubject()).isEqualTo(subject);
        // Sujeito UUID do token e tambem o user_id da chave do aceite da P1.24 (D6).
        assertThat(context.signerUserId()).isEqualTo(SigningTestSupport.USER_ID);
        assertThat(context.acceptanceDocumentCode()).isEqualTo(SigningTestSupport.ACCEPTANCE_CODE);
    }

    @Test
    @DisplayName("controller (D3): corpo com tenantId ou signerSubject divergente do contexto e recusado (403)")
    void divergentBodyIdentityRefused() {
        TenantContextHolder.setTenantId(SigningTestSupport.TENANT);
        String subject = SigningTestSupport.USER_ID.toString();
        Map<String, String> foreignTenant = body();
        foreignTenant.put("tenantId", "99999999-9999-9999-9999-999999999999");
        assertThatThrownBy(() -> controller().sign(subject, foreignTenant))
                .isInstanceOf(ApiException.Forbidden.class)
                .hasMessageContaining("tenantId");
        Map<String, String> foreignSigner = body();
        foreignSigner.put("signerSubject", "outra-pessoa");
        assertThatThrownBy(() -> controller().sign(subject, foreignSigner))
                .isInstanceOf(ApiException.Forbidden.class)
                .hasMessageContaining("signerSubject");
        assertThat(captured.get()).isNull();
    }

    @Test
    @DisplayName("controller (D3): sem contexto de tenant autenticado, recusa — corpo nao supre identidade")
    void missingTenantContextRefused() {
        Map<String, String> withTenantInBody = body();
        withTenantInBody.put("tenantId", SigningTestSupport.TENANT.toString());
        assertThatThrownBy(() -> controller().sign(SigningTestSupport.USER_ID.toString(), withTenantInBody))
                .isInstanceOf(ApiException.Forbidden.class)
                .hasMessageContaining("contexto de tenant");
        assertThat(captured.get()).isNull();
    }
}
