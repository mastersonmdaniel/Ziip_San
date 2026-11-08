package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.core.api.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recertificação periódica de acessos pelo ADMIN do tenant (ED-06): campanha
 * com prazo, um item por usuário com acesso vigente, e o REGISTRO de cada
 * decisão (manter ou revogar) — é esse registro que a ED-73 usa como
 * evidência da ISO/IEC 27001. Campanha sem decisão registrada para um item
 * NÃO o mantém por omissão: ao fim do prazo, item não decidido aparece como
 * pendente na consulta (a revogação em si é ato do ADMIN, não automática —
 * esta tarefa registra a decisão; aplicá-la à atribuição é o passo seguinte
 * do ADMIN na gestão de usuários da P0.13, declarado no relatório).
 */
public class RecertificationService {

    public enum DecisionKind { KEEP, REVOKE }

    public record Campaign(UUID id, UUID tenantId, String name, Instant opensAt, Instant dueAt,
                           UUID createdBy) {
        public Campaign {
            if (id == null || tenantId == null || createdBy == null || opensAt == null || dueAt == null) {
                throw new IllegalArgumentException("campanha exige id, tenant, criador e vigência");
            }
            if (!dueAt.isAfter(opensAt)) {
                throw new IllegalArgumentException("campanha exige prazo depois da abertura");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("campanha exige nome");
            }
        }
    }

    public record Decision(UUID id, UUID campaignId, UUID subjectUserId, UUID decidedBy,
                           DecisionKind kind, String note, Instant decidedAt) {
        public Decision {
            if (id == null || campaignId == null || subjectUserId == null || decidedBy == null
                    || kind == null || decidedAt == null) {
                throw new IllegalArgumentException("decisão exige campanha, usuário, decisor, tipo e instante");
            }
            if (note == null || note.isBlank()) {
                throw new IllegalArgumentException("decisão de recertificação exige a justificativa registrada");
            }
        }
    }

    private final UUID tenantId;
    private final AccessControlPorts.AccessControlStore store;
    private final Clock clock;

    public RecertificationService(UUID tenantId, AccessControlPorts.AccessControlStore store, Clock clock) {
        if (tenantId == null || store == null || clock == null) {
            throw new IllegalArgumentException("tenant, loja e relógio são obrigatórios");
        }
        this.tenantId = tenantId;
        this.store = store;
        this.clock = clock;
    }

    public Campaign openCampaign(UUID actorId, String name, Instant dueAt) {
        Campaign campaign = new Campaign(UUID.randomUUID(), tenantId, name, clock.instant(), dueAt, actorId);
        store.insertRecertificationCampaign(campaign);
        return campaign;
    }

    public Decision decide(UUID actorId, UUID campaignId, UUID subjectUserId, DecisionKind kind, String note) {
        Campaign campaign = store.findCampaign(campaignId)
                .orElseThrow(() -> new ApiException.NotFound("Campanha de recertificação não encontrada."));
        Instant now = clock.instant();
        if (now.isAfter(campaign.dueAt())) {
            throw new ApiException.Conflict("recertification-campaign-closed",
                    "A campanha está encerrada; decisões fora do prazo não são registradas.");
        }
        for (Decision existing : store.decisionsOfCampaign(campaignId)) {
            if (existing.subjectUserId().equals(subjectUserId)) {
                throw new ApiException.Conflict("recertification-already-decided",
                        "Já há decisão registrada para este usuário nesta campanha.");
            }
        }
        Decision decision = new Decision(UUID.randomUUID(), campaignId, subjectUserId, actorId, kind, note, now);
        store.insertRecertificationDecision(decision);
        return decision;
    }

    public List<Decision> decisions(UUID campaignId) {
        return store.decisionsOfCampaign(campaignId);
    }
}
