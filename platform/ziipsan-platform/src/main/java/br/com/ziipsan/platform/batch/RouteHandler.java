package br.com.ziipsan.platform.batch;

/**
 * P2.1: o trabalho de uma rota do lote. Implementado por cada frente
 * consumidora (ex.: a frente que processa um ciclo na P2.5); a fundação só
 * orquestra: trava, ordem, retomada, fila justa e registro.
 *
 * <p>Redação neutra de propósito: o teste de fronteira da P0.47
 * (VerticalBoundaryTest) recusa termos de vertical no código do núcleo.
 */
@FunctionalInterface
public interface RouteHandler {

    void execute(RouteExecutionContext context) throws Exception;
}
