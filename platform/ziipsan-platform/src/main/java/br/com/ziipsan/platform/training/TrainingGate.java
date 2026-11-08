package br.com.ziipsan.platform.training;

import br.com.ziipsan.platform.implantation.ImplantationPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Treinamento concluído como gate de prontidão (ED-11, PLT-21): a regra que
 * decide quando o passo {@code TRAINING} da jornada de implantação do
 * console da P1.6 pode ser marcado como concluído.
 *
 * <p>Na ED-09, o passo {@code TRAINING} já é obrigatório na jornada
 * ({@link ImplantationPlan#STEP_TRAINING}), mas não é gate em
 * {@code ImplantationPlan.GATE_STEP_KEYS}. Esta classe é a régua que a
 * ED-11 acrescenta sobre ele — o passo só conclui quando:
 * <ol>
 *   <li>o tenant de pré-treinamento está pronto ({@link PreTrainingTenant})
 *       — treinamento "concluído" sem o ambiente do aceite não aconteceu
 *       no ambiente do aceite;</li>
 *   <li>o escopo requerido da implantação está declarado e não é vazio
 *       (gate sem escopo concluiria qualquer coisa);</li>
 *   <li>o módulo da TI do cliente ({@link TrainingPlan#MOD_TI}) está no
 *       escopo — a capacitação da TI é item próprio do aceite da ED-11,
 *       não módulo optativo (decisão de desenho desta tarefa, declarada
 *       aqui e no relatório);</li>
 *   <li>cada módulo requerido tem plano válido e registro de conclusão
 *       aprovado por {@link TrainingCompletion};</li>
 *   <li>a capacitação da TI está completa em conteúdo
 *       ({@link ItEnablement}), com as práticas feitas.</li>
 * </ol>
 *
 * <p>A fiação é da integração: o console da P1.6 consulta esta régua antes
 * de gravar {@code DONE} no passo {@code TRAINING} de
 * {@code platform.onboarding_step}, e a promoção do passo a gate em
 * {@code GATE_STEP_KEYS} da ED-09 é o ajuste correspondente nomeado no
 * relatório da ED-11. Esta classe não grava passo nenhum — como o
 * {@code ReadinessGate} da ED-09, ela é a leitura de prontidão.
 */
public final class TrainingGate {

    private TrainingGate() {
    }

    /** A chave do passo da jornada a que este gate se aplica. */
    public static String journeyStepKey() {
        return ImplantationPlan.STEP_TRAINING;
    }

    public record Assessment(boolean trainingConcluded, List<String> blockers) {
    }

    /**
     * Avalia o gate do treinamento de uma implantação.
     *
     * @param requiredModuleKeys escopo de treinamento declarado da implantação
     * @param plans              plano por módulo (do catálogo ou concreto válido)
     * @param completions        registro de conclusão por módulo
     * @param tenantSpec         prontidão do tenant de pré-treinamento
     * @param itCompletion       conteúdo da capacitação da TI
     * @param dedicatedInstanceRequired contrato exige instância dedicada (LC.15)
     */
    public static Assessment assess(List<String> requiredModuleKeys,
                                    Map<String, TrainingPlan.ModulePlan> plans,
                                    Map<String, TrainingCompletion.ModuleCompletion> completions,
                                    PreTrainingTenant.Spec tenantSpec,
                                    ItEnablement.ItCompletion itCompletion,
                                    boolean dedicatedInstanceRequired) {
        if (requiredModuleKeys == null || plans == null || completions == null
                || tenantSpec == null) {
            throw new IllegalArgumentException(
                    "Escopo, planos, conclusões e tenant de pré-treinamento são obrigatórios.");
        }
        List<String> blockers = new ArrayList<>();
        for (String b : PreTrainingTenant.blockers(tenantSpec)) {
            blockers.add("Tenant de pré-treinamento: " + b);
        }
        if (requiredModuleKeys.isEmpty()) {
            blockers.add("Escopo de treinamento da implantação não declarado: "
                    + "sem módulos requeridos, o gate não tem o que concluir.");
        }
        if (!requiredModuleKeys.contains(TrainingPlan.MOD_TI)) {
            blockers.add("A capacitação da TI do cliente (" + TrainingPlan.MOD_TI
                    + ") está fora do escopo requerido: ela é item do aceite da ED-11.");
        }
        for (String key : requiredModuleKeys) {
            if (TrainingPlan.module(key) == null) {
                blockers.add("Módulo requerido fora do catálogo de treinamento: " + key);
                continue;
            }
            TrainingPlan.ModulePlan plan = plans.get(key);
            if (plan == null) {
                blockers.add("Módulo requerido sem plano de treinamento: " + key);
                continue;
            }
            TrainingCompletion.ModuleCompletion completion = completions.get(key);
            if (completion == null) {
                blockers.add("Módulo requerido sem registro de conclusão: " + key);
                continue;
            }
            TrainingCompletion.Result r = TrainingCompletion.evaluate(plan, completion);
            for (String f : r.failures()) {
                blockers.add("Módulo " + key + ": " + f);
            }
        }
        if (requiredModuleKeys.contains(TrainingPlan.MOD_TI)) {
            if (itCompletion == null) {
                blockers.add("Capacitação da TI sem registro de conteúdo "
                        + "(tópicos e práticas da TI não avaliados).");
            } else {
                ItEnablement.Result r = ItEnablement.evaluate(itCompletion,
                        dedicatedInstanceRequired);
                for (String f : r.failures()) {
                    blockers.add("Capacitação da TI: " + f);
                }
            }
        }
        return new Assessment(blockers.isEmpty(), List.copyOf(blockers));
    }
}
