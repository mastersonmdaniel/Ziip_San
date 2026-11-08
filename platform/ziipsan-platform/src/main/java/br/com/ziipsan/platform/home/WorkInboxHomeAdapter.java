package br.com.ziipsan.platform.home;

import br.com.ziipsan.platform.work.DueFilter;
import br.com.ziipsan.platform.work.WorkInboxService;
import br.com.ziipsan.platform.work.WorkItem;

import java.util.List;
import java.util.UUID;

/**
 * Adaptador da porta de tarefas da pagina inicial para a caixa da P2.28:
 * delega SEM refiltrar — o escopo do usuario (enderecamento, delegacoes
 * vigentes, permissao atual do delegante) e aplicado inteirinho pela
 * {@link WorkInboxService#listInbox} da dona. Filtro de prazo da pagina:
 * {@link DueFilter#ALL} (a pagina mostra a caixa; filtrar por prazo e da
 * tela Minhas tarefas).
 */
public final class WorkInboxHomeAdapter implements HomePorts.WorkItems {

    private final WorkInboxService service;

    public WorkInboxHomeAdapter(WorkInboxService service) {
        if (service == null) {
            throw new IllegalArgumentException("WorkInboxService e obrigatorio");
        }
        this.service = service;
    }

    @Override
    public List<WorkItem> inboxOf(UUID userId, int limit) {
        return service.listInbox(userId, DueFilter.ALL, limit);
    }
}
