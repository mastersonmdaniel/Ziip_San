package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem da outbox da P1.9 (core V8) como a caixa a consome: {@code type} é o nome do
 * evento, {@code payload} é JSON sem dado pessoal (só identificadores e códigos, regra
 * da P1.38) lido SEMPRE pela classe {@code Json} do core. O {@code id} é a identidade da
 * mensagem: a ingestão o grava em {@code work_items.source_message_id} e a reentrega da
 * mesma mensagem de ABERTURA devolve o item existente, sem duplicar.
 *
 * <p><b>Convenção DESTA porta (declaração explícita, como na P3.36):</b> os campos que a
 * caixa exige DENTRO da carga — ver {@link WorkItemIngestRule} — são convenção desta
 * porta da P2.28, adotada porque o catálogo da P1.38 pôde ser confirmado como CONTRATO
 * (aceite e porta da P3.36), mas os esquemas de carga de cada evento de abertura/
 * encerramento são declarados pelos módulos de origem nos seus {@code vertical.json} e
 * não foram espelhados nesta execução (espelho interrompido por HTTP 429 do GitHub MCP;
 * ver o relatório). Quando os esquemas forem conferidos na integração, o esquema real
 * manda e esta convenção é revisada contra ele. Ausência de campo obrigatório é recusa
 * explícita, nunca valor presumido.
 */
public record WorkOutboxMessage(UUID id, UUID tenantId, String type, String payloadJson, Instant createdAt) {
}
