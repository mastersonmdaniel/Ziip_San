package br.com.ziipsan.platform.training;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plano de treinamento (ED-11): 40 h por módulo, como manda o aceite.
 *
 * <p>O único número de carga horária com fonte é o do próprio aceite da
 * ED-11 — <b>40 h por módulo</b>. O que é "módulo" aqui é o módulo de
 * treinamento do catálogo abaixo, e cada módulo do catálogo está amarrado a
 * áreas reais do produto (tarefas do plano v3.0 e módulos do repositório):
 * nenhum módulo de treinamento existe sem a função correspondente existir
 * no ZiipSan. Um tenant não cursa necessariamente o catálogo inteiro: o
 * conjunto de módulos requeridos de uma implantação é o escopo declarado
 * daquela implantação (ver {@link TrainingGate}) — esta classe não decide
 * escopo por cliente.
 *
 * <p>A divisão das 40 h em unidades é <b>desenho do modelo</b> (como as
 * janelas de dias dos planos-modelo da ED-09): cinco fases canônicas, na
 * mesma ordem em todo módulo — fundamentos, operação guiada no tenant de
 * pré-treinamento, prática supervisionada, exceções e controles, avaliação
 * e encerramento. O plano concreto de um módulo pode redistribuir as horas
 * entre as suas unidades, desde que a validação passe: total de exatamente
 * 40 h, nenhuma unidade repetida ou zerada e módulo conhecido do catálogo.
 * O que esta classe NÃO faz é prometer aproveitamento: o critério mínimo
 * de aproveitamento da avaliação não consta de nenhuma fonte do projeto e
 * fica como pendência nomeada no pacote da ED-11 — a conclusão do módulo
 * (ver {@link TrainingCompletion}) mede entrega e participação, não nota.
 */
public final class TrainingPlan {

    private TrainingPlan() {
    }

    /** Carga horária por módulo, do aceite da ED-11. É o único total válido. */
    public static final int REQUIRED_HOURS_PER_MODULE = 40;

    /**
     * Um módulo de treinamento do catálogo.
     *
     * @param key          chave estável do módulo
     * @param title        título do módulo
     * @param sourceRefs   tarefas do plano v3.0 que constroem a área treinada
     * @param audiences    perfis de fábrica da P1.37 a quem o módulo se destina
     *                     (nomes como no aceite da P1.37; papéis mínimos da
     *                     P0.13 e papéis de plataforma da P0.43 onde citados)
     */
    public record TrainingModule(String key, String title, List<String> sourceRefs,
                                 List<String> audiences) {
        public TrainingModule {
            if (key == null || key.isBlank() || title == null || title.isBlank()) {
                throw new IllegalArgumentException("Módulo de treinamento exige chave e título.");
            }
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
        }
    }

    public static final String MOD_CADASTRO = "CADASTRO";
    public static final String MOD_MEDICAO_CONTA = "MEDICAO_CONTA";
    public static final String MOD_ARRECADACAO = "ARRECADACAO";
    public static final String MOD_FISCAL = "FISCAL";
    public static final String MOD_PORTAL = "PORTAL_ATENDIMENTO";
    public static final String MOD_OS_CAMPO = "OS_CAMPO";
    public static final String MOD_GIS = "GIS_CADASTRO_TECNICO";
    public static final String MOD_PERDAS_QUALIDADE = "PERDAS_QUALIDADE";
    public static final String MOD_PLATAFORMA = "PLATAFORMA_IMPLANTACAO";
    public static final String MOD_PRIVACIDADE = "PRIVACIDADE_SEGURANCA";
    public static final String MOD_TI = "TI_CLIENTE";

    /** Catálogo de módulos de treinamento, amarrado às áreas reais do produto. */
    public static final List<TrainingModule> CATALOG = List.of(
            new TrainingModule(MOD_CADASTRO, "Cadastro e unidade consumidora",
                    List.of("P1.3", "P1.2", "P1.31", "P1.41", "P1.40"),
                    List.of("atendente", "supervisor comercial", "gestor")),
            new TrainingModule(MOD_MEDICAO_CONTA, "Medição, ciclo e conta",
                    List.of("P2.5", "P2.7", "P2.20", "P2.29"),
                    List.of("leiturista", "faturista", "supervisor comercial")),
            new TrainingModule(MOD_ARRECADACAO, "Arrecadação: retorno, Pix e contas a receber",
                    List.of("P3.1", "P3.2", "P3.29", "P2.5"),
                    List.of("analista de arrecadação", "faturista", "contador")),
            new TrainingModule(MOD_FISCAL, "Fiscal: NFAg-e, NFS-e e documentos fiscais",
                    List.of("P1.8", "P2.6", "P2.13", "P3.6", "P2.33", "P3.7"),
                    List.of("faturista", "contador", "gestor")),
            new TrainingModule(MOD_PORTAL, "Portal do consumidor e atendimento",
                    List.of("P3.3", "P3.20", "P1.36", "P3.36", "P3.35"),
                    List.of("atendente", "ouvidor", "supervisor comercial")),
            new TrainingModule(MOD_OS_CAMPO, "Ordens de serviço e operação de campo",
                    List.of("P1.31", "P1.35", "P3.4", "P2.28"),
                    List.of("programador de OS", "executor de campo", "almoxarife",
                            "operador de estação")),
            new TrainingModule(MOD_GIS, "Cadastro técnico e GIS",
                    List.of("P1.26", "P1.27", "P3.25", "P3.26", "P3.27", "P2.21", "P2.22", "P2.23"),
                    List.of("editor do cadastro técnico", "revisor do cadastro técnico", "gestor")),
            new TrainingModule(MOD_PERDAS_QUALIDADE, "Perdas, qualidade e certificações",
                    List.of("P1.32", "P3.28", "P3.27"),
                    List.of("analista de perdas", "laboratorista",
                            "responsável técnico da qualidade", "auditor interno")),
            new TrainingModule(MOD_PLATAFORMA, "Plataforma, parâmetros e jornada de implantação",
                    List.of("P1.6", "P0.19", "P0.13", "P1.37", "ED-09"),
                    List.of("ADMIN", "gestor")),
            new TrainingModule(MOD_PRIVACIDADE, "Privacidade, LGPD e segurança de acesso",
                    List.of("P3.40", "P3.10", "P0.11", "P0.15", "P3.17", "P0.13"),
                    List.of("encarregado de dados", "auditor interno", "ADMIN")),
            new TrainingModule(MOD_TI, "Capacitação da TI do cliente",
                    List.of("P0.29", "P2.26", "P3.37", "P0.28", "P3.33", "P3.11", "LC.15"),
                    List.of("ADMIN")));

