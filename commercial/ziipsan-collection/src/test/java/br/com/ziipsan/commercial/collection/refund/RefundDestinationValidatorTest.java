package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundDestination;
import br.com.ziipsan.commercial.collection.refund.RefundDestinationValidator.PixOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * P3.29 - um teste por opcao de pay.refund_destination.
 * Documentos de teste com digito valido: CPF 529.982.247-25, CNPJ 11.222.333/0001-81.
 */
class RefundDestinationValidatorTest {

    private static final String CPF = "52998224725";
    private static final String CNPJ = "11222333000181";

    // ---- PIX_RETURN_TO_PAYER ----
    @Test
    void pixReturnToPayerAceitoQuandoOriginalPixEPspOferece() {
        assertDoesNotThrow(() -> RefundDestinationValidator.validate(RefundDestination.PIX_RETURN_TO_PAYER,
                new PixOrigin("PIX", "E12345678202601011234567890ABCDE", true),
                null, CPF, null, null, null, null));
    }

    @Test
    void pixReturnToPayerRecusadoQuandoOriginalNaoFoiPix() {
        assertThatThrownBy(() -> RefundDestinationValidator.validatePixReturnToPayer(
                new PixOrigin("BOLETO", null, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("original foi Pix");
    }

    @Test
    void pixReturnToPayerRecusadoQuandoPspNaoOferece() {
        assertThatThrownBy(() -> RefundDestinationValidator.validatePixReturnToPayer(
                new PixOrigin("PIX", "E12345678202601011234567890ABCDE", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao oferece");
    }

    // ---- PIX_KEY ----
    @Test
    void pixKeyCpfOuCnpjIgualAoTitularAceita() {
        assertDoesNotThrow(() -> RefundDestinationValidator.validatePixKey("529.982.247-25", CPF));
        assertDoesNotThrow(() -> RefundDestinationValidator.validatePixKey("11.222.333/0001-81", CNPJ));
    }

    @Test
    void pixKeyDeOutroDocumentoERecusada() {
        assertThatThrownBy(() -> RefundDestinationValidator.validatePixKey(CNPJ, CPF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("igual ao do titular");
    }

    @Test
    void pixKeyEmailTelefoneOuAleatoriaERecusada() {
        for (String key : new String[]{"titular@exemplo.com", "+5511999999999",
                "9f3c2a1e-0000-4000-8000-000000000000"}) {
            assertThatThrownBy(() -> RefundDestinationValidator.validatePixKey(key, CPF))
                    .as("chave %s deve ser recusada", key)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("so pode ser CPF ou CNPJ");
        }
    }

    // ---- BANK_ACCOUNT ----
    @Test
    void contaBancariaExigeTitularidadeValidadaPelaPorta() {
        RefundDestinationValidator.BankAccountHolderPort owned =
                (bank, branch, account, doc) -> doc.equals(CPF);
        RefundDestinationValidator.BankAccountHolderPort notOwned =
                (bank, branch, account, doc) -> false;
        assertDoesNotThrow(() -> RefundDestinationValidator.validateBankAccount(
                owned, "001", "1234", "56789-0", CPF));
        assertThatThrownBy(() -> RefundDestinationValidator.validateBankAccount(
                notOwned, "001", "1234", "56789-0", CPF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titularidade");
    }
}
