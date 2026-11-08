package br.com.ziipsan.platform.portal.verify;

import java.util.List;

/**
 * Textos padrao da protecao contra cobranca falsa (P3.35). Sao os textos que
 * o aceite fixa; nenhum deles contem canal, telefone, dominio ou endereco —
 * os canais entram SOMENTE pela lista oficial do tenant
 * ({@link OfficialChannels}), interpolada por quem exibe.
 *
 * <ul>
 *   <li>{@link #NEVER_LOOSE_LINK_TEXT} — o texto dos canais: o tenant nunca
 *       pede pagamento por link avulso;</li>
 *   <li>{@link #pixReceiverGuidance} — a orientacao impressa na conta para
 *       conferir recebedor e CNPJ no aplicativo do banco; razao social e
 *       CNPJ sao os do cadastro do tenant (P1.41), entregues pelo chamador;</li>
 *   <li>{@link #verificationGuidance} — como usar o codigo/QR da conta.</li>
 * </ul>
 */
public final class FraudProtectionTexts {

    /** Texto padrao dos canais (conta, portal e e-mails). */
    public static final String NEVER_LOOSE_LINK_TEXT =
            "Atencao: nos nunca pedimos pagamento por link avulso enviado por mensagem, "
            + "e-mail ou rede social. Pague somente pela fatura emitida, pelo portal oficial "
            + "ou pelo aplicativo oficial, e confira sempre o nome do recebedor antes de confirmar.";

    private FraudProtectionTexts() { }

    /**
     * Orientacao impressa do Pix: conferir no aplicativo do banco o nome do
     * recebedor e o CNPJ. Os valores sao a razao social e o CNPJ do tenant
     * (dados publicos do fornecedor, CDC art. 42-A, ja impressos na conta
     * pela P2.32) — nunca inventados aqui.
     */
    public static String pixReceiverGuidance(String receiverLegalName, String receiverTaxId) {
        if (receiverLegalName == null || receiverLegalName.isBlank()
                || receiverTaxId == null || receiverTaxId.isBlank()) {
            throw new IllegalArgumentException(
                    "orientacao do Pix exige a razao social e o CNPJ do tenant do cadastro (P1.41)");
        }
        return "Antes de confirmar o Pix, confira no aplicativo do seu banco: o recebedor deve ser "
                + receiverLegalName.strip() + ", CNPJ " + receiverTaxId.strip()
                + ". Se aparecer outro nome ou outro CNPJ, nao pague e avise pelos canais oficiais.";
    }

    /** Como verificar a fatura pelo codigo curto ou pelo QR impresso. */
    public static String verificationGuidance() {
        return "Para confirmar que esta fatura e verdadeira, acesse a pagina oficial de verificacao "
                + "do prestador, digite o codigo de verificacao impresso nesta conta (ou aponte a camera "
                + "para o codigo QR) e compare valor, vencimento e recebedor com os desta fatura.";
    }

    /** Bloco de canais oficiais para impressao/exibicao: rotulo + valor, um por linha. */
    public static List<String> officialChannelLines(OfficialChannels officialChannels) {
        if (officialChannels == null) {
            throw new IllegalArgumentException("canais oficiais sao obrigatorios para montar o bloco");
        }
        return officialChannels.channels().stream()
                .map(c -> c.label() + ": " + c.value())
                .toList();
    }
}
