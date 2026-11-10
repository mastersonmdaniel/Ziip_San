package br.com.ziipsan.commercial.billingrules.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Versao de uma cobranca do catalogo unico, com vigencia propria
 * (proposta de arquitetura tarifaria, secoes 2.1 e 2.2): o registro e
 * versionado — mudou o parametro, entra uma versao nova com vigencia
 * nova; a versao anterior nao e reescrita. A resolucao "qual versao
 * vale nesta data" e do {@link ChargeCatalog}; aqui vivem apenas a
 * vigencia, os canais nomeados de fonte e os parametros tipados do
 * modelo, com a coerencia modelo x canal/parametro validada na
 * construcao.
 *
 * <p>O {@code calculationModel} e carregado tambem na versao (alem de
 * declarado na {@link ChargeDefinition}) para que a coerencia
 * modelo x parametro seja validavel neste record, sem depender de
 * consulta externa; o {@link ChargeCatalog} recusa versao cujo modelo
 * diverge do modelo da definicao.
 *
 * <p><b>Canais nomeados (INV-C1-03 na forma do desenho da Camada 0,
 * secao 1):</b> cada versao referencia a sua fonte por UM canal
 * nomeado, conforme o modelo — {@code tariffVersionId} (versao de
 * tarifa da P2.5), {@code priceItemCode} (item da tabela de precos da
 * ED-34) ou {@code externalContractId} (contrato de convenio da
 * ED-25). Os canais sao exclusivos por modelo: o construtor exige o
 * canal do modelo e proibe os demais. O {@link ChargeParameters}
 * tipado da producao fica para os valores do modelo
 * ({@code percent}, {@code fixedAmount}, {@code unitPrice}).
 *
 * <p>Coerencia modelo x canal/parametro (recusa na construcao):
 * <ul>
 *   <li>PROGRESSIVE_BY_CONSUMPTION exige {@code tariffVersionId} e
 *       proibe {@code priceItemCode}/{@code externalContractId}
 *       (precedente {@code SliceCharge.tariffVersionId} da P2.5: a
 *       cobranca referencia a versao de tarifa provada);</li>
 *   <li>DERIVED_FROM_VOLUME exige {@code tariffVersionId} OU
 *       {@code percent} — acerto verificado da producao mantido: o
 *       esgoto derivado pode ser percentual sobre a base/volume OU
 *       pode referenciar a estrutura tarifaria de que deriva
 *       (precedente dos parametros de esgoto da P2.5); proibe
 *       {@code priceItemCode}/{@code externalContractId}; nenhum dos
 *       dois presentes e recusado, os dois presentes sao aceitos e a
 *       precedencia entre eles e da calculadora da camada seguinte;</li>
 *   <li>FIXED_PER_UNIT exige {@code fixedAmount} e proibe os tres
 *       canais nomeados;</li>
 *   <li>CATALOG_PRICE exige {@code priceItemCode} e proibe
 *       {@code tariffVersionId}/{@code externalContractId}. Tensao
 *       registada com o INV-C1-03 do desenho: o desenho pede
 *       {@code priceItemCode} como canal; a producao tinha
 *       {@code unitPrice} OU {@code priceTableItemCode}. Forma
 *       reconciliada: {@code priceItemCode} e o canal;
 *       {@code unitPrice} no {@link ChargeParameters} e o valor
 *       resolvido desse item quando o chamador o traz — por isso
 *       {@code unitPrice} sozinho tambem e admitido como alternativa
 *       documentada (o item fica identificado pelo valor resolvido
 *       que o chamador resolveu da tabela);</li>
 *   <li>PERCENT_OF_BASE exige {@code percent} e proibe os tres
 *       canais nomeados;</li>
 *   <li>EXTERNAL_VALUE exige {@code externalContractId} e proibe
 *       {@code tariffVersionId}/{@code priceItemCode} (o valor em si
 *       nao vive no catalogo: vem do contrato/convenio).</li>
 * </ul>
 *
 * <p>Nenhum arredondamento monetario e aplicado aqui: escala e
 * arredondamento sao da calculadora de cada modelo (camada seguinte);
 * esta camada so recusa valores negativos.
 *
 * @param id                  identificador da versao
 * @param chargeDefinitionId  definicao a que a versao pertence
 * @param version             numero da versao dentro da definicao (&gt;= 1)
 * @param validFrom           inicio de vigencia, obrigatorio
 * @param validTo             fim de vigencia, inclusive; nulo = aberta
 * @param calculationModel    modelo de calculo (igual ao da definicao)
 * @param tariffVersionId     canal nomeado: versao de tarifa (P2.5);
 *                            anulavel conforme o modelo
 * @param priceItemCode       canal nomeado: item da tabela de precos
 *                            (ED-34); anulavel conforme o modelo
 * @param externalContractId  canal nomeado: contrato de convenio
 *                            (ED-25); anulavel conforme o modelo
 * @param parameters          parametros tipados de valor do modelo
 */
