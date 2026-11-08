package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Certidao de Divida Ativa — CDA (ED-31; POC-35): impressa pelo MOTOR DE
 * MODELOS da P2.30 (JOR-06), nunca por texto montado aqui. A emissao grava
 * codigo, versao do modelo e referencia do documento; a REIMPRESSAO devolve
 * exatamente esses dados congelados, sem renderizar de novo (a CDA nao muda
 * quando o modelo muda — mesmo principio da P2.30/P4.1).
 *
 * <p>REAVALIACAO DE PRESCRICAO NA EMISSAO (correcao D6a da revisao): a
 * emissao reavalia a prescricao ITEM A ITEM no relogio atual — inscricao
 * que prescreveu (ou ficou indeterminada) depois de inscrita NAO gera CDA.
 * {@code legalAnalysisRecorded} e o registro real da analise juridica de
 * prescricao; a sobrecarga sem o parametro usa {@code false} (cautela:
 * linha dependente de analise bloqueia). A reimpressao NAO reavalia: ela
 * devolve o documento ja emitido e congelado, como na P2.30.
 *
 * <p>MARCADOR DECLARADO: o codigo do modelo vem do parametro do tenant
 * ({@code da.cda_template_code}); a MINUTA da CDA nao existe nas fontes
 * desta entrega (pendencia de minutas, como na P3.9) — sem parametro, a
 * emissao e recusada, nenhum codigo e inventado.
 */
public final class CdaDocument {

    public record IssuedCda(UUID enrollmentId, String templateCode, String templateVersion, String documentRef) {
        public IssuedCda {
            if (enrollmentId == null || templateCode == null || templateCode.isBlank()
                    || templateVersion == null || templateVersion.isBlank()
                    || documentRef == null || documentRef.isBlank())
                throw new IllegalArgumentException("CDA exige inscricao, codigo e versao do modelo e referencia do documento");
        }
    }

    private final DunningPorts.TemplatePort templatePort;
    private final ActiveDebtParameters params;
    private final PrescriptionGate gate;
    private final Prescription.ProviderNature nature;
    private final Prescription.ClaimType claim;

    public CdaDocument(DunningPorts.TemplatePort templatePort, ActiveDebtParameters params,
                       PrescriptionGate gate, Prescription.ProviderNature nature,
                       Prescription.ClaimType claim) {
        this.templatePort = Objects.requireNonNull(templatePort, "motor de modelos (P2.30) e obrigatorio");
        this.params = Objects.requireNonNull(params);
        this.gate = Objects.requireNonNull(gate, "portao de prescricao (P4.1) e obrigatorio: a emissao reavalia a prescricao");
        this.nature = Objects.requireNonNull(nature);
        this.claim = Objects.requireNonNull(claim);
    }

    /** Emissao sem registro de analise juridica de prescricao (cautela: {@code false}). */
    public IssuedCda issue(ActiveDebtEnrollment.Enrollment enrollment) {
        return issue(enrollment, false);
    }

    public IssuedCda issue(ActiveDebtEnrollment.Enrollment enrollment, boolean legalAnalysisRecorded) {
        Objects.requireNonNull(enrollment, "inscricao e obrigatoria");
        if (enrollment.status() == ActiveDebtEnrollment.Status.CANCELLED)
            throw new IllegalStateException("CDA de inscricao cancelada e recusada");
        gate.assertAllowedForAll(PrescriptionGate.Operation.CDA_ISSUANCE, nature, claim,
                enrollment.items(), legalAnalysisRecorded);
        String code = params.cdaTemplateCode();
        var rendered = templatePort.render(enrollment.tenantId(), code, Map.of(
                "enrollmentId", enrollment.id().toString(),
                "bookTermNumber", enrollment.bookTermNumber(),
                "total", enrollment.total().toPlainString(),
                "enrolledOn", enrollment.enrolledOn().toString()));
        return new IssuedCda(enrollment.id(), rendered.templateCode(), rendered.templateVersion(), rendered.documentRef());
    }

    /** Reimpressao: devolve a CDA congelada na emissao; o motor NAO e chamado. */
    public IssuedCda reprint(IssuedCda issued) { return Objects.requireNonNull(issued, "CDA emitida e obrigatoria"); }
}
