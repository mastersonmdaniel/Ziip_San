package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.CoordinatesView;
import br.com.ziipsan.commercial.registry.domain.Address;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.core.api.ApiException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.application.JpqlQuery.blankToNull;

/**
 * ED-12: endereço alternativo da unidade consumidora — um segundo endereço
 * da unidade (entrega/correspondência alternativos, referência de acesso),
 * distinto do endereço físico principal ({@code consumer_units.address_id},
 * tipo {@code PHYSICAL}, {@code primary=true}).
 *
 * <p>Nenhuma tabela nova: o endereço alternativo é uma linha da tabela
 * polimórfica {@code addresses} que já existe desde a V1_1, com
 * {@code entity_type = "consumer_units"}, {@code entity_id} da unidade e
 * {@code type = ALTERNATIVE} — o valor {@code ALTERNATIVE} do enum
 * {@code Address.Type} existe desde a criação da entidade e não tinha
 * escrita no cadastro de unidades. No máximo UM alternativo por unidade:
 * gravar de novo substitui o anterior (PUT), e a remoção apaga a linha —
 * endereço não é catálogo, e o histórico da troca fica na auditoria da
 * tabela, como nos endereços de cliente.
 *
 * <p>Fronteira declarada: a escrita principal da unidade
 * ({@code ConsumerUnitService}) registra as trocas de endereço pela OS
 * {@code DELIVERY_ADDRESS_CHANGE} (ADR-0039); esta rota é um complemento
 * focado e NÃO abre OS nesta entrega — a fiação da OS exige tocar o
 * maquinário de diffs do {@code ConsumerUnitService}, fora do corte seguro
 * sem compilação local. A pendência está registrada no relatório da ED-12;
 * até lá, a trilha é a auditoria padrão das tabelas de tenant.
 */
@Service
@Transactional(readOnly = true)
public class UnitAlternativeAddressService {

    /** Mesmo entity_type das linhas de endereço da unidade (ConsumerUnitService). */
    static final String UNIT_ENTITY_TYPE = "consumer_units";

    private static final GeometryFactory GEOMETRY_4674 =
            new GeometryFactory(new PrecisionModel(), 4674);

    private final ConsumerUnitRepository units;
    private final AddressRepository addresses;

    public UnitAlternativeAddressService(ConsumerUnitRepository units, AddressRepository addresses) {
        this.units = units;
        this.addresses = addresses;
    }

    /** Endereço alternativo vigente da unidade, ou vazio. */
    public Optional<AddressView> get(UUID unitId) {
        unit(unitId);
        return findAlternative(unitId).map(UnitAlternativeAddressService::view);
    }

    /** Cria ou substitui o endereço alternativo da unidade. */
    @Transactional
    public AddressView set(UUID unitId, AddressInput in) {
        ConsumerUnit unit = unit(unitId);
        Address a = findAlternative(unitId).orElseGet(() -> {
            Address created = RegistryContext.forCreate(new Address());
            created.setEntityType(UNIT_ENTITY_TYPE);
            created.setEntityId(unit.getId());
            created.setType(Address.Type.ALTERNATIVE);
            created.setPrimary(false);
            return created;
        });
        if (a.getId() != null) {
            RegistryContext.touch(a);
        }
        apply(a, in);
        return view(addresses.saveAndFlush(a));
    }

    /** Remove o endereço alternativo. Sem alternativo, é 404 — nunca no-op silencioso. */
    @Transactional
    public void remove(UUID unitId) {
        unit(unitId);
        Address a = findAlternative(unitId).orElseThrow(() ->
                new ApiException.NotFound("A unidade nao tem endereco alternativo cadastrado."));
        addresses.delete(a);
    }

    private Optional<Address> findAlternative(UUID unitId) {
        return addresses
                .findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(
                        RegistryContext.tenantId(), UNIT_ENTITY_TYPE, unitId)
                .stream()
                .filter(a -> a.getType() == Address.Type.ALTERNATIVE)
                .findFirst();
    }

    private ConsumerUnit unit(UUID unitId) {
        ConsumerUnit unit = RegistryContext.require(units.findById(unitId),
                () -> "Unidade consumidora nao encontrada.");
        if (unit.getDeletedAt() != null) {
            throw new ApiException.Conflict("record-deleted", "Unidade excluida nao aceita endereco alternativo.");
        }
        return unit;
    }

    /** Mesma normalização do endereço principal (ConsumerUnitService.apply de Address). */
    private static void apply(Address a, AddressInput in) {
        a.setCep(in.cep() == null ? null : in.cep().replaceAll("\\D", ""));
        a.setStreet(in.streetName().trim());
        a.setNumber(blankToNull(in.number()));
        a.setComplement(blankToNull(in.complement()));
        a.setNeighborhood(blankToNull(in.neighborhood()));
        a.setCity(blankToNull(in.city()));
        a.setState(in.state() == null ? null : in.state().toUpperCase(java.util.Locale.ROOT));
        a.setCountry("BR");
        a.setMunicipalityIbge(blankToNull(in.municipalityIbge()));
        a.setReferencePoint(blankToNull(in.referencePoint()));
        a.setGeocodePrecision(blankToNull(in.geocodePrecision()));
        a.setStreetId(in.streetId());
        a.setNeighborhoodId(in.neighborhoodId());
        if (in.coordinates() == null || (in.coordinates().lat() == null && in.coordinates().lng() == null)) {
            a.setCoordinates(null);
        } else if (in.coordinates().lat() == null || in.coordinates().lng() == null) {
            throw new ApiException.BadRequest("coordinates",
                    "Informe latitude e longitude juntas, ou deixe as duas em branco.");
        } else {
            a.setCoordinates(GEOMETRY_4674.createPoint(
                    new Coordinate(in.coordinates().lng(), in.coordinates().lat())));
        }
    }

    static AddressView view(Address a) {
        CoordinatesView coordinates = a.getCoordinates() == null ? null
                : new CoordinatesView(a.getCoordinates().getY(), a.getCoordinates().getX());
        return new AddressView(a.getCep(), a.getStreet(), a.getNumber(), a.getComplement(),
                a.getNeighborhood(), a.getCity(), a.getState(), coordinates,
                a.getMunicipalityIbge(), a.getReferencePoint(), a.getGeocodePrecision());
    }
}
