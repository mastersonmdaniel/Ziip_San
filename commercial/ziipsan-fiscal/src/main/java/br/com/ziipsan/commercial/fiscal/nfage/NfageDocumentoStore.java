package br.com.ziipsan.commercial.fiscal.nfage;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência do documento da NFAg-e sobre o dicionário fiscal
 * ({@code fiscal_documents} da V4_5 + extensão {@code fiscal_nfage_details} da V4_7).
 * É por esta porta que a idempotência da emissão se apoia: antes de reservar número
 * novo, o serviço consulta a NFAg-e <b>viva</b> da origem (BILL + bill_id); a unicidade
 * de origem entre documentos vivos também é garantida no banco pelo dicionário
 * (DAD-18) — as duas camadas se somam, nenhuma substitui a outra.
 *
 * <p>A implementação JPA desta porta (sobre {@code fiscal_documents}, que é da V4_5 e
 * não foi espelhada nesta tarefa pelo rate limit do conector) é trabalho da integração;
 * as entidades e repositórios da extensão do modelo 75 entregues aqui
 * ({@code NfageDetailEntity} e pares) são a metade desta tarefa dessa implementação.
 */
public interface NfageDocumentoStore {

    /** NFAg-e viva da origem, se existir (estados de {@link NfageStateMachine#isLive}). */
    Optional<NfageEmissionService.NfageEmitida> buscarVivaPorOrigem(UUID tenantId, String sourceType, UUID sourceId);

    /** Grava o documento no estado em que ele está (reserva, assinatura, desfecho). */
    void registrar(NfageEmissionService.NfageEmitida documento);

    /** Atualiza estado/protocolo/rejeição de um documento já registrado. */
    void atualizar(NfageEmissionService.NfageEmitida documento);
}
