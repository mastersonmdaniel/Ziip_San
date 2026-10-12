package br.com.sigsan.commercial.collection.returnfile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Arquivo de retorno de arrecadação com código de barras, padrão FEBRABAN de 150 posições
 * (registros A, G e Z). Layout: "Layout Padrão de Arrecadação/Recebimento com Utilização do
 * Código de Barras", versão 08, seção 06 (ver README do módulo).
 *
 * @param header   registro A
 * @param payments registros G, na ordem do arquivo
 * @param trailer  registro Z
 * @param rawLines linhas cruas na ordem do arquivo (header, detalhes, trailer), para guardar como
 *                 evidência (o GSAN também guardava a linha crua)
 */
public record Febraban150File(Header header, List<Payment> payments, Trailer trailer, List<String> rawLines) {

    public Febraban150File {
        payments = List.copyOf(payments);
        rawLines = List.copyOf(rawLines);
    }

    /**
     * Registro A. Campos: A.03 convênio (definido pelo banco), A.04 nome da empresa, A.05 banco na
     * câmara de compensação, A.06 nome do banco, A.07 data de geração, A.08 NSA (evolui de 1 em
     * 1 por arquivo), A.09 versão do layout, A.10 identificação do serviço.
     */
    public record Header(String convenioCode, String companyName, String bankCode, String bankName,
                         LocalDate generatedOn, long nsa, int layoutVersion, String serviceId) {
    }

    /**
     * Registro G.
     *
     * @param lineNumber       linha do arquivo (1 = header)
     * @param creditedAccount  G.02 agência/conta/dígito creditada (texto livre do banco)
     * @param paidOn           G.03
     * @param creditedOn       G.04
     * @param barcode          G.05, 44 posições; pode vir em branco nas formas de captura "sem fatura/guia"
     * @param amountReceived   G.06 valor efetivamente recebido
     * @param fee              G.07 tarifa por comprovante (informada se acordada; zero se não)
     * @param nsr              G.08 número sequencial de registro (uso do banco)
     * @param collectingBranch G.09 agência arrecadadora
     * @param captureForm      G.10 forma de arrecadação/captura ({@code 1}-{@code 9}, {@code a}-{@code i}, {@code l})
     * @param authentication   G.11 autenticação de caixa ou código de transação
     * @param paymentForm      G.12 1 dinheiro, 2 cheque, 3 não identificado/outras, 4 cartão de
     *                         crédito, 5 cartão/multibanco, 6 débito em conta
     * @param rawLine          a linha de 150 posições
     */
    public record Payment(int lineNumber, String creditedAccount, LocalDate paidOn, LocalDate creditedOn,
                          String barcode, BigDecimal amountReceived, BigDecimal fee, long nsr,
                          String collectingBranch, char captureForm, String authentication, int paymentForm,
                          String rawLine) {

        /** Captura por Pix (G.10 = {@code 9} com fatura/guia, {@code i} sem). */
        public boolean isPix() {
            return captureForm == '9' || captureForm == 'i';
        }
    }

    /**
     * Registro Z: Z.02 total de registros do arquivo, incluindo header e trailer; Z.03 valor total.
     */
    public record Trailer(int totalRecords, BigDecimal totalAmount) {
    }
}
