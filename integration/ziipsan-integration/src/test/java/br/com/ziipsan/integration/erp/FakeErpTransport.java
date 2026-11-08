package br.com.ziipsan.integration.erp;

import br.com.ziipsan.integration.hub.ExternalConnector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DUBLE HONESTO declarado (somente testes): nao e um ERP de verdade, nao
 * fala com Betha/IPM/Fiorilli e nao simula leiaute de fornecedor nenhum.
 * Apenas registra as cargas renderizadas no mapeamento DECLARADO do
 * teste e devolve o desfecho programado, para provar o nucleo da ED-62
 * (regras, ganchos, portas e idempotencia). A prova contra o ambiente
 * real do fornecedor e a pendencia externa B1 do relatorio.
 */
final class FakeErpTransport implements ErpConnector.ErpTransport {
    final List<String> sentPayloads = new ArrayList<>();
    final List<String> sentOperations = new ArrayList<>();
    ExternalConnector.Outcome outcome = ExternalConnector.Outcome.SUCCESS;
    String fingerprint = "declared-test-fingerprint";
    int pingCalls = 0;

    @Override
    public ExternalConnector.ConnectorResponse send(ExternalConnector.ConnectorContext context, String operation, String renderedPayload) {
        sentOperations.add(operation);
        sentPayloads.add(renderedPayload);
        return new ExternalConnector.ConnectorResponse(outcome, outcome == ExternalConnector.Outcome.SUCCESS ? "ext-1" : null, "{}", "fake");
    }

    @Override
    public ExternalConnector.ConnectionTestResult ping(ExternalConnector.ConnectorContext context) {
        pingCalls++;
        return new ExternalConnector.ConnectionTestResult(true, 1, "fake-ping", Instant.now());
    }

    @Override
    public String homologationFingerprint(ExternalConnector.ConnectorContext homologationContext) {
        return fingerprint;
    }
}
