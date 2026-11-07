package br.com.ziipsan.commercial.billing.content;

/**
 * RAS-28 — PONTO DE EXTENSAO ISOLADO, AGUARDANDO PARECER JURIDICO.
 *
 * <p>O valor aproximado dos tributos na fatura (Lei 12.741/2012;
 * Decreto 8.264/2014) so entra no modelo da conta DEPOIS de parecer juridico
 * confirmando a aplicacao a conta de agua/esgoto. Sem parecer:
 * <ul>
 *   <li>a chave {@link #FIELD_KEY} NAO esta em {@link RequiredBillContent}
 *       (nenhum perfil a exige);</li>
 *   <li>{@link InvoiceTemplateContentRule} RECUSA qualquer modelo que a
 *       contenha (o campo nao entra no modelo por iniciativa do tenant);</li>
 *   <li>nenhum texto, aliquota ou fonte de aliquotas e declarado aqui —
 *       inventar esse conteudo esta proibido pela spec.</li>
 * </ul>
 *
 * <p>Se o parecer confirmar: o campo entra em {@code RequiredBillContent}
 * como requisito federal novo (com a fonte das aliquotas que o parecer
 * indicar), a recusa desta classe e removida e a versao do catalogo sobe.
 * Ate la, esta classe nao tem comportamento alem de declarar a pendencia.
 */
public final class PendingTaxDisclosure {

    /** Chave reservada do campo pendente (NAO exigida, NAO permitida sem parecer). */
    public static final String FIELD_KEY = "taxes.approximate_total_disclosure";

    /** Estado: nenhum parecer juridico consta do projeto ate esta entrega. */
    public static final boolean LEGAL_OPINION_CONFIRMED = false;

    private PendingTaxDisclosure() {
    }
}
