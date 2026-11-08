package br.com.ziipsan.core.stationops;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;

/**
 * Registro do verificador de existencia do alvo
 * {@code OPERATIONAL_SITE} da P1.31 sobre a tabela de unidades da
 * ED-87: e ele que habilita os tipos de servico declarados sobre
 * estacoes (a classe sem verificador nao pode ser declarada por tipo
 * de servico — contrato tipado da P1.31/COE-10). A fiacao chama
 * {@link #register} na inicializacao do registro do tenant, com o
 * schema do tenant.
 */
public final class StationTargetRegistration {

    /** Tabela das unidades operacionais criada pelo SQL desta entrega. */
    public static final String UNITS_TABLE = "ops_station_units";

    private StationTargetRegistration() {}

    public static void register(TargetVerifierRegistry registry, String tenantSchema) {
        if (registry == null) throw new IllegalArgumentException("registro de verificadores e obrigatorio");
        registry.register(TargetType.OPERATIONAL_SITE,
                TargetVerifierRegistry.sqlVerifier(tenantSchema, UNITS_TABLE));
    }
}
