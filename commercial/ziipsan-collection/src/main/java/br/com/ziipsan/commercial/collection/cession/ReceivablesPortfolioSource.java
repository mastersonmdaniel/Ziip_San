package br.com.ziipsan.commercial.collection.cession;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-103/E1 (decisao 62) — porta propria da carteira de recebiveis do
 * modulo {@code ziipsan-collection}. Produtor (execucao ED-103,
 * decisao 62): sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p><b>Resolucao da tensao aceite x granularidade.</b> O aceite pede o
 * relatorio da carteira "a partir das contas a receber da ED-32". A ED-32
 * entregou, em {@code ziipsan-accounting}, a visao CONTABIL AGREGADA
 * mensal ({@code ReceivablesMonthlyBalance} — saldos FIN-05 por esfera e
 * prazo), que nao tem granularidade por titulo. Aging, adimplencia por
 * faixa, cessao e reconciliacao exigem linhas POR RECEBIVEL. Esta porta
 * define esse contrato por titulo DENTRO do modulo de cobranca, sem
 * importar tipos de billing/accounting e sem nenhuma aresta nova de pom
 * entre modulos (precedente ED-102/{@code GisProjectPort}).
 *
 * <p>A fiacao a um adapter real sobre os recebiveis vivos de
 * billing/collection e a pendencia de integracao nomeada
 * <b>P-ED103-INT-01</b>: ate la, a prova corre sobre fixtures declaradas
 * que implementam esta porta.
 *
 * <p><b>Minimizacao por desenho (E3):</b> a linha da carteira NAO tem
 * nome, CPF/CNPJ nem endereco do devedor — so a chave pseudonimizada
 * estavel ({@code debtorKey}), que permite agrupar por devedor sem o
 * identificar. Nenhum campo fora desta whitelist existe no record; o
 * teste de whitelist por reflexao garante-o.
 */
public interface ReceivablesPortfolioSource {

    /** Linhas por recebivel da carteira de um tenant. */
    List<PortfolioLine> portfolio(UUID tenantId);

    /**
     * Linha por recebivel (whitelist fechada): identificador do
     * recebivel, chave pseudonimizada estavel do devedor, valor de face,
     * valor ja pago, vencimento e origem declarada do recebivel.
     */
    record PortfolioLine(String receivableId, String debtorKey, BigDecimal faceAmount,
                         BigDecimal paidAmount, LocalDate dueDate, String origin) {

        public PortfolioLine {
            if (receivableId == null || receivableId.isBlank())
                throw new ReceivablesCessionException("PORTFOLIO_RECEIVABLE_ID_REQUIRED",
                        "linha da carteira exige o identificador do recebivel");
            if (debtorKey == null || debtorKey.isBlank())
                throw new ReceivablesCessionException("PORTFOLIO_DEBTOR_KEY_REQUIRED",
                        "linha da carteira exige a chave pseudonimizada do devedor");
            Objects.requireNonNull(faceAmount, "valor de face e obrigatorio");
            Objects.requireNonNull(paidAmount, "valor pago e obrigatorio");
            if (faceAmount.signum() < 0 || paidAmount.signum() < 0)
                throw new ReceivablesCessionException("PORTFOLIO_AMOUNT_NEGATIVE",
                        "valores da linha da carteira nao podem ser negativos");
            if (paidAmount.compareTo(faceAmount) > 0)
                throw new ReceivablesCessionException("PORTFOLIO_PAID_EXCEEDS_FACE",
                        "valor pago (" + paidAmount + ") maior que o valor de face (" + faceAmount
                                + ") do recebivel " + receivableId + ": inconsistencia da origem");
            Objects.requireNonNull(dueDate, "vencimento e obrigatorio");
            if (origin == null || origin.isBlank())
                throw new ReceivablesCessionException("PORTFOLIO_ORIGIN_REQUIRED",
                        "linha da carteira exige a origem declarada do recebivel");
        }

        /** Saldo em aberto (face - pago), aritmetica exacta. */
        public BigDecimal outstanding() {
            return faceAmount.subtract(paidAmount);
        }

        /** Liquidado pelo devedor quando nada resta em aberto. */
        public boolean fullyPaid() {
            return outstanding().signum() == 0;
        }
    }
}
