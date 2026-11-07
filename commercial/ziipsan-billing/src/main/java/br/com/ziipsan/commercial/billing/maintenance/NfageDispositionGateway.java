package br.com.ziipsan.commercial.billing.maintenance;

import java.util.UUID;

/**
 * Porta para o registro do evento de cancelamento da NFAg-e viva no modulo
 * fiscal (P2.6): o cancelamento de conta com documento fiscal vivo gera o
 * evento {@code CANCELLATION} em {@code fiscal_nfage_events} (vocabulario e
 * tabela da V4_7 fiscal; entidade {@code NfageEventEntity} do ziipsan-fiscal),
 * do qual a maquina de estados da P2.6 leva o documento de AUTHORIZED a
 * CANCELLED. Quem decide o evento e o {@link NfageDisposition} desta frente,
 * invocado pelo {@link BillMaintenanceService}; quem registra e transmite
 * e o modulo fiscal, dono do documento.
 *
 * <p>So o CANCELLATION passa por aqui, e por uma razao de schema, nao de
 * gosto: o evento {@code SUBSTITUTION} da V4_7 tem CHECK
 * {@code related_document_id IS NOT NULL} -- o documento substituto precisa
 * existir para o evento nascer, e ele so existe quando o
 * {@code NfageEmissionService} da P2.6 emite a NFAg-e da conta nova. A
 * disposicao SUBSTITUTION da retificacao fica decidida e devolvida no
 * resultado da operacao (a conta anterior conserva o
 * {@code fiscal_document_id} vivo na cadeia, de onde a emissao o le), e o
 * registro do evento e do emissor do substituto -- pendencia nomeada no
 * RELATORIO-P2.20, nao caminho fingido aqui.
 *
 * <p>A transmissao do evento a SEFAZ e aceite externo pendente de
 * homologacao (ver RELATORIO-P2.20): nenhum metodo desta porta promete
 * protocolo da SEFAZ.
 */
public interface NfageDispositionGateway {

    /**
     * Registra o evento CANCELLATION da NFAg-e viva da conta cancelada.
     *
     * @param fiscalDocumentId documento vivo ({@code bills.fiscal_document_id})
     * @param reason           anotacao da operacao de cancelamento (motivo textual do evento)
     * @return id do evento registrado em {@code fiscal_nfage_events}
     */
    UUID registerCancellation(UUID tenantId, UUID fiscalDocumentId, String reason, UUID actorId);
}
