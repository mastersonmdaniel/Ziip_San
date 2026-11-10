package br.com.ziipsan.commercial.billingrules.catalog;

/**
 * Modelo de calculo de uma cobranca do catalogo unico (proposta de
 * arquitetura tarifaria, secao 2.2): nao ha uma formula unica — ha uma
 * calculadora por modelo, e o registro declara qual modelo a cobranca usa.
 * Esta camada (registro/catalogo) apenas DECLARA o modelo e valida a
 * coerencia dos parametros da versao ({@link ChargeVersion}); as
 * calculadoras de cada modelo sao da camada seguinte (billing maestro)
 * e nao vivem aqui.
 *
 * <ul>
 *   <li>{@link #PROGRESSIVE_BY_CONSUMPTION} — progressiva por consumo
 *       (ex.: tarifa de agua); a versao referencia as versoes de tarifa
 *       ja provadas da P2.5, sem reescrever formula nenhuma;</li>
 *   <li>{@link #DERIVED_FROM_VOLUME} — derivada de volume/percentual
 *       (ex.: esgoto derivado da agua);</li>
 *   <li>{@link #FIXED_PER_UNIT} — fixa por economia/unidade
 *       (ex.: TFDI e taxas fixas);</li>
 *   <li>{@link #CATALOG_PRICE} — preco de catalogo (servicos da ED-34:
 *       preco unitario proprio ou referencia ao item da tabela de
 *       precos);</li>
 *   <li>{@link #PERCENT_OF_BASE} — percentual sobre uma base de
 *       calculo definida pela calculadora do modelo;</li>
 *   <li>{@link #EXTERNAL_VALUE} — valor externo informado
 *       (convenio/cofaturamento): o valor vem da fonte externa
 *       declarada, nunca de tabela propria versionada aqui.</li>
 * </ul>
 */
public enum CalculationModel {
    PROGRESSIVE_BY_CONSUMPTION,
    DERIVED_FROM_VOLUME,
    FIXED_PER_UNIT,
    CATALOG_PRICE,
    PERCENT_OF_BASE,
    EXTERNAL_VALUE
}
