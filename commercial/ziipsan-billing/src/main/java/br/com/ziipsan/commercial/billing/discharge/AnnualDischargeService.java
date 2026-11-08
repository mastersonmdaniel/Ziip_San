package br.com.ziipsan.commercial.billing.discharge;

import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;
import br.com.ziipsan.commercial.billing.domain.AnnualDischargeStatementEntity;
import br.com.ziipsan.commercial.billing.parameters.StatementParameters;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servico da declaracao anual de quitacao (P2.24, Lei 12.007/2009) para os
 * chamadores do ciclo: decide QUAIS declaracoes vencem numa fatura, entrega
 * o contexto de emissao da P2.32 ja marcado quando o canal e o espaco na
 * fatura, e monta o registro da declaracao emitida.
 *
 * <p>A declaracao como MENSAGEM LEGAL e a da P2.32
 * ({@code legal.annual_discharge_declaration}, disparo
 * {@code ANNUAL_DISCHARGE_DUE}): esta tarefa nao tem texto proprio nem
 * motor proprio — ela calcula o disparo ({@link AnnualDischargeRule}) e o
 * canal ({@code quit.statement_channel}). Como no padrao da P2.8, o
 * contexto de emissao completo e do chamador; este servico devolve o
 * contexto com o campo {@code annualDischargeDue} ajustado, sem tocar em
 * nenhum outro campo.
 *
 * <p>Canal diferente do espaco na fatura (documento anexo ou portal): o
 * contexto NAO e marcado — a declaracao nao ocupa o espaco da fatura — e a
 * entrega pelo canal escolhido e do dono do canal (impressao/entrega da
 * P2.8 para o anexo; portal da P3.20). O registro da emissao
 * ({@link #newStatementRecord}) vale para os tres canais: e ele que
 * alimenta o {@code alreadyIssuedYears} das avaliacoes seguintes, para a
 * declaracao sair uma unica vez por ano.
 */
public interface AnnualDischargeService {

    /** Pedido de avaliacao para a fatura de uma competencia. */
    record DischargeRequest(
            UUID unitId,
            LocalDate targetReferenceMonth,
            List<AnnualBillFact> history,
            Set<Integer> alreadyIssuedYears,
            java.util.Map<String, String> parameterValues) {

        public DischargeRequest {
            if (unitId == null) {
                throw new IllegalArgumentException("unitId e obrigatorio");
            }
            if (targetReferenceMonth == null || targetReferenceMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException(
                        "targetReferenceMonth deve ser o primeiro dia do mes: " + targetReferenceMonth);
            }
            history = history == null ? List.of() : List.copyOf(history);
            alreadyIssuedYears = alreadyIssuedYears == null ? Set.of() : Set.copyOf(alreadyIssuedYears);
            parameterValues = parameterValues == null ? java.util.Map.of() : java.util.Map.copyOf(parameterValues);
        }
    }

    /** Declaracoes devidas na fatura da competencia do pedido (regra pura + historico). */
    List<AnnualDischargeRule.DischargeDecision> dueStatements(DischargeRequest request);

    /** O canal vigente da declaracao ({@code quit.statement_channel}). */
    StatementParameters.StatementChannel channel(DischargeRequest request);

    /**
     * O contexto de emissao com {@code annualDischargeDue} ajustado: true
     * somente quando ha declaracao devida E o canal e o espaco na fatura.
     * Todos os demais campos do contexto sao copiados sem alteracao.
     */
    LegalMessageEngine.EmissionContext contextWithDischarge(LegalMessageEngine.EmissionContext base,
                                                            DischargeRequest request);

    /**
     * O registro de uma declaracao emitida, pronto para persistir
     * ({@code AnnualDischargeStatementEntity}). No canal de espaco na
     * fatura, {@code billId} e a conta que a carregou (obrigatorio); nos
     * demais canais, {@code billId} pode ser nulo e o registro e a unica
     * prova da emissao.
     */
    AnnualDischargeStatementEntity newStatementRecord(UUID tenantId, UUID unitId,
                                                      AnnualDischargeRule.DischargeDecision decision,
                                                      StatementParameters.StatementChannel channel,
                                                      UUID billId, LocalDate issuedOn);
}
