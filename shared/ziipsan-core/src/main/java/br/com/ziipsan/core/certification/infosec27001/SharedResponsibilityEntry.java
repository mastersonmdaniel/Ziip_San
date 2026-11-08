package br.com.ziipsan.core.certification.infosec27001;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Uma linha da matriz de responsabilidade compartilhada Ziip × cliente
 * por controle (ED-73): quem responde pelo controle — o fornecedor
 * (Ziip), o cliente, ou os dois — referenciada pela mesma chave da
 * Declaração de Aplicabilidade ({@link AnnexAControlEntry}).
 *
 * <p>Regra do aceite: a marcação de controle IMPLEMENTADO PELO FORNECEDOR
 * só existe acompanhada da evidência do fornecedor — sem referência de
 * evidência do pacote do fornecedor ({@link SupplierEvidencePack}), a
 * linha é recusada. Responsabilidade sem dono também é recusada: o campo
 * é um enum fechado, não texto livre.
 *
 * <p>Correções da revisão cruzada da ED-73: <b>(D1)</b> a ativação é
 * componente do registro e o construtor canônico a exige ligada — o
 * portão não vive só na fábrica; <b>(D3)</b> a chave segue o MESMO
 * padrão de referência da SoA ({@link AnnexAControlEntry}), as
 * referências de evidência são aparadas (trim) como nos demais records
 * do pacote, e a fábrica {@link #declare(Iso27001Activation,
 * StatementOfApplicability, String, Responsibility, boolean, List,
 * String)} confere a existência da chave contra a SoA montada — no
 * banco, a existência é travada por FK do SQL pendente
 * ({@code fk_i27001_matrix_soa_control}).
 */
public record SharedResponsibilityEntry(Iso27001Activation activation, String controlKey,
                                        Responsibility responsibility,
                                        boolean implementedBySupplier,
                                        List<String> supplierEvidenceRefs, String note) {

    /** Dono da responsabilidade pelo controle. */
    public enum Responsibility {
        SUPPLIER, CLIENT, SHARED
    }

    public SharedResponsibilityEntry {
        Objects.requireNonNull(activation, "activation é obrigatória: a linha da matriz só nasce "
                + "no contexto da ativação da norma (correção D1 da revisão da ED-73)");
        activation.requireEnabled();
        if (controlKey == null || !AnnexAControlEntry.KEY.matcher(controlKey.trim()).matches()
                || controlKey.trim().length() > AnnexAControlEntry.CONTROL_KEY_MAX) {
            throw new IllegalArgumentException("controlKey é obrigatório, deve ser só a chave de "
                    + "referência da SoA (grupos separados por ponto, mesmo padrão de "
                    + "AnnexAControlEntry), com no máximo " + AnnexAControlEntry.CONTROL_KEY_MAX
                    + " caracteres: " + controlKey);
        }
        controlKey = controlKey.trim();
        Objects.requireNonNull(responsibility, "responsibility é obrigatória: toda linha da "
                + "matriz tem um dono declarado (fornecedor, cliente ou compartilhada)");
        List<String> trimmedRefs = new ArrayList<>();
        for (String ref : supplierEvidenceRefs == null ? List.<String>of() : supplierEvidenceRefs) {
            if (ref == null || ref.isBlank() || ref.trim().length() > AnnexAControlEntry.EVIDENCE_REF_MAX) {
                throw new IllegalArgumentException("referência de evidência do fornecedor inválida: "
                        + "identificador do item do pacote de evidência, sem copiar conteúdo");
            }
            trimmedRefs.add(ref.trim());
        }
        supplierEvidenceRefs = List.copyOf(trimmedRefs);
        if (implementedBySupplier && supplierEvidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("controle marcado como implementado pelo fornecedor "
                    + "exige a evidência do fornecedor (aceite da ED-73): sem referência ao pacote "
                    + "de evidência, a marcação não nasce");
        }
        if (implementedBySupplier && responsibility == Responsibility.CLIENT) {
            throw new IllegalArgumentException("controle de responsabilidade só do cliente não pode "
                    + "ser marcado como implementado pelo fornecedor: a matriz não se contradiz");
        }
        note = note == null ? "" : note.trim();
    }

    /** Nascimento da linha: só com a norma ligada (porta da ativação). */
    public static SharedResponsibilityEntry declare(Iso27001Activation activation, String controlKey,
                                                    Responsibility responsibility,
                                                    boolean implementedBySupplier,
                                                    List<String> supplierEvidenceRefs, String note) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new SharedResponsibilityEntry(activation, controlKey, responsibility,
                implementedBySupplier, supplierEvidenceRefs, note);
    }

    /**
     * Nascimento da linha COM conferência contra a SoA (correção D3): a
     * chave precisa existir na Declaração de Aplicabilidade montada — a
     * matriz não aponta para controle fora da declaração. É a fábrica a
     * usar quando a SoA já está montada (fluxo normal do pacote); no
     * banco, a mesma existência é travada pela FK
     * {@code fk_i27001_matrix_soa_control} do SQL pendente.
     */
    public static SharedResponsibilityEntry declare(Iso27001Activation activation,
                                                    StatementOfApplicability soa, String controlKey,
                                                    Responsibility responsibility,
                                                    boolean implementedBySupplier,
                                                    List<String> supplierEvidenceRefs, String note) {
        Objects.requireNonNull(soa, "soa é obrigatória para conferir a chave da matriz");
        if (!soa.containsControl(controlKey)) {
            throw new IllegalArgumentException("controlKey fora da Declaração de Aplicabilidade: "
                    + "a matriz só referencia controle declarado na SoA: " + controlKey);
        }
        return declare(activation, controlKey, responsibility, implementedBySupplier,
                supplierEvidenceRefs, note);
    }
}
