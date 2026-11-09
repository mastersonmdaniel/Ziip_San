package br.com.ziipsan.core.certification.lims17025;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Dossie de validacao do sistema por versao (ED-51-PD08; PD-08,
 * decisao do dono de 09/11/2026, item 40 do DECISOES-DO-DONO.md —
 * pacote de validacao do sistema por versao para apoio a ISO/IEC
 * 17025, pendencia P-ED51-01 da ED-51).
 *
 * <p>MECANISMO MINIMO, derivado somente dos padroes que a propria
 * ED-51 ja estabeleceu (cada padrao citado na doc
 * ED-51-PD08-VALIDACAO-SISTEMA.md):
 * <ul>
 *   <li><b>nascimento gated:</b> o dossie so nasce com a norma ligada,
 *       pela regua real {@link Lims17025Activation#requireEnabled()}
 *       (ADR-0041) — desligada, nada nasce e nada se apaga;</li>
 *   <li><b>tudo e dado declarado:</b> o identificador da versao do
 *       sistema ({@code systemVersion}), os itens do plano
 *       ({@link SystemValidationPlanItem}) e as referencias de
 *       evidencia das execucoes sao declarados pelo tenant/
 *       laboratorio — nenhum valor de fabrica, nenhum item de
 *       checklist embutido (o conteudo normativo do checklist e
 *       [A DEFINIR], pendencia P-ED51PD08-02);</li>
 *   <li><b>segregacao por segunda pessoa</b> (padrao de
 *       {@code AssayExecution.validateBySecondPerson} da ED-51): quem
 *       aprova/conclui a validacao nao pode ser quem declarou o plano
 *       nem quem executou/registo qualquer item;</li>
 *   <li><b>completude aritmetica sobre o declarado:</b> a validacao so
 *       conclui quando TODOS os itens declarados do plano tem
 *       execucao registada — sem limiar nem faixa: o resultado de cada
 *       item e o como registado ({@link SystemValidationExecution}),
 *       e o desfecho do dossie e a aritmetica sobre esses registos
 *       (qualquer item {@code FAILED} no registo vigente faz o
 *       desfecho {@code FAILED}; todos {@code PASSED}, {@code PASSED}
 *       — nenhuma reinterpretacao);</li>
 *   <li><b>rastreabilidade sem reescrita</b> (padroes de
 *       {@code LabEquipment} e {@code LabReport} da ED-51): as
 *       execucoes sao historico por acrescimo — um novo registo para
 *       o mesmo item PRESERVA o anterior e passa a ser o vigente; a
 *       correcao de um dossie CONCLUIDO faz-se por EMENDA: nasce a
 *       revisao seguinte, com motivo obrigatorio e referencia a
 *       revisao substituida, e a anterior fica {@code SUPERSEDED},
 *       preservada com o seu historico.</li>
 * </ul>
 *
 * <p>O que este mecanismo NAO faz, porque nenhuma fonte o declara
 * (pendencias na doc): nao bloqueia o uso do laboratorio por versao
 * nao validada; nao fixa gatilhos de revalidacao por tipo de mudanca
 * de versao, prazos nem retencao; nao conhece nenhum requisito ou
 * clausula da ISO/IEC 17025 alem do que a ED-51 ja fixou.
 *
 * <p>Maquina de estados: OPEN -> CONCLUDED, com a revisao concluida
 * passando a SUPERSEDED quando emendada.
 */
public final class SystemValidation {

    public enum Status { OPEN, CONCLUDED, SUPERSEDED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID laboratoryId;
    private final String systemVersion;
    private final List<SystemValidationPlanItem> plan;
    private final Map<String, SystemValidationPlanItem> planByKey;
    private final String declaredByRef;
    private final Instant declaredAt;
    private final int revision;
    private final UUID supersedesId;
    private final String amendmentReason;

    private final List<SystemValidationExecution> executions = new ArrayList<>();
    private Status status = Status.OPEN;
    private String concludedByRef;
    private Instant concludedAt;

    private SystemValidation(UUID id, UUID tenantId, UUID laboratoryId, String systemVersion,
                             List<SystemValidationPlanItem> plan, String declaredByRef,
                             Instant declaredAt, int revision, UUID supersedesId,
                             String amendmentReason) {
        this.id = id;
        this.tenantId = tenantId;
        this.laboratoryId = laboratoryId;
        this.systemVersion = systemVersion;
        this.plan = List.copyOf(plan);
        this.planByKey = new LinkedHashMap<>();
        for (SystemValidationPlanItem item : plan) this.planByKey.put(item.itemKey(), item);
        this.declaredByRef = declaredByRef;
        this.declaredAt = declaredAt;
        this.revision = revision;
        this.supersedesId = supersedesId;
        this.amendmentReason = amendmentReason;
    }

    private static List<SystemValidationPlanItem> requirePlan(List<SystemValidationPlanItem> plan) {
        if (plan == null || plan.isEmpty())
            throw new IllegalArgumentException("o plano de validacao e obrigatorio e nao pode ser "
                    + "vazio: sem itens declarados nao ha o que validar (nenhum checklist de "
                    + "fabrica substitui a declaracao do tenant)");
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (SystemValidationPlanItem item : plan) {
            Objects.requireNonNull(item, "item do plano nao pode ser nulo");
            if (seen.put(item.itemKey(), Boolean.TRUE) != null)
                throw new IllegalArgumentException("item do plano duplicado: " + item.itemKey());
        }
        return plan;
    }

    /**
     * Nascimento do dossie (revisao 1): so com a norma ligada, com a
     * versao do sistema e o plano DECLARADOS pelo tenant/laboratorio.
     */
    public static SystemValidation open(Lims17025Activation activation,
                                        UUID id, UUID tenantId, UUID laboratoryId,
                                        String systemVersion, List<SystemValidationPlanItem> plan,
                                        String declaredByRef, Instant declaredAt) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        Objects.requireNonNull(id, "id do dossie de validacao e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(laboratoryId, "laboratoryId e obrigatorio (o laboratorio do dossie)");
        if (systemVersion == null || systemVersion.isBlank())
            throw new IllegalArgumentException("systemVersion e obrigatorio (o identificador da "
                    + "versao do sistema, declarado pelo tenant/laboratorio)");
        requirePlan(plan);
        if (declaredByRef == null || declaredByRef.isBlank())
            throw new IllegalArgumentException("declaredByRef e obrigatorio (quem declara o plano)");
        Objects.requireNonNull(declaredAt, "declaredAt e obrigatorio");
        return new SystemValidation(id, tenantId, laboratoryId, systemVersion, plan,
                declaredByRef, declaredAt, 1, null, null);
    }

    /**
     * Registo de execucao de um item do plano, por acrescimo ao
     * historico: um novo registo para um item ja executado PRESERVA o
     * anterior (que continua no historico) e passa a ser o vigente —
     * nunca ha sobrescrita (padrao do historico de calibracao do
     * {@code LabEquipment} da ED-51).
     */
    public void recordExecution(SystemValidationExecution execution) {
        if (status != Status.OPEN)
            throw new IllegalStateException("dossie " + id + ": execucao exige estado OPEN, "
                    + "estado atual " + status + " — a correcao de dossie concluido faz-se "
                    + "por emenda (amend), que preserva esta revisao");
        Objects.requireNonNull(execution, "execution e obrigatoria");
        if (!planByKey.containsKey(execution.itemKey()))
            throw new IllegalArgumentException("item " + execution.itemKey() + " nao faz parte "
                    + "do plano declarado deste dossie");
        executions.add(execution);
    }

    /** Execucao vigente de um item: o registo mais recente do historico, se houver. */
    public Optional<SystemValidationExecution> currentExecution(String itemKey) {
        SystemValidationExecution current = null;
        for (SystemValidationExecution e : executions) {
            if (e.itemKey().equals(itemKey)) current = e;
        }
        return Optional.ofNullable(current);
    }

    /** Verdadeiro quando todos os itens declarados do plano tem execucao registada. */
    public boolean isComplete() {
        for (String key : planByKey.keySet()) {
            if (currentExecution(key).isEmpty()) return false;
        }
        return true;
    }

    /** Itens declarados do plano ainda sem execucao registada. */
    public List<String> missingItemKeys() {
        List<String> missing = new ArrayList<>();
        for (String key : planByKey.keySet()) {
            if (currentExecution(key).isEmpty()) missing.add(key);
        }
        return List.copyOf(missing);
    }

    /**
     * Desfecho aritmetico sobre os registos vigentes: so existe com o
     * plano completo; qualquer item vigente {@code FAILED} faz o
     * desfecho {@code FAILED}, todos {@code PASSED} fazem {@code
     * PASSED}. Nenhum limiar nem faixa — o resultado de cada item e o
     * como registado por quem o executou.
     */
    public Optional<SystemValidationExecution.Outcome> outcome() {
        if (!isComplete()) return Optional.empty();
        for (String key : planByKey.keySet()) {
            if (currentExecution(key).orElseThrow().outcome()
                    == SystemValidationExecution.Outcome.FAILED) {
                return Optional.of(SystemValidationExecution.Outcome.FAILED);
            }
        }
        return Optional.of(SystemValidationExecution.Outcome.PASSED);
    }

    /**
     * Conclusao/aprovacao da validacao por SEGUNDA PESSOA (padrao de
     * {@code AssayExecution.validateBySecondPerson} da ED-51): quem
     * conclui nao pode ser quem declarou o plano nem quem executou/
     * registou qualquer item. E a completude e aritmetica: item
     * declarado sem execucao registada recusa a conclusao.
     */
    public void conclude(String concludedBy, Instant at) {
        if (status != Status.OPEN)
            throw new IllegalStateException("dossie " + id + ": conclusao exige estado OPEN, "
                    + "estado atual " + status);
        if (concludedBy == null || concludedBy.isBlank())
            throw new IllegalArgumentException("concludedByRef e obrigatorio (quem aprova/conclui "
                    + "a validacao — a segunda pessoa)");
        Objects.requireNonNull(at, "concludedAt e obrigatorio");
        if (concludedBy.equals(declaredByRef))
            throw new IllegalArgumentException("segregacao por segunda pessoa: quem conclui ("
                    + concludedBy + ") nao pode ser quem declarou o plano da validacao");
        for (SystemValidationExecution e : executions) {
            if (concludedBy.equals(e.executedByRef()))
                throw new IllegalArgumentException("segregacao por segunda pessoa: quem conclui ("
                        + concludedBy + ") nao pode ser quem executou/registo itens da validacao "
                        + "(item " + e.itemKey() + ")");
        }
        List<String> missing = missingItemKeys();
        if (!missing.isEmpty())
            throw new IllegalStateException("validacao nao conclui: itens declarados do plano sem "
                    + "execucao registada: " + missing + " (completude aritmetica sobre o plano "
                    + "declarado — nenhum item declarado pode faltar)");
        this.concludedByRef = concludedBy;
        this.concludedAt = at;
        this.status = Status.CONCLUDED;
    }

    /**
     * Emenda rastreavel de um dossie CONCLUIDO (padrao de
     * {@code LabReport.amend} da ED-51): nasce a revisao seguinte —
     * mesmo tenant/laboratorio, versao do sistema e plano como
     * declarados na emenda — com o motivo obrigatorio e a referencia
     * a esta revisao; esta fica {@code SUPERSEDED}, preservada com o
     * seu historico de execucoes e a sua conclusao. Emendar revisao
     * nao vigente (OPEN ou ja SUPERSEDED) e recusado.
     */
    public SystemValidation amend(Lims17025Activation activation, UUID newId,
                                  String newSystemVersion, List<SystemValidationPlanItem> newPlan,
                                  String reason, String amendedByRef, Instant amendedAt) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        if (status != Status.CONCLUDED)
            throw new IllegalStateException("dossie " + id + ": emenda exige dossie CONCLUIDO, "
                    + "estado atual " + status + " — dossie OPEN corrige-se por novo registo de "
                    + "execucao no historico, sem emenda");
        Objects.requireNonNull(newId, "id da nova revisao e obrigatorio");
        if (newSystemVersion == null || newSystemVersion.isBlank())
            throw new IllegalArgumentException("systemVersion da emenda e obrigatorio (declarado)");
        requirePlan(newPlan);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("emenda de dossie concluido exige o motivo "
                    + "(rastreabilidade)");
        if (amendedByRef == null || amendedByRef.isBlank())
            throw new IllegalArgumentException("amendedByRef e obrigatorio (quem emenda)");
        Objects.requireNonNull(amendedAt, "amendedAt e obrigatorio");
        SystemValidation next = new SystemValidation(newId, tenantId, laboratoryId,
                newSystemVersion, newPlan, amendedByRef, amendedAt,
                revision + 1, id, reason);
        this.status = Status.SUPERSEDED;
        return next;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID laboratoryId() { return laboratoryId; }
    public String systemVersion() { return systemVersion; }
    /** Plano declarado, na ordem declarada (copia imutavel). */
    public List<SystemValidationPlanItem> plan() { return plan; }
    public String declaredByRef() { return declaredByRef; }
    public Instant declaredAt() { return declaredAt; }
    public int revision() { return revision; }
    public UUID supersedesId() { return supersedesId; }
    public String amendmentReason() { return amendmentReason; }
    /** Historico completo de execucoes, em ordem de registo (nunca truncado nem reescrito). */
    public List<SystemValidationExecution> executions() { return List.copyOf(executions); }
    public Status status() { return status; }
    public String concludedByRef() { return concludedByRef; }
    public Instant concludedAt() { return concludedAt; }
    public boolean concluded() { return status == Status.CONCLUDED; }
}
