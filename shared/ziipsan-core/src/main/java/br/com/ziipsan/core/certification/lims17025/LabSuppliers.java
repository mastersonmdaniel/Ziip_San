package br.com.ziipsan.core.certification.lims17025;

import br.com.ziipsan.core.certification.governance.SupplierRecord;

import java.util.Objects;
import java.util.UUID;

/**
 * Porta de leitura dos fornecedores do laboratorio no cadastro da
 * P3.31 (JOR-18; aceite da ED-51): laboratorio externo, provedor de
 * ensaio de proficiencia e fornecedor de material de referencia sao
 * fornecedores DA P3.31 — esta classe so EXIGE que o fornecedor
 * exista no tenant e seja do tipo que o papel pede. Nenhum cadastro
 * paralelo nasce aqui (padrao da ED-37 com o tipo CONTRACTOR).
 *
 * <p><b>Decisao de desenho (declarada, sem fonte escondida):</b> a
 * P3.31 tem exatamente quatro tipos — {@code MATERIAL},
 * {@code SERVICE}, {@code EXTERNAL_LABORATORY} e {@code CONTRACTOR} —
 * e nenhum tipo dedicado a provedor de ensaio de proficiencia. O
 * mapeamento papel -&gt; tipo existente e:
 * <ul>
 *   <li>laboratorio externo -&gt; {@code EXTERNAL_LABORATORY} (tipo
 *       proprio da P3.31);</li>
 *   <li>fornecedor de material de referencia -&gt; {@code MATERIAL}
 *       (fornece material);</li>
 *   <li>provedor de ensaio de proficiencia -&gt; {@code SERVICE}
 *       (presta o servico do programa interlaboratorial).</li>
 * </ul>
 * O papel concreto vive no registro da ED-51 que referencia o
 * fornecedor; criar um quinto tipo seria alterar a P3.31, fora do
 * escopo desta tarefa (pendencia P-ED51-05, se o dono quiser o tipo
 * dedicado).
 */
public final class LabSuppliers {

    /** Papeis de fornecedor que o laboratorio consome (aceite da ED-51, JOR-18). */
    public enum Role {
        EXTERNAL_LABORATORY,
        PROFICIENCY_PROVIDER,
        REFERENCE_MATERIAL_SUPPLIER
    }

    private LabSuppliers() {}

    /** Tipo da P3.31 exigido para cada papel (mapeamento declarado no javadoc). */
    public static SupplierRecord.Type expectedType(Role role) {
        Objects.requireNonNull(role, "role e obrigatorio");
        return switch (role) {
            case EXTERNAL_LABORATORY -> SupplierRecord.Type.EXTERNAL_LABORATORY;
            case PROFICIENCY_PROVIDER -> SupplierRecord.Type.SERVICE;
            case REFERENCE_MATERIAL_SUPPLIER -> SupplierRecord.Type.MATERIAL;
        };
    }

    /**
     * Exige o fornecedor do papel no cadastro da P3.31: inexistente no
     * tenant, de outro tenant ou de tipo diverso = recusa nomeada.
     */
    public static SupplierRecord require(LimsPorts.SupplierCatalogPort catalog, UUID tenantId,
                                         UUID supplierId, Role role) {
        Objects.requireNonNull(catalog, "catalog (porta da P3.31) e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(supplierId, "supplierId e obrigatorio");
        Objects.requireNonNull(role, "role e obrigatorio");
        SupplierRecord supplier = catalog.findSupplier(tenantId, supplierId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "fornecedor inexistente neste tenant no cadastro da P3.31 (JOR-18): "
                                + supplierId + " — sem cadastro paralelo (a ED-51 le a P3.31)"));
        if (!tenantId.equals(supplier.tenantId())) {
            throw new IllegalArgumentException("fornecedor " + supplierId
                    + " pertence a outro tenant no cadastro da P3.31");
        }
        SupplierRecord.Type expected = expectedType(role);
        if (supplier.type() != expected) {
            throw new IllegalArgumentException("fornecedor " + supplierId + " nao serve ao papel "
                    + role + ": tipo na P3.31 e " + supplier.type() + ", o papel exige " + expected);
        }
        return supplier;
    }
}
