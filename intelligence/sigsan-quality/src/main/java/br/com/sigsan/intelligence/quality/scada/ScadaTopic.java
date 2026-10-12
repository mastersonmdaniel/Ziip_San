package br.com.sigsan.intelligence.quality.scada;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tópico MQTT de telemetria: {@code sigsan/{tenantUuid}/scada/{data|alert}/{tag}}.
 *
 * <p>O tenant vem do tópico porque o callback do cliente MQTT roda numa thread do Paho, sem requisição
 * HTTP nem contexto de tenant. A versão anterior assinava {@code sigsan/+/quality/+} mas esperava
 * {@code data}/{@code alert} na terceira posição (nenhuma mensagem casava) e comparava o tenant do
 * tópico com o contexto da thread, que no callback é sempre vazio: toda mensagem era descartada.
 *
 * <p>Classe pura, sem Spring nem Paho, para ser testada isoladamente.
 */
public record ScadaTopic(UUID tenantId, Type type, String tag) {

    /** Filtro de assinatura que casa exatamente com o formato aceito por {@link #parse}. */
    public static final String SUBSCRIPTION = "sigsan/+/scada/+/+";

    // Mesmo alfabeto do nome de tag no cadastro; barra e curingas MQTT (+, #) ficam de fora.
    private static final Pattern TAG = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");

    public enum Type { DATA, ALERT }

    /**
     * Interpreta o tópico. Formato diferente, tenant que não é UUID, tipo desconhecido ou tag com
     * caractere fora do alfabeto devolvem vazio: quem chama descarta a mensagem.
     */
    public static Optional<ScadaTopic> parse(String topic) {
        if (topic == null) {
            return Optional.empty();
        }
        String[] parts = topic.split("/", -1);
        if (parts.length != 5 || !"sigsan".equals(parts[0]) || !"scada".equals(parts[2])) {
            return Optional.empty();
        }
        UUID tenant = parseUuid(parts[1]);
        Type type = switch (parts[3]) {
            case "data" -> Type.DATA;
            case "alert" -> Type.ALERT;
            default -> null;
        };
        if (tenant == null || type == null || !TAG.matcher(parts[4]).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ScadaTopic(tenant, type, parts[4]));
    }

    /**
     * Tópico de comando para uma tag: {@code sigsan/{tenantUuid}/command/{tag}}. Fica fora de
     * {@link #SUBSCRIPTION} de propósito, para o serviço não receber de volta o que publica.
     */
    public static String command(UUID tenantId, String tag) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Comando SCADA sem tenant");
        }
        if (tag == null || !TAG.matcher(tag).matches()) {
            throw new IllegalArgumentException("Tag SCADA inválida para comando");
        }
        return "sigsan/" + tenantId + "/command/" + tag;
    }

    // UUID.fromString aceita formas abreviadas ("1-1-1-1-1") e maiúsculas; o tópico exige a forma
    // canônica em minúsculas, para que o mesmo tenant não apareça com dois nomes nas ACLs do broker.
    private static UUID parseUuid(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            return id.toString().equals(raw) ? id : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
