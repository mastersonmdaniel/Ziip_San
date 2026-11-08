package br.com.ziipsan.platform.orgscope;

/**
 * O tipo de ÁREA de território a que uma unidade organizacional ou um escopo
 * direto se liga (P3.30, aceite: "ligadas a áreas (localidade, setor, rota)").
 * Cada tipo é uma tabela do registry: {@code localities},
 * {@code commercial_sectors} e {@code reading_routes} (V3_1/V3_6). Os nomes
 * deste enum são EXATAMENTE os literais do CHECK de {@code area_type} na V1_8
 * (conferido por OrgScopeMigrationConsistencyTest, igualdade nos dois
 * sentidos).
 */
public enum OrgAreaType {
    LOCALITY,
    SECTOR,
    ROUTE
}
