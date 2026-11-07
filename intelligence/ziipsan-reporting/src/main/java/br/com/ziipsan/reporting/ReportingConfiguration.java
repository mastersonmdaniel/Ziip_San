package br.com.ziipsan.reporting;

import br.com.ziipsan.reporting.domain.JpaReportExecutionRecorder;
import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.domain.ReportExecutionRepository;
import br.com.ziipsan.reporting.domain.ReportScheduleRepository;
import br.com.ziipsan.reporting.export.ExportAuditRecorder;
import br.com.ziipsan.reporting.export.GeneratedFileDelivery;
import br.com.ziipsan.reporting.export.JdbcGeneratedFileDelivery;
import br.com.ziipsan.reporting.export.PersonalDataAccessExportAuditRecorder;
import br.com.ziipsan.reporting.export.ReportExportService;
import br.com.ziipsan.reporting.query.JdbcReportQueryExecutor;
import br.com.ziipsan.reporting.query.ReportDataSourceRouter;
import br.com.ziipsan.reporting.registry.ExternalDefinitionStore;
import br.com.ziipsan.reporting.registry.ExternalReportRegistrar;
import br.com.ziipsan.reporting.registry.JdbcExternalDefinitionStore;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.schedule.ReportScheduleService;
import br.com.ziipsan.reporting.schedule.ReportWorker;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Fiacao completa do nucleo (correcao R1 pos-revisao): TODOS os beans de
 * infraestrutura do modulo sao declarados aqui - router/executor, store
 * de registros externos, registry, registrar, entrega em generated_files,
 * auditoria, gravador de execucoes, servico de exportacao, escritores,
 * servico de agendamento e o worker (agendados + fila ad-hoc). As classes
 * de infraestrutura nao tem mais @Component: esta configuracao e a unica
 * fiacao, para nao haver bean duplicado quando o app varre o pacote.
 *
 * <p>O que AINDA depende do ziipsan-app (declarado com precisao):
 * <ul>
 *   <li>component-scan/import desta configuracao e do ReportController,
 *       mais @EnableJpaRepositories sobre br.com.ziipsan.reporting.domain;</li>
 *   <li>@EnableScheduling (sem ele os @Scheduled do worker nao disparam);</li>
 *   <li>o atributo de requisicao {@code ziipsan.reportUser} populado pela
 *       seguranca da borda (sem ele o controller recusa, nao fabrica);</li>
 *   <li>opcional: bean {@code readReplicaDataSource} (replica de leitura)
 *       e substituicoes dos resolvedores de identidade conservadores
 *       abaixo (beans do app com o mesmo tipo prevalecem -
 *       @ConditionalOnMissingBean).</li>
 * </ul>
 *
 * <p>HONESTIDADE ARQUITETURAL (correcao G3): este modulo NAO usa a fiacao
 * de exportacao que mora no ziipsan-app - um modulo nao pode depender do
 * app. A entrega/auditoria daqui e uma SEGUNDA implementacao, escrita
 * contra o mesmo contrato da P1.12 (generated_files da V3_11 +
 * personal_data_access_log do core). O risco de divergencia entre as
 * duas implementacoes esta registrado no relatorio da tarefa.
 */
@Configuration
public class ReportingConfiguration {

    @Bean
    ReportDataSourceRouter reportDataSourceRouter(DataSource dataSource,
                                                  @Qualifier("readReplicaDataSource")
                                                  org.springframework.beans.factory.ObjectProvider<DataSource> replica) {
        return new ReportDataSourceRouter(dataSource, replica.getIfAvailable());
    }

    @Bean
    JdbcReportQueryExecutor jdbcReportQueryExecutor(ReportDataSourceRouter router) {
        return new JdbcReportQueryExecutor(router);
    }

    @Bean
    ExternalDefinitionStore externalDefinitionStore(EntityManager entityManager) {
        return new JdbcExternalDefinitionStore(entityManager);
    }

    @Bean
    ReportRegistry reportRegistry(ExternalDefinitionStore store) {
        return new ReportRegistry(store);
    }

    @Bean
    ExternalReportRegistrar externalReportRegistrar(ReportRegistry registry) {
        return new ExternalReportRegistrar(registry);
    }

    @Bean
    GeneratedFileDelivery generatedFileDelivery(EntityManager entityManager) {
        return new JdbcGeneratedFileDelivery(entityManager);
    }

    @Bean
    ExportAuditRecorder exportAuditRecorder(EntityManager entityManager) {
        return new PersonalDataAccessExportAuditRecorder(entityManager);
    }

    @Bean
    ReportExecutionRecorder reportExecutionRecorder(ReportExecutionRepository executions) {
        return new JpaReportExecutionRecorder(executions);
    }

    @Bean
    ReportExportService reportExportService(JdbcReportQueryExecutor executor,
                                            ExportAuditRecorder audit,
                                            GeneratedFileDelivery delivery,
                                            ReportExecutionRecorder executions,
                                            java.util.List<br.com.ziipsan.reporting.export.ReportWriter> writers) {
        return new ReportExportService(executor, audit, delivery, executions, writers);
    }

    @Bean
    br.com.ziipsan.reporting.export.ReportWriter csvReportWriter() {
        return new br.com.ziipsan.reporting.export.CsvReportWriter();
    }

    @Bean
    br.com.ziipsan.reporting.export.ReportWriter xlsxReportWriter() {
        return new br.com.ziipsan.reporting.export.XlsxReportWriter();
    }

    @Bean
    br.com.ziipsan.reporting.export.ReportWriter pdfReportWriter() {
        return new br.com.ziipsan.reporting.export.PdfReportWriter();
    }

    @Bean
    ReportScheduleService reportScheduleService(ReportScheduleRepository schedules, ReportRegistry registry) {
        return new ReportScheduleService(schedules, registry);
    }

    /** Padrao conservador: sem a fiacao de identidade do app, agendados sao
     *  negados e registrados (mascara total, nenhuma permissao). */
    @Bean
    @ConditionalOnMissingBean(ReportWorker.ScheduleOwnerResolver.class)
    ReportWorker.ScheduleOwnerResolver conservativeScheduleOwnerResolver() {
        return ReportWorker::conservativeContext;
    }

    /** Padrao conservador para a fila ad-hoc: sem identidade, sem permissao. */
    @Bean
    @ConditionalOnMissingBean(br.com.ziipsan.reporting.spi.ReportUserContextResolver.class)
    br.com.ziipsan.reporting.spi.ReportUserContextResolver conservativeUserContextResolver() {
        return (tenantId, userId) -> new ReportUserContext(tenantId, userId,
                Set.of(), ReportUserContext.MaskCeiling.FULL, Set.of());
    }

    @Bean
    ReportWorker reportWorker(ReportScheduleRepository schedules, ReportExecutionRepository executions,
                              ReportExecutionRecorder recorder, ReportRegistry registry,
                              ReportExportService exportService, GeneratedFileDelivery delivery,
                              ReportWorker.ScheduleOwnerResolver ownerResolver,
                              br.com.ziipsan.reporting.spi.ReportUserContextResolver userContextResolver) {
        return new ReportWorker(schedules, executions, recorder, registry, exportService, delivery,
                ownerResolver, userContextResolver);
    }
}
