package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Item de almoxarifado e local de estoque (ED-58). O catalogo de itens e
 * PROPRIO do almoxarifado (materiais de rede, hidrometros como item
 * serializado, reagentes, quimicos, EPI, insumos) — nao duplica os 22
 * catalogos auxiliares da ED-12 nem o cadastro de hidrometro da ED-14:
 * o item serializado REFERENCIA o modelo/serial; o ciclo de vida do
 * hidrometro instalado continua na ED-14.
 *
 * <p>{@code reorderPoint} (ponto de reposicao) e POR ITEM e opcional:
 * nenhuma fonte fixa estoque minimo nem ponto de reposicao — ausencia
 * significa "sem alerta de reposicao por ponto", nunca um numero
 * presumido (pendencia nomeada no relatorio).
 */
public final class InventoryItem {
    private InventoryItem() {}

    public enum Kind { NETWORK_MATERIAL, METER, REAGENT, CHEMICAL, PPE, CONSUMABLE }

    public record Item(String code, String name, Kind kind, String unitOfMeasure,
                       boolean controlled, boolean serialized, boolean lotTracked,
                       BigDecimal reorderPoint, boolean active) {
        public Item {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do item e obrigatorio");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("nome do item e obrigatorio: " + code);
            Objects.requireNonNull(kind, "tipo do item e obrigatorio: " + code);
            if (unitOfMeasure == null || unitOfMeasure.isBlank()) throw new IllegalArgumentException("unidade de medida e obrigatoria: " + code);
            if (reorderPoint != null && reorderPoint.signum() < 0) throw new IllegalArgumentException("ponto de reposicao nao pode ser negativo: " + code);
            if (serialized && kind != Kind.METER) throw new IllegalArgumentException("item serializado nesta entrega e o hidrometro (ED-14): " + code);
            if (lotTracked && !(kind == Kind.REAGENT || kind == Kind.CHEMICAL || kind == Kind.PPE))
                throw new IllegalArgumentException("lote/validade se aplica a reagentes, quimicos e EPI: " + code);
        }
        /** EPI exige certificado de aprovacao (CA) no lote — ver LotControl. */
        public boolean requiresCa() { return kind == Kind.PPE; }
    }

    public record StockLocation(String code, String name, boolean active) {
        public StockLocation {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do local de estoque e obrigatorio");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("nome do local de estoque e obrigatorio: " + code);
        }
    }
}
