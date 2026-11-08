package br.com.ziipsan.platform.implantation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plano-modelo de implantação (ED-09): os modelos de 60 e de 90 dias.
 *
 * <p>Os totais (60 e 90 dias) vêm do aceite da ED-09 — são os únicos números
 * com fonte. A sequência de passos vem do checklist da jornada de implantação
 * da P1.6 (PLT-21): parâmetros pendentes de aceite, importação e conciliação,
 * certificado fiscal, teste com o PSP, política de MFA, restauração ensaiada,
 * encarregado informado, DPA assinado e treinamento — acrescida dos passos
 * que a própria ED-09 exige (metodologia aplicada, dicionário de dados,
 * linhagem da migração e, quando houver clone, o gate do RIPD da ED-09).
 *
 * <p>As janelas de dias de cada passo são DESENHO do modelo (a divisão do
 * total entre os passos), não norma nem dado externo: o plano concreto de um
 * tenant pode redistribuir os dias entre passos, desde que a validação
 * ({@link #validate}) passe — passos obrigatórios todos presentes, ordem
 * respeitada, nenhum passo fora do total e gates marcados. O que esta classe
 * NÃO faz é prometer prazo a cliente: o modelo é a régua interna da
 * implantação (e da simulação da ED-09), não um compromisso contratual.
 */
public final class ImplantationPlan {

    private ImplantationPlan() {
    }

    /** Passo do plano-modelo. {@code gate} = gate de prontidão (PLT-21). */
    public record Step(String key, String title, int startDay, int endDay, boolean gate) {
        public Step {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("A chave do passo é obrigatória.");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("O título do passo é obrigatório: " + key);
            }
            if (startDay < 1 || endDay < startDay) {
                throw new IllegalArgumentException(
                        "Janela inválida do passo " + key + ": " + startDay + ".." + endDay);
            }
        }
    }

    /** Um plano-modelo (ou um plano concreto de tenant, validado contra o modelo). */
    public record Plan(int totalDays, List<Step> steps) {
        public Plan {
            if (totalDays <= 0) {
                throw new IllegalArgumentException("O total de dias do plano deve ser positivo.");
            }
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    // Chaves dos passos. As nove primeiras são o checklist literal da P1.6;
    // as demais são os entregáveis da própria ED-09.
    public static final String STEP_PARAMETERS = "PARAMETERS_ACCEPTANCE";
    public static final String STEP_MIGRATION = "LEGACY_IMPORT_AND_RECONCILIATION";
    public static final String STEP_LINEAGE = "MIGRATION_LINEAGE";
    public static final String STEP_DICTIONARY = "DATA_DICTIONARY";
    public static final String STEP_FISCAL_CERT = "FISCAL_CERTIFICATE";
    public static final String STEP_PSP_TEST = "PSP_TEST";
    public static final String STEP_MFA_POLICY = "MFA_POLICY";
    public static final String STEP_RESTORE_REHEARSAL = "RESTORE_REHEARSAL";
    public static final String STEP_DPO = "DPO_NAMED";
    public static final String STEP_DPA = "DPA_SIGNED";
    public static final String STEP_TRAINING = "TRAINING";

    /** Ordem canônica dos passos obrigatórios (precedência do modelo). */
    public static final List<String> MANDATORY_STEP_KEYS = List.of(
            STEP_PARAMETERS, STEP_MIGRATION, STEP_LINEAGE, STEP_DICTIONARY,
            STEP_FISCAL_CERT, STEP_PSP_TEST, STEP_MFA_POLICY,
            STEP_RESTORE_REHEARSAL, STEP_DPO, STEP_DPA, STEP_TRAINING);

    /** Passos que são gates de prontidão: sem eles, não há produção (ver {@link ReadinessGate}). */
    public static final Set<String> GATE_STEP_KEYS = Set.of(
            STEP_PARAMETERS, STEP_MIGRATION, STEP_FISCAL_CERT, STEP_MFA_POLICY, STEP_DPA);

    private static Step step(String key, String title, int start, int end) {
        return new Step(key, title, start, end, GATE_STEP_KEYS.contains(key));
    }

    /** Plano-modelo de 60 dias (o da implantação simulada do aceite). */
    public static Plan template60() {
        return new Plan(60, List.of(
                step(STEP_PARAMETERS, "Parâmetros pendentes de aceite", 1, 10),
                step(STEP_MIGRATION, "Importação do legado e conciliação", 5, 25),
                step(STEP_LINEAGE, "Linhagem da migração registrada", 20, 27),
                step(STEP_DICTIONARY, "Dicionário de dados gerado do schema", 25, 30),
                step(STEP_FISCAL_CERT, "Certificado fiscal", 10, 20),
                step(STEP_PSP_TEST, "Teste com o PSP", 20, 35),
                step(STEP_MFA_POLICY, "Política de MFA", 30, 40),
                step(STEP_RESTORE_REHEARSAL, "Restauração ensaiada", 35, 45),
                step(STEP_DPO, "Encarregado informado", 1, 15),
                step(STEP_DPA, "DPA assinado", 15, 30),
                step(STEP_TRAINING, "Treinamento", 40, 60)));
    }

    /** Plano-modelo de 90 dias (mesma sequência, janelas mais largas). */
    public static Plan template90() {
        return new Plan(90, List.of(
                step(STEP_PARAMETERS, "Parâmetros pendentes de aceite", 1, 15),
                step(STEP_MIGRATION, "Importação do legado e conciliação", 8, 38),
                step(STEP_LINEAGE, "Linhagem da migração registrada", 30, 40),
                step(STEP_DICTIONARY, "Dicionário de dados gerado do schema", 38, 45),
                step(STEP_FISCAL_CERT, "Certificado fiscal", 15, 30),
                step(STEP_PSP_TEST, "Teste com o PSP", 30, 52),
                step(STEP_MFA_POLICY, "Política de MFA", 45, 60),
                step(STEP_RESTORE_REHEARSAL, "Restauração ensaiada", 52, 68),
                step(STEP_DPO, "Encarregado informado", 1, 22),
                step(STEP_DPA, "DPA assinado", 22, 45),
                step(STEP_TRAINING, "Treinamento", 60, 90)));
    }

    /** Problema de validação de um plano concreto contra o modelo. */
    public enum Problem {
        MISSING_MANDATORY_STEP,
        DUPLICATE_STEP,
        STEP_OUT_OF_TOTAL,
        ORDER_VIOLATED,
        GATE_FLAG_MISMATCH
    }

    public record Finding(Problem problem, String stepKey, String detail) {
    }

    /**
     * Valida um plano concreto: todos os passos obrigatórios presentes uma
     * única vez, na ordem canônica, dentro do total declarado, e com a marca
     * de gate do modelo. Devolve TODOS os achados (não para no primeiro).
     */
    public static List<Finding> validate(Plan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("O plano é obrigatório.");
        }
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Step s : plan.steps()) {
            if (!seen.add(s.key())) {
                findings.add(new Finding(Problem.DUPLICATE_STEP, s.key(),
                        "Passo repetido no plano: " + s.key()));
            }
            if (s.endDay() > plan.totalDays()) {
                findings.add(new Finding(Problem.STEP_OUT_OF_TOTAL, s.key(),
                        "Passo termina no dia " + s.endDay() + ", além do total de "
                                + plan.totalDays() + " dias."));
            }
            if (MANDATORY_STEP_KEYS.contains(s.key())
                    && s.gate() != GATE_STEP_KEYS.contains(s.key())) {
                findings.add(new Finding(Problem.GATE_FLAG_MISMATCH, s.key(),
                        "A marca de gate do passo diverge do modelo."));
            }
        }
        for (String key : MANDATORY_STEP_KEYS) {
            if (!seen.contains(key)) {
                findings.add(new Finding(Problem.MISSING_MANDATORY_STEP, key,
                        "Passo obrigatório ausente do plano: " + key));
            }
        }
        // Ordem: a posição de cada passo obrigatório segue a ordem canônica.
        List<String> presentInOrder = new ArrayList<>();
        for (Step s : plan.steps()) {
            if (MANDATORY_STEP_KEYS.contains(s.key()) && !presentInOrder.contains(s.key())) {
                presentInOrder.add(s.key());
            }
        }
        List<String> expected = new ArrayList<>();
        for (String key : MANDATORY_STEP_KEYS) {
            if (presentInOrder.contains(key)) {
                expected.add(key);
            }
        }
        if (!presentInOrder.equals(expected)) {
            findings.add(new Finding(Problem.ORDER_VIOLATED, null,
                    "A ordem dos passos obrigatórios diverge da ordem canônica do modelo."));
        }
        return List.copyOf(findings);
    }

    /** O plano-modelo é válido por construção — trava de regressão dos templates. */
    public static boolean isTemplate(int totalDays) {
        return totalDays == 60 || totalDays == 90;
    }
}
