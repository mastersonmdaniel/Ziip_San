package br.com.ziipsan.platform.orgscope;

import java.util.Set;
import java.util.UUID;

/**
 * O escopo EFETIVO de um usuário, já expandido (P3.30): os conjuntos de
 * localidades, setores e rotas que ele alcança, as unidades organizacionais
 * cobertas (as concedidas e suas descendentes) e a marca de irrestrito.
 *
 * <p>A expansão é DESCENDENTE e acontece na resolução (OrgScopeService):
 * conceder uma localidade inclui os setores dela e as rotas dela/dos setores;
 * conceder um setor inclui as rotas dele; conceder uma unidade organizacional
 * inclui as áreas dela e das descendentes, já expandidas do mesmo jeito.
 * Sem expansão, um escopo "localidade A" não cobriria um registro cuja
 * âncora resolvida é o setor — e o teste do aceite (quem tem escopo na
 * localidade A não vê a localidade B) exige a comparação na mesma base.
 *
 * <p>{@link #EMPTY} é o escopo de quem NÃO tem concessão nenhuma: não cobre
 * registro nenhum, nem os sem âncora. Ausência de fato não concede nada —
 * o filtro é obrigatório e falha fechado (o RLS continua sendo só do tenant;
 * este recorte é de aplicação, por cima dele).
 */
public record EffectiveScope(boolean unrestricted, Set<UUID> orgUnitIds,
                             Set<UUID> localityIds, Set<UUID> sectorIds, Set<UUID> routeIds) {

    public static final EffectiveScope EMPTY =
            new EffectiveScope(false, Set.of(), Set.of(), Set.of(), Set.of());

    public static final EffectiveScope UNRESTRICTED =
            new EffectiveScope(true, Set.of(), Set.of(), Set.of(), Set.of());

    public EffectiveScope {
        orgUnitIds = orgUnitIds == null ? Set.of() : Set.copyOf(orgUnitIds);
        localityIds = localityIds == null ? Set.of() : Set.copyOf(localityIds);
        sectorIds = sectorIds == null ? Set.of() : Set.copyOf(sectorIds);
        routeIds = routeIds == null ? Set.of() : Set.copyOf(routeIds);
    }

    /** União de dois escopos (a resolução soma as concessões do usuário). */
    public EffectiveScope union(EffectiveScope other) {
        if (other == null) {
            return this;
        }
        if (unrestricted || other.unrestricted) {
            return UNRESTRICTED;
        }
        Set<UUID> units = new java.util.HashSet<>(orgUnitIds);
        units.addAll(other.orgUnitIds);
        Set<UUID> localities = new java.util.HashSet<>(localityIds);
        localities.addAll(other.localityIds);
        Set<UUID> sectors = new java.util.HashSet<>(sectorIds);
        sectors.addAll(other.sectorIds);
        Set<UUID> routes = new java.util.HashSet<>(routeIds);
        routes.addAll(other.routeIds);
        return new EffectiveScope(false, units, localities, sectors, routes);
    }
}
