package br.com.ziipsan.commercial.billing.printing;

/**
 * Motivo de nao entrega da conta, de CATALOGO FECHADO (P2.8, JOR-35): o
 * retorno da grafica, dos Correios ou da entrega em campo so registra motivo
 * deste catalogo — texto livre de retorno nao entra, porque e este motivo
 * que a qualidade cadastral da P3.32 e o contato verificado consomem como
 * dado comparavel. Motivo novo entra por nova versao do catalogo, com a
 * mesma disciplina dos catalogos da P2.32.
 *
 * <p>Versao 1, com os dois motivos nomeados no aceite: a devolucao (a conta
 * voltou) e o endereco nao localizado. Nenhum outro motivo e presumido.
 */
public enum UndeliveredReason {

    /** Devolucao: a conta voltou ao remetente (retorno de devolucao do aceite). */
    DEVOLUCAO,

    /** Endereco nao localizado no ato da entrega. */
    ENDERECO_NAO_LOCALIZADO;

    /** Versao do catalogo de motivos (dado versionado, como na P2.32). */
    public static final int CATALOG_VERSION = 1;

    /** Chave estavel do motivo (a gravada no banco e a do arquivo de retorno). */
    public String key() {
        return name();
    }

    /**
     * O retorno implica endereco NAO verificado? So o endereco nao localizado
     * diz algo sobre o endereco: a devolucao, sozinha, nao localiza o erro
     * (pode ser recusa, ausencia ou extravio do malote) — quem consome
     * (P3.32, contato verificado) nao deve marcar o endereco por ela.
     */
    public boolean impliesUnverifiedAddress() {
        return this == ENDERECO_NAO_LOCALIZADO;
    }

    /** Le a chave do arquivo de retorno. Fora do catalogo = erro explicito. */
    public static UndeliveredReason fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("motivo de nao entrega e obrigatorio (catalogo fechado da P2.8)");
        }
        for (UndeliveredReason reason : values()) {
            if (reason.name().equals(key.strip())) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Motivo de nao entrega fora do catalogo da P2.8: " + key
                + " (catalogo versao " + CATALOG_VERSION + ": DEVOLUCAO, ENDERECO_NAO_LOCALIZADO)");
    }
}
