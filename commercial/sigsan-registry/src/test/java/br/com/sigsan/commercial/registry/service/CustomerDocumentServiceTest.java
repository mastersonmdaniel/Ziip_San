package br.com.sigsan.commercial.registry.service;

import br.com.sigsan.commercial.registry.domain.Customer;
import br.com.sigsan.commercial.registry.domain.Customer.PersonType;
import br.com.sigsan.commercial.registry.repository.CustomerRepository;
import br.com.sigsan.commercial.registry.service.CustomerDocumentService.DuplicateDocumentException;
import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.security.crypto.BlindIndex;
import br.com.sigsan.security.crypto.FieldCipher;
import br.com.sigsan.security.crypto.FieldProtector;
import br.com.sigsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerDocumentServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    // CPF e CNPJ válidos de exemplo (os mesmos dos testes de DocumentNumber), não de pessoas reais.
    private static final String CPF = "529.982.247-25";
    private static final String CNPJ = "11.222.333/0001-81";

    private CustomerRepository customers;
    private AuditLogRepository auditLog;
    private CustomerDocumentService service;

    @BeforeEach
    void setUp() {
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (i + 1);
        }
        var keys = new EnvDerivedTenantKeyProvider(master);
        customers = mock(CustomerRepository.class);
        auditLog = mock(AuditLogRepository.class);
        when(customers.findByTenantIdAndDocumentHashAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());
        service = new CustomerDocumentService(new FieldProtector(new FieldCipher(keys), new BlindIndex(keys)), customers, auditLog);
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static Customer customer(PersonType type) {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setTenantId(TENANT);
        c.setPersonType(type);
        c.setName("Cliente de Teste");
        return c;
    }

    @Test
    @DisplayName("grava cifra, índice e máscara; nenhum dos três contém o CPF em claro")
    void assignStoresOnlyProtectedForms() {
        Customer c = customer(PersonType.PF);
        service.assign(c, CPF);
        assertThat(c.getDocumentCiphertext()).isNotBlank().doesNotContain("52998224725").doesNotContain("982");
        assertThat(c.getDocumentHash()).hasSize(64).doesNotContain("52998224725");
        assertThat(c.getDocumentMasked()).isEqualTo("***.982.247-**");
    }

    @Test
    @DisplayName("documento inválido ou incompatível com o tipo de pessoa é recusado")
    void rejectsInvalidOrMismatchedDocument() {
        assertThatThrownBy(() -> service.assign(customer(PersonType.PF), "529.982.247-26"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assign(customer(PersonType.PF), CNPJ))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("incompatível");
        assertThatThrownBy(() -> service.assign(customer(PersonType.PJ), CPF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("documento já usado por outro cliente ativo: duplicidade detectada pelo índice cego")
    void detectsDuplicateByBlindIndex() {
        Customer existing = customer(PersonType.PF);
        service.assign(existing, CPF);
        when(customers.findByTenantIdAndDocumentHashAndDeletedAtIsNull(TENANT, existing.getDocumentHash()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.assign(customer(PersonType.PF), "52998224725"))
                .as("mesmo CPF sem pontuação").isInstanceOf(DuplicateDocumentException.class);
        service.assign(existing, CPF); // o próprio cliente pode regravar o mesmo documento
    }

    @Test
    @DisplayName("busca por documento usa o mesmo índice, com ou sem pontuação")
    void findUsesTheSameIndex() {
        Customer c = customer(PersonType.PF);
        service.assign(c, CPF);
        when(customers.findByTenantIdAndDocumentHashAndDeletedAtIsNull(TENANT, c.getDocumentHash())).thenReturn(Optional.of(c));
        assertThat(service.findActiveByDocument("52998224725")).contains(c);
        assertThat(service.findActiveByDocument("documento qualquer")).isEmpty();
    }

    @Test
    @DisplayName("revelar exige finalidade e grava o acesso na auditoria, sem o valor")
    void revealIsAudited() {
        Customer c = customer(PersonType.PF);
        service.assign(c, CPF);

        assertThatThrownBy(() -> service.reveal(c, " ")).isInstanceOf(IllegalArgumentException.class);
        verify(auditLog, never()).save(any());

        assertThat(service.reveal(c, "atendimento: confirmar titularidade")).isEqualTo(CPF);
        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLog).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo(AuditLog.AuditAction.READ);
        assertThat(saved.getValue().getEntityId()).isEqualTo(c.getId());
        assertThat(saved.getValue().getMetadata()).contains("customers.document").doesNotContain("52998224725").doesNotContain("982");
    }

    @Test
    @DisplayName("sem tenant no contexto: erro")
    void requiresTenant() {
        TenantContextHolder.clear();
        assertThatThrownBy(() -> service.assign(customer(PersonType.PF), CPF)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.findActiveByDocument(CPF)).isInstanceOf(IllegalStateException.class);
        verify(customers, never()).findByTenantIdAndDocumentHashAndDeletedAtIsNull(eq(TENANT), any());
    }
}
