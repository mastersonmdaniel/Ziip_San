package br.com.ziipsan.integration.hub;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * P3.37 (PLT-20): porta COMUM de todo conector externo do Ziip SAN — PSP,
 * bancos, SEFAZ, NFS-e, WhatsApp, e-mail, SMS, birôs, CENPROT, gráfica e
 * ERP. Todo conector específico (ED-62 e demais) implementa ESTA porta;
 * o hub não conhece endpoint, URL nem credencial de terceiro nenhum: a
 * chamada sai pelo adaptador do parceiro, que resolve a credencial pela
 * referência do {@link ConnectorContext} no cofre da P0.45.
 *
 * <p>Contratos da porta:
 * <ul>
 *   <li>{@link #testConnection} não produz efeito externo de negócio —
 *       é o "teste de conexão" da tela;</li>
 *   <li>{@link #send} é idempotente pela chave da requisição: repetir a
 *       mesma {@code idempotencyKey} para o mesmo tenant/integração não
 *       duplica o efeito no parceiro (o adaptador declara isso ao
 *       implementar; o hub reforça com a sua loja de idempotência);</li>
 *   <li>{@link #homologationLayoutFingerprint} devolve a impressão do
 *       leiaute vigente NO AMBIENTE DE HOMOLOGAÇÃO do parceiro, para o
 *       teste de contrato diário comparar com a esperada do catálogo,
 *       por versão do parceiro — sem presumir leiaute de ninguém.</li>
 * </ul>
 */
public interface ExternalConnector {

    /** Famílias de conector do aceite da P3.37 (uma por tipo de terceiro). */
    enum Kind { PSP, BANK, SEFAZ, NFSE, WHATSAPP, EMAIL, SMS, BUREAU, CENPROT, PRINT_SHOP, ERP }

    /** Resultado de uma chamada ao parceiro. */
    enum Outcome {
        /** Efeito confirmado pelo parceiro. */
        SUCCESS,
        /** Recusa de negócio do parceiro: NÃO adianta repetir igual. */
        REJECTED,
        /** Parceiro indisponível/tempo esgotado: pode repetir (política do hub). */
        UNAVAILABLE
    }

    /** Identidade estável do conector no hub (ex.: {@code psp-pix}, {@code sefaz-nfe}). */
    String name();

    Kind kind();

    /** Versão do parceiro/API a que este adaptador foi escrito (teste de contrato é por versão). */
    String partnerVersion();

    /** Teste de conexão no contexto dado (modo e credencial do tenant). Sem efeito de negócio. */
    ConnectionTestResult testConnection(ConnectorContext context);

    /** Envia a requisição ao parceiro. Idempotente pela {@code idempotencyKey} da requisição. */
    ConnectorResponse send(ConnectorContext context, ConnectorRequest request);

    /**
     * Impressão (hash/identificador) do leiaute que o ambiente de
     * HOMOLOGAÇÃO do parceiro apresenta agora, na versão declarada.
     * O hub compara com a esperada do catálogo; divergência = mudança
     * de leiaute detectada. Nunca é lida em modo PRODUCTION.
     */
    String homologationLayoutFingerprint(ConnectorContext homologationContext);

    /** Contexto de uma chamada: tenant, modo operável e REFERÊNCIA da credencial (nunca o segredo). */
    record ConnectorContext(UUID tenantId, IntegrationMode mode, String credentialRef, String correlationId) {
        public ConnectorContext {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório no contexto do conector");
            Objects.requireNonNull(mode, "modo é obrigatório no contexto do conector");
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlation_id é obrigatório no contexto do conector");
            }
            // credentialRef pode ser nulo para conectores sem credencial
            // (ex.: consulta pública); quem exige credencial recusa no uso.
        }
    }

    /** Requisição ao parceiro: operação do conector + carga JSON + chaves de correlação/idempotência. */
    record ConnectorRequest(String correlationId, String idempotencyKey, String operation, String payloadJson) {
        public ConnectorRequest {
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlation_id é obrigatório na requisição ao conector");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("chave de idempotência é obrigatória na requisição ao conector");
            }
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("operação é obrigatória na requisição ao conector");
            }
            if (payloadJson == null) {
                throw new IllegalArgumentException("carga da requisição é obrigatória (pode ser \"{}\")");
            }
        }
    }

    /** Resposta do parceiro: desfecho, identificador externo (se houver) e carga JSON de retorno. */
    record ConnectorResponse(Outcome outcome, String externalId, String payloadJson, String detail) {
        public ConnectorResponse {
            Objects.requireNonNull(outcome, "desfecho da resposta do conector é obrigatório");
        }

        public boolean isSuccess() {
            return outcome == Outcome.SUCCESS;
        }
    }

    /** Resultado do teste de conexão: alcançou/autenticou ou não, latência medida e instante. */
    record ConnectionTestResult(boolean reachable, long latencyMillis, String detail, Instant at) {
        public ConnectionTestResult {
            if (latencyMillis < 0) {
                throw new IllegalArgumentException("latência do teste de conexão não pode ser negativa: " + latencyMillis);
            }
            Objects.requireNonNull(at, "instante do teste de conexão é obrigatório");
        }
    }
}
