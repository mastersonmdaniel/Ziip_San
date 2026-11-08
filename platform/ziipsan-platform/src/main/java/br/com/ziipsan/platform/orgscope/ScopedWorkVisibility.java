package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.platform.work.WorkDelegation;
import br.com.ziipsan.platform.work.WorkItem;
import br.com.ziipsan.platform.work.WorkItemVisibility;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * O filtro de escopo da CAIXA "Minhas tarefas" (P3.30 sobre a P2.28): a
 * visibilidade da caixa ({@link WorkItemVisibility}) decide QUEM é o dono
 * do trabalho; esta classe aplica DEPOIS o recorte por área — exatamente a
 * fronteira que a P2.28 declarou ("quando a P3.30 nascer, ela filtra DEPOIS
 * desta visibilidade, sem mudar este contrato").
 *
 * <p>O recorte é o do USUÁRIO QUE OLHA a caixa (o escopo efetivo dele),
 * inclusive no item DELEGADO: a delegação move o trabalho e um subconjunto
 * de permissões do delegante, mas NÃO move o escopo territorial dele —
 * item da localidade B não aparece a quem só tem escopo na A, ainda que o
 * delegante seja da B (aceite, com teste). Quem precisa atender a B por
 * delegação precisa de escopo próprio em B, concedido pela GrantRule.
 *
 * <p>A âncora vem do OBJETO do item (o registro do módulo de origem), pela
 * porta de âncoras — item cujo objeto não resolve âncora (registro sumiu)
 * não aparece para ninguém fora do irrestrito: some da caixa em vez de
 * vazar, e a pendência é do módulo de origem, não desta classe.
 */
public final class ScopedWorkVisibility {

    private ScopedWorkVisibility() {
    }

    public static boolean isVisible(WorkItem item, UUID userId, Set<String> userPermissions,
                                    List<WorkDelegation> delegationsReceivedByUser,
                                    Map<UUID, Set<String>> currentPermissionsByDelegator,
                                    Instant now, EffectiveScope viewerScope, AreaAnchor anchor) {
        if (viewerScope == null || anchor == null) {
            throw new IllegalArgumentException("escopo do usuário e âncora do item são obrigatórios");
        }
        return WorkItemVisibility.isVisibleTo(item, userId, userPermissions,
                delegationsReceivedByUser, currentPermissionsByDelegator, now)
                && ScopeRule.covers(viewerScope, anchor);
    }

    /** Filtra a caixa já visível pela P2.28, resolvendo a âncora item a item. */
    public static List<WorkItem> filterInbox(List<WorkItem> visibleItems, EffectiveScope viewerScope,
                                             Function<WorkItem, AreaAnchor> anchorResolver) {
        if (visibleItems == null || viewerScope == null || anchorResolver == null) {
            throw new IllegalArgumentException("itens, escopo e resolvedor de âncora são obrigatórios");
        }
        return visibleItems.stream()
                .filter(item -> {
                    AreaAnchor anchor = anchorResolver.apply(item);
                    // Resolvedor que devolve null = âncora não resolvida:
                    // vale a âncora vazia (fail-closed), nunca "sem recorte".
                    return ScopeRule.covers(viewerScope, anchor == null ? AreaAnchor.EMPTY : anchor);
                })
                .toList();
    }
}
