package br.com.ziipsan.commercial.billing.printing;

/**
 * Leiaute do arquivo para a grafica (P2.8): versao, separador e ordem dos
 * campos. Arquivo texto UTF-8, uma linha por registro, quebra {@code \n}:
 *
 * <pre>
 * HDR|LAYOUT=1|BATCH=...|GERADO_EM=...|PERFIL=...|CONTEUDO_V=...|MENSAGENS_V=...
 * CTA|SEQ=...|CONTA=...|VERSAO=...|UNIDADE=...|MATRICULA=...|TITULAR=...|
 *     ENDERECO_ENTREGA=...|COMPETENCIA=...|VENCIMENTO=...|TOTAL=...|
 *     MODELO_VERSAO=...|CAMPOS=...|MENSAGENS=...|VINCULO_PORTAL=...|ACESSIVEL=...
 * TRL|REGISTROS=...|SHA256=...
 * </pre>
 *
 * <p>O leiaute NAO tem texto proprio: os campos {@code CAMPOS} e
 * {@code MENSAGENS} carregam as CHAVES do conteudo minimo e das mensagens
 * legais da P2.32 (RAS-04) e as versoes dos catalogos vao no cabecalho — o
 * texto impresso e o do modelo aprovado do tenant no motor da P2.30, na
 * versao indicada em {@code MODELO_VERSAO} de cada registro. Nenhuma frase
 * legal e escrita por esta tarefa.
 *
 * <p>A aprovacao deste leiaute com amostra impressa e homologacao externa
 * (item de processo do aceite): o codigo gera o arquivo neste leiaute; a
 * amostra sintetica de conferencia acompanha a entrega da tarefa.
 */
public final class PrintLayout {

    /** Versao do leiaute. Mudanca de campo ou de ordem sobe a versao. */
    public static final int LAYOUT_VERSION = 1;

    /** Separador de campos. Valor de campo com o separador ou quebra de linha e recusado na geracao. */
    public static final String DELIMITER = "|";

    public static final String HEADER_TAG = "HDR";
    public static final String RECORD_TAG = "CTA";
    public static final String TRAILER_TAG = "TRL";

    private PrintLayout() {
    }
}
