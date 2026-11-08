package br.com.ziipsan.intelligence.losses.pressure;

import br.com.ziipsan.core.incident.LeakDetectionFinding;
import br.com.ziipsan.intelligence.losses.dmc.NightFlowAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Campanha de pesquisa de vazamento nao visivel por DMC (ED-86,
 * OPE-17), sobre o balanco da ED-85: a campanha nasce com HIPOTESE,
 * so inicia com EQUIPAMENTO e EQUIPE atribuidos, acumula EVIDENCIA
 * e ACHADOS, e so conclui com o GANHO medido pela comparacao da
 * vazao minima noturna (liquida) ANTES e DEPOIS — as duas medicoes
 * sao o {@code NightFlowResult} real da ED-85
 * ({@link NightFlowAnalyzer}), nao um numero solto.
 *
 * <p>O ACHADO e o modelo da ED-92, consumido sem redefinicao:
 * {@link LeakDetectionFinding} (DAD-27) — o achado liga ocorrencia e
 * OS e o REPARO vive so na OS. Por isso a campanha nao tem estado de
 * reparo do achado: ela guarda as OS de reparo vinculadas e exige,
 * para concluir, todo achado com disposicao final (CONFIRMED_LINKED
 * ou NOT_A_LEAK) — achado pendente bloqueia a conclusao.
 */
public final class LeakSurveyCampaign {

    public enum Status { PLANNED, IN_PROGRESS, COMPLETED, CANCELLED }

    /** Ganho medido: vazao noturna liquida antes x depois (L/h). */
    public record SurveyGain(double beforeNetNightFlowLPerH, double afterNetNightFlowLPerH,
                             double absoluteGainLPerH, OptionalDouble percentGain) {}

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final UUID dmcProfileId;
    private final String dmcCode;
    private final String dmcClass;
    private final String hypothesis;
    private List<String> equipment = List.of();
    private List<String> teamMemberRefs = List.of();
    private Status status = Status.PLANNED;
    private final List<String> evidenceRefs = new ArrayList<>();
    private final List<LeakDetectionFinding> findings = new ArrayList<>();
    private final List<UUID> repairWorkOrderIds = new ArrayList<>();
    private NightFlowAnalyzer.NightFlowResult nightFlowBefore;
    private NightFlowAnalyzer.NightFlowResult nightFlowAfter;
    private SurveyGain gain;

