package br.com.ziipsan.core.certification.lims17025;

import java.time.Instant;
import java.util.Objects;

/**
 * Registo de execucao de UM item do plano de validacao do sistema por
 * versao (ED-51-PD08; PD-08).
 *
 * <p>O resultado ({@link Outcome}) e o resultado COMO REGISTADO por
 * quem executou o item — dado reportado, nao limiar codificado nesta
 * classe: nenhuma faixa, percentual ou criterio de aprovacao de item
 * nasce de fabrica aqui, porque nenhuma fonte do projeto o fixa para
 * este pacote (mesma disciplina da avaliacao de ensaio de
 * proficiencia da ED-51, {@code ProficiencyTesting}, cuja avaliacao e
 * a como reportada pelo provedor). Um item registado {@code FAILED}
 * fica {@code FAILED} no dossie: a conclusao da validacao depende da
 * COMPLETUDE aritmetica do plano (todos os itens com execucao
 * registada), nao de reinterpretar resultados.
 *
 * <p>A referencia de evidencia ({@code evidenceRef}) e obrigatoria:
 * execucao sem evidencia referenciada nao se regista — e tambem e
 * dado declarado, nunca gerado por esta classe.
 */
public record SystemValidationExecution(String itemKey, Outcome outcome, String evidenceRef,
                                        String executedByRef, Instant executedAt, String note) {

    /** Resultado do item COMO REGISTADO por quem o executou (dado, nao limiar desta classe). */
    public enum Outcome { PASSED, FAILED }

    public SystemValidationExecution {
        if (itemKey == null || itemKey.isBlank())
            throw new IllegalArgumentException("itemKey e obrigatorio (o item do plano executado)");
        Objects.requireNonNull(outcome, "outcome e obrigatorio (o resultado como registado "
                + "por quem executou o item)");
        if (evidenceRef == null || evidenceRef.isBlank())
            throw new IllegalArgumentException("evidenceRef e obrigatoria: execucao de item de "
                    + "validacao sem referencia de evidencia nao se regista");
        if (executedByRef == null || executedByRef.isBlank())
            throw new IllegalArgumentException("executedByRef e obrigatorio (quem executou o item)");
        Objects.requireNonNull(executedAt, "executedAt e obrigatorio (quando o item foi executado)");
    }
}
