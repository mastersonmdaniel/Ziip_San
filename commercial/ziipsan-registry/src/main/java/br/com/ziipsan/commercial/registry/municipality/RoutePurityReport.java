package br.com.ziipsan.commercial.registry.municipality;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MM-03 — Relatorio de validacao de pureza das rotas de um tenant
 * (P-BF-07): a classificacao de TODAS as rotas + a lista de excecoes
 * (as rotas {@code MIXED}).
 *
 * <p>A invariante de rotas nasce como VALIDACAO COM RELATORIO — nao
 * como bloqueio seco nem como {@code NOT NULL} imposto (disposicao
 * expressa do estudo, ANALISE-MULTIMUNICIPIO §6: cadastros reais podem
 * ter rotas mistas de facto). Este relatorio e a pre-condicao declarada
 * de qualquer imposicao futura ao nivel do banco (pendencia nomeada do
 * pacote MM-03): impor sem antes listar as excecoes reais quebraria
 * cadastros legitimos sem diagnostico.
 *
 * @param tenantId tenant validado
 * @param scopes   escopo de cada rota do tenant, ordenado por routeId
 *                 (ordem deterministica para relatorios reproduziveis)
 */
public record RoutePurityReport(UUID tenantId, List<RouteMunicipalityScope> scopes) {

    public RoutePurityReport {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(scopes, "scopes");
        for (RouteMunicipalityScope s : scopes) {
            Objects.requireNonNull(s, "escopo nulo no relatorio");
            if (!tenantId.equals(s.tenantId())) {
                throw new IllegalArgumentException("escopo da rota " + s.routeId()
                        + " e do tenant " + s.tenantId()
                        + ", nao do tenant do relatorio " + tenantId);
            }
        }
        scopes = scopes.stream()
                .sorted((a, b) -> a.routeId().toString().compareTo(b.routeId().toString()))
                .toList();
    }

    public int totalRoutes() {
        return scopes.size();
    }

    public long pureCount() {
        return scopes.stream().filter(RouteMunicipalityScope::isPure).count();
    }

    public long mixedCount() {
        return exceptions().size();
    }

    public long emptyCount() {
        return scopes.stream()
                .filter(s -> s.kind() == RouteMunicipalityScope.Kind.EMPTY).count();
    }

    /** As excecoes: SOMENTE as rotas MIXED, na ordem do relatorio. */
    public List<RouteMunicipalityScope> exceptions() {
        return scopes.stream()
                .filter(s -> s.kind() == RouteMunicipalityScope.Kind.MIXED)
                .toList();
    }

    /**
     * Relatorio de excecoes em texto deterministico: uma linha por rota
     * mista, com os municipios presentes e as UCs por municipio (e as
     * inderivaveis, quando houver). Sem excecoes, devolve texto vazio —
     * "nenhuma excecao" nao se finge com linha decorativa.
     */
    public String formatExceptions() {
        StringBuilder sb = new StringBuilder();
        for (RouteMunicipalityScope s : exceptions()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("ROTA ").append(s.routeId())
                    .append(" MIXED motivo=").append(s.mixedReason())
                    .append(" municipios=").append(s.unitsByMunicipality())
                    .append(" inderivaveis=").append(s.underivedUnits())
                    .append(" total=").append(s.totalUnits());
        }
        return sb.toString();
    }
}
