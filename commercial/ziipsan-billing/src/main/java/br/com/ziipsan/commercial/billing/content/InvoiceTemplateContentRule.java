package br.com.ziipsan.commercial.billing.content;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Aprovacao do parametro {@code bill.invoice_template} (G1) pelo CONTEUDO
 * (P2.32): o modelo da conta — montado no motor da P2.30, onde cada campo
 * obrigatorio corresponde a um bloco SYSTEM travado identificado pela chave
 * do campo — so e aprovado se contiver TODOS os campos obrigatorios do
 * perfil vigente. Campo faltante RECUSA a aprovacao, nomeando os faltantes.
 *
 * <p>Regras travadas, sem excecao por tenant:
 * <ol>
 *   <li>o bloco do CDC, art. 42-A ({@code supplier.identification_block})
 *       precisa estar presente E marcado como travado;</li>
 *   <li>o campo pendente do RAS-28 ({@link PendingTaxDisclosure#FIELD_KEY})
 *       recusa o modelo enquanto nao houver parecer juridico;</li>
 *   <li>perfil desconhecido ou conjunto de campos nulo = erro explicito.</li>
 * </ol>
 */
public final class InvoiceTemplateContentRule {

    private InvoiceTemplateContentRule() {
    }

    /**
     * Problemas de conteudo do modelo para o perfil na data. Lista vazia =
     * aprovavel pelo conteudo (as demais travas — envelope da P0.19,
     * proponente diferente do aprovador, previa — sao da P2.30/P0.19).
     *
     * @param templateFieldKeys  chaves dos campos/blocos SYSTEM do modelo
     * @param lockedFieldKeys    subconjunto das chaves em bloco travado
     * @param profileKey         perfil regulatorio vigente (P2.31)
     * @param date               data da vigencia avaliada
     */
    public static List<String> contentProblems(Set<String> templateFieldKeys, Set<String> lockedFieldKeys,
                                               String profileKey, LocalDate date) {
        if (templateFieldKeys == null) {
            throw new IllegalArgumentException("templateFieldKeys e obrigatorio");
        }
        if (date == null) {
            throw new IllegalArgumentException("date e obrigatoria");
        }
        List<String> problems = new ArrayList<>();
        for (BillFieldRequirement required : RequiredBillContent.requiredFor(profileKey, date)) {
            if (!templateFieldKeys.contains(required.fieldKey())) {
                problems.add("Campo obrigatorio ausente no modelo: " + required.fieldKey()
                        + " (" + required.sourceNorm() + ", " + required.sourceReference() + ")");
            } else if (required.lockedBlock()
                    && (lockedFieldKeys == null || !lockedFieldKeys.contains(required.fieldKey()))) {
                problems.add("Campo obrigatorio fora do bloco travado: " + required.fieldKey()
                        + " (" + required.sourceNorm() + ", " + required.sourceReference() + ")");
            }
        }
        if (templateFieldKeys.contains(PendingTaxDisclosure.FIELD_KEY)
                && !PendingTaxDisclosure.LEGAL_OPINION_CONFIRMED) {
            problems.add("Campo " + PendingTaxDisclosure.FIELD_KEY + " recusado: o valor aproximado"
                    + " dos tributos (Lei 12.741/2012) aguarda parecer juridico (RAS-28)"
                    + " e nao entra no modelo sem ele");
        }
        return List.copyOf(problems);
    }

    /** Aprova ou lanca {@link IllegalArgumentException} com todos os problemas. */
    public static void assertApprovable(Set<String> templateFieldKeys, Set<String> lockedFieldKeys,
                                        String profileKey, LocalDate date) {
        List<String> problems = contentProblems(templateFieldKeys, lockedFieldKeys, profileKey, date);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Modelo da conta recusado para o perfil " + profileKey
                    + ": " + String.join("; ", problems));
        }
    }
}
