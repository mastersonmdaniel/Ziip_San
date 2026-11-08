package br.com.ziipsan.core.fieldos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Importacao pelo importador da P1.40 (ED-20): materiais, funcionarios de
 * campo e cadastros de consistencia entram PELO importador — esta entrega
 * so valida o pedido e o encaminha a porta; nenhum parser de arquivo e
 * duplicado aqui.
 */
public final class FieldImport {

    public enum Kind { MATERIALS, FIELD_EMPLOYEES, CONSISTENCY_REGISTERS }

    private FieldImport() {}

    public static String importBatch(FieldOsPorts.ImporterPort importer, UUID tenantId, Kind kind,
                                     List<String> externalRefs) {
        Objects.requireNonNull(importer, "importador da P1.40 e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(kind, "tipo da importacao e obrigatorio");
        if (externalRefs == null || externalRefs.isEmpty()) throw new IllegalArgumentException(
                "lote de importacao vazio nao e importacao");
        return importer.importBatch(tenantId, kind, List.copyOf(externalRefs));
    }
}
