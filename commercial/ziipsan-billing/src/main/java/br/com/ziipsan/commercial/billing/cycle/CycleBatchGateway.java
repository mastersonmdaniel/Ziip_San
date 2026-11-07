package br.com.ziipsan.commercial.billing.cycle;

import br.com.ziipsan.core.parameters.TenantTime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Porta de despacho para a fundacao de lote da P2.1, consumida pelo servico de
 * ciclo. O billing NAO reimplementa lote: trava de (tenant, ciclo), fila justa,
 * retomada de rotas e registro de disparos sao da fundacao (core V10,
 * {@code job_runs}).
 *
 * <p>O adaptador de producao delega, um para um, para a API publica da fundacao
 * ({@code BatchJobService.dispatch} do modulo de plataforma, no raiz de composicao
 * da aplicacao): a assinatura desta porta espelha a dela -- tenant, referencia do
 * ciclo, rotas, tempo do tenant, autor e agendamento -- e o resultado espelha o
 * dela: ACCEPTED com o id da execucao criada, ou REFUSED_DUPLICATE com o id da
 * execucao existente quando (tenant, ciclo) ja tem execucao (o segundo disparo
 * nunca corre, e a recusa fica registrada na fundacao). A referencia de ciclo
 * montada pelo servico ({@code CycleService.cycleRef}) e unica por referencia,
 * grupo e atividade, que e o que faz a trava da fundacao valer por grupo.
 */
public interface CycleBatchGateway {

    enum Outcome {
        ACCEPTED,
        REFUSED_DUPLICATE
    }

    record Dispatch(Outcome outcome, UUID runId) {
    }

    Dispatch dispatch(UUID tenantId, String cycleRef, List<String> routes, TenantTime tenantTime,
                      UUID triggeredBy, Instant scheduledFor);
}
