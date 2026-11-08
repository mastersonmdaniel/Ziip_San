package br.com.ziipsan.integration.erp;

import br.com.ziipsan.integration.hub.ExternalConnector;

import java.time.Instant;
import java.util.Objects;

/**
 * ED-62: conector ERP sobre a porta COMUM do hub da P3.37
 * ({@link ExternalConnector}, kind ERP). Um conector por fornecedor
 * (Betha, IPM, Fiorilli); todos compartilham esta implementacao — o que
 * muda e o {@link ErpLayoutMapping} declarado e a versao de leiaute
 * declarada nos parametros do tenant ({@link ErpParameters}).
 *
 * <p><b>Transporte:</b> a chamada real (API HTTP ou arquivo, conforme
 * {@code erp.<p>.transport}) sai pela porta {@link ErpTransport} da
 * fiacao, que resolve endpoint e credencial pelo cofre da P0.45 via hub.
 * NENHUM endpoint, leiaute ou credencial de Betha/IPM/Fiorilli esta
 * escrito aqui: fornecedores reais sem leiaute nas fontes locais. A
 * prova contra leiaute publicado/ambiente do fornecedor e a pendencia
 * externa B1 do relatorio; nos testes, o transporte e um DUBLE HONESTO
 * declarado ({@code FakeErpTransport}, so em src/test).
 */
public final class ErpConnector implements ExternalConnector {

    /** Porta do transporte real (API ou arquivo) — implementada na fiacao por fornecedor. */
    public interface ErpTransport {
        ConnectorResponse send(ConnectorContext context, String operation, String renderedPayload);
        ConnectionTestResult ping(ConnectorContext context);
        /** Impressao do leiaute vigente na HOMOLOGACAO do fornecedor (teste de contrato diario da P3.37). */
        String homologationFingerprint(ConnectorContext homologationContext);
    }

    private final ErpProvider provider;
    private final String partnerVersion;
    private final ErpTransport transport;

    public ErpConnector(ErpProvider provider, String partnerVersion, ErpTransport transport) {
        this.provider = Objects.requireNonNull(provider, "fornecedor ERP e obrigatorio");
        if (partnerVersion == null || partnerVersion.isBlank()) {
            throw new IllegalArgumentException("versao do parceiro/leiaute DECLARADA e obrigatoria (erp.*.layout_version): nenhuma versao de Betha/IPM/Fiorilli e presumida");
        }
        this.partnerVersion = partnerVersion;
        this.transport = Objects.requireNonNull(transport, "transporte ERP da fiacao e obrigatorio");
    }

    @Override public String name() { return provider.connectorName(); }
    @Override public Kind kind() { return Kind.ERP; }
    @Override public String partnerVersion() { return partnerVersion; }
    public ErpProvider provider() { return provider; }

    @Override
    public ConnectionTestResult testConnection(ConnectorContext context) {
        Objects.requireNonNull(context, "contexto e obrigatorio");
        long start = System.nanoTime();
        ConnectionTestResult raw = transport.ping(context);
        Objects.requireNonNull(raw, "transporte devolveu teste de conexao nulo");
        long measured = Math.max(0, (System.nanoTime() - start) / 1_000_000);
        return new ConnectionTestResult(raw.reachable(), measured, raw.detail(), Instant.now());
    }

    @Override
    public ConnectorResponse send(ConnectorContext context, ConnectorRequest request) {
        Objects.requireNonNull(context, "contexto e obrigatorio");
        Objects.requireNonNull(request, "requisicao e obrigatoria");
        // A carga que chega aqui ja foi renderizada no leiaute DECLARADO
        // pelo ErpExportService; o conector nao traduz nem inventa campo.
        return transport.send(context, request.operation(), request.payloadJson());
    }

    @Override
    public String homologationLayoutFingerprint(ConnectorContext homologationContext) {
        Objects.requireNonNull(homologationContext, "contexto de homologacao e obrigatorio");
        return transport.homologationFingerprint(homologationContext);
    }
}
