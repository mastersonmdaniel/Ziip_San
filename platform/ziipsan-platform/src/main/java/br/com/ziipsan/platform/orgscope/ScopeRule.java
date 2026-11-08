package br.com.ziipsan.platform.orgscope;

import java.util.Set;
import java.util.UUID;

/**
 * A regra pura do FILTRO OBRIGATÓRIO de escopo (P3.30, aceite): dado o escopo
 * EFETIVO (já expandido) de um usuário e a ÂNCORA territorial de um registro,
 * decide se o registro está dentro do recorte dele. Vale para leitura E para
 * escrita de protocolo, OS, unidade consumidora e conta — o serviço
 * (OrgScopeService) chama {@link #covers} nos dois caminhos, sem exceção:
 * método de repositório dos módulos donos que não passe por esta regra é
 * defeito de fiação, declarado no relatório da tarefa.
 *
 * <p>Semântica, sem margem:
 * <ul>
 *   <li>escopo irrestrito cobre tudo, inclusive registro sem âncora;</li>
 *   <li>registro sem âncora resolvível ({@link AreaAnchor#EMPTY}) só é
 *       coberto pelo irrestrito — fail-closed: sem saber a que área o
 *       registro pertence, não se presume que ele é "de todos";</li>
 *   <li>nos demais casos, basta UM componente da âncora estar no conjunto
 *       expandido correspondente (localidade ∈ localidades, setor ∈
 *       setores, rota ∈ rotas). Como a expansão do escopo é descendente e a
 *       âncora é resolvida pela cadeia inteira, os três testes são a mesma
 *       pergunta em profundidades diferentes;</li>
 *   <li>escopo vazio (usuário sem concessão) não cobre NADA.</li>
 * </ul>
 *
 * <p>O RLS continua sendo só o tenant: esta regra é o recorte por área
 * DENTRO do tenant, aplicado pela aplicação — exatamente o que o aceite
 * pede ("o RLS continua só para o tenant").
 */
public final class ScopeRule {

    private ScopeRule() {
    }

    public static boolean covers(EffectiveScope scope, AreaAnchor anchor) {
        if (scope == null || anchor == null) {
            throw new IllegalArgumentException("escopo e âncora são obrigatórios");
        }
        if (scope.unrestricted()) {
            return true;
        }
        if (!anchor.hasAny()) {
            return false;
        }
        return (anchor.localityId() != null && scope.localityIds().contains(anchor.localityId()))
                || (anchor.sectorId() != null && scope.sectorIds().contains(anchor.sectorId()))
                || (anchor.routeId() != null && scope.routeIds().contains(anchor.routeId()));
    }

    /**
     * O escopo cobre TODAS as áreas de {@code required} (usado no trâmite e
     * na concessão por unidade: não basta tocar uma área da unidade, é
     * preciso alcançá-las todas). Irrestrito cobre qualquer exigência;
     * exigência vazia (unidade sem área nenhuma) não é coberta por escopo
     * comum — unidade sem área não se presume coberta (fail-closed), só o
     * irrestrito a alcança.
     */
    public static boolean coversAll(EffectiveScope scope, EffectiveScope required) {
        if (scope == null || required == null) {
            throw new IllegalArgumentException("escopo e exigência são obrigatórios");
        }
        if (scope.unrestricted()) {
            return true;
        }
        if (required.unrestricted()) {
            return false;
        }
        boolean requiredEmpty = required.localityIds().isEmpty() && required.sectorIds().isEmpty()
                && required.routeIds().isEmpty() && required.orgUnitIds().isEmpty();
        if (requiredEmpty) {
            return false;
        }
        return scope.localityIds().containsAll(required.localityIds())
                && scope.sectorIds().containsAll(required.sectorIds())
                && scope.routeIds().containsAll(required.routeIds())
                && scope.orgUnitIds().containsAll(required.orgUnitIds());
    }

    /**
     * Como {@link #coversAll}, mas comparando SÓ as áreas (localidades,
     * setores e rotas), sem as unidades organizacionais. É a comparação do
     * TRÂMITE e da atribuição: quem tem escopo por ÁREA direta (sem
     * concessão por unidade) precisa conseguir tramitar entre unidades
     * cujas áreas ele cobre — exigir também a unidade no recorte tornaria
     * o escopo por área inútil para tramitar (defeito achado na revisão
     * cruzada desta entrega e corrigido aqui).
     */
    public static boolean coversAllAreas(EffectiveScope scope, EffectiveScope required) {
        if (scope == null || required == null) {
            throw new IllegalArgumentException("escopo e exigência são obrigatórios");
        }
        if (scope.unrestricted()) {
            return true;
        }
        if (required.unrestricted()) {
            return false;
        }
        boolean requiredEmpty = required.localityIds().isEmpty() && required.sectorIds().isEmpty()
                && required.routeIds().isEmpty();
        if (requiredEmpty) {
            return false;
        }
        return scope.localityIds().containsAll(required.localityIds())
                && scope.sectorIds().containsAll(required.sectorIds())
                && scope.routeIds().containsAll(required.routeIds());
    }

    /** A unidade organizacional está no recorte (concedida ou descendente de concedida)? */
    public static boolean coversOrgUnit(EffectiveScope scope, UUID orgUnitId) {
        if (scope == null || orgUnitId == null) {
            throw new IllegalArgumentException("escopo e unidade são obrigatórios");
        }
        return scope.unrestricted() || scope.orgUnitIds().contains(orgUnitId);
    }

    /** Os componentes da âncora que estão FORA do escopo (para mensagens e auditoria). */
    public static Set<String> uncoveredComponents(EffectiveScope scope, AreaAnchor anchor) {
        if (scope == null || anchor == null) {
            throw new IllegalArgumentException("escopo e âncora são obrigatórios");
        }
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        if (scope.unrestricted()) {
            return missing;
        }
        if (!anchor.hasAny()) {
            missing.add("anchor:unresolved");
            return missing;
        }
        if (anchor.localityId() != null && !scope.localityIds().contains(anchor.localityId())) {
            missing.add("locality:" + anchor.localityId());
        }
        if (anchor.sectorId() != null && !scope.sectorIds().contains(anchor.sectorId())) {
            missing.add("sector:" + anchor.sectorId());
        }
        if (anchor.routeId() != null && !scope.routeIds().contains(anchor.routeId())) {
            missing.add("route:" + anchor.routeId());
        }
        return missing;
    }
}
