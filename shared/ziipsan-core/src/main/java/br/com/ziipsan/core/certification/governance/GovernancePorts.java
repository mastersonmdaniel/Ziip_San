package br.com.ziipsan.core.certification.governance;

import java.util.List; import java.util.Optional; import java.util.UUID;
/** Portas para dependencias de outras tarefas (sem acoplar modulos). */
public final class GovernancePorts {
    private GovernancePorts(){}
    /** Registro de indicadores da P3.41. */
    public interface IndicatorCatalog { boolean exists(UUID tenantId, String indicatorCode); }
    /** Motor de modelos da P2.30: recebe o payload JSON (montado so via Json do core) e devolve a referencia do relatorio gerado. */
    public interface AuditReportTemplateEngine { String render(String templateCode, String payloadJson); }
    /** Catalogo de fabrica do pacote sanitation: conteudo vem do pacote; esta tarefa nao inventa requisitos. */
    public interface SanitationLegalCatalog { List<LegalRequirement> factoryRequirements(UUID tenantId); }
    /** Parametros do tenant (registro reg.*): resolve o valor declarado pelo tenant para um nome de
     *  parametro — por exemplo reg.incident_report_channel (aceite P3.31, correcao D6). O valor nunca
     *  e embutido neste pacote; quem resolve e a fiacao com o registro de parametros. */
    public interface TenantParameters { Optional<String> resolve(UUID tenantId, String parameterName); }
}
