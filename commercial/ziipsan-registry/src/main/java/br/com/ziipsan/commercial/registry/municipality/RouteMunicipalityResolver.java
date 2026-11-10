package br.com.ziipsan.commercial.registry.municipality;

import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.Derivation;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.DerivationStatus;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.UnitRef;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * MM-03 — Resolve o {@link RouteMunicipalityScope} de uma rota a partir
 * das suas UCs, consumindo a derivacao canonica do MM-01
 * ({@link MunicipalityDerivation}) — a cadeia NAO e reimplementada
 * aqui (D-MM-02).
 *
 * <p>A composicao da rota (quais UCs lhe pertencem) e dada por quem
 * chama, a partir de {@code consumer_units.reading_route_id} (V3_1) —
 * no banco, a view companheira {@code route_municipality_counts}
 * (MM-03__route_municipality_counts.sql) faz a mesma agregacao sobre a
 * view do MM-01.
 *
 * <p>UC cujo estado detalhado nao e {@code DERIVED} (sem quadra, cadeia
 * quebrada, IBGE nulo na localidade) conta como inderivavel: nao e
 * descartada em silencio nem atribuida a municipio nenhum — entra em
 * {@code underivedUnits} do escopo e impede a certificacao de pureza.
 *
 * <p>Classe pura, sem JPA/Spring, como a derivacao que consome.
 */
public final class RouteMunicipalityResolver {

    private final MunicipalityDerivation derivation;

    public RouteMunicipalityResolver(MunicipalityDerivation derivation) {
        this.derivation = Objects.requireNonNull(derivation, "derivation");
    }

    /**
     * Escopo da rota {@code routeId} do tenant {@code tenantId} a partir
     * das suas UCs. UC de outro tenant na lista e erro de quem chama
     * (a composicao de uma rota e intra-tenant pelas FK compostas) —
     * falha explicita, nunca contagem silenciosa.
     */
    public RouteMunicipalityScope resolve(UUID tenantId, UUID routeId,
                                          Iterable<UnitRef> unitsOfRoute) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(unitsOfRoute, "unitsOfRoute");
        Map<String, Integer> counts = new TreeMap<>();
        int underived = 0;
        for (UnitRef unit : unitsOfRoute) {
            Objects.requireNonNull(unit, "unidade nula na composicao da rota");
            if (!tenantId.equals(unit.tenantId())) {
                throw new IllegalArgumentException("unidade " + unit.id()
                        + " e do tenant " + unit.tenantId()
                        + ", nao do tenant da rota " + tenantId);
            }
            Derivation d = derivation.deriveDetailed(unit);
            if (d.status() == DerivationStatus.DERIVED) {
                counts.merge(d.municipalityIbge(), 1, Integer::sum);
            } else {
                underived++;
            }
        }
        return RouteMunicipalityScope.of(routeId, tenantId, counts, underived);
    }
}
