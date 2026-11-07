package br.com.ziipsan.commercial.billing.printing;

/**
 * Origem do retorno de nao entrega (P2.8, JOR-35): de onde veio a informacao
 * de que a conta nao foi entregue. Catalogo fechado, como o do motivo — a
 * P3.32 pondera a qualidade cadastral tambem pela origem (o retorno da
 * grafica vem do arquivo de retorno dela; o de campo, do entregador).
 */
public enum ReturnOrigin {

    /** Arquivo de retorno da grafica que imprimiu e postou o lote. */
    GRAFICA,

    /** Retorno dos Correios (devolucao postal). */
    CORREIOS,

    /** Entrega em campo pela equipe propria ou terceirizada de entrega. */
    ENTREGA_EM_CAMPO,

    /** Informacao registrada no atendimento (balcao, telefone, portal). */
    ATENDIMENTO;

    /** Le a chave do arquivo de retorno. Fora do catalogo = erro explicito. */
    public static ReturnOrigin fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("origem do retorno e obrigatoria (catalogo fechado da P2.8)");
        }
        for (ReturnOrigin origin : values()) {
            if (origin.name().equals(key.strip())) {
                return origin;
            }
        }
        throw new IllegalArgumentException("Origem do retorno fora do catalogo da P2.8: " + key
                + " (GRAFICA, CORREIOS, ENTREGA_EM_CAMPO, ATENDIMENTO)");
    }
}
