package br.com.ziipsan.commercial.billing.printing;

/**
 * Formato acessivel da conta IMPRESSA (P2.8), lido do cadastro da P1.3
 * (CAD-81, RAS-10; Lei 13.146/2015, art. 62): o perfil de atendimento da
 * unidade marca o formato pedido em {@code registry.accessible_formats}, e o
 * arquivo da grafica sai com o INDICADOR do formato em cada registro, para a
 * grafica separar a producao (braile e tiragem separada; fonte ampliada e
 * corpo maior no mesmo leiaute).
 *
 * <p>O cadastro conhece quatro formatos (braile, fonte ampliada, PDF
 * acessivel e audio); SO os dois impressos tem indicador aqui. PDF acessivel
 * e audio sao canais digitais (portal, P3.20): mapear um deles para braile
 * ou fonte ampliada imprimiria o formato errado, por isso
 * {@link #fromCadastro(String)} RECUSA essas chaves com erro explicito em
 * vez de aproxima-las. Cabe ao chamador, antes de montar o arquivo, decidir
 * o canal: a copia impressa de quem pediu formato digital entra com
 * {@link #NONE} explicito e o formato digital segue pelo canal proprio.
 * [Texto esclarecido na revisao cruzada: a redacao anterior sugeria que
 * fromCadastro devolvia NONE para essas chaves; ele lanca excecao.]
 *
 * <p>Braile tem ainda o parametro do tenant {@code bill.braille_enabled}
 * (classe d, P1.3): com o parametro desligado, conta marcada em braile no
 * cadastro e conflito explicito na geracao (ver
 * {@code DefaultPrintFileService}), nunca rebaixamento silencioso para
 * impressao comum.
 */
public enum AccessiblePrintFormat {

    /** Sem formato acessivel pedido: impressao comum. */
    NONE("NENHUM"),

    /** Braile (tiragem separada da grafica). */
    BRAILLE("BRAILE"),

    /** Fonte ampliada. */
    LARGE_PRINT("FONTE_AMPLIADA");

    /** Parametro do tenant que habilita a producao em braile (P1.3, classe d). */
    public static final String BRAILLE_ENABLED_PARAMETER_KEY = "bill.braille_enabled";

    private final String fileToken;

    AccessiblePrintFormat(String fileToken) {
        this.fileToken = fileToken;
    }

    /** Indicador gravado no campo {@code ACESSIVEL} do registro da grafica. */
    public String fileToken() {
        return fileToken;
    }

    /**
     * Le a chave do formato como marcada no cadastro da P1.3. Nulo ou em
     * branco = nenhum formato pedido ({@link #NONE}). Chave desconhecida e
     * erro explicito; {@code ACCESSIBLE_PDF} e {@code AUDIO} sao recusados
     * com a razao (canal digital, ver javadoc da classe).
     */
    public static AccessiblePrintFormat fromCadastro(String cadastroKey) {
        if (cadastroKey == null || cadastroKey.isBlank()) {
            return NONE;
        }
        return switch (cadastroKey.strip()) {
            case "NONE", "NENHUM" -> NONE;
            case "BRAILLE", "BRAILE" -> BRAILLE;
            case "LARGE_PRINT", "FONTE_AMPLIADA" -> LARGE_PRINT;
            case "ACCESSIBLE_PDF", "AUDIO" -> throw new IllegalArgumentException(
                    "Formato acessivel " + cadastroKey + " e digital (portal/P3.20), nao impresso:"
                            + " nao tem indicador no arquivo da grafica (P2.8 so imprime BRAILLE e LARGE_PRINT)");
            default -> throw new IllegalArgumentException(
                    "Formato acessivel fora do catalogo da P1.3: " + cadastroKey);
        };
    }
}
