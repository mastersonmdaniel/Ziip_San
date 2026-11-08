package br.com.ziipsan.reporting.catalog;

/**
 * Situacao de um item do catalogo completo da ED-46. A distincao e o
 * coracao da tarefa: so {@link #EXECUTABLE} e {@link #DELIVERED_P322} tem
 * view executavel hoje; os demais estados nomeiam QUEM entrega a fonte,
 * em vez de prometer um relatorio que nao existe.
 */
public enum CatalogStatus {

    /** Relatorio novo desta tarefa: definicao em {@link Ed46ReportDefinitions} + view na migration. */
    EXECUTABLE,

    /** Ja entregue pela P3.22 (ED-45): o item referencia o codigo RPT-0xx existente. */
    DELIVERED_P322,

    /** A POC/item e reivindicado pela acceptance da tarefa-fonte (ex.: POC-66..68 na ED-32). */
    DELIVERED_BY_SOURCE_TASK,

    /** Catalogado com a dependencia textual da fonte: sem view ate a tarefa-fonte entregar. */
    DEPENDENT_SOURCE,

    /** Fora do escopo da ED-46 pela matriz viva: relatorio de fonte propria (ED-16, ED-31, ED-32, ED-48, ED-49, ED-50). */
    EXCLUDED_OWN_SOURCE
}
