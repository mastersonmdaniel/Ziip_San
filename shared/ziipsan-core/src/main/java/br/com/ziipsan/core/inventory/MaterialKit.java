package br.com.ziipsan.core.inventory;

import br.com.ziipsan.core.backoffice.PlannedMaterials;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Kit de materiais por tipo de OS (SG-476): na abertura da OS, o kit do
 * tipo PREENCHE A PREVISAO — devolve linhas no formato REAL do
 * {@link PlannedMaterials} da ED-36 (previsto sem usado), para a OS
 * nascer com o previsto do kit e o executor ajustar. Kit e dado do
 * tenant (catalogo), nao constante: tipo sem kit = lista vazia nomeada
 * pelo chamador, nunca um kit presumido.
 */
public final class MaterialKit {
    private MaterialKit() {}

    public record KitLine(String itemCode, BigDecimal qty) {
        public KitLine {
            if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item do kit e obrigatorio");
            if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("quantidade do kit deve ser positiva: " + itemCode);
        }
    }

    public record Kit(UUID tenantId, String orderTypeCode, List<KitLine> lines) {
        public Kit {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (orderTypeCode == null || orderTypeCode.isBlank()) throw new IllegalArgumentException("tipo de OS do kit e obrigatorio");
            if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("kit exige ao menos uma linha");
            lines = List.copyOf(lines);
        }
    }

    /** Previsao (ED-36) preenchida pelo kit: usado nasce nulo, como o PlannedMaterials exige. */
    public static List<PlannedMaterials.Line> prefillPlanned(Kit kit) {
        Objects.requireNonNull(kit, "kit e obrigatorio");
        return kit.lines().stream()
                .map(l -> new PlannedMaterials.Line(l.itemCode(), l.qty(), null)).toList();
    }
}
