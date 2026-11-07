package br.com.ziipsan.reporting.registry;

import java.util.Set;
import java.util.UUID;

/**
 * Mecanismo de registro de relatorios de modulos externos - e por aqui que a
 * P3.31 (certificacoes: mapa de clausulas, pendencias e vencimentos) registra
 * os relatorios dela no nucleo, sem tocar no catalogo de fabrica. A P3.22
 * entrega SO o mecanismo; os relatorios da certificacao sao da P3.31.
 *
 * <p>Regras: origem CERTIFICATION ou EXTERNAL, codigo sem colisao, view no
 * padrao {@code rpt_*}, permissao {@code <prefixo>.<codigo>:export}
 * (validada na propria ReportDefinition, coerente com o canExport) e teto
 * de linhas positivo. Definicao invalida e recusada na hora, nunca na
 * execucao.
 *
 * <p>CORRECAO G5 (pos-revisao): o registro e POR TENANT e PERSISTIDO em
 * report_definitions com o tenant_id (via ReportRegistry/
 * ExternalDefinitionStore). A versao anterior guardava so num mapa em
 * memoria global: sumia no restart, um tenant enxergava o registro do
 * outro e o javadoc afirmava uma gravacao que nao existia.
 */
public final class ExternalReportRegistrar {

    private static final Set<ReportOrigin> ALLOWED = Set.of(ReportOrigin.CERTIFICATION, ReportOrigin.EXTERNAL);

    private final ReportRegistry registry;

    public ExternalReportRegistrar(ReportRegistry registry) {
        this.registry = registry;
    }

    public ReportDefinition register(UUID tenantId, ReportDefinition definition) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Registro externo exige o tenant dono");
        }
        if (definition == null) {
            throw new IllegalArgumentException("Definicao obrigatoria");
        }
        if (!ALLOWED.contains(definition.origin())) {
            throw new IllegalArgumentException(
                    "Registro externo nao aceita origem " + definition.origin()
                            + " (BUILTIN e do catalogo de fabrica)");
        }
        if (registry.find(definition.code(), tenantId).isPresent()) {
            throw new IllegalStateException("Codigo ja registrado: " + definition.code());
        }
        registry.registerExternal(tenantId, definition);
        return definition;
    }
}
