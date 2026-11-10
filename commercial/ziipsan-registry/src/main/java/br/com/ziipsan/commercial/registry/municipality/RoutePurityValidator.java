package br.com.ziipsan.commercial.registry.municipality;

import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.UnitRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * MM-03 — Validador de pureza de rotas de um tenant (P-BF-07).
 *
 * <p>Classifica TODAS as rotas do tenant (via {@link RouteMunicipalityResolver},
 * sobre a derivacao canonica do MM-01) e devolve o {@link RoutePurityReport}
 * com as excecoes (rotas mistas). Rota mista NAO e erro deste validador:
 * e listada, nunca lancada como excecao de programacao — a invariante e
 * validacao com relatorio, nao bloqueio (estudo §6). Erros de uso
 * (tenant divergente nas UCs, argumentos nulos) falham explicitamente
 * no resolvedor.
 *
 * <p>Sem efeitos laterais: nao escreve em tabela nenhuma, nao altera
 * {@code reading_routes} e nao toca o artefacto da ED-19.
 */
public final class RoutePurityValidator {

    private final RouteMunicipalityResolver resolver;

    public RoutePurityValidator(MunicipalityDerivation derivation) {
        this.resolver = new RouteMunicipalityResolver(
                Objects.requireNonNull(derivation, "derivation"));
    }

    public RoutePurityValidator(RouteMunicipalityResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * @param tenantId      tenant a validar
     * @param unitsByRoute  composicao das rotas: routeId -&gt; UCs da rota
     *                      (de {@code consumer_units.reading_route_id});
     *                      rota sem UC entra com iteravel vazio (EMPTY)
     */
    public RoutePurityReport validate(UUID tenantId,
                                      Map<UUID, ? extends Iterable<UnitRef>> unitsByRoute) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(unitsByRoute, "unitsByRoute");
        List<RouteMunicipalityScope> scopes = new ArrayList<>();
        for (Map.Entry<UUID, ? extends Iterable<UnitRef>> e : unitsByRoute.entrySet()) {
            Objects.requireNonNull(e.getKey(), "routeId nulo na composicao");
            scopes.add(resolver.resolve(tenantId, e.getKey(), e.getValue()));
        }
        return new RoutePurityReport(tenantId, scopes);
    }
}
