package br.com.ziipsan.reporting.energy;

/**
 * Fontes do inventario simplificado de emissoes da ED-90 (GES-21),
 * escopos 1 e 2 — exatamente as tres nomeadas no aceite:
 * energia eletrica (escopo 2), metano de ETE e combustivel (escopo 1).
 * Nenhuma outra fonte entra sem emenda do aceite.
 */
public enum EmissionSource {
    /** Energia eletrica comprada — escopo 2. */
    ELECTRICITY(2),
    /** Metano de ETE — escopo 1. */
    WASTEWATER_METHANE(1),
    /** Combustivel (frota) — escopo 1. */
    FLEET_FUEL(1);

    private final int scope;

    EmissionSource(int scope) {
        this.scope = scope;
    }

    public int scope() {
        return scope;
    }
}
