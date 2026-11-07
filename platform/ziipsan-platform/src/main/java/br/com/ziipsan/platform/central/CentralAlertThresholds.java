package br.com.ziipsan.platform.central;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

/**
 * Limiares dos alertas das abas da P2.11, como CONFIGURAÇÃO da Central
 * (linha de {@code platform.config_item}, chave
 * {@code central.billing_fiscal.thresholds}), lidos só pela classe
 * {@code Json} do core.
 *
 * <p>O limiar da fila fiscal é o do <b>RNF-08</b>. O valor NÃO está
 * fixado neste código: o documento de requisitos não-funcionais não foi
 * localizado nos fontes disponíveis nesta execução (ver
 * RELATORIO-P2.11.md), e inventar o número violaria a regra do projeto.
 * Por isso o campo é obrigatório no JSON de configuração: configuração
 * sem {@code fiscal_queue_limit_rnf08} é recusada com erro explícito e
 * o alerta fiscal NÃO é avaliado — nunca com um padrão silencioso.
 *
 * <p>O alerta de ciclo atrasado não tem limiar numérico: o atraso nasce
 * do próprio cronograma da P2.5 (item obrigatório com
 * {@code planned_on} anterior a hoje e não realizado).
 */
public record CentralAlertThresholds(long fiscalQueueLimitRnf08) {

    public static final String CONFIG_ITEM_KEY = "central.billing_fiscal.thresholds";

    public CentralAlertThresholds {
        if (fiscalQueueLimitRnf08 < 1) {
            throw new IllegalArgumentException(
                    "fiscal_queue_limit_rnf08 deve ser >= 1 (limiar do RNF-08): " + fiscalQueueLimitRnf08);
        }
    }

    /**
     * Lê os limiares do JSON do config_item. Campo ausente, JSON que não
     * é objeto ou valor NÃO INTEGRAL = erro explícito (correção
     * pós-revisão m-1: {@code asLong()} truncaria {@code 10.5} para
     * {@code 10} em silêncio — distorção silenciosa do limiar).
     */
    public static CentralAlertThresholds fromJson(String json) {
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode() || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Configuração " + CONFIG_ITEM_KEY + " ausente ou não é um objeto JSON: "
                            + "sem o limiar do RNF-08 o alerta fiscal não é avaliado");
        }
        if (!root.hasNonNull("fiscal_queue_limit_rnf08") || !root.get("fiscal_queue_limit_rnf08").isNumber()) {
            throw new IllegalArgumentException(
                    "Campo obrigatório ausente na configuração " + CONFIG_ITEM_KEY
                            + ": fiscal_queue_limit_rnf08 (limiar do RNF-08; sem padrão silencioso)");
        }
        JsonNode limit = root.get("fiscal_queue_limit_rnf08");
        if (!limit.isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "fiscal_queue_limit_rnf08 deve ser um número inteiro na configuração "
                            + CONFIG_ITEM_KEY + " (limiar do RNF-08); valor fracionário é recusado, "
                            + "nunca truncado: " + limit);
        }
        return new CentralAlertThresholds(limit.asLong());
    }
}
