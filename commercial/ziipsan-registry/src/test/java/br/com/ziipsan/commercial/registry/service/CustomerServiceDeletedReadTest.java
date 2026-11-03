package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.commercial.registry.application.CustomerService;
import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerContactRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1.15: o GET por id não pode ser a porta dos fundos do filtro de excluídos
 * das listas. Cliente excluído é 404 — e a leitura nem chega a ser registrada
 * como acesso a dado pessoal, porque nenhum dado foi entregue.
 */
class CustomerServiceDeletedReadTest {

    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private CustomerRepository customers;
    private PersonalDataAccessService accessLog;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        customers = mock(CustomerRepository.class);
        accessLog = mock(PersonalDataAccessService.class);
        service = new CustomerService(customers, mock(CustomerContactRepository.class),
                mock(AddressRepository.class), mock(CustomerDocumentService.class), accessLog);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Customer customer(UUID id, boolean deleted) {
        Customer c = new Customer();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setName("Cliente Teste");
        if (deleted) {
            c.setDeletedAt(OffsetDateTime.now());
            c.setDeletedReason("pedido do titular");
        }
        return c;
    }

    @Test
    @DisplayName("cliente excluído: GET por id é 404 e não registra acesso")
    void deletedCustomerGetIsNotFound() {
        UUID id = UUID.randomUUID();
        when(customers.findById(id)).thenReturn(Optional.of(customer(id, true)));

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ApiException.NotFound.class);
        verify(accessLog, never()).recordPersonalRead(anyString(), any());
    }

    @Test
    @DisplayName("cliente inexistente continua 404")
    void missingCustomerGetIsNotFound() {
        UUID id = UUID.randomUUID();
        when(customers.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ApiException.NotFound.class);
    }
}
