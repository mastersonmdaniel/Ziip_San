package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.CoordinatesInput;
import br.com.ziipsan.commercial.registry.domain.Address;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ED-12: endereço alternativo da unidade — uma linha {@code ALTERNATIVE} na
 * tabela polimórfica {@code addresses}, sem tocar o endereço principal.
 */
class UnitAlternativeAddressServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNIT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ConsumerUnitRepository units;
    private AddressRepository addresses;
    private UnitAlternativeAddressService service;

    @BeforeEach
    void setUp() {
        units = mock(ConsumerUnitRepository.class);
        addresses = mock(AddressRepository.class);
        service = new UnitAlternativeAddressService(units, addresses);
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        ConsumerUnit unit = new ConsumerUnit();
        unit.setId(UNIT);
        unit.setTenantId(TENANT);
        when(units.findById(UNIT)).thenReturn(Optional.of(unit));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static AddressInput input() {
        return new AddressInput("88000-000", "Rua Alternativa", "45", null, "Centro",
                "Florianopolis", "SC", new CoordinatesInput(-27.59, -48.54),
                "4205407", null, null, null, null);
    }

    private void noAddresses() {
        when(addresses.findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(TENANT, "consumer_units", UNIT))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("gravar cria a linha ALTERNATIVE, não primária, com CEP normalizado e coordenada x=lng, y=lat")
    void setCreatesAlternative() {
        noAddresses();
        when(addresses.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.set(UNIT, input());

        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        verify(addresses).saveAndFlush(saved.capture());
        Address a = saved.getValue();
        assertThat(a.getEntityType()).isEqualTo("consumer_units");
        assertThat(a.getEntityId()).isEqualTo(UNIT);
        assertThat(a.getType()).isEqualTo(Address.Type.ALTERNATIVE);
        assertThat(a.isPrimary()).isFalse();
        assertThat(a.getCep()).isEqualTo("88000000");
        assertThat(a.getCountry()).isEqualTo("BR");
        assertThat(a.getCoordinates().getX()).isEqualTo(-48.54);
        assertThat(a.getCoordinates().getY()).isEqualTo(-27.59);
        assertThat(view.streetName()).isEqualTo("Rua Alternativa");
    }

    @Test
    @DisplayName("gravar de novo substitui a mesma linha (PUT), sem criar segunda")
    void setReplacesExisting() {
        Address existing = new Address();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT);
        existing.setEntityType("consumer_units");
        existing.setEntityId(UNIT);
        existing.setType(Address.Type.ALTERNATIVE);
        when(addresses.findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(TENANT, "consumer_units", UNIT))
                .thenReturn(List.of(existing));
        when(addresses.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.set(UNIT, input());

        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        verify(addresses).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getStreet()).isEqualTo("Rua Alternativa");
    }

    @Test
    @DisplayName("ler sem alternativo devolve vazio; o endereço PHYSICAL da unidade não conta como alternativo")
    void getIgnoresPhysical() {
        Address physical = new Address();
        physical.setType(Address.Type.PHYSICAL);
        when(addresses.findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(TENANT, "consumer_units", UNIT))
                .thenReturn(List.of(physical));
        assertThat(service.get(UNIT)).isEmpty();
    }

    @Test
    @DisplayName("remover sem alternativo é 404 explícito; com alternativo, apaga a linha")
    void removeRules() {
        noAddresses();
        assertThatThrownBy(() -> service.remove(UNIT)).isInstanceOf(ApiException.NotFound.class);
        verify(addresses, never()).delete(any());

        Address existing = new Address();
        existing.setId(UUID.randomUUID());
        existing.setType(Address.Type.ALTERNATIVE);
        when(addresses.findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(TENANT, "consumer_units", UNIT))
                .thenReturn(List.of(existing));
        service.remove(UNIT);
        verify(addresses).delete(existing);
    }

    @Test
    @DisplayName("unidade inexistente é 404 e unidade excluída é conflito, sem gravar nada")
    void unitGuards() {
        when(units.findById(UNIT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.set(UNIT, input())).isInstanceOf(ApiException.NotFound.class);

        ConsumerUnit deleted = new ConsumerUnit();
        deleted.setId(UNIT);
        deleted.setTenantId(TENANT);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(units.findById(UNIT)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.set(UNIT, input())).isInstanceOf(ApiException.Conflict.class);
        verify(addresses, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("coordenada parcial (só latitude) é recusada como erro de campo")
    void partialCoordinatesRejected() {
        noAddresses();
        AddressInput partial = new AddressInput("88000-000", "Rua Alternativa", "45", null, "Centro",
                "Florianopolis", "SC", new CoordinatesInput(-27.59, null),
                null, null, null, null, null);
        assertThatThrownBy(() -> service.set(UNIT, partial)).isInstanceOf(ApiException.BadRequest.class);
        verify(addresses, never()).saveAndFlush(any());
    }
}
