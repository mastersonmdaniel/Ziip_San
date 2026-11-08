package br.com.ziipsan.core.stationops;

import java.util.List;
import java.util.UUID;

/**
 * Roteiro de ronda (ED-87): a sequencia ordenada de verificacoes de
 * uma unidade, com o checklist que o operador executa. O roteiro e
 * cadastro do prestador — nenhum item de checklist nasce de fabrica.
 * O formulario da ronda funciona SEM REDE: o roteiro viaja inteiro no
 * pacote offline ({@link RoundSyncService}) e a execucao e registrada
 * localmente ate a sincronizacao.
 */
public record RoundRoute(
        UUID id,
        UUID tenantId,
        UUID unitId,
        String code,
        List<ChecklistItem> items,
        boolean active) {

    /** Um passo do checklist, na ordem da ronda. */
    public record ChecklistItem(int sequence, String itemKey, String label, boolean required) {
        public ChecklistItem {
            if (sequence < 1) throw new IllegalArgumentException("sequencia do item comeca em 1");
            if (itemKey == null || itemKey.isBlank())
                throw new IllegalArgumentException("itemKey do item do checklist e obrigatorio");
            if (label == null || label.isBlank())
                throw new IllegalArgumentException("rotulo do item " + itemKey + " e obrigatorio");
        }
    }

    public RoundRoute {
        if (id == null) throw new IllegalArgumentException("id do roteiro e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId do roteiro e obrigatorio");
        if (unitId == null) throw new IllegalArgumentException("unitId do roteiro e obrigatorio");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code do roteiro e obrigatorio");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("roteiro " + code + " sem itens nao e roteiro");
        items = items.stream().sorted(java.util.Comparator.comparingInt(ChecklistItem::sequence)).toList();
        long distinctSeq = items.stream().map(ChecklistItem::sequence).distinct().count();
        long distinctKey = items.stream().map(ChecklistItem::itemKey).distinct().count();
        if (distinctSeq != items.size() || distinctKey != items.size())
            throw new IllegalArgumentException("roteiro " + code + " com sequencia ou item duplicado");
    }

    public boolean hasItem(String itemKey) {
        return items.stream().anyMatch(i -> i.itemKey().equals(itemKey));
    }
}
