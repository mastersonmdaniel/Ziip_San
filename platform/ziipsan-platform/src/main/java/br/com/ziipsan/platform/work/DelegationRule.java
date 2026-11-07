package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras puras da delegação temporária, com os casos calculados à mão nos testes:
 *
 * <ul>
 *   <li><b>Janela válida:</b> fim estritamente depois do início, e a delegação não nasce
 *       já expirada (fim no futuro);</li>
 *   <li><b>Teto {@code wf.delegation_max_days} (classe d):</b> a duração
 *       {@code endsAt - startsAt} não pode exceder o teto — duração EXATAMENTE igual
 *       ao teto é aceita; um segundo além recusa (422);</li>
 *   <li><b>Nunca maior que a permissão do delegante:</b> o conjunto concedido precisa
 *       ser subconjunto das permissões do delegante NA CONCESSÃO (422 nomeando as
 *       excedentes), e a permissão EFETIVA do delegado é sempre
 *       {@code próprias ∪ (concedidas ∩ vigentes do delegante)} — se o delegante perde
 *       a permissão depois, o delegado perde junto (a interseção é com o conjunto
 *       ATUAL do delegante, não com a foto da concessão). Esta interseção NÃO é só
 *       teoria desta classe: ela é o insumo do canal delegado da
 *       {@link ApprovalRule} e da visibilidade por delegação da
 *       {@link WorkItemVisibility}, via {@link #effectiveGrantedPermissions} — o
 *       serviço resolve o conjunto ATUAL de cada delegante pela porta
 *       {@code UserPermissions} a cada decisão/leitura (correção pós-revisão A2);</li>
 *   <li><b>Expiração:</b> vigente em {@code [início, fim)} — no instante do fim, já
 *       expirou (ver {@link WorkDelegation#isActiveAt}).</li>
 * </ul>
 */
public final class DelegationRule {

    private DelegationRule() {
    }

    /**
     * Valida a criação. {@code maxDays} ausente/inválido (&lt; 1) é erro de estado do
     * chamador (parâmetro não resolvido), não um teto presumido.
     */
    public static void assertCanCreate(UUID delegatorId, UUID delegateId, Instant startsAt, Instant endsAt,
                                       Set<String> grantedPermissions, Set<String> delegatorPermissions,
                                       int maxDays, Instant now) {
        if (delegatorId == null || delegateId == null || startsAt == null || endsAt == null || now == null) {
            throw new IllegalArgumentException("delegante, delegado, início, fim e instante são obrigatórios");
        }
        if (maxDays < 1) {
            throw new IllegalStateException("wf.delegation_max_days precisa ser resolvido (>= 1) antes de delegar; recebido: " + maxDays);
        }
        if (delegatorId.equals(delegateId)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "delegateId", "self-delegation", "Delegar para si mesmo não é delegação."));
        }
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "endsAt", "invalid-window", "O fim da delegação precisa ser depois do início."));
        }
        if (!endsAt.isAfter(now)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "endsAt", "delegation-expired", "A delegação já nasceria expirada: o fim precisa estar no futuro."));
        }
        if (Duration.between(startsAt, endsAt).compareTo(Duration.ofDays(maxDays)) > 0) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "endsAt", "delegation-too-long",
                    "A delegação excede o teto de " + maxDays + " dias (wf.delegation_max_days)."));
        }
        Set<String> granted = grantedPermissions == null ? Set.of() : grantedPermissions;
        Set<String> owned = delegatorPermissions == null ? Set.of() : delegatorPermissions;
        Set<String> beyond = new HashSet<>(granted);
        beyond.removeAll(owned);
        if (!beyond.isEmpty()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "grantedPermissions", "beyond-delegator",
                    "O delegado não pode receber permissão além da do delegante: " + beyond.stream().sorted().toList()));
        }
    }

    /**
     * Permissões efetivas de {@code userId} no instante: as próprias, mais, para cada
     * delegação VIGENTE recebida, {@code concedidas ∩ permissões ATUAIS do delegante}.
     * Delegante sem conjunto conhecido em {@code currentPermissionsByDelegator} contribui
     * com ZERO — ausência de fato não concede nada (nunca se presume a foto da concessão).
     */
    public static Set<String> effectivePermissions(UUID userId, Set<String> ownPermissions,
                                                   Map<UUID, Set<String>> currentPermissionsByDelegator,
                                                   List<WorkDelegation> delegations, Instant now) {
        if (userId == null || now == null) {
            throw new IllegalArgumentException("usuário e instante são obrigatórios");
        }
        Set<String> effective = new HashSet<>(ownPermissions == null ? Set.of() : ownPermissions);
        for (WorkDelegation delegation : delegations == null ? List.<WorkDelegation>of() : delegations) {
            if (!delegation.delegateId().equals(userId) || !delegation.isActiveAt(now)) {
                continue;
            }
            Set<String> delegatorNow = currentPermissionsByDelegator == null
                    ? null : currentPermissionsByDelegator.get(delegation.delegatorId());
            effective.addAll(effectiveGrantedPermissions(delegation, delegatorNow));
        }
        return Set.copyOf(effective);
    }

    /**
     * O escopo EFETIVO de UMA delegação: {@code concedidas ∩ permissões ATUAIS do
     * delegante}. É o conjunto que o canal delegado da {@link ApprovalRule} e a
     * visibilidade por delegação da {@link WorkItemVisibility} usam — nunca a foto
     * {@code grantedPermissions} sozinha: delegante sem conjunto conhecido
     * ({@code null}) contribui com ZERO (ausência de fato não concede nada), e
     * permissão que o delegante ganhou DEPOIS da concessão não entra (não está em
     * {@code grantedPermissions}, logo não está na interseção — a foto limita).
     */
    public static Set<String> effectiveGrantedPermissions(WorkDelegation delegation,
                                                          Set<String> delegatorCurrentPermissions) {
        if (delegation == null) {
            throw new IllegalArgumentException("delegação é obrigatória");
        }
        if (delegatorCurrentPermissions == null || delegatorCurrentPermissions.isEmpty()) {
            return Set.of();
        }
        Set<String> effective = new HashSet<>(delegation.grantedPermissions());
        effective.retainAll(delegatorCurrentPermissions);
        return Set.copyOf(effective);
    }
}
