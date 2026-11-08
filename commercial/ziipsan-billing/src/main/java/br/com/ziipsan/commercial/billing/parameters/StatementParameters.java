package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Leitura tipada dos dois parametros do tenant que a P2.24 consome
 * (PARAMETROS-DO-TENANT, levantamento 20f SS5 e Q7; os dois de classe d,
 * ou seja, escolha livre do tenant, com padrao de fabrica declarado):
 *
 * <ul>
 *   <li>{@code quit.statement_channel} — canal da declaracao anual de
 *       quitacao (Lei 12.007/2009). Padrao de fabrica: espaco na fatura.</li>
 *   <li>{@code bill.water_quality_source} — fonte do bloco mensal de
 *       qualidade da agua (Decreto 5.440/2005). Padrao de fabrica: planilha
 *       mensal do laboratorio, ate o modulo de qualidade da ED-50 estar
 *       ativo.</li>
 * </ul>
 *
 * <p>Os dois parametros TEM padrao de fabrica declarado no catalogo de
 * parametros; por isso a ausencia da chave devolve o padrao (nomeado em
 * {@link #STATEMENT_CHANNEL_FACTORY_DEFAULT} e
 * {@link #WATER_QUALITY_SOURCE_FACTORY_DEFAULT}), e nao um erro. Valor
 * presente mas fora do catalogo e erro explicito: parametro de classe d
 * mal gravado nao pode virar canal silenciosamente.
 *
 * <p>Formato JSON de cada chave (o mesmo que vai em
 * {@code parameter_versions.value}, lido SEMPRE pela classe {@code Json}
 * do core, como no {@code BillingParameters} da P2.5):
 * <pre>
 *   quit.statement_channel     ENUM  "INVOICE_SPACE" | "ANNEX_DOCUMENT" | "PORTAL"
 *   bill.water_quality_source  ENUM  "LAB_SPREADSHEET" | "QUALITY_MODULE"
 * </pre>
 * Os literais em ingles seguem o vocabulario dos demais enums de parametro
 * do billing (P2.5); as opcoes em portugues sao as do catalogo
 * (PARAMETROS-DO-TENANT): espaco na fatura / documento anexo / portal e
 * planilha mensal do laboratorio / modulo de qualidade (ED-50).
 */
public final class StatementParameters {

    public static final String STATEMENT_CHANNEL = "quit.statement_channel";
    public static final String WATER_QUALITY_SOURCE = "bill.water_quality_source";

    /** Canal da declaracao anual de quitacao (Lei 12.007/2009). */
    public enum StatementChannel {
        /** Espaco na fatura: a declaracao sai como mensagem legal do motor da P2.32. */
        INVOICE_SPACE,
        /** Documento anexo a fatura (a entrega do anexo e do canal de impressao/entrega). */
        ANNEX_DOCUMENT,
        /** Portal do consumidor (a publicacao no portal e da P3.20; aqui so o canal e decidido). */
        PORTAL
    }

    /** Fonte dos dados do bloco mensal de qualidade da agua (Decreto 5.440/2005). */
    public enum WaterQualitySource {
        /** Planilha mensal do laboratorio, pelo importador generico da P1.40 (JOR-26). */
        LAB_SPREADSHEET,
        /** Modulo de qualidade da ED-50 (so depois de a ED-50 estar ativa no tenant). */
        QUALITY_MODULE
    }

    /** Padrao de fabrica de {@code quit.statement_channel} (PARAMETROS-DO-TENANT). */
    public static final StatementChannel STATEMENT_CHANNEL_FACTORY_DEFAULT = StatementChannel.INVOICE_SPACE;

    /** Padrao de fabrica de {@code bill.water_quality_source} (PARAMETROS-DO-TENANT). */
    public static final WaterQualitySource WATER_QUALITY_SOURCE_FACTORY_DEFAULT = WaterQualitySource.LAB_SPREADSHEET;

    private final Map<String, String> jsonByKey;

    private StatementParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    /** @param jsonByKey chave do parametro -> JSON vigente e aceito (texto da coluna {@code value}) */
    public static StatementParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new StatementParameters(Map.copyOf(jsonByKey));
    }

    /** {@code quit.statement_channel}; ausente, vale o padrao de fabrica declarado. */
    public StatementChannel statementChannel() {
        return parseEnum(STATEMENT_CHANNEL, StatementChannel.class, STATEMENT_CHANNEL_FACTORY_DEFAULT);
    }

    /** {@code bill.water_quality_source}; ausente, vale o padrao de fabrica declarado. */
    public WaterQualitySource waterQualitySource() {
        return parseEnum(WATER_QUALITY_SOURCE, WaterQualitySource.class, WATER_QUALITY_SOURCE_FACTORY_DEFAULT);
    }

    private <E extends Enum<E>> E parseEnum(String key, Class<E> type, E factoryDefault) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            return factoryDefault;
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            return factoryDefault;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("Parametro '" + key + "' invalido: esperado texto do catalogo");
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Parametro '" + key + "' invalido: valor fora do catalogo: " + node.asText());
        }
    }
}
