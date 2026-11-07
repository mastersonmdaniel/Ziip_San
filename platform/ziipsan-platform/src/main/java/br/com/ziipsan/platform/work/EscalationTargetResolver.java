package br.com.ziipsan.platform.work;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta que diz PARA QUEM um item vencido escala (a chefia/responsável de acompanhamento
 * do responsável atual, pela estrutura do tenant). A caixa NÃO inventa hierarquia:
 * resolvedor sem alvo devolve vazio e o item NÃO escala nesta rodada — fica vencido,
 * visível e contado como {@code skippedWithoutTarget} no resultado da rodada, para a
 * operação ver a pendência em vez de ela sumir em silêncio.
 */
public interface EscalationTargetResolver {
    Optional<UUID> escalationTargetFor(UUID tenantId, WorkItem item);
}
