package br.com.ziipsan.platform.clientapp;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Push do app do cliente SEM dado pessoal (ED-39, sobre os alertas ao
 * consumidor da ED-17 — GES-27): o payload que sai para o servico de
 * push (APNs/FCM) carrega SOMENTE codigos — nunca texto livre.
 *
 * <p>Por construcao: titulo e corpo sao CHAVES DE MODELO de um conjunto
 * FECHADO, resolvidas no aparelho (o texto final, generico, vive no
 * app); o deep link e um caminho de tela de uma lista fechada, sem
 * identificador de unidade, matricula ou documento; e o codigo de
 * alerta e um dos codigos da ED-17/P3.5.
 *
 * <p><b>Correcao D1 da revisao cruzada:</b> na versao original,
 * {@code titleKey}/{@code bodyKey} aceitavam QUALQUER texto que nao
 * casasse com os regexes de CPF/e-mail/telefone/valor — nome de pessoa
 * ("Ola Maria Silva, sua conta venceu") e endereco ("Rua das Flores
 * 123") PASSAVAM, e a garantia "sem dado pessoal" era furavel. Agora a
 * validacao e em camadas, nesta ordem:
 * <ol>
 *   <li>conjunto de chaves do payload fechado
 *       ({@code alertCode}/{@code titleKey}/{@code bodyKey}/{@code deepLink});</li>
 *   <li>{@code alertCode} em lista fechada e {@code deepLink} em lista
 *       fechada;</li>
 *   <li>{@code titleKey}/{@code bodyKey}, quando presentes, DEVEM ser
 *       exatamente a chave de modelo do alerta correspondente
 *       ({@code push.<modelo>.title} / {@code push.<modelo>.body}, do
 *       mapa fechado {@link #TEMPLATE_STEM_BY_ALERT_CODE}) — texto
 *       livre, nome, endereco ou chave de modelo de OUTRO alerta sao
 *       recusados;</li>
 *   <li>como defesa adicional (nao como garantia principal), qualquer
 *       valor com cara de dado pessoal (CPF, e-mail, telefone, valor
 *       monetario) continua recusado em qualquer chave.</li>
 * </ol>
 * Espelhado no TypeScript do app ({@code cliente/src/push/pushPayload.ts})
 * com os mesmos vetores de teste, incluindo os vetores de nome e
 * endereco da revisao.
 */
public final class PushPayloadSanitizer {

    /** Codigos de alerta ao consumidor: os da ED-17 (telemetria) e os genericos do portal. */
    public static final Set<String> ALLOWED_ALERT_CODES = Set.of(
            "telemetry.meter-alarm",
            "telemetry.continuous-consumption",
            "telemetry.peak-consumption",
            "bill.available",
            "outage.scheduled",
            "outage.restored");

    /**
     * Mapa FECHADO alerta -> modelo de texto: para cada codigo de alerta,
     * o UNICO par titleKey/bodyKey aceito e
     * {@code push.<modelo>.title} / {@code push.<modelo>.body}.
     * Os textos genericos desses modelos vivem no app
     * (LOCAL_PUSH_TEXTS do espelho TS, indexados pelo alertCode).
     */
    public static final Map<String, String> TEMPLATE_STEM_BY_ALERT_CODE = Map.of(
            "telemetry.meter-alarm", "meter-alarm",
            "telemetry.continuous-consumption", "continuous",
            "telemetry.peak-consumption", "peak",
            "bill.available", "bill",
            "outage.scheduled", "outage-scheduled",
            "outage.restored", "outage-restored");

    /** Todas as chaves de modelo aceitas (derivadas do mapa fechado acima). */
    public static final Set<String> ALLOWED_TEMPLATE_KEYS = Set.of(
            "push.meter-alarm.title", "push.meter-alarm.body",
            "push.continuous.title", "push.continuous.body",
            "push.peak.title", "push.peak.body",
            "push.bill.title", "push.bill.body",
            "push.outage-scheduled.title", "push.outage-scheduled.body",
            "push.outage-restored.title", "push.outage-restored.body");

    /** Telas que um push pode abrir: nenhuma carrega identificador na URL. */
    public static final Set<String> ALLOWED_DEEP_LINKS = Set.of(
            "/consumo", "/conta", "/avisos", "/inicio");

    private static final Set<String> ALLOWED_KEYS = Set.of("alertCode", "titleKey", "bodyKey", "deepLink");

    private static final Pattern CPF = Pattern.compile("\\d{3}[.]\\d{3}[.]\\d{3}-\\d{2}|\\b\\d{11}\\b");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+[.][^\\s@]+");
    private static final Pattern MONEY = Pattern.compile("R\\$\\s?[\\d.,]+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s()\\-]{7,}\\d");

    private PushPayloadSanitizer() {
    }

    /** O payload saneado, pronto para APNs/FCM. */
    public record PushPayload(String alertCode, String titleKey, String bodyKey, String deepLink) {
    }

    /**
     * Valida e reduz o payload bruto ao contrato sem dado pessoal.
     * Recusa (IllegalArgumentException) qualquer desvio — push com dado
     * pessoal nao e "corrigido": nao sai.
     */
    public static PushPayload sanitize(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("payload de push obrigatorio");
        }
        for (String key : raw.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("chave fora do contrato de push sem dado pessoal: " + key);
            }
        }
        String alertCode = raw.get("alertCode");
        if (alertCode == null || !ALLOWED_ALERT_CODES.contains(alertCode)) {
            throw new IllegalArgumentException("codigo de alerta fora da lista do push: " + alertCode);
        }
        String deepLink = raw.get("deepLink");
        if (deepLink != null && !ALLOWED_DEEP_LINKS.contains(deepLink)) {
            throw new IllegalArgumentException("deep link fora das telas permitidas do push: " + deepLink);
        }
        // Camada principal (correcao D1): titleKey/bodyKey so podem ser a
        // chave de modelo FECHADA do proprio alerta — nunca texto livre.
        String stem = TEMPLATE_STEM_BY_ALERT_CODE.get(alertCode);
        String titleKey = raw.get("titleKey");
        if (titleKey != null && !titleKey.equals("push." + stem + ".title")) {
            throw new IllegalArgumentException(
                    "titleKey fora do modelo fechado do alerta " + alertCode + ": " + titleKey);
        }
        String bodyKey = raw.get("bodyKey");
        if (bodyKey != null && !bodyKey.equals("push." + stem + ".body")) {
            throw new IllegalArgumentException(
                    "bodyKey fora do modelo fechado do alerta " + alertCode + ": " + bodyKey);
        }
        // Defesa adicional: regexes de dado pessoal em qualquer valor.
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String v = e.getValue();
            if (v == null) {
                continue;
            }
            if (CPF.matcher(v).find() || EMAIL.matcher(v).find()
                    || MONEY.matcher(v).find() || PHONE.matcher(v).find()) {
                throw new IllegalArgumentException(
                        "valor com cara de dado pessoal recusado no push (chave " + e.getKey() + ")");
            }
        }
        return new PushPayload(alertCode, titleKey, bodyKey, deepLink);
    }
}
