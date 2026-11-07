package br.com.ziipsan.platform.work;

import java.util.UUID;

/**
 * Porta para os parâmetros {@code wf.*} do tenant (catálogo global de parâmetros):
 * <ul>
 *   <li>{@code wf.self_approval} — BOOLEAN, classe <b>a</b>, padrão {@code false}:
 *       quem solicita NÃO aprova. Classe a significa travado no padrão; o console
 *       (P1.6) recusa qualquer proposta diferente e esta porta recusa um catálogo
 *       que diga o contrário ({@link TenantWorkParameters});</li>
 *   <li>{@code wf.delegation_max_days} — INT, classe <b>d</b>: teto da delegação
 *       temporária em dias;</li>
 *   <li>{@code wf.escalation_hours} — INT, classe <b>d</b>: horas após o vencimento
 *       para o item vencido escalar.</li>
 * </ul>
 * Ausência de definição ou de valor é erro explícito da implementação
 * ({@code MissingParameterException} do core), nunca um padrão inventado aqui:
 * sem teto de delegação conhecido, nenhuma delegação nasce; sem janela de
 * escalonamento conhecida, nenhum item escala em silêncio.
 */
public interface WorkParameters {
    boolean selfApproval(UUID tenantId);

    int delegationMaxDays(UUID tenantId);

    long escalationHours(UUID tenantId);
}
