package br.com.ziipsan.platform.batch;

/**
 * P2.1: executa UMA rota dentro do Spring Batch. A implementação de
 * produção ({@link SpringBatchRouteExecutor}) lança um Job por rota, com
 * o JobRepository JDBC das tabelas BATCH_* da V10; testes usam um
 * executor falso, sem banco.
 */
public interface BatchRouteExecutor {

    /**
     * Executa a rota. Se o handler falhar, lança exceção (o serviço marca
     * a rota FAILED e aborta a execução, deixando as rotas restantes
     * PENDING para a retomada).
     */
    void execute(RouteExecutionContext context, RouteHandler handler) throws Exception;
}
