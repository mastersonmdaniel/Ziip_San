package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitService;
import br.com.ziipsan.commercial.registry.application.RegistrySettingsService;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.commercial.registry.repository.UnitCustomerRepository;
import br.com.ziipsan.commercial.registry.repository.UnitStatusHistoryRepository;
import br.com.ziipsan.commercial.registry.repository.UnitSubcategoryActivityRepository;
import br.com.ziipsan.commercial.registry.repository.UnitSubcategoryRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1.15: as três leituras por id do imóvel (ficha, vínculos e linha do tempo)
 * têm registry:read e, portanto, são alcançáveis pelo VIEWER. Imóvel excluído
 * é 404 em todas — as listas já o escondiam, o GET por id não.
 */
class ConsumerUnitServiceDeletedReadTest {

    private static final UUID TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ConsumerUnitRepository units;
    private ConsumerUnitService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        units = mock(ConsumerUnitRepository.class);
        service = new ConsumerUnitService(units, mock(AddressRepository.class),
                mock(UnitCustomerRepository.class), mock(UnitSubcategoryRepository.class),
                mock(UnitSubcategoryActivityRepository.class), mock(UnitStatusHistoryRepository.class),
                mock(AuditLogRepository.class), mock(CustomerDocumentService.class),
                mock(RegistrySettingsService.class), mock(PersonalDataAccessService.class));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("imóvel excluído: ficha, vínculos e linha do tempo são 404")
    void deletedUnitReadsAreNotFound() {
        UUID id = UUID.randomUUID();
        ConsumerUnit u = new ConsumerUnit();
        u.setId(id);
        u.setTenantId(TENANT);
        u.setDeletedAt(OffsetDateTime.now());
        when(units.findById(id)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ApiException.NotFound.class);
        assertThatThrownBy(() -> service.customers(id)).isInstanceOf(ApiException.NotFound.class);
        assertThatThrownBy(() -> service.history(id)).isInstanceOf(ApiException.NotFound.class);
    }
}