public record ChargeVersion(UUID id, UUID chargeDefinitionId, int version,
                            LocalDate validFrom, LocalDate validTo,
                            CalculationModel calculationModel,
                            UUID tariffVersionId, String priceItemCode,
                            UUID externalContractId, ChargeParameters parameters) {

    public ChargeVersion {
        Objects.requireNonNull(id, "id da versao da cobranca e obrigatorio");
        Objects.requireNonNull(chargeDefinitionId, "definicao da versao e obrigatoria");
        if (version < 1) {
            throw new IllegalArgumentException("numero da versao deve ser >= 1: " + version);
        }
        Objects.requireNonNull(validFrom, "inicio de vigencia da versao e obrigatorio");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("vigencia invalida: fim anterior ao inicio");
        }
        Objects.requireNonNull(calculationModel, "modelo de calculo da versao e obrigatorio");
        Objects.requireNonNull(parameters, "parametros da versao sao obrigatorios");
        if (priceItemCode != null && priceItemCode.isBlank()) {
            throw new IllegalArgumentException("priceItemCode em branco nao referencia item da tabela");
        }
        validateCoherence(calculationModel, tariffVersionId, priceItemCode, externalContractId, parameters);
    }

    /**
     * Forma anterior a correcao da Camada 1 (sem os canais nomeados):
     * delega com os tres canais nulos. So e valida para os modelos que
     * proibem os canais nomeados (FIXED_PER_UNIT, PERCENT_OF_BASE) ou
     * para DERIVED_FROM_VOLUME sustentado so por {@code percent}; os
     * modelos que exigem canal nomeado recusam nesta forma, como
     * recusariam no construtor canonico sem o canal.
     */
    public ChargeVersion(UUID id, UUID chargeDefinitionId, int version,
                         LocalDate validFrom, LocalDate validTo,
                         CalculationModel calculationModel, ChargeParameters parameters) {
        this(id, chargeDefinitionId, version, validFrom, validTo, calculationModel,
                null, null, null, parameters);
    }

    private static void validateCoherence(CalculationModel model, UUID tariffVersionId,
                                          String priceItemCode, UUID externalContractId,
                                          ChargeParameters p) {
        switch (model) {
            case PROGRESSIVE_BY_CONSUMPTION -> {
                if (tariffVersionId == null) {
                    throw new IllegalArgumentException(
                            "modelo PROGRESSIVE_BY_CONSUMPTION exige tariffVersionId");
                }
                requireNoOtherChannels(model, priceItemCode, externalContractId);
            }
            case DERIVED_FROM_VOLUME -> {
                if (tariffVersionId == null && p.percent() == null) {
                    throw new IllegalArgumentException(
                            "modelo DERIVED_FROM_VOLUME exige tariffVersionId ou percent");
                }
                requireNoOtherChannels(model, priceItemCode, externalContractId);
            }
            case FIXED_PER_UNIT -> {
                if (p.fixedAmount() == null) {
                    throw new IllegalArgumentException("modelo FIXED_PER_UNIT exige fixedAmount");
                }
                requireNoNamedChannels(model, tariffVersionId, priceItemCode, externalContractId);
            }
            case CATALOG_PRICE -> {
                if (priceItemCode == null && p.unitPrice() == null) {
                    throw new IllegalArgumentException(
                            "modelo CATALOG_PRICE exige priceItemCode ou unitPrice resolvido");
                }
                if (tariffVersionId != null || externalContractId != null) {
                    throw new IllegalArgumentException(
                            "modelo CATALOG_PRICE proibe tariffVersionId/externalContractId");
                }
            }
            case PERCENT_OF_BASE -> {
                if (p.percent() == null) {
                    throw new IllegalArgumentException("modelo PERCENT_OF_BASE exige percent");
                }
                requireNoNamedChannels(model, tariffVersionId, priceItemCode, externalContractId);
            }
            case EXTERNAL_VALUE -> {
                if (externalContractId == null) {
                    throw new IllegalArgumentException(
                            "modelo EXTERNAL_VALUE exige externalContractId");
                }
                if (tariffVersionId != null || priceItemCode != null) {
                    throw new IllegalArgumentException(
                            "modelo EXTERNAL_VALUE proibe tariffVersionId/priceItemCode");
                }
            }
        }
    }

    private static void requireNoOtherChannels(CalculationModel model, String priceItemCode,
                                               UUID externalContractId) {
        if (priceItemCode != null || externalContractId != null) {
            throw new IllegalArgumentException(
                    "modelo " + model + " proibe priceItemCode/externalContractId");
        }
    }

    private static void requireNoNamedChannels(CalculationModel model, UUID tariffVersionId,
                                               String priceItemCode, UUID externalContractId) {
        if (tariffVersionId != null || priceItemCode != null || externalContractId != null) {
            throw new IllegalArgumentException(
                    "modelo " + model + " proibe os canais nomeados "
                            + "(tariffVersionId/priceItemCode/externalContractId)");
        }
    }

    /**
     * A versao esta vigente na data? No espirito do
     * {@code ServicePriceItem.appliesOn} da ED-34: inicio e fim
     * inclusivos; fim nulo significa vigencia aberta.
     */
    public boolean appliesTo(LocalDate date) {
        Objects.requireNonNull(date, "data de referencia e obrigatoria");
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    /**
     * Parametros tipados de valor de uma versao (proposta, secao 2.2).
     * Na forma reconciliada da correcao da Camada 1, este record guarda
     * apenas os valores do modelo — {@code percent},
     * {@code fixedAmount}, {@code unitPrice}; as referencias de fonte
     * vivem nos canais nomeados da {@link ChargeVersion}
     * ({@code tariffVersionId}, {@code priceItemCode},
     * {@code externalContractId}). A coerencia com o modelo e validada
     * por {@link ChargeVersion}, e os limites de cada campo (nunca
     * negativos) sao validados aqui.
     *
     * @param percent     percentual (ex.: 80 = 80%); nulo ou &gt;= 0
     * @param fixedAmount valor fixo por unidade; nulo ou &gt;= 0
     * @param unitPrice   preco unitario de catalogo — valor resolvido
     *                    do item {@code priceItemCode} quando o
     *                    chamador o traz; nulo ou &gt;= 0
     */
    public record ChargeParameters(BigDecimal percent, BigDecimal fixedAmount,
                                   BigDecimal unitPrice) {

        public ChargeParameters {
            if (percent != null && percent.signum() < 0) {
                throw new IllegalArgumentException("percent nao pode ser negativo: " + percent);
            }
            if (fixedAmount != null && fixedAmount.signum() < 0) {
                throw new IllegalArgumentException("fixedAmount nao pode ser negativo: " + fixedAmount);
            }
            if (unitPrice != null && unitPrice.signum() < 0) {
                throw new IllegalArgumentException("unitPrice nao pode ser negativo: " + unitPrice);
            }
        }

        /** Parametros vazios (nenhum campo preenchido). */
        public static ChargeParameters empty() {
            return new ChargeParameters(null, null, null);
        }
    }
}
