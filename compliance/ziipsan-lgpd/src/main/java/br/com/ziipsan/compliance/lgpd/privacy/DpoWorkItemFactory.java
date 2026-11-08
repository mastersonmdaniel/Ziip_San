package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila do encarregado (aceite da P3.40): todo pedido do titular vira item de "Minhas
 * tarefas" da P2.28, com responsável e prazo. O tipo é o que a P2.28 já tem para isso —
 * {@code HOLDER_REQUEST} ("pedido de titular", em {@code WorkItemType} e no CHECK da
 * {@code V1_11}) — e os campos seguem o CONTRATO do item de trabalho da P2.28 (tipo,
 * objeto, módulo de origem, solicitante, responsável ou permissão, prazo, estado e
 * endereço do registro).
 *
 * <p>O módulo lgpd NÃO depende do módulo platform (o {@code pom.xml} do lgpd, lido no
 * repo, depende de core/webmvc/data-jpa/cache — a montagem é da aplicação), por isso a
 * fábrica produz o contrato serializado (JSON pela classe {@code Json} do core) e a
 * integração o converte no {@code WorkItem} da P2.28. Endereçamento espelha a invariante
 * da caixa: responsável direto OU permissão exigida — item sem dono é recusado aqui,
 * como o construtor do {@code WorkItem} recusa lá.
 *
 * <p>A permissão do encarregado é INFORMADA por quem chama: o nome exato no catálogo
 * {@code recurso:ação} da P0.13/P1.37 (perfil de fábrica "encarregado de dados") não foi
 * lido nesta frente — inventar a string seria criar permissão fora do catálogo, que o
 * CI da P1.37 recusa. Pendência nomeada no relatório.
 */
public final class DpoWorkItemFactory {

    private DpoWorkItemFactory() {
    }

    /** Tipo do item na caixa da P2.28 (já existe: {@code WorkItemType.HOLDER_REQUEST}). */
    public static final String ITEM_TYPE = "HOLDER_REQUEST";
    /** Tipo do objeto de origem (a tabela do pedido). */
    public static final String OBJECT_TYPE = "lgpd_data_subject_request";
    /** Módulo de origem. */
    public static final String ORIGIN_MODULE = "lgpd";
    /** Rota da tela do pedido no backoffice (item de menu Privacidade, ver doc da tarefa). */
    public static final String RECORD_ADDRESS_PREFIX = "/privacidade/pedidos/";

    /**
     * Monta o item da fila do encarregado para um pedido recém-aberto.
     *
     * @param dpoAssigneeId  usuário encarregado responsável, quando já atribuído
     * @param dpoPermission  permissão {@code recurso:ação} do encarregado (catálogo P0.13),
     *                       usada quando não há responsável nomeado
     * @param dueAt          prazo do item = prazo do pedido (art. 19, II, via {@link DsrDeadline})
     */
    public static String createWorkItemJson(UUID tenantId, UUID requestId, UUID requesterId,
                                            UUID dpoAssigneeId, String dpoPermission,
                                            Instant dueAt, Instant createdAt,
                                            String sourceEventName, int sourceEventVersion,
                                            UUID sourceMessageId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        Objects.requireNonNull(requestId, "requestId é obrigatório (objeto do item)");
        Objects.requireNonNull(requesterId, "requesterId é obrigatório (solicitante do item)");
        Objects.requireNonNull(dueAt, "dueAt é obrigatório: item da fila tem prazo (o do pedido)");
        Objects.requireNonNull(createdAt, "createdAt é obrigatório");
        if (sourceEventName == null || sourceEventName.isBlank() || sourceEventVersion < 1) {
            throw new IllegalArgumentException("o item exige o evento de origem do catálogo "
                    + "da P1.38 (nome e versão >= 1)");
        }
        Objects.requireNonNull(sourceMessageId, "sourceMessageId é obrigatório");
        boolean hasPermission = dpoPermission != null && !dpoPermission.isBlank();
        if (dpoAssigneeId == null && !hasPermission) {
            throw new IllegalArgumentException("item sem dono: informe o encarregado "
                    + "responsável ou a permissão do encarregado — item sem dono não entra "
                    + "na caixa de ninguém (invariante da P2.28)");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tenantId", tenantId.toString());
        map.put("type", ITEM_TYPE);
        map.put("objectType", OBJECT_TYPE);
        map.put("objectId", requestId.toString());
        map.put("originModule", ORIGIN_MODULE);
        map.put("requesterId", requesterId.toString());
        map.put("assigneeId", dpoAssigneeId == null ? null : dpoAssigneeId.toString());
        map.put("requiredPermission", hasPermission ? dpoPermission : null);
        map.put("dueAt", dueAt.toString());
        map.put("status", "OPEN");
        map.put("recordAddress", RECORD_ADDRESS_PREFIX + requestId);
        map.put("sourceEventName", sourceEventName);
        map.put("sourceEventVersion", sourceEventVersion);
        map.put("sourceMessageId", sourceMessageId.toString());
        map.put("createdAt", createdAt.toString());
        return Json.write(map);
    }
}