    public LeakSurveyCampaign(UUID id, UUID tenantId, String code, UUID dmcProfileId,
                              String dmcCode, String dmcClass, String hypothesis) {
        this.id = Objects.requireNonNull(id, "campanha sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "campanha sem tenant");
        if (code == null || code.isBlank()) throw new IllegalArgumentException(
                "codigo da campanha obrigatorio (unico por tenant)");
        this.dmcProfileId = Objects.requireNonNull(dmcProfileId,
                "campanha exige o DMC (perfil loss_dmc_profiles da ED-85)");
        if (dmcCode == null || dmcCode.isBlank()) throw new IllegalArgumentException("codigo do DMC obrigatorio");
        if (dmcClass == null || dmcClass.isBlank()) throw new IllegalArgumentException(
                "classe de DMC obrigatoria (chave de leak.survey_cycle_days_by_dmc_class)");
        if (hypothesis == null || hypothesis.isBlank()) throw new IllegalArgumentException(
                "hipotese da campanha obrigatoria (aceite da ED-86)");
        this.code = code;
        this.dmcCode = dmcCode;
        this.dmcClass = dmcClass;
        this.hypothesis = hypothesis;
    }

    /** Equipamento e equipe da pesquisa (exigidos para iniciar). */
    public void assignResources(List<String> equipment, List<String> teamMemberRefs) {
        if (status != Status.PLANNED) throw new IllegalStateException(
                "equipamento/equipe so se atribuem no planejamento: " + status);
        Objects.requireNonNull(equipment, "equipamento obrigatorio");
        Objects.requireNonNull(teamMemberRefs, "equipe obrigatoria");
        if (equipment.isEmpty() || equipment.stream().anyMatch(e -> e == null || e.isBlank()))
            throw new IllegalArgumentException("campanha exige ao menos um equipamento identificado");
        if (teamMemberRefs.isEmpty() || teamMemberRefs.stream().anyMatch(e -> e == null || e.isBlank()))
            throw new IllegalArgumentException("campanha exige ao menos um membro de equipe identificado");
        this.equipment = List.copyOf(equipment);
        this.teamMemberRefs = List.copyOf(teamMemberRefs);
    }

    public void start() {
        if (status != Status.PLANNED) throw new IllegalStateException("campanha so inicia de PLANNED: " + status);
        if (equipment.isEmpty() || teamMemberRefs.isEmpty()) throw new IllegalStateException(
                "campanha nao inicia sem equipamento e equipe atribuidos");
        status = Status.IN_PROGRESS;
    }

    public void addEvidence(String evidenceRef) {
        requireInProgress("evidencia");
        if (evidenceRef == null || evidenceRef.isBlank()) throw new IllegalArgumentException(
                "referencia de evidencia obrigatoria");
        evidenceRefs.add(evidenceRef);
    }

    /** Achado no modelo da ED-92 (DAD-27): mesmo tenant, codigo unico na campanha. */
    public void addFinding(LeakDetectionFinding finding) {
        requireInProgress("achado");
        Objects.requireNonNull(finding, "achado obrigatorio");
        if (!finding.tenantId().equals(tenantId)) throw new IllegalArgumentException(
                "achado de outro tenant: " + finding.code());
        if (findings.stream().anyMatch(f -> f.code().equals(finding.code())))
            throw new IllegalArgumentException("achado ja registrado na campanha: " + finding.code());
        findings.add(finding);
    }

    /** OS de reparo vinculada a campanha (o reparo vive na OS — DAD-27). */
    public void linkRepairWorkOrder(UUID workOrderId) {
        requireInProgress("reparo");
        Objects.requireNonNull(workOrderId, "OS de reparo obrigatoria");
        if (!repairWorkOrderIds.contains(workOrderId)) repairWorkOrderIds.add(workOrderId);
    }

    /**
     * Medicao ANTES sobre o balanco da ED-85.
     *
     * <p><b>Limitacao declarada (ressalva D4 da revisao da ED-86,
     * pendencia P-ED86-01):</b> o tipo {@code NightFlowResult} da
     * ED-85 NAO carrega o DMC de origem nem o instante da medicao —
     * logo este dominio NAO tem como conferir que o resultado veio
     * do DMC desta campanha, nem que precede a medicao posterior.
     * A FIACAO (integracao) deve garantir, ao chamar este metodo e
     * {@link #recordNightFlowAfter}: (1) mesma origem — resultado
     * produzido pelo {@code NightFlowAnalyzer} sobre o perfil do
     * DMC desta campanha ({@link #dmcProfileId()}); e (2) ordem —
     * a medicao "antes" capturada antes do reparo/pesquisa e a
     * "depois" apos ele. Sem essa garantia da fiacao, uma chamada
     * trocada computaria ganho entre DMCs distintos ou invertido
     * sem alarme do dominio — nenhum vinculo adicional e inventado
     * aqui porque a fonte (o tipo da ED-85) nao o tem.
     */
    public void recordNightFlowBefore(NightFlowAnalyzer.NightFlowResult result) {
        requireInProgress("medicao anterior");
        this.nightFlowBefore = Objects.requireNonNull(result, "vazao noturna anterior obrigatoria (ED-85)");
    }

    /**
     * Medicao DEPOIS sobre o balanco da ED-85. Vale a mesma
     * limitacao declarada em {@link #recordNightFlowBefore}: o tipo
     * da ED-85 nao carrega DMC nem instante, e a fiacao
     * (P-ED86-01) deve garantir mesma origem DMC desta campanha e
     * ordem posterior a medicao "antes".
     */
    public void recordNightFlowAfter(NightFlowAnalyzer.NightFlowResult result) {
        requireInProgress("medicao posterior");
        this.nightFlowAfter = Objects.requireNonNull(result, "vazao noturna posterior obrigatoria (ED-85)");
    }

    /**
     * Conclusao: exige evidencia, as duas medicoes de vazao noturna
     * da ED-85 e nenhum achado pendente. O ganho e a diferenca da
     * vazao noturna LIQUIDA (antes - depois); o percentual fica
     * ausente quando a vazao anterior nao e positiva (sem base, sem
     * percentual inventado). Ganho negativo e exposto, nao truncado.
     */
    public SurveyGain complete() {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException(
                "campanha so conclui de IN_PROGRESS: " + status);
        if (evidenceRefs.isEmpty()) throw new IllegalStateException(
                "campanha nao conclui sem evidencia registrada");
        if (nightFlowBefore == null || nightFlowAfter == null) throw new IllegalStateException(
                "ganho exige a vazao minima noturna ANTES e DEPOIS sobre o balanco da ED-85");
        List<String> pending = findings.stream()
                .filter(f -> f.disposition() == LeakDetectionFinding.Disposition.PENDING)
                .map(LeakDetectionFinding::code).toList();
        if (!pending.isEmpty()) throw new IllegalStateException(
                "campanha nao conclui com achado pendente de disposicao (modelo ED-92): " + pending);
        double before = nightFlowBefore.netNightFlowLPerH();
        double after = nightFlowAfter.netNightFlowLPerH();
        double absolute = before - after;
        OptionalDouble percent = before > 0 ? OptionalDouble.of(absolute / before * 100.0)
                : OptionalDouble.empty();
        this.gain = new SurveyGain(before, after, absolute, percent);
        this.status = Status.COMPLETED;
        return gain;
    }

    public void cancel() {
        if (status == Status.COMPLETED) throw new IllegalStateException("campanha concluida nao cancela");
        status = Status.CANCELLED;
    }

    private void requireInProgress(String what) {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException(
                what + " so se registra com a campanha em execucao: " + status);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public UUID dmcProfileId() { return dmcProfileId; }
    public String dmcCode() { return dmcCode; }
    public String dmcClass() { return dmcClass; }
    public String hypothesis() { return hypothesis; }
    public List<String> equipment() { return equipment; }
    public List<String> teamMemberRefs() { return teamMemberRefs; }
    public Status status() { return status; }
    public List<String> evidenceRefs() { return List.copyOf(evidenceRefs); }
    public List<LeakDetectionFinding> findings() { return List.copyOf(findings); }
    public List<UUID> repairWorkOrderIds() { return List.copyOf(repairWorkOrderIds); }
    public Optional<SurveyGain> gain() { return Optional.ofNullable(gain); }
}
