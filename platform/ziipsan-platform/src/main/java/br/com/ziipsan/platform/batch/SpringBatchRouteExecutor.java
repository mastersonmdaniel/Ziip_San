package br.com.ziipsan.platform.batch;

import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * P2.1: cada rota corre como um Job Spring Batch, com o JobRepository
 * JDBC das tabelas BATCH_* da core V10.
 *
 * <p>Spring Batch 6 (Boot 4.1): as classes de job vivem em
 * {@code core.job} / {@code core.job.builder} / {@code core.job.parameters},
 * não mais em {@code core} (API do Batch 5) — importar do pacote antigo
 * não compila.
 *
 * <p>Parâmetros identificadores do job: tenant, ciclo, rota e run. A
 * retomada de uma rota FAILED reusa a mesma instância do Batch (a
 * semântica de restart do framework) e cria uma nova execução — o
 * contador de tentativas de negócio continua em
 * {@code job_run_routes.attempts}, não no Batch. Uma rota DONE nunca é
 * relançada pelo serviço, então o Batch nunca recebe um
 * JobInstanceAlreadyComplete por esse caminho.
 *
 * <p>O lançador é síncrono (SyncTaskExecutor): a tarefa da execução
 * espera a rota terminar antes de passar à próxima, o que preserva a
 * ordem das rotas dentro do lote. O paralelismo vive no
 * {@link FairBatchDispatcher}, entre lotes de tenants diferentes.
 */
public final class SpringBatchRouteExecutor implements BatchRouteExecutor {

    /** Nome único do job de rota no repositório do Batch. */
    public static final String JOB_NAME = "ziipsan-batch-route";

    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;
    private final PlatformTransactionManager transactionManager;

    public SpringBatchRouteExecutor(DataSource dataSource) throws Exception {
        Objects.requireNonNull(dataSource, "DataSource é obrigatório");
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);
        factory.setDatabaseType("POSTGRES");
        factory.afterPropertiesSet();
        this.jobRepository = factory.getObject();
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SyncTaskExecutor());
        launcher.afterPropertiesSet();
        this.jobLauncher = launcher;
    }

    @Override
    public void execute(RouteExecutionContext context, RouteHandler handler) throws Exception {
        Objects.requireNonNull(context, "O contexto é obrigatório");
        Objects.requireNonNull(handler, "O handler é obrigatório");
        var step = new StepBuilder("route-step", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    handler.execute(context);
                    return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
        Job job = new JobBuilder(JOB_NAME, jobRepository).start(step).build();
        var parameters = new JobParametersBuilder()
                .addString("tenantId", context.tenantId().toString())
                .addString("cycleRef", context.cycleRef())
                .addString("route", context.route())
                .addString("runId", context.runId().toString())
                .addString("referenceDate", context.referenceDate().toString())
                .addString("timezone", context.timezone().getId())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(job, parameters);
        if (execution.getStatus() != org.springframework.batch.core.BatchStatus.COMPLETED) {
            String exit = execution.getExitStatus() == null
                    ? execution.getStatus().toString() : execution.getExitStatus().getExitDescription();
            throw new RouteExecutionException(
                    "Rota " + context.route() + " do ciclo " + context.cycleRef()
                            + " terminou com status " + execution.getStatus()
                            + (exit == null || exit.isBlank() ? "" : ": " + exit));
        }
    }

    /** Falha de uma rota dentro do Batch (status final diferente de COMPLETED). */
    public static final class RouteExecutionException extends Exception {
        public RouteExecutionException(String message) {
            super(message);
        }
    }
}