    public static TrainingModule module(String key) {
        for (TrainingModule m : CATALOG) {
            if (m.key().equals(key)) {
                return m;
            }
        }
        return null;
    }

    // Fases canônicas de qualquer módulo (desenho do modelo; somam 40 h).
    public static final String UNIT_FUNDAMENTOS = "FUNDAMENTOS";
    public static final String UNIT_OPERACAO_GUIADA = "OPERACAO_GUIADA";
    public static final String UNIT_PRATICA = "PRATICA_SUPERVISIONADA";
    public static final String UNIT_EXCECOES = "EXCECOES_E_CONTROLES";
    public static final String UNIT_AVALIACAO = "AVALIACAO_E_ENCERRAMENTO";

    public record Unit(String key, String title, int hours) {
        public Unit {
            if (key == null || key.isBlank() || title == null || title.isBlank()) {
                throw new IllegalArgumentException("Unidade de treinamento exige chave e título.");
            }
            if (hours <= 0) {
                throw new IllegalArgumentException(
                        "Unidade com carga horária não positiva: " + key);
            }
        }
    }

    /** Plano de um módulo: o conjunto de unidades que soma as 40 h. */
    public record ModulePlan(String moduleKey, List<Unit> units) {
        public ModulePlan {
            if (moduleKey == null || moduleKey.isBlank()) {
                throw new IllegalArgumentException("O plano exige o módulo.");
            }
            units = units == null ? List.of() : List.copyOf(units);
        }

        public int totalHours() {
            int total = 0;
            for (Unit u : units) {
                total += u.hours();
            }
            return total;
        }
    }

    /** Plano-padrão de um módulo do catálogo: as cinco fases canônicas. */
    public static ModulePlan standardPlan(String moduleKey) {
        TrainingModule m = module(moduleKey);
        if (m == null) {
            throw new IllegalArgumentException("Módulo fora do catálogo: " + moduleKey);
        }
        return new ModulePlan(moduleKey, List.of(
                new Unit(UNIT_FUNDAMENTOS, "Fundamentos e mapa do módulo: " + m.title(), 8),
                new Unit(UNIT_OPERACAO_GUIADA,
                        "Operação guiada no tenant de pré-treinamento: " + m.title(), 12),
                new Unit(UNIT_PRATICA,
                        "Prática supervisionada com os exercícios do roteiro: " + m.title(), 12),
                new Unit(UNIT_EXCECOES,
                        "Exceções, controles e auditoria do módulo: " + m.title(), 4),
                new Unit(UNIT_AVALIACAO,
                        "Avaliação de aproveitamento e encerramento: " + m.title(), 4)));
    }

    public enum Problem {
        UNKNOWN_MODULE,
        EMPTY_PLAN,
        TOTAL_DIFFERS_FROM_40,
        DUPLICATE_UNIT
    }

    public record Finding(Problem problem, String moduleKey, String detail) {
    }

    /**
     * Valida um plano concreto contra o modelo: módulo do catálogo, pelo
     * menos uma unidade, nenhuma unidade repetida e total de exatamente
     * {@link #REQUIRED_HOURS_PER_MODULE} horas. Devolve TODOS os achados.
     */
    public static List<Finding> validate(ModulePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("O plano do módulo é obrigatório.");
        }
        List<Finding> findings = new ArrayList<>();
        if (module(plan.moduleKey()) == null) {
            findings.add(new Finding(Problem.UNKNOWN_MODULE, plan.moduleKey(),
                    "Módulo fora do catálogo de treinamento: " + plan.moduleKey()));
        }
        if (plan.units().isEmpty()) {
            findings.add(new Finding(Problem.EMPTY_PLAN, plan.moduleKey(),
                    "Plano sem nenhuma unidade: " + plan.moduleKey()));
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Unit u : plan.units()) {
            if (!seen.add(u.key())) {
                findings.add(new Finding(Problem.DUPLICATE_UNIT, plan.moduleKey(),
                        "Unidade repetida no plano: " + u.key()));
            }
        }
        if (plan.totalHours() != REQUIRED_HOURS_PER_MODULE) {
            findings.add(new Finding(Problem.TOTAL_DIFFERS_FROM_40, plan.moduleKey(),
                    "O plano soma " + plan.totalHours() + " h; o aceite da ED-11 exige "
                            + REQUIRED_HOURS_PER_MODULE + " h por módulo."));
        }
        return List.copyOf(findings);
    }
}
