package br.com.ziipsan.commercial.billing.altsolution;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

/**
 * Leitura tipada, SEMPRE pela classe {@code Json} do core, dos parâmetros da
 * ED-88 (soluções individuais e ligação obrigatória), gravados como JSON pelo
 * tenant. Ausência é erro explícito ({@link MissingParameterException}), nunca
 * padrão assumido — princípio do catálogo (PARAMETROS-DO-TENANT, ADR-0002).
 *
 * <pre>
 *   sewer.alt_solution_service_enabled       BOOLEAN  true|false
 *   sewer.alt_solution_service_interval_months INT    meses entre esgotamentos periódicos
 *   sewer.alt_solution_tariff_source         TEXT     código da fonte tarifária definida pela agência
 *   avail.notice_days                        INT      classe c — piso legal de 90 dias
 *   avail.max_connect_months                 INT      classe c — teto legal de 12 meses (1 ano)
 *   avail.exemption_requires_proof           BOOLEAN  classe a — travado em true
 * </pre>
 *
 * <p>Travas (não são valores inventados: vêm do próprio acceptance da ED-88):
 * <ul>
 *   <li>{@code avail.notice_days} (classe c): o tenant/agência só pode AMPLIAR
 *       o aviso; abaixo do piso de 90 dias é recusado;</li>
 *   <li>{@code avail.max_connect_months} (classe c): teto de 1 ano da
 *       Lei 11.445/2007, art. 45 e das NR 13/ANA e NR 11/ANA citadas no
 *       acceptance; acima de 12 meses é recusado;</li>
 *   <li>{@code avail.exemption_requires_proof} (classe a): travado — valor
 *       {@code false} (dispensa sem prova de inviabilidade) é recusado;</li>
 *   <li>a obrigação legal de conexão NÃO é parâmetro: não existe chave de
 *       desligamento. {@link #requireObligationNotDisabled(String)} recusa
 *       qualquer tentativa de gravar {@code false} numa chave de obrigação.</li>
 * </ul>
 *
 * <p>{@code sewer.alt_solution_tariff_source}: nenhuma fonte consultada
 * (acceptance, PARAMETROS-DO-TENANT, pacotes ED-22/ED-34) enumera os códigos
 * possíveis — a fonte é definida pela agência. Por isso o valor é lido como
 * código textual obrigatório (em branco/ausente = erro) e carregado até o
 * lançamento, SEM mapear código desconhecido para um comportamento assumido.
 * Enumerar valores aqui seria inventar dado da agência.
 */
public final class AltSolutionParameters {

    public static final String SERVICE_ENABLED = "sewer.alt_solution_service_enabled";
    public static final String SERVICE_INTERVAL_MONTHS = "sewer.alt_solution_service_interval_months";
    public static final String TARIFF_SOURCE = "sewer.alt_solution_tariff_source";
    public static final String NOTICE_DAYS = "avail.notice_days";
    public static final String MAX_CONNECT_MONTHS = "avail.max_connect_months";
    public static final String EXEMPTION_REQUIRES_PROOF = "avail.exemption_requires_proof";
    /** Chave que NÃO existe no catálogo: só aparece aqui para recusar a tentativa de criá-la. */
    public static final String CONNECTION_OBLIGATION_ENABLED = "avail.connection_obligation_enabled";

    /** Piso do aviso na fatura anterior (acceptance ED-88; NR 13/ANA, arts. 17 a 20). */
    public static final int NOTICE_DAYS_FLOOR = 90;
    /** Teto para ligar: 1 ano (acceptance ED-88; Lei 11.445/2007, art. 45). */
    public static final int MAX_CONNECT_MONTHS_CEILING = 12;

    private AltSolutionParameters() {}

    /** Serviço de esgotamento prestado (true) ou solução apenas cadastrada (false). */
    public static boolean serviceEnabled(String json) {
        JsonNode root = tree(json, SERVICE_ENABLED);
        if (!root.isBoolean()) throw new IllegalStateException(SERVICE_ENABLED + " deve ser booleano JSON");
        return root.asBoolean();
    }

