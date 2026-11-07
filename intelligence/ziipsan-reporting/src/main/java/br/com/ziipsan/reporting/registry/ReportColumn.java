package br.com.ziipsan.reporting.registry;

/**
 * Coluna declarativa. {@code personal} marca dado pessoal (LGPD): na
 * exportacao, a coluna passa SEMPRE pelo mascaramento do teto do usuario
 * (ADR-0008, regra unica da P1.12) - nunca sai em claro por este nucleo.
 *
 * <p>{@code aggregable} (correcao R2 pos-revisao): so colunas somaveis
 * viram {@code SUM(...)} no agrupamento do ReportQueryBuilder. O padrao
 * do construtor de 3 argumentos e somavel apenas para LONG/MONEY;
 * colunas de razao/percentual (DECIMAL, ex.: percentual_nao_atendido da
 * RPT-027) NUNCA sao somadas - somar percentuais de grupos distintos
 * produz um numero sem significado. Para declarar uma DECIMAL somavel
 * (ou uma LONG nao somavel), use o construtor completo.
 */
public record ReportColumn(String name, ReportColumnType type, boolean personal, boolean aggregable) {

    public ReportColumn {
        if (name == null || !name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Nome de coluna invalido: " + name);
        }
        if (type == null) {
            throw new IllegalArgumentException("Tipo obrigatorio para a coluna " + name);
        }
    }

    /** Padrao: LONG/MONEY somaveis; DECIMAL (razao/percentual) nao. */
    public ReportColumn(String name, ReportColumnType type, boolean personal) {
        this(name, type, personal,
                type == ReportColumnType.LONG || type == ReportColumnType.MONEY);
    }
}
