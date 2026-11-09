package br.com.ziipsan.core.certification.lims17025;

/**
 * Item do plano de validacao do sistema por versao (ED-51-PD08; PD-08,
 * decisao do dono de 09/11/2026, item 40 do DECISOES-DO-DONO.md).
 *
 * <p>O item e DADO DECLARADO do tenant/laboratorio: a chave
 * ({@code itemKey}) e a descricao do que se valida sao declaradas por
 * quem abre o dossie — nenhum item de plano nasce de fabrica nesta
 * classe, porque nenhuma fonte do projeto fixa o conteudo/checklist
 * normativo da validacao por versao (pendencia P-ED51PD08-02,
 * [A DEFINIR]: o conteudo normativo do checklist e do tenant, quando
 * ele o declarar). Esta classe so garante a forma: chave e descricao
 * presentes.
 */
public record SystemValidationPlanItem(String itemKey, String description) {

    public SystemValidationPlanItem {
        if (itemKey == null || itemKey.isBlank())
            throw new IllegalArgumentException("itemKey e obrigatorio (chave declarada do item do plano)");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description e obrigatoria (o que o item valida, "
                    + "declarado pelo tenant/laboratorio)");
    }
}
