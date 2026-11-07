package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundDestination;
import br.com.ziipsan.core.document.DocumentNumber;

import java.util.Objects;

/**
 * Validacao do destino da devolucao bancaria (P3.29,
 * {@code pay.refund_destination}, classe b). Regras puras; a titularidade
 * bancaria efetiva e conferida pela porta {@link BankAccountHolderPort}
 * (PSP/banco), nunca presumida.
 *
 * <ul>
 *   <li>{@code PIX_RETURN_TO_PAYER}: so quando o pagamento ORIGINAL foi Pix
 *       E o PSP oferece a devolucao ao pagador de origem; exige o
 *       {@code endToEndId} do pagamento original;</li>
 *   <li>{@code PIX_KEY}: a chave Pix do titular so pode ser do tipo CPF ou
 *       CNPJ (chave de e-mail, telefone ou aleatoria e RECUSADA) e o
 *       documento da chave tem de ser IGUAL ao documento do titular;</li>
 *   <li>{@code BANK_ACCOUNT}: conta bancaria so com a validacao de
 *       titularidade devolvida pela porta (documento da conta = documento
 *       do titular).</li>
 * </ul>
 *
 * <p>Nenhum metodo desta classe devolve ou registra o dado de destino em
 * claro: quem persiste grava SOMENTE o texto cifrado (ver
 * {@link RefundDestinationProtector}).
 */
public final class RefundDestinationValidator {

    /** Porta da validacao de titularidade da conta bancaria (PSP/banco). */
    public interface BankAccountHolderPort {
        /** Verdadeiro somente se a conta pertence ao documento do titular. */
        boolean isAccountOwnedBy(String bankCode, String branch, String account, String holderDocument);
    }

    /**
     * @param originalChannel      canal do pagamento de origem (PIX, BOLETO, ...)
     * @param originalEndToEndId   endToEndId do Pix original (nulo fora do Pix)
     * @param pspOffersPixReturn   o PSP oferece devolucao Pix ao pagador de origem.
     *                             <b>Fonte (correcao pos-revisao M5):</b> o contrato
     *                             real da P3.2 ({@code PspPixPort.PspBehavior}) NAO
     *                             declara esta capacidade - ela nao pode ser lida
     *                             dele nem presumida. E entrada OBRIGATORIA e
     *                             EXPLICITA da fiacao (primitivo, sem default),
     *                             a resolver do contrato/cadastro do PSP na
     *                             integracao; enquanto a fonte nao existe, quem
     *                             fia decide e registra - nunca se presume
     *                             {@code true}.
     */
    public record PixOrigin(String originalChannel, String originalEndToEndId, boolean pspOffersPixReturn) {
    }

    private RefundDestinationValidator() {
    }

    public static void validatePixReturnToPayer(PixOrigin origin) {
        Objects.requireNonNull(origin, "origem do pagamento e obrigatoria");
        if (!"PIX".equals(origin.originalChannel())) {
            throw new IllegalArgumentException("PIX_RETURN_TO_PAYER exige pagamento original Pix; "
                    + "canal de origem: " + origin.originalChannel());
        }
        if (origin.originalEndToEndId() == null || origin.originalEndToEndId().isBlank()) {
            throw new IllegalArgumentException("PIX_RETURN_TO_PAYER exige o endToEndId do Pix original");
        }
        if (!origin.pspOffersPixReturn()) {
            throw new IllegalArgumentException("o PSP nao oferece devolucao Pix ao pagador de origem: "
                    + "escolha outro destino de pay.refund_destination");
        }
    }

    /**
     * Chave Pix do titular: so CPF ou CNPJ, e igual ao documento do titular.
     * A chave recebida e o proprio documento (tipo CPF/CNPJ do Pix); qualquer
     * outro formato (e-mail, telefone, chave aleatoria) e recusado.
     */
    public static void validatePixKey(String pixKey, String holderDocument) {
        Objects.requireNonNull(holderDocument, "documento do titular e obrigatorio");
        if (pixKey == null || pixKey.isBlank()) {
            throw new IllegalArgumentException("chave Pix do titular e obrigatoria");
        }
        DocumentNumber.Kind keyKind = DocumentNumber.kind(pixKey);
        if (keyKind != DocumentNumber.Kind.CPF && keyKind != DocumentNumber.Kind.CNPJ) {
            throw new IllegalArgumentException("chave Pix do titular so pode ser CPF ou CNPJ; "
                    + "e-mail, telefone e chave aleatoria nao sao aceitos");
        }
        String holder = DocumentNumber.normalize(holderDocument);
        if (DocumentNumber.kind(holder) == DocumentNumber.Kind.INVALID) {
            throw new IllegalArgumentException("documento do titular invalido");
        }
        if (!DocumentNumber.normalize(pixKey).equals(holder)) {
            throw new IllegalArgumentException("a chave Pix so e aceita com documento igual "
                    + "ao do titular da devolucao");
        }
    }

    /** Conta bancaria: titularidade validada pela porta; sem validacao, recusa. */
    public static void validateBankAccount(BankAccountHolderPort port, String bankCode, String branch,
                                           String account, String holderDocument) {
        Objects.requireNonNull(port, "porta de validacao de titularidade e obrigatoria");
        if (bankCode == null || !bankCode.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("codigo do banco deve ter 3 digitos");
        }
        if (branch == null || branch.isBlank() || account == null || account.isBlank()) {
            throw new IllegalArgumentException("agencia e conta sao obrigatorias");
        }
        if (DocumentNumber.kind(holderDocument) == DocumentNumber.Kind.INVALID) {
            throw new IllegalArgumentException("documento do titular invalido");
        }
        if (!port.isAccountOwnedBy(bankCode, branch, account, DocumentNumber.normalize(holderDocument))) {
            throw new IllegalArgumentException("titularidade da conta bancaria nao validada "
                    + "para o titular da devolucao");
        }
    }

    /** Despacha a validacao conforme o destino parametrizado. */
    public static void validate(RefundDestination destination, PixOrigin origin, String pixKey,
                                String holderDocument, BankAccountHolderPort port,
                                String bankCode, String branch, String account) {
        Objects.requireNonNull(destination, "pay.refund_destination e obrigatorio");
        switch (destination) {
            case PIX_RETURN_TO_PAYER -> validatePixReturnToPayer(origin);
            case PIX_KEY -> validatePixKey(pixKey, holderDocument);
            case BANK_ACCOUNT -> validateBankAccount(port, bankCode, branch, account, holderDocument);
        }
    }
}
