package br.com.ziipsan.commercial.billing.bill;

import java.util.UUID;

/**
 * Tipo de débito do catálogo {@code debit_types} (DAD-20, P2.5): substitui o {@code type_code} em
 * texto livre de {@code debit_receivables}. O catálogo é tabela de referência do tenant e começa
 * vazio — nenhum código é semeado nem embutido no código, porque tipo de débito é política do
 * prestador (mesma regra da V4_1/V4_2: "nenhum dado inventado").
 *
 * <p>Este record é a fotografia do item do catálogo já resolvida pelo chamador (que lê o banco),
 * no mesmo espírito do {@code BillInput}: o domínio puro não consulta o catálogo; quem monta o
 * {@link DebitReceivable} informa o tipo resolvido, e o {@code generates_bill} viaja com ele.
 *
 * @param generatesBill indicador "gera conta" do tipo: se um débito deste tipo, sozinho, justifica
 *                      emitir conta. Sem padrão — é decisão do tipo, cadastrada no catálogo
 *                      (a coluna sai de {@code debit_receivables} e passa a viver só aqui).
 */
public record DebitType(UUID id, String code, boolean generatesBill) {

    public DebitType {
        if (id == null || code == null || code.isBlank()) {
            throw new IllegalArgumentException("Tipo de débito sem identificação ou código (catálogo debit_types)");
        }
    }
}
