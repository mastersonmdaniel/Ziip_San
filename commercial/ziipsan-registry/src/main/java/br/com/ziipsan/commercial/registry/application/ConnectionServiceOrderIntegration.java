package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Integração da ED-13 com o motor de OS (P1.31): o que o módulo de
 * cadastro registra numa instância de {@code ServiceOrderService} para
 * as ligações virarem alvo e efeito de OS de verdade.
 *
 * <p>Sem esta integração, nenhuma OS de ligação pode sequer ser
 * DECLARADA: o motor só registra de fábrica o verificador de
 * {@code CONSUMER_UNIT}, e {@code declareType}/{@code addTarget}
 * recusam classe sem verificador ({@code no-verifier}). Por isso aqui
 * vão, juntos:
 * <ol>
 *   <li>os VERIFICADORES de {@code WATER_CONNECTION}/
 *     {@code SEWER_CONNECTION} — com checagem de TIPO × CLASSE sobre
 *     {@code service_connections} (mais forte que o {@code sqlVerifier}
 *     genérico, que só confere existência): o alvo existe, é do tenant,
 *     NÃO está encerrado e o kind da ligação é o da classe do alvo;</li>
 *   <li>os HANDLERS dos 6 efeitos ({@link ConnectionEffectHandler});</li>
 *   <li>as DECLARAÇÕES dos 6 tipos de serviço ({@code declareType}),
 *     cada uma com o {@code effect_schema} da sua carga —
 *     {@code statusCode} OBRIGATÓRIO em todas.</li>
 * </ol>
 *
 * <p>As declarações são o desenho de fábrica proposto pela ED-13:
 * código do tipo = código do efeito, indicador FIELD (trabalho de
 * campo), sem preço (preço é da ED-34, {@code priceItemRef} nulo) e
 * sem exigências de SST próprias (os controles de fábrica do motor
 * nascem das flags do tipo, todas desligadas aqui). Prazo, aprovação e
 * preço são decisão do tenant ao declarar/ajustar o tipo na
 * integração — nada disso é presumido nesta entrega.
 */
public final class ConnectionServiceOrderIntegration {

    private static final Pattern SCHEMA_NAME = Pattern.compile("^[a-z][a-z0-9_]*$");

    private ConnectionServiceOrderIntegration() {
    }

    /** Registro completo numa instância do motor (verificadores + handlers). */
    public static void registerAll(ServiceOrderService service, String schema) {
        registerVerifiers(service.verifiers(), schema);
        ConnectionEffectHandler.registerAll(service.effects());
    }

    /** Verificadores de existência com checagem kind × classe de alvo. */
    public static void registerVerifiers(TargetVerifierRegistry verifiers, String schema) {
        if (schema == null || !SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("schema invalido para verificador de ligacao: " + schema);
        }
        verifiers.register(TargetType.WATER_CONNECTION,
                kindVerifier(schema, ConnectionStatus.Kind.WATER));
        verifiers.register(TargetType.SEWER_CONNECTION,
                kindVerifier(schema, ConnectionStatus.Kind.SEWER));
    }

    /**
     * O alvo existe para o tenant quando há ligação NÃO ENCERRADA com
     * esse id e com o kind da classe (WATER_CONNECTION só aceita
     * ligação de água; SEWER_CONNECTION, só de esgoto).
     */
    public static TargetVerifierRegistry.TargetExistenceVerifier kindVerifier(
            String schema, ConnectionStatus.Kind expectedKind) {
        String sql = "SELECT kind FROM " + schema + ".service_connections"
                + " WHERE tenant_id = ? AND id = ? AND ended_on IS NULL";
        return (connection, tenantId, targetId) -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, targetId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && expectedKind.name().equals(rs.getString(1));
                }
            }
        };
    }

    /** As 6 declarações de tipo de serviço, prontas para {@code declareType}. */
    public static List<ServiceOrderService.ServiceTypeInput> serviceTypeDeclarations() {
        return java.util.Arrays.stream(ConnectionOperation.values())
                .map(ConnectionServiceOrderIntegration::declaration)
                .toList();
    }

    private static ServiceOrderService.ServiceTypeInput declaration(ConnectionOperation op) {
        List<TargetType> targetClasses = op.allowsKind(ConnectionStatus.Kind.WATER)
                ? List.of(TargetType.WATER_CONNECTION, TargetType.SEWER_CONNECTION)
                : List.of(TargetType.SEWER_CONNECTION);
        return new ServiceOrderService.ServiceTypeInput(
                op.effectCode(), typeName(op), op.name(), op.effectCode(), effectSchemaJson(op),
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                targetClasses, null, null, null,
                false, false, false, false, List.of());
    }

    private static String typeName(ConnectionOperation op) {
        return switch (op) {
            case CONNECT -> "Ligacao de agua/esgoto";
            case CUT -> "Corte de ligacao";
            case SUPPRESS -> "Supressao de ligacao";
            case RESTORE -> "Restabelecimento de ligacao";
            case RECONNECT -> "Religacao de ligacao";
            case PLUG -> "Tamponamento de ramal de esgoto";
        };
    }

    /**
     * Schema da carga de cada efeito (formato do
     * {@code EffectPayloadValidator}): {@code statusCode} obrigatório
     * em todos; a supressão declara o portão de débito/permissão; o
     * corte/supressão declaram {@code sewerStatusCode} (a cascata
     * AUTOMATIC o exige na carga quando há esgoto vigente — exigência
     * condicional que o validador não expressa e o handler impõe).
     */
    static String effectSchemaJson(ConnectionOperation op) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("statusCode", "string");
        properties.put("reason", "string");
        if (op == ConnectionOperation.SUPPRESS) {
            properties.put("suppressionPermissionGranted", "boolean");
            properties.put("hasOpenDebt", "boolean");
        }
        if (op.triggersSewerOutOfUseCascade()) {
            properties.put("sewerStatusCode", "string");
        }
        return Json.write(Map.of(
                "required", List.of("statusCode"),
                "properties", properties));
    }
}
