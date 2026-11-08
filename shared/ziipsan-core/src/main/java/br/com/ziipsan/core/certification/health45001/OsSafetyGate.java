package br.com.ziipsan.core.certification.health45001;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ANÁLISE DE RISCO DA TAREFA e PERMISSÃO DE TRABALHO como ETAPAS DA OS
 * (ED-71), exigidas por tipo de OS de risco, executadas no backoffice
 * pela ED-36 e em campo pela ED-20 sobre a MESMA régua (o canal é só um
 * dado da avaliação: {@link Channel}).
 *
 * <p>FRONTEIRA com a P1.31: o motor de OS já guarda os fatos em
 * {@code service_order_safety} (refs de análise/permissão, profundidade
 * da vala, liberação), {@code service_order_crew} (executor, papel,
 * {@code qualification_code} e validade — é a HABILITAÇÃO do executor,
 * achado OPE-14) e {@code service_order_entry_permits} (PET de espaço
 * confinado) da V9_1. Esta classe NÃO redefine essas tabelas: é a régua
 * de PORTÃO que lê os mesmos fatos (por referência) e decide se a OS de
 * risco pode INICIAR. O catálogo de SST é o do tenant na P1.31
 * ({@code sst.required_controls_by_service_type}, classe c — o tenant só
 * adiciona controles); os códigos de habilitação são os códigos desse
 * catálogo, inclusive a NR-10 (achado RAS-25, citada na V9_1 na redação
 * vigente a partir de 01/06/2027 — aqui entra só como CÓDIGO de
 * habilitação exigido para trabalho elétrico, nunca como texto da NR).
 *
 * <p>Regras do portão (todas devolvidas como bloqueios nomeados por
 * {@link #evaluateStart(LocalDate)}; {@link #requireStartable(LocalDate)}
 * lança com a lista):
 * <ul>
 *   <li>tipo de risco (qualquer flag) sem análise de risco registrada →
 *       bloqueia;</li>
 *   <li>espaço confinado, altura ou trabalho elétrico sem permissão de
 *       trabalho registrada → bloqueia;</li>
 *   <li>espaço confinado com menos de 2 executores → bloqueia (trabalho
 *       solitário vedado; piso da P1.31, {@code sst.min_crew_confined_space});</li>
 *   <li>vala com profundidade &gt; 1,25 m sem liberação de profissional
 *       habilitado → bloqueia (a profundidade continua registrável sem
 *       liberação — quem bloqueia é o INÍCIO, como na V9_1);</li>
 *   <li>executor sem habilitação válida na data de início → bloqueia
 *       (fail-closed da P1.31: sem habilitação, sem programação e sem
 *       encerramento); trabalho elétrico exige, de CADA executor, a
 *       habilitação de código {@value #ELECTRICAL_QUALIFICATION_CODE}
 *       válida na data.</li>
 * </ul>
 */
public record OsSafetyGate(Iso45001Activation activation, UUID orderId, String serviceTypeCode,
                           Channel channel, boolean confinedSpace, boolean trenchWork,
                           boolean heightWork, boolean electricalWork, BigDecimal trenchDepthM,
                           String riskAnalysisRef, UUID riskAnalysisApprovedBy,
                           String workPermitRef, List<ExecutorQualification> executors,
                           UUID qualifiedReleaseBy) {

    /** Canal de execução da etapa: backoffice (ED-36) ou campo (ED-20) — a régua é a mesma. */
    public enum Channel {
        BACKOFFICE, FIELD
    }

    /** Código de habilitação elétrica no catálogo de SST da P1.31 (RAS-25: NR-10). */
    public static final String ELECTRICAL_QUALIFICATION_CODE = "NR-10";
    /** Profundidade (m) a partir da qual a vala exige liberação (âncora da V9_1/P1.31). */
    public static final BigDecimal TRENCH_RELEASE_DEPTH_M = new BigDecimal("1.25");
    /** Equipe mínima em espaço confinado (piso da P1.31: trabalho solitário vedado). */
    public static final int CONFINED_MIN_CREW = 2;

    /** Habilitação de um executor: código do catálogo de SST da P1.31 + validade (OPE-14). */
    public record ExecutorQualification(UUID workerId, String qualificationCode,
                                        LocalDate validUntil) {
        public ExecutorQualification {
            Objects.requireNonNull(workerId, "workerId do executor é obrigatório");
            if (qualificationCode == null || qualificationCode.isBlank()
                    || qualificationCode.trim().length() > 100) {
                throw new IllegalArgumentException("qualificationCode (código do catálogo de SST "
                        + "da P1.31) é obrigatório e tem no máximo 100 caracteres");
            }
            qualificationCode = qualificationCode.trim();
            Objects.requireNonNull(validUntil, "validUntil da habilitação é obrigatório");
        }

        /** Habilitação válida na data (validade inclusiva). */
        public boolean validOn(LocalDate date) {
            return !validUntil.isBefore(date);
        }
    }

    /** Resultado do portão: pode iniciar + bloqueios nomeados (vazio = liberado). */
    public record GateResult(boolean startable, List<String> blockers) {
        public GateResult {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            if (startable != blockers.isEmpty()) {
                throw new IllegalArgumentException("GateResult incoerente: startable deve ser "
                        + "exatamente 'sem bloqueios'");
            }
        }
    }

    public OsSafetyGate {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        Objects.requireNonNull(orderId, "orderId (OS da P1.31) é obrigatório");
        if (serviceTypeCode == null || serviceTypeCode.isBlank()
                || serviceTypeCode.trim().length() > 60) {
            throw new IllegalArgumentException("serviceTypeCode (tipo de OS da P1.31) é "
                    + "obrigatório e tem no máximo 60 caracteres");
        }
        serviceTypeCode = serviceTypeCode.trim();
        Objects.requireNonNull(channel, "channel (ED-36 backoffice ou ED-20 campo) é obrigatório");
        if (trenchDepthM != null && trenchDepthM.signum() < 0) {
            throw new IllegalArgumentException("trenchDepthM não pode ser negativa");
        }
        if (!trenchWork && trenchDepthM != null) {
            throw new IllegalArgumentException("trenchDepthM só vale com trenchWork declarado");
        }
        riskAnalysisRef = blankToNull(riskAnalysisRef);
        workPermitRef = blankToNull(workPermitRef);
        if ((riskAnalysisRef == null) != (riskAnalysisApprovedBy == null)) {
            throw new IllegalArgumentException("análise de risco registrada exige aprovador, e "
                    + "aprovador sem análise registrada é incoerente");
        }
        executors = executors == null ? List.of() : List.copyOf(executors);
    }

    /** Nascimento da avaliação: só com a norma ligada (a ativação é componente e reconfere). */
    public static OsSafetyGate assess(Iso45001Activation activation, UUID orderId,
                                      String serviceTypeCode, Channel channel,
                                      boolean confinedSpace, boolean trenchWork, boolean heightWork,
                                      boolean electricalWork, BigDecimal trenchDepthM,
                                      String riskAnalysisRef, UUID riskAnalysisApprovedBy,
                                      String workPermitRef,
                                      List<ExecutorQualification> executors,
                                      UUID qualifiedReleaseBy) {
        return new OsSafetyGate(activation, orderId, serviceTypeCode, channel, confinedSpace,
                trenchWork, heightWork, electricalWork, trenchDepthM, riskAnalysisRef,
                riskAnalysisApprovedBy, workPermitRef, executors, qualifiedReleaseBy);
    }

    /** Tipo de risco: qualquer flag de perigo declarada no tipo de OS. */
    public boolean riskyType() {
        return confinedSpace || trenchWork || heightWork || electricalWork;
    }

    /** Permissão de trabalho exigida para este tipo (espaço confinado, altura, elétrico). */
    public boolean workPermitRequired() {
        return confinedSpace || heightWork || electricalWork;
    }

    /** Avalia o portão de início na data: devolve os bloqueios nomeados, sem lançar. */
    public GateResult evaluateStart(LocalDate startDate) {
        Objects.requireNonNull(startDate, "startDate é obrigatória");
        List<String> blockers = new ArrayList<>();
        if (riskyType() && riskAnalysisRef == null) {
            blockers.add("ANALISE_DE_RISCO_AUSENTE");
        }
        if (workPermitRequired() && workPermitRef == null) {
            blockers.add("PERMISSAO_DE_TRABALHO_AUSENTE");
        }
        if (confinedSpace && distinctWorkers() < CONFINED_MIN_CREW) {
            blockers.add("EQUIPE_MINIMA_ESPACO_CONFINADO");
        }
        if (trenchWork && trenchDepthM != null
                && trenchDepthM.compareTo(TRENCH_RELEASE_DEPTH_M) > 0
                && qualifiedReleaseBy == null) {
            blockers.add("LIBERACAO_VALA_PROFUNDA_AUSENTE");
        }
        if (executors.isEmpty()) {
            blockers.add("SEM_EXECUTOR");
        }
        for (UUID worker : executors.stream().map(ExecutorQualification::workerId)
                .distinct().toList()) {
            List<ExecutorQualification> quals = executors.stream()
                    .filter(q -> q.workerId().equals(worker)).toList();
            if (quals.stream().noneMatch(q -> q.validOn(startDate))) {
                blockers.add("HABILITACAO_VENCIDA_OU_AUSENTE:" + worker);
            } else if (electricalWork && quals.stream().noneMatch(q -> q.validOn(startDate)
                    && ELECTRICAL_QUALIFICATION_CODE.equals(q.qualificationCode()))) {
                blockers.add("HABILITACAO_ELETRICA_AUSENTE:" + worker);
            }
        }
        return new GateResult(blockers.isEmpty(), blockers);
    }

    /** Portão imperativo: lança com todos os bloqueios se a OS não pode iniciar na data. */
    public void requireStartable(LocalDate startDate) {
        GateResult result = evaluateStart(startDate);
        if (!result.startable()) {
            throw new IllegalStateException("OS de risco não pode iniciar em " + startDate
                    + ": " + String.join(", ", result.blockers()));
        }
    }

    private long distinctWorkers() {
        return executors.stream().map(ExecutorQualification::workerId).distinct().count();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
