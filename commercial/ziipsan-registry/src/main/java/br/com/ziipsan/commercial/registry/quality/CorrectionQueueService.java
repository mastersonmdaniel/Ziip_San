package br.com.ziipsan.commercial.registry.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fila de correcao da qualidade cadastral (P3.32): recebe os achados do
 * motor, deduplica os abertos, agrupa itens em OS de lote (ADR-0039) e
 * resolve itens SOMENTE pela OS que os agrupou.
 *
 * <p>Correcao so por OS — a garantia central do aceite:
 * <ul>
 *   <li>este servico nao tem porta de escrita para {@code customers},
 *       {@code consumer_units} ou qualquer tabela do cadastro: ele muda o
 *       estado dos ITENS DA FILA, nunca o cadastro;</li>
 *   <li>{@link #resolveItem} exige o {@code serviceOrderId} da OS de lote
 *       que agrupou o item e confere a igualdade — resolver "na mao", sem
 *       OS ou com outra OS, e recusado;</li>
 *   <li>a correcao do cadastro em si e efeito da OS (a ED-16 executa a
 *       campanha sobre esta fila e aplica campo a campo como efeito da OS
 *       de lote, com historico).</li>
 * </ul>
 *
 * <p>Agrupamento: item com unidade vai para o grupo da unidade. Item so de
 * cliente (documento, contato, obito) vai para o grupo das unidades
 * atualmente ligadas ao cliente, resolvidas pela porta
 * {@link LinkedUnitsLookup}; cliente sem unidade ligada nao e agrupavel
 * nesta rodada e volta nomeado no resultado — nunca some em silencio.
 */
public final class CorrectionQueueService {

    /** Persistencia da fila (a fiacao liga as entidades JPA; os testes usam a memoria). */
    public interface Store {
        void save(CorrectionQueueItem item);

        List<CorrectionQueueItem> findByFindingKey(String findingKey);

        List<CorrectionQueueItem> findByIds(List<UUID> ids);

        List<CorrectionQueueItem> findByServiceOrderId(UUID serviceOrderId);
    }

    /** Unidades atualmente ligadas ao cliente (qualquer papel vigente). */
    public interface LinkedUnitsLookup {
        List<UUID> currentUnitIdsOfCustomer(UUID customerId);
    }

    /** Resultado da entrada na fila. */
    public record EnqueueResult(int created, int duplicatesSkipped) {
    }

    /** Um lote aberto: a OS e os itens que entraram nela. */
    public record OpenedBatch(UUID serviceOrderId, List<UUID> itemIds, List<UUID> targetUnitIds) {
    }

    /** Resultado do agrupamento: lotes abertos + itens nao agrupaveis, nomeados. */
    public record BatchResult(List<OpenedBatch> opened, List<CorrectionQueueItem> ungroupable) {
    }

    private final Store store;
    private final BatchOrderGateway gateway;
    private final LinkedUnitsLookup linkedUnits;

    public CorrectionQueueService(Store store, BatchOrderGateway gateway, LinkedUnitsLookup linkedUnits) {
        if (store == null || gateway == null || linkedUnits == null) {
            throw new IllegalArgumentException("store, gateway de OS de lote e lookup de vinculos sao obrigatorios");
        }
        this.store = store;
        this.gateway = gateway;
        this.linkedUnits = linkedUnits;
    }

    /**
     * Entra com os achados de uma varredura. Achado cuja chave ja tem item
     * OPEN ou BATCHED nao duplica; chave com item RESOLVED/DISMISSED cria
     * item novo (reincidencia: a correcao anterior nao esconde o problema
     * que voltou).
     */
    public EnqueueResult enqueue(List<QualityFinding> findings) {
        if (findings == null) {
            throw new IllegalArgumentException("lista de achados e obrigatoria");
        }
        int created = 0;
        int skipped = 0;
        for (QualityFinding finding : findings) {
            boolean openExists = store.findByFindingKey(finding.key()).stream()
                    .anyMatch(i -> i.status() == CorrectionQueueItem.Status.OPEN
                            || i.status() == CorrectionQueueItem.Status.BATCHED);
            if (openExists) {
                skipped++;
                continue;
            }
            store.save(CorrectionQueueItem.fromFinding(finding));
            created++;
        }
        return new EnqueueResult(created, skipped);
    }

    /**
     * Agrupa os itens OPEN pedidos numa unica OS de lote (a OS da P1.31 tem
     * um lote e varios alvos CONSUMER_UNIT). Itens nao OPEN ou sem unidade
     * resolvivel voltam como nao agrupaveis, nomeados no resultado.
     */
    public BatchResult openCorrectionBatch(List<UUID> itemIds, UUID actorId) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("nenhum item informado para a OS de lote");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("ator e obrigatorio para abrir a OS de lote");
        }
        List<CorrectionQueueItem> items = store.findByIds(itemIds);
        Map<UUID, List<String>> keysByUnit = new LinkedHashMap<>();
        List<CorrectionQueueItem> batchable = new ArrayList<>();
        List<CorrectionQueueItem> ungroupable = new ArrayList<>();
        for (CorrectionQueueItem item : items) {
            if (item.status() != CorrectionQueueItem.Status.OPEN) {
                ungroupable.add(item);
                continue;
            }
            List<UUID> targets = item.unitId() != null
                    ? List.of(item.unitId())
                    : linkedUnits.currentUnitIdsOfCustomer(item.customerId());
            if (targets == null || targets.isEmpty()) {
                ungroupable.add(item);
                continue;
            }
            for (UUID unitId : targets) {
                keysByUnit.computeIfAbsent(unitId, k -> new ArrayList<>()).add(item.findingKey());
            }
            batchable.add(item);
        }
        if (batchable.isEmpty()) {
            return new BatchResult(List.of(), List.copyOf(ungroupable));
        }
        BatchOrderGateway.OpenedCorrectionOrder order = gateway.openCorrectionBatch(keysByUnit, actorId);
        for (CorrectionQueueItem item : batchable) {
            item.markBatched(order.orderId());
            store.save(item);
        }
        OpenedBatch opened = new OpenedBatch(order.orderId(),
                batchable.stream().map(CorrectionQueueItem::id).toList(),
                List.copyOf(keysByUnit.keySet()));
        return new BatchResult(List.of(opened), List.copyOf(ungroupable));
    }

    /**
     * Resolve um item pela OS de lote: exige item BATCHED e a MESMA OS que
     * o agrupou. E o unico caminho de resolucao alem de
     * {@link #resolveByServiceOrder}.
     */
    public CorrectionQueueItem resolveItem(UUID itemId, UUID serviceOrderId, UUID actorId) {
        if (itemId == null || serviceOrderId == null || actorId == null) {
            throw new IllegalArgumentException("item, OS e ator sao obrigatorios para resolver pela OS");
        }
        CorrectionQueueItem item = store.findByIds(List.of(itemId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("item da fila nao encontrado: " + itemId));
        item.markResolvedBy(serviceOrderId);
        store.save(item);
        return item;
    }

    /** Resolve todos os itens BATCHED de uma OS (chamado na confirmacao do efeito dela). */
    public int resolveByServiceOrder(UUID serviceOrderId, UUID actorId) {
        if (serviceOrderId == null || actorId == null) {
            throw new IllegalArgumentException("OS e ator sao obrigatorios para resolver pela OS");
        }
        List<CorrectionQueueItem> items = store.findByServiceOrderId(serviceOrderId).stream()
                .filter(i -> i.status() == CorrectionQueueItem.Status.BATCHED)
                .toList();
        if (items.isEmpty()) {
            throw new IllegalStateException("nenhum item da fila aguarda a OS " + serviceOrderId
                    + "; resolucao sem item agrupado nao existe");
        }
        for (CorrectionQueueItem item : items) {
            item.markResolvedBy(serviceOrderId);
            store.save(item);
        }
        return items.size();
    }

    /** Triagem: declara, com motivo, que o achado nao procede. Nao corrige cadastro nenhum. */
    public CorrectionQueueItem dismiss(UUID itemId, String reason, UUID actorId) {
        if (itemId == null || actorId == null) {
            throw new IllegalArgumentException("item e ator sao obrigatorios para a triagem");
        }
        CorrectionQueueItem item = store.findByIds(List.of(itemId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("item da fila nao encontrado: " + itemId));
        item.markDismissed(reason);
        store.save(item);
        return item;
    }
}
