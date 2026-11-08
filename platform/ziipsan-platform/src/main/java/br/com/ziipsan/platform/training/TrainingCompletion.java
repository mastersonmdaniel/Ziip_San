package br.com.ziipsan.platform.training;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Conclusão de um módulo de treinamento (ED-11): quando um módulo pode ser
 * dado por concluído.
 *
 * <p>A régua mede <b>entrega e participação</b>, e só isso:
 * <ol>
 *   <li>o plano do módulo é válido contra o modelo (40 h, ver
 *       {@link TrainingPlan});</li>
 *   <li>todas as unidades do plano foram entregues;</li>
 *   <li>as horas entregues somam pelo menos as 40 h do plano (entregar a
 *       unidade "no nome" com carga cortada não conclui);</li>
 *   <li>todos os participantes inscritos concluíram — participante é
 *       referenciado pelo id de usuário do tenant, nunca por nome: o
 *       registro de conclusão não é uma lista de presença com dado pessoal;</li>
 *   <li>há pelo menos um participante inscrito (módulo "concluído" sem
 *       ninguém treinado é registro vazio, não capacitação).</li>
 * </ol>
 *
 * <p>O aproveitamento na avaliação (nota ou corte mínimo) <b>não</b> entra
 * nesta régua porque nenhuma fonte do projeto o fixa: exigi-lo aqui seria
 * inventar critério. Ele está nomeado como pendência no pacote da ED-11;
 * quando o dono o fixar, esta classe ganha o item sem mudar os demais.
 */
public final class TrainingCompletion {

    private TrainingCompletion() {
    }

    public record Participant(UUID userId, boolean completed) {
        public Participant {
            if (userId == null) {
                throw new IllegalArgumentException("Participante exige o id do usuário.");
            }
        }
    }

    /**
     * Registro de execução de um módulo para um tenant.
     *
     * @param deliveredUnitKeys chaves das unidades efetivamente entregues
     * @param deliveredHours    horas efetivamente entregues (relógio da turma)
     * @param concludedOn       data do encerramento (obrigatória no registro)
     */
    public record ModuleCompletion(String moduleKey, Set<String> deliveredUnitKeys,
                                   int deliveredHours, List<Participant> participants,
                                   LocalDate concludedOn) {
        public ModuleCompletion {
            if (moduleKey == null || moduleKey.isBlank()) {
                throw new IllegalArgumentException("O registro exige o módulo.");
            }
            if (deliveredHours < 0) {
                throw new IllegalArgumentException("Horas entregues não podem ser negativas.");
            }
            if (concludedOn == null) {
                throw new IllegalArgumentException("O registro exige a data de encerramento.");
            }
            deliveredUnitKeys = deliveredUnitKeys == null ? Set.of() : Set.copyOf(deliveredUnitKeys);
            participants = participants == null ? List.of() : List.copyOf(participants);
        }
    }

    public record Result(boolean concluded, List<String> failures) {
    }

    public static Result evaluate(TrainingPlan.ModulePlan plan, ModuleCompletion completion) {
        if (plan == null || completion == null) {
            throw new IllegalArgumentException("Plano e registro de conclusão são obrigatórios.");
        }
        List<String> failures = new ArrayList<>();
        if (!plan.moduleKey().equals(completion.moduleKey())) {
            failures.add("O registro é do módulo " + completion.moduleKey()
                    + " e o plano é do módulo " + plan.moduleKey() + ".");
            return new Result(false, List.copyOf(failures));
        }
        for (TrainingPlan.Finding f : TrainingPlan.validate(plan)) {
            failures.add("Plano inválido (" + f.problem() + "): " + f.detail());
        }
        for (TrainingPlan.Unit u : plan.units()) {
            if (!completion.deliveredUnitKeys().contains(u.key())) {
                failures.add("Unidade do plano não entregue: " + u.key());
            }
        }
        if (completion.deliveredHours() < TrainingPlan.REQUIRED_HOURS_PER_MODULE) {
            failures.add("Horas entregues (" + completion.deliveredHours()
                    + ") abaixo das " + TrainingPlan.REQUIRED_HOURS_PER_MODULE
                    + " h do plano do módulo.");
        }
        if (completion.participants().isEmpty()) {
            failures.add("Módulo sem nenhum participante inscrito: não há quem tenha "
                    + "sido capacitado.");
        }
        for (Participant p : completion.participants()) {
            if (!p.completed()) {
                failures.add("Participante sem conclusão registrada: " + p.userId());
            }
        }
        return new Result(failures.isEmpty(), List.copyOf(failures));
    }
}
