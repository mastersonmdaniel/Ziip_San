package br.com.ziipsan.reporting.energy;

/**
 * Tipo da unidade operacional dona de uma instalacao de energia (aceite
 * da ED-90, OPE-08): o consumo especifico e apurado "por sistema, ETA e
 * elevatoria". A ETE entra porque o inventario de emissoes (GES-21)
 * apura o metano de ETE e a unidade precisa existir no mesmo cadastro.
 * Nenhum tipo alem dos nomeados no aceite e nas suas fronteiras.
 */
public enum OperationalUnitKind {
    /** Sistema de abastecimento (conjunto) — "por sistema" do aceite. */
    WATER_SYSTEM,
    /** Estacao de tratamento de agua — "ETA" do aceite. */
    ETA,
    /** Elevatoria (bombeamento) — "elevatoria" do aceite. */
    PUMPING_STATION,
    /** Estacao de tratamento de esgoto — unidade do metano do inventario (GES-21). */
    ETE
}
