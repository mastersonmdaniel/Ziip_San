package br.com.ziipsan.platform.orgscope;

import java.util.UUID;

/**
 * A ÂNCORA territorial de um registro recortado pelo escopo (protocolo, OS,
 * unidade consumidora ou conta): a localidade, o setor e a rota resolvidos
 * pela cadeia do cadastro (unidade consumidora → quadra → setor → localidade;
 * unidade consumidora → rota de leitura). Qualquer componente pode ser nulo
 * quando a cadeia não o tem (ex.: unidade sem rota); a âncora é resolvida
 * pela porta de âncoras do serviço (OrgScopePorts.AnchorPort), nunca montada
 * à mão pelo chamador a partir de dado não conferido.
 *
 * <p>Âncora SEM nenhum componente ({@link #EMPTY}) é o registro sem âncora
 * territorial resolvível (ex.: protocolo só com texto de local, sem unidade
 * consumidora): pela regra fail-closed da {@link ScopeRule}, ele só é
 * acessível a quem tem escopo irrestrito no tenant.
 */
public record AreaAnchor(UUID localityId, UUID sectorId, UUID routeId) {

    public static final AreaAnchor EMPTY = new AreaAnchor(null, null, null);

    public boolean hasAny() {
        return localityId != null || sectorId != null || routeId != null;
    }
}
