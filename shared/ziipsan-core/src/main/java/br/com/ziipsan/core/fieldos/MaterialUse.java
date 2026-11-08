package br.com.ziipsan.core.fieldos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Material usado registrado POR TAREFA (ED-20, ADR-0032): o registro sobe
 * na sincronizacao COM o {@code task_id} (o mesmo task_id da ED-18), e a
 * BAIXA no estoque e da ED-58 — esta entrega nao movimenta estoque, apenas
 * entrega as linhas a porta {@link FieldOsPorts.StockWriteOffPort}.
 * O previsto x usado continua sendo conferido pela ED-36
 * (PlannedMaterials): aqui so viaja o USADO efetivo de campo.
 */
public final class MaterialUse {

    public record Line(String materialCode, BigDecimal usedQty) {
        public Line {
            if (materialCode == null || materialCode.isBlank()) throw new IllegalArgumentException("codigo do material e obrigatorio");
            Objects.requireNonNull(usedQty, "quantidade usada e obrigatoria");
            if (usedQty.signum() < 0) throw new IllegalArgumentException("quantidade usada nao pode ser negativa: " + materialCode);
        }
    }

    /** Carga de sincronizacao de materiais de UMA tarefa. */
    public record TaskMaterialUse(String taskId, List<Line> lines) {
        public TaskMaterialUse {
            if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("task_id e obrigatorio (ADR-0032)");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
        public boolean hasUse() { return !lines.isEmpty(); }
    }

    private MaterialUse() {}
}
