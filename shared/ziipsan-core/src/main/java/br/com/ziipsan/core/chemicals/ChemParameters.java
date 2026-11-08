package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Parametros da ED-89, SEMPRE lidos do tenant — nenhum valor de negocio
 * e constante nesta classe:
 * <pre>
 *   chem.min_stock_days                     INTEGER dias minimos de cobertura
 *       de estoque do insumo quimico — chave NOMEADA pelo acceptance.
 *       Sem fabrica: [A DEFINIR].
 *   chem.controlled_products_list           TEXT identificacao/versao da lista
 *       vigente de produtos controlados pela Policia Federal (Lei
 *       10.357/2001; Portaria MJSP 204/2022, alterada pela Portaria MJSP
 *       223/2022), mantida e atualizada pelo Ziip no catalogo global
 *       ({@link ControlledCatalog}). Sem fabrica: [SEM FONTE] — a lista
 *       vigente e dado de carga normativa, nao valor presumido.
 *   ops.reservoir_cleaning_interval_months  INTEGER periodicidade (meses) do
 *       plano de limpeza de reservatorio — chave NOMEADA pelo acceptance:
 *       "valor da agencia, sem padrao de norma confirmado". Sem fabrica:
 *       [A DEFINIR].
 *   chem.pf_control_enabled                 BOOLEAN liga/desliga o controle
 *       opcional de produtos sujeitos a Policia Federal, por tenant.
 *       CHAVE PROPOSTA por esta entrega (o acceptance pede o controle
 *       "opcional, por tenant" sem nomear a chave) — confirmar o registro
 *       no catalogo na integracao. Sem fabrica: ausencia = desligado por
 *       omissao NUNCA presumida; a leitura explicita e exigida para ligar.
 *   chem.reconciliation_tolerance_pct       DECIMAL tolerancia (%) da
 *       conciliacao dosagem teorica x consumo real. CHAVE PROPOSTA por
 *       esta entrega: nenhuma fonte fixa tolerancia — sem ela, a
 *       conciliacao REPORTA os valores sem veredito (nunca diverge nem
 *       concilia por presuncao). Sem fabrica: [A DEFINIR].
 * </pre>
 * Ausencia da chave exigida pela operacao = recusa nomeada
 * ({@link MissingParameterException}, padrao ED-50/ED-88). Os 14 valores
 * da P3.9 NAO sao tocados por esta entrega.
 */
public interface ChemParameters {

    String MIN_STOCK_DAYS = "chem.min_stock_days";
    String CONTROLLED_PRODUCTS_LIST = "chem.controlled_products_list";
    String RESERVOIR_CLEANING_INTERVAL_MONTHS = "ops.reservoir_cleaning_interval_months";
    String PF_CONTROL_ENABLED = "chem.pf_control_enabled";
    String RECONCILIATION_TOLERANCE_PCT = "chem.reconciliation_tolerance_pct";

    Optional<Integer> minStockDays(UUID tenantId);
    Optional<String> controlledProductsListRef(UUID tenantId);
    Optional<Integer> reservoirCleaningIntervalMonths(UUID tenantId);
    Optional<Boolean> pfControlEnabled(UUID tenantId);
    Optional<BigDecimal> reconciliationTolerancePct(UUID tenantId);

    static int requireMinStockDays(Optional<Integer> v) {
        Integer x = v == null ? null : v.orElse(null);
        if (x == null) throw new MissingParameterException(MIN_STOCK_DAYS,
                "dias minimos de cobertura nao configurados no tenant [A DEFINIR]: alerta de estoque minimo nao opera");
        if (x <= 0) throw new IllegalArgumentException(MIN_STOCK_DAYS + " deve ser positivo: " + x);
        return x;
    }

    static String requireControlledListRef(Optional<String> v) {
        String x = v == null ? null : v.orElse(null);
        if (x == null || x.isBlank()) throw new MissingParameterException(CONTROLLED_PRODUCTS_LIST,
                "lista vigente de produtos controlados (Policia Federal) nao identificada para o tenant [SEM FONTE]");
        return x.trim();
    }

    static int requireReservoirIntervalMonths(Optional<Integer> v) {
        Integer x = v == null ? null : v.orElse(null);
        if (x == null) throw new MissingParameterException(RESERVOIR_CLEANING_INTERVAL_MONTHS,
                "periodicidade da limpeza de reservatorio nao configurada (valor da agencia) [A DEFINIR]: plano nao opera");
        if (x <= 0) throw new IllegalArgumentException(RESERVOIR_CLEANING_INTERVAL_MONTHS + " deve ser positivo: " + x);
        return x;
    }

    /** Ligar o controle PF exige a chave explicita; ausencia nao liga nada por presuncao. */
    static boolean requirePfControlEnabled(Optional<Boolean> v) {
        Boolean x = v == null ? null : v.orElse(null);
        if (x == null) throw new MissingParameterException(PF_CONTROL_ENABLED,
                "controle opcional da Policia Federal sem definicao explicita no tenant (chave proposta pela ED-89)");
        return x;
    }
}
