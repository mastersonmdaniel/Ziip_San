package br.com.ziipsan.commercial.billing.maintenance;

/**
 * Operacoes de manutencao de conta pelo backoffice (aceite da P2.20, ED-21).
 * Cada operacao diz se gera <b>nova versao</b> da conta, qual o tipo de OS
 * interna do nucleo da P1.31 que a carrega (quando ha OS) e se e mera
 * consulta/emissao de documento (2a via e espelho nao alteram a conta).
 *
 * <p>Os codigos de OS sao os nomeados no aceite: {@code BILL_RECTIFICATION},
 * {@code BILL_CANCELLATION}, {@code BILL_REVIEW} e
 * {@code BILL_DUE_DATE_CHANGE}. O aceite remete os "demais tipos" a tabela
 * 2.9 do levantamento 20b, que <b>nao esta</b> entre as fontes locais do
 * projeto (ver RELATORIO-P2.20): nenhuma operacao abaixo inventa um codigo
 * novo -- as retificacoes de leitura/consumo/servicos/cadastro sao a mesma
 * retificacao ({@code BILL_RECTIFICATION}) com campos distintos, e as
 * operacoes sem tipo nomeado nao geram OS propria (geram versao e evento,
 * ligadas a OS da operacao principal quando houver).
 */
public enum BillOperation {

    CANCEL(false, BillOrderType.BILL_CANCELLATION),
    CHANGE_DUE_DATE(true, BillOrderType.BILL_DUE_DATE_CHANGE),
    RECTIFY_READING(true, BillOrderType.BILL_RECTIFICATION),
    RECTIFY_CONSUMPTION(true, BillOrderType.BILL_RECTIFICATION),
    RECTIFY_SERVICES(true, BillOrderType.BILL_RECTIFICATION),
    RECALCULATE_FOR_REGISTRY_CHANGE(true, BillOrderType.BILL_RECTIFICATION),
    REMOVE_WATER_OR_SEWER(true, BillOrderType.BILL_RECTIFICATION),
    OPEN_REVIEW(false, BillOrderType.BILL_REVIEW),
    CLOSE_REVIEW(false, BillOrderType.BILL_REVIEW),
    /**
     * Credito avulso: COBERTURA PARCIAL declarada (correcao A8) -- reusa o
     * rito da retificacao; o "credito avulso" e o creditsValue que o
     * chamador compoe na nova versao, sem semantica propria nesta frente.
     */
    GRANT_ADHOC_CREDIT(true, BillOrderType.BILL_RECTIFICATION),
    /**
     * Pagamento a confirmar: COBERTURA PARCIAL declarada (correcao A8) --
     * reusa o rito da retificacao e NENHUM marcador de "pagamento a
     * confirmar" e gravado na versao nova nesta frente; quem confirma o
     * pagamento e a arrecadacao (P3.1).
     */
    PAYMENT_TO_CONFIRM(true, BillOrderType.BILL_RECTIFICATION),
    /**
     * Emitir fora do lote: SO ENUMERADA nesta frente (correcao A8) --
     * emissao avulsa de conta ainda nao emitida e da emissao (P2.5/P2.32),
     * sem versao anterior: nao e manutencao.
     */
    ISSUE_OUT_OF_BATCH(false, null),
    /** 2a via: SO ENUMERADA nesta frente -- leitura da versao vigente, da frente dona do documento. */
    SECOND_COPY(false, null),
    /** Espelho: SO ENUMERADO nesta frente -- leitura da versao vigente, da frente dona do documento. */
    MIRROR(false, null);

    private final boolean createsNewVersion;
    private final BillOrderType orderType;

    BillOperation(boolean createsNewVersion, BillOrderType orderType) {
        this.createsNewVersion = createsNewVersion;
        this.orderType = orderType;
    }

    /** A operacao substitui a conta vigente por uma nova versao (DAD-14/DAD-16)? */
    public boolean createsNewVersion() {
        return createsNewVersion;
    }

    /** Tipo de OS interna tipada da P1.31; {@code null} = operacao sem OS propria. */
    public BillOrderType orderType() {
        return orderType;
    }
}
