package br.com.ziipsan.platform.implantation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gates de prontidão da jornada de implantação (ED-09 sobre o console da
 * P1.6, PLT-21).
 *
 * <p>A jornada vive em {@code platform.onboarding_step} (core {@code V9}):
 * uma linha por (tenant, {@code step_key}) com {@code status} {@code PENDING}
 * ou {@code DONE}. Esta regra é a leitura de prontidão sobre esse estado —
 * ela NÃO grava passo nenhum (quem completa passo é o console da P1.6) e NÃO
 * inventa passo: as chaves são as do {@link ImplantationPlan}.
 *
 * <p>Duas leituras, com semânticas distintas:
 * <ul>
 *   <li><b>pronto para produção</b>: TODOS os passos obrigatórios do modelo
 *       concluídos — é o gate automático da P1.6 ("produção bloqueada sem
 *       parâmetros aceitos" é o caso particular do passo de parâmetros);</li>
 *   <li><b>percentual da jornada</b>: concluídos ÷ obrigatórios, a mesma
 *       porcentagem que o painel do tenant da P1.6 mostra.</li>
 * </ul>
 * Passo desconhecido no estado é ignorado na conta (o console pode ter
 * passos de vertical — ADR-0037 — que não são desta metodologia), mas passo
 * obrigatório AUSENTE do estado conta como pendente, nunca como concluído.
 */
public final class ReadinessGate {

    private ReadinessGate() {
    }

    public enum StepStatus { PENDING, DONE }

    /** Estado da jornada: chave do passo → situação (como em onboarding_step). */
    public record JourneyState(Map<String, StepStatus> statusByStep) {
        public JourneyState {
            statusByStep = statusByStep == null ? Map.of() : Map.copyOf(statusByStep);
        }
    }

    public record Assessment(boolean readyForProduction, int completedMandatory,
                             int totalMandatory, int percentComplete,
                             List<String> pendingMandatorySteps) {
    }

    public static Assessment assess(JourneyState state) {
        if (state == null) {
            throw new IllegalArgumentException("O estado da jornada é obrigatório.");
        }
        List<String> pending = new ArrayList<>();
        int done = 0;
        for (String key : ImplantationPlan.MANDATORY_STEP_KEYS) {
            if (state.statusByStep().get(key) == StepStatus.DONE) {
                done++;
            } else {
                pending.add(key);
            }
        }
        int total = ImplantationPlan.MANDATORY_STEP_KEYS.size();
        int percent = total == 0 ? 100 : (done * 100) / total;
        return new Assessment(pending.isEmpty(), done, total, percent, List.copyOf(pending));
    }

    /**
     * O portão em forma de recusa: devolve a lista de motivos que bloqueiam a
     * produção (vazia = liberado). Quem chama (console/job de ativação) decide
     * o erro HTTP; aqui fica só a regra, testável sem banco.
     */
    public static List<String> productionBlockers(JourneyState state) {
        Assessment a = assess(state);
        List<String> blockers = new ArrayList<>();
        for (String key : a.pendingMandatorySteps()) {
            blockers.add("Passo obrigatório da implantação pendente: " + key);
        }
        return List.copyOf(blockers);
    }
}
