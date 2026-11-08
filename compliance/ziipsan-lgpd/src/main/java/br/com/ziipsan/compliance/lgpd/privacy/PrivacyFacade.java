package br.com.ziipsan.compliance.lgpd.privacy;

import java.util.UUID;

/**
 * Porta de integração das telas da P3.40 (portal e backoffice do encarregado) com a
 * persistência do módulo lgpd. Os controllers dependem DESTA interface; a implementação
 * JPA sobre as entidades já modeladas ({@code ConsentRecord}, {@code DataSubjectRequest},
 * {@code DpiaRecord}, {@code RetentionSchedule} — nomes do cabeçalho da {@code V2_5}) e
 * sobre as tabelas da {@code V23_1} é passo de INTEGRAÇÃO nomeado no relatório: o
 * conteúdo das classes de {@code domain/}/{@code repository/}/{@code service/} do módulo
 * não foi lido nesta frente (HTTP 429 do conector), e escrever uma implementação contra
 * assinaturas não lidas seria fabricar API.
 *
 * <p>Contrato de comportamento da implementação (o que as réguas desta tarefa impõem):
 * <ul>
 *   <li>abrir pedido = criar o protocolo da P1.36 (especificação de privacidade do
 *       catálogo do tenant), gravar o pedido com {@code protocol_id}/{@code origin_channel}
 *       e o prazo de {@link DsrDeadline}, e registrar o item da fila do encarregado
 *       ({@link DpoWorkItemFactory}) — tudo ou nada;</li>
 *   <li>entregar = conferir o chamador contra o titular DO PEDIDO (o pedido só é
 *       entregue ao próprio titular verificado ou a representante com representação
 *       {@code ACTIVE} e prova da P1.41 sobre esse titular — {@link IdentityGate}),
 *       passar pelo {@link IdentityGate} e gerar a entrada de
 *       {@code personal_data_access_log} ({@link AccessDelivery}) na mesma transação;</li>
 *   <li>eliminar = decidir antes com {@link RetentionDecision} e gravar a justificativa
 *       na resposta;</li>
 *   <li>relatório = agregar e delegar a montagem a {@link DpoReport} e as pendências a
 *       {@link ProcessingCoverage};</li>
 *   <li>encarregado e política = ler da fonte única ({@link DpoDisplay}) e da versão
 *       publicada ({@link PrivacyPolicy#currentForPortal}).</li>
 * </ul>
 * Todos os retornos são JSON produzido pelas classes desta tarefa (classe {@code Json}
 * do core), nunca montagem manual no controller.
 */
public interface PrivacyFacade {

    /** Encarregado exibido no portal (JSON de {@link DpoDisplay#render}). */
    String dpoForPortal();

    /** Política de privacidade publicada vigente (versão, título, corpo e publicação). */
    String currentPolicyJson();

    /** Abre pedido do titular autenticado no portal e devolve o protocolo e o prazo. */
    String openRequestFromPortal(UUID subjectId, DataSubjectRight right, String description);

    /** Revoga consentimento do titular autenticado (JSON de {@link ConsentRevocation.Revocation}). */
    String revokeConsent(UUID subjectId, UUID consentId, String method);

    /**
     * Entrega resposta/arquivo do pedido, com log de acesso. O {@code subjectId} é o
     * chamador da sessão do portal (cabeçalho {@code X-Portal-Subject}, o mesmo vínculo
     * da abertura e da revogação): a implementação TEM de carregar o pedido e conferir
     * o titular dele contra o chamador antes de entregar — entrega ao próprio titular
     * via {@link AccessDelivery#deliverToHolder} ou, sendo o chamador representante,
     * via {@link AccessDelivery#deliverToRepresentative} com representação
     * {@code ACTIVE} e prova da P1.41 sobre o titular do pedido. Chamador que não é
     * nem o titular do pedido nem representante válido dele = entrega recusada.
     */
    String deliver(UUID subjectId, UUID requestId, AccessDelivery.Kind kind);

    /** Relatório do encarregado (JSON de {@link DpoReport#buildJson}). */
    String dpoReportJson();

    /** Pendências de cobertura do registro de operações (colunas sem operação). */
    String coverageGapsJson();

    /** Registros de operações de tratamento do tenant (ativos, com versão). */
    String processingRecordsJson();
}
