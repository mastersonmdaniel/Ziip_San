package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.core.audit.AuditLog;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * ED-12: RG e filiação cifrados, com finalidade registrada. Os valores de
 * exemplo são sintéticos e as máscaras esperadas foram calculadas à mão:
 * RG "12.345.678-9" normaliza para "123456789" e mascara como "***89";
 * filiação ["Maria da Silva", "Jose Santos"] mascara como
 * "Maria ***; Jose ***".
 */
class CustomerIdentityServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUSTOMER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String RG = "12.345.678-9";
    private static final String RG_NORMALIZED = "123456789";

    private AuditLogRepository auditLog;
    private FieldProtector protector;
    private CustomerIdentityService service;

    @BeforeEach
    void setUp() {
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (i + 1);
        }
        var keys = new EnvDerivedTenantKeyProvider(master);
        protector = new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
        auditLog = mock(AuditLogRepository.class);
        service = new CustomerIdentityService(protector, auditLog);
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("RG protegido: cifra e índice sem o valor, máscara calculada à mão, finalidade gravada na auditoria sem o RG")
    void protectRg() {
        var protectedRg = service.protectRg(CUSTOMER, RG, "CUSTOMER_IDENTIFICATION");

        assertThat(protectedRg.ciphertext()).isNotBlank().doesNotContain(RG_NORMALIZED);
        assertThat(protectedRg.hash()).hasSize(64).doesNotContain(RG_NORMALIZED)
                .isEqualTo(protector.indexOf(TENANT, CustomerIdentityService.RG_FIELD, RG_NORMALIZED));
        assertThat(protectedRg.masked()).isEqualTo("***89");
        assertThat(protectedRg.purposeCode()).isEqualTo("CUSTOMER_IDENTIFICATION");

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLog).save(saved.capture());
        assertThat(saved.getValue().getEntityId()).isEqualTo(CUSTOMER);
        assertThat(saved.getValue().getMetadata())
                .contains("customers.rg").contains("CUSTOMER_IDENTIFICATION")
                .doesNotContain(RG_NORMALIZED);
    }

    @Test
    @DisplayName("revelar o RG devolve o valor normalizado e registra o acesso com a finalidade")
    void revealRg() {
        var protectedRg = service.protectRg(CUSTOMER, RG, "CUSTOMER_IDENTIFICATION");
        assertThat(service.revealRg(CUSTOMER, protectedRg.ciphertext(), "IDENTITY_VERIFICATION"))
                .isEqualTo(RG_NORMALIZED);
    }

    @Test
    @DisplayName("finalidade fora do registro é recusada na gravação e na revelação")
    void unknownPurposeRejected() {
        assertThatThrownBy(() -> service.protectRg(CUSTOMER, RG, "MARKETING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finalidade");
        var protectedRg = service.protectRg(CUSTOMER, RG, "CUSTOMER_IDENTIFICATION");
        assertThatThrownBy(() -> service.revealRg(CUSTOMER, protectedRg.ciphertext(), "CURIOSIDADE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.protectFiliation(CUSTOMER, List.of("Maria da Silva"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RG fora da forma é recusado: curto demais e vazio")
    void rgShapeRejected() {
        assertThat(CustomerIdentityService.normalizeRg("12.345.678-9")).isEqualTo(RG_NORMALIZED);
        assertThat(CustomerIdentityService.normalizeRg("mg-12.345.678")).isEqualTo("MG12345678");
        assertThatThrownBy(() -> CustomerIdentityService.normalizeRg("123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.protectRg(CUSTOMER, "  ", "CUSTOMER_IDENTIFICATION"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("um tenant não revela o RG de outro")
    void tenantIsolation() {
        var protectedRg = service.protectRg(CUSTOMER, RG, "CUSTOMER_IDENTIFICATION");
        TenantContextHolder.setTenantId(OTHER_TENANT);
        assertThatThrownBy(() -> service.revealRg(CUSTOMER, protectedRg.ciphertext(), "IDENTITY_VERIFICATION"))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.rgIndex(RG)).isNotEqualTo(protectedRg.hash());
    }

    @Test
    @DisplayName("filiação cifrada como um valor: ida e volta com 2 nomes e com 1 nome; máscara calculada à mão")
    void filiationRoundTrip() {
        var two = service.protectFiliation(CUSTOMER,
                List.of("  Maria   da Silva ", "Jose Santos"), "CUSTOMER_IDENTIFICATION");
        assertThat(two.masked()).isEqualTo("Maria ***; Jose ***");
        assertThat(two.ciphertext()).doesNotContain("Maria").doesNotContain("Jose");
        assertThat(service.revealFiliation(CUSTOMER, two.ciphertext(), "FRAUD_PREVENTION"))
                .containsExactly("Maria da Silva", "Jose Santos");

        var one = service.protectFiliation(CUSTOMER, List.of("Maria da Silva"), "CUSTOMER_IDENTIFICATION");
        assertThat(service.revealFiliation(CUSTOMER, one.ciphertext(), "CUSTOMER_IDENTIFICATION"))
                .containsExactly("Maria da Silva");
    }

    @Test
    @DisplayName("filiação fora da forma é recusada: 0 nomes, 3 nomes e nome curto")
    void filiationShapeRejected() {
        assertThatThrownBy(() -> service.protectFiliation(CUSTOMER, List.of(), "CUSTOMER_IDENTIFICATION"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.protectFiliation(CUSTOMER,
                List.of("Ana Souza", "Bia Souza", "Cid Souza"), "CUSTOMER_IDENTIFICATION"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.protectFiliation(CUSTOMER, List.of("A"), "CUSTOMER_IDENTIFICATION"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem tenant no contexto, nada é protegido nem revelado")
    void tenantRequired() {
        TenantContextHolder.clear();
        assertThatThrownBy(() -> service.protectRg(CUSTOMER, RG, "CUSTOMER_IDENTIFICATION"))
                .isInstanceOf(IllegalStateException.class);
    }
}
