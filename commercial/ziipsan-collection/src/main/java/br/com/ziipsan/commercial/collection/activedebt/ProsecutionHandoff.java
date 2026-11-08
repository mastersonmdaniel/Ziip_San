package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Arquivo e peticao-modelo para a PROCURADORIA (ED-31): gera, pelo motor de
 * modelos da P2.30, o arquivo de remessa das inscricoes e a peticao-modelo
 * que acompanha a execucao fiscal. O produto ENTREGA o material a
 * procuradoria; quem peticiona e conduz o processo e a procuradoria
 * (ADR-0028, d) — esta classe nao tem nenhuma operacao judicial.
 *
 * <p>REAVALIACAO DE PRESCRICAO NA REMESSA (correcao D6a da revisao): cada
 * inscricao remetida e reavaliada ITEM A ITEM no relogio atual — inscricao
 * que prescreveu (ou ficou indeterminada) depois de inscrita NAO vai a
 * procuradoria por esta via. {@code legalAnalysisRecorded} e o registro
 * real da analise juridica de prescricao; a sobrecarga sem o parametro usa
 * {@code false} (cautela).
 *
 * <p>MARCADORES DECLARADOS: codigos de modelo vem dos parametros do tenant
 * ({@code da.handoff_template_code}, {@code da.petition_template_code}); as
 * minutas nao existem nas fontes — sem parametro, recusa explicita.
 */
public final class ProsecutionHandoff {

    public record Handoff(UUID tenantId, List<UUID> enrollmentIds, String fileTemplateCode,
                          String fileTemplateVersion, String fileDocumentRef,
                          String petitionTemplateCode, String petitionTemplateVersion, String petitionDocumentRef) {
        public Handoff { enrollmentIds = List.copyOf(enrollmentIds); }
    }

    private final DunningPorts.TemplatePort templatePort;
    private final ActiveDebtParameters params;
    private final PrescriptionGate gate;
    private final Prescription.ProviderNature nature;
    private final Prescription.ClaimType claim;

    public ProsecutionHandoff(DunningPorts.TemplatePort templatePort, ActiveDebtParameters params,
                              PrescriptionGate gate, Prescription.ProviderNature nature,
                              Prescription.ClaimType claim) {
        this.templatePort = Objects.requireNonNull(templatePort);
        this.params = Objects.requireNonNull(params);
        this.gate = Objects.requireNonNull(gate, "portao de prescricao (P4.1) e obrigatorio: a remessa reavalia a prescricao");
        this.nature = Objects.requireNonNull(nature);
        this.claim = Objects.requireNonNull(claim);
    }

    /** Remessa sem registro de analise juridica de prescricao (cautela: {@code false}). */
    public Handoff generate(UUID tenantId, List<ActiveDebtEnrollment.Enrollment> enrollments) {
        return generate(tenantId, enrollments, false);
    }

    public Handoff generate(UUID tenantId, List<ActiveDebtEnrollment.Enrollment> enrollments,
                            boolean legalAnalysisRecorded) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (enrollments == null || enrollments.isEmpty())
            throw new IllegalArgumentException("remessa a procuradoria exige ao menos uma inscricao");
        for (var e : enrollments) {
            if (e.status() == ActiveDebtEnrollment.Status.CANCELLED || e.status() == ActiveDebtEnrollment.Status.SETTLED)
                throw new IllegalStateException("inscricao " + e.id() + " (" + e.status() + ") nao vai a procuradoria");
            gate.assertAllowedForAll(PrescriptionGate.Operation.PROSECUTION_HANDOFF, nature, claim,
                    e.items(), legalAnalysisRecorded);
        }
        List<UUID> ids = enrollments.stream().map(ActiveDebtEnrollment.Enrollment::id).toList();
        var file = templatePort.render(tenantId, params.handoffTemplateCode(),
                Map.of("enrollmentIds", ids.toString(), "count", ids.size()));
        var petition = templatePort.render(tenantId, params.petitionTemplateCode(),
                Map.of("enrollmentIds", ids.toString(), "count", ids.size()));
        return new Handoff(tenantId, ids, file.templateCode(), file.templateVersion(), file.documentRef(),
                petition.templateCode(), petition.templateVersion(), petition.documentRef());
    }
}