    /** Intervalo da agenda periódica, em meses. Só é lido quando o serviço é periódico. */
    public static int serviceIntervalMonths(String json) {
        JsonNode root = tree(json, SERVICE_INTERVAL_MONTHS);
        if (!root.isIntegralNumber()) throw new IllegalStateException(SERVICE_INTERVAL_MONTHS + " deve ser inteiro JSON");
        int months = root.asInt();
        if (months < 1) throw new IllegalArgumentException(SERVICE_INTERVAL_MONTHS + " deve ser ao menos 1: " + months);
        return months;
    }

    /** Código da fonte tarifária definida pela agência (texto obrigatório, sem enumeração inventada). */
    public static String tariffSource(String json) {
        JsonNode root = tree(json, TARIFF_SOURCE);
        if (!root.isString() || root.asString().isBlank())
            throw new IllegalStateException(TARIFF_SOURCE + " deve ser texto JSON nao vazio (fonte definida pela agencia)");
        return root.asString().trim();
    }

    /** Aviso na fatura anterior: classe c, piso de 90 dias — reduzir é recusado. */
    public static int noticeDays(String json) {
        JsonNode root = tree(json, NOTICE_DAYS);
        if (!root.isIntegralNumber()) throw new IllegalStateException(NOTICE_DAYS + " deve ser inteiro JSON");
        int days = root.asInt();
        if (days < NOTICE_DAYS_FLOOR)
            throw new IllegalArgumentException(NOTICE_DAYS + " (classe c): piso de " + NOTICE_DAYS_FLOOR
                    + " dias (NR 13/ANA, arts. 17 a 20); recebidos: " + days);
        return days;
    }

    /** Prazo máximo para ligar: classe c, teto de 12 meses — ampliar é recusado. */
    public static int maxConnectMonths(String json) {
        JsonNode root = tree(json, MAX_CONNECT_MONTHS);
        if (!root.isIntegralNumber()) throw new IllegalStateException(MAX_CONNECT_MONTHS + " deve ser inteiro JSON");
        int months = root.asInt();
        if (months < 1) throw new IllegalArgumentException(MAX_CONNECT_MONTHS + " deve ser ao menos 1: " + months);
        if (months > MAX_CONNECT_MONTHS_CEILING)
            throw new IllegalArgumentException(MAX_CONNECT_MONTHS + " (classe c): teto de "
                    + MAX_CONNECT_MONTHS_CEILING + " meses / 1 ano (Lei 11.445/2007, art. 45); recebidos: " + months);
        return months;
    }

    /** Dispensa só por inviabilidade comprovada: classe a, travado em true. */
    public static boolean exemptionRequiresProof(String json) {
        JsonNode root = tree(json, EXEMPTION_REQUIRES_PROOF);
        if (!root.isBoolean()) throw new IllegalStateException(EXEMPTION_REQUIRES_PROOF + " deve ser booleano JSON");
        if (!root.asBoolean())
            throw new IllegalArgumentException(EXEMPTION_REQUIRES_PROOF + " (classe a): travado em true — "
                    + "dispensa da ligacao obrigatoria so por inviabilidade comprovada");
        return true;
    }

    /**
     * A obrigação legal de conexão NÃO é desativável (teste do acceptance):
     * ela nasce da lei (Lei 11.445/2007, art. 45), não de parâmetro. Ausência
     * de chave NÃO desliga nada (a obrigação vale mesmo assim); uma tentativa
     * explícita de gravar {@code false} é recusada.
     */
    public static void requireObligationNotDisabled(String jsonOrNull) {
        if (jsonOrNull == null || jsonOrNull.isBlank()) return; // sem chave: a lei continua valendo
        JsonNode root = Json.readTree(jsonOrNull);
        if (root.isBoolean() && !root.asBoolean())
            throw new IllegalArgumentException(CONNECTION_OBLIGATION_ENABLED + ": a obrigacao legal de conexao "
                    + "a rede disponivel nao e desativavel (Lei 11.445/2007, art. 45)");
        if (!root.isBoolean())
            throw new IllegalStateException(CONNECTION_OBLIGATION_ENABLED + " deve ser booleano JSON");
    }

    private static JsonNode tree(String json, String key) {
        if (json == null || json.isBlank())
            throw new MissingParameterException(key, "conteudo JSON ausente para a ED-88");
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode()) throw new MissingParameterException(key, "conteudo JSON vazio");
        return root;
    }
}
