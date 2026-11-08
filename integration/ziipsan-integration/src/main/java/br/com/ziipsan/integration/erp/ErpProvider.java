package br.com.ziipsan.integration.erp;

/**
 * ED-62 (GES-23): os tres ERPs nomeados pelo aceite — Betha, IPM e Fiorilli.
 * Sao fornecedores REAIS: nenhum leiaute, endpoint, versao de API ou campo
 * deles e presumido nesta entrega. O que existe aqui e a identidade do
 * conector no hub da P3.37 (nome estavel {@code erp-<fornecedor>}) e os
 * dominios do aceite (contabilidade, estoque, RH/leitura de indicadores).
 * O leiaute concreto de cada fornecedor entra SOMENTE como mapeamento
 * declarado pelo tenant ({@link ErpLayoutMapping}) e a prova contra o
 * leiaute publicado/ambiente do fornecedor e pendencia externa nomeada
 * (ver RELATORIO-ED-62, pendencia B1).
 */
public enum ErpProvider {
    BETHA("erp-betha", "Betha"),
    IPM("erp-ipm", "IPM"),
    FIORILLI("erp-fiorilli", "Fiorilli");

    /** Dominios de integracao do aceite da ED-62. */
    public enum Domain { ACCOUNTING, STOCK, HR }

    /** Quem detem o estoque oficial (ADR-0032): decide a exportacao de movimentos. */
    public enum StockOfficial { ZIIPSAN, ERP }

    private final String connectorName;
    private final String displayName;

    ErpProvider(String connectorName, String displayName) {
        this.connectorName = connectorName;
        this.displayName = displayName;
    }

    /** Nome estavel do conector no hub da P3.37 / catalogo da P2.26 (GES-11). */
    public String connectorName() { return connectorName; }

    public String displayName() { return displayName; }
}
