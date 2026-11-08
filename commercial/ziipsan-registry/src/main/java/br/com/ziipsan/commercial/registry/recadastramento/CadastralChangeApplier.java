package br.com.ziipsan.commercial.registry.recadastramento;

import java.util.UUID;

/**
 * Aplicador de UMA alteracao aprovada do recadastramento (ED-16): e a
 * UNICA porta de escrita para o cadastro desta entrega. O servico de
 * recadastramento nunca escreve {@code customers}/{@code consumer_units}
 * diretamente — ele chama esta porta uma vez por campo aprovado, com o
 * {@code serviceOrderId} da OS de lote, e a porta devolve o id do
 * registro gravado no historico do cadastro
 * ({@code cadastral_change_requests} da P1.3, com o tipo de OS do campo
 * pelo mapa da P1.31).
 *
 * <p>A implementacao de producao, na fiacao de integracao, compoe o
 * {@code CadastralChangeService.record(...)} da P1.3 com a escrita do
 * servico dono do campo ({@code CustomerService}/
 * {@code ConsumerUnitService}), na mesma transacao do encerramento do
 * efeito — o mesmo desenho que a P3.32 declarou para a correcao dela.
 * Aplicar a mesma alteracao duas vezes nao pode duplicar o efeito: a
 * garantia de "efeito aplicado uma vez" e do motor de OS da P1.31, e o
 * servico so chama esta porta para campo em estado APPROVED.
 */
public interface CadastralChangeApplier {

    /**
     * Aplica a alteracao de um campo como efeito da OS de lote.
     *
     * @return id da linha gravada em {@code cadastral_change_requests}
     */
    UUID apply(UUID serviceOrderId, String targetType, UUID targetId, String field,
               String oldValue, String newValue, UUID actorId);
}
