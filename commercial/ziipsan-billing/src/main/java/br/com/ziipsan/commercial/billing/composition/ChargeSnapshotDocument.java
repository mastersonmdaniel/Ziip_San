package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Documento do snapshot completo da composicao (desenho da Camada 0,
 * secao 2.1 item 13, com o municipio do adendo D-04; regra de
 * fronteira (vi)).
 *
 * <p>Conteudo: referencia da conta, unidade, competencia e, no
 * CABECALHO, o {@code municipalityIbge} da UC (derivado, D-02.1);
 * por LINHA: {@code chargeCode}, {@code chargeVersionId},
 * {@code owner}, {@code nature}, {@code model}, {@code amount} e o
 * {@code municipalityIbge} do ESCOPO RESOLVIDO daquela linha (o da
 * definicao vencedora — D-04: nao e um valor declarado, e o escopo
 * efectivamente usado); mais os {@code parameterVersionIds} herdados
 * do mecanismo vigente e os totais da composicao.
 *
 * <p><b>INV-C2-06 estendida:</b> {@link #fromComposition} recusa
 * composicao com qualquer linha sem {@code chargeVersionId} — linha
 * sem versao registada = snapshot incompleto = falha. E o documento
 * novo que fecha, no desenho, o furo PD-TAR-06 (nenhum produtor do
 * {@code tariff_snapshot} vigente foi localizado nos pacotes); as
 * colunas vigentes nao sao alteradas.
 *
 * <p>Serializacao: {@link #toJson()} produz JSON deterministico
 * (ordem das linhas = ordem de composicao), pronto para a camada de
 * aplicacao persistir pela {@code Json} do core (precedente
 * {@code BillSnapshotService}) ou directamente na coluna do documento.
 */
public record ChargeSnapshotDocument(String billReference, String unitRef,
                                     java.time.LocalDate competence, String municipalityIbge,
                                     List<UUID> parameterVersionIds, List<Line> lines,
                                     BigDecimal ownSubtotal, BigDecimal thirdPartyTotal,
                                     BigDecimal total) {

    /** Linha do snapshot: a fotografia de UMA linha da composicao. */
    public record Line(String chargeCode, UUID chargeVersionId, ChargeOwner owner,
                       RevenueNature nature, CalculationModel model, BigDecimal amount,
                       String municipalityIbge) {
        public Line {
            if (chargeCode == null || chargeCode.isBlank()) {
                throw new IllegalArgumentException("chargeCode da linha do snapshot e obrigatorio");
            }
            Objects.requireNonNull(chargeVersionId,
                    "chargeVersionId da linha do snapshot e obrigatorio (INV-C2-06)");
            Objects.requireNonNull(owner, "owner da linha do snapshot e obrigatorio");
            Objects.requireNonNull(nature, "nature da linha do snapshot e obrigatoria");
            Objects.requireNonNull(model, "model da linha do snapshot e obrigatorio");
            Objects.requireNonNull(amount, "amount da linha do snapshot e obrigatorio");
            ChargeCalculationContext.validateMunicipalityFormat(municipalityIbge,
                    "municipalityIbge da linha do snapshot");
        }
    }

    public ChargeSnapshotDocument {
        Objects.requireNonNull(unitRef, "unidade do snapshot e obrigatoria");
        Objects.requireNonNull(competence, "competencia do snapshot e obrigatoria");
        ChargeCalculationContext.validateMunicipalityFormat(municipalityIbge,
                "municipalityIbge do cabecalho do snapshot");
        parameterVersionIds = parameterVersionIds == null
                ? List.of() : List.copyOf(parameterVersionIds);
        lines = lines == null ? List.of() : List.copyOf(lines);
        Objects.requireNonNull(ownSubtotal, "ownSubtotal do snapshot e obrigatorio");
        Objects.requireNonNull(thirdPartyTotal, "thirdPartyTotal do snapshot e obrigatorio");
        Objects.requireNonNull(total, "total do snapshot e obrigatorio");
    }

    /**
     * Constroi o documento a partir da composicao e do contexto que a
     * produziu (cabecalho e ids herdados vem do contexto).
     *
     * @throws IllegalStateException se alguma linha da composicao nao
     *         tiver {@code chargeVersionId} (snapshot incompleto,
     *         INV-C2-06)
     */
    public static ChargeSnapshotDocument fromComposition(ChargeCalculationContext context,
                                                           BillComposition composition) {
        Objects.requireNonNull(context, "contexto e obrigatorio");
        Objects.requireNonNull(composition, "composicao e obrigatoria");
        List<Line> lines = new java.util.ArrayList<>();
        for (BillChargeLine line : composition.lines()) {
            if (line.chargeVersionId() == null) {
                throw new IllegalStateException("snapshot incompleto: linha " + line.chargeCode()
                        + " sem chargeVersionId (INV-C2-06)");
            }
            lines.add(new Line(line.chargeCode(), line.chargeVersionId(), line.owner(),
                    line.revenueNature(), line.model(), line.amount(), line.municipalityIbge()));
        }
        return new ChargeSnapshotDocument(context.billReference(), context.unitRef(),
                context.competence(), context.municipalityIbge(), context.parameterVersionIds(),
                lines, composition.ownSubtotal(), composition.thirdPartyTotal(),
                composition.total());
    }

    /** JSON deterministico do documento (valores monetarios como texto, escala preservada). */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendText(json, "billReference", billReference).append(',');
        appendText(json, "unitRef", unitRef).append(',');
        json.append("\"competence\":\"").append(competence).append("\",");
        appendText(json, "municipalityIbge", municipalityIbge).append(',');
        json.append("\"parameterVersionIds\":[");
        for (int i = 0; i < parameterVersionIds.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(parameterVersionIds.get(i)).append('"');
        }
        json.append("],\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            Line line = lines.get(i);
            json.append('{');
            appendText(json, "chargeCode", line.chargeCode()).append(',');
            json.append("\"chargeVersionId\":\"").append(line.chargeVersionId()).append("\",");
            json.append("\"owner\":\"").append(line.owner()).append("\",");
            json.append("\"nature\":\"").append(line.nature()).append("\",");
            json.append("\"model\":\"").append(line.model()).append("\",");
            json.append("\"amount\":\"").append(line.amount().toPlainString()).append("\",");
            appendText(json, "municipalityIbge", line.municipalityIbge());
            json.append('}');
        }
        json.append("],");
        json.append("\"ownSubtotal\":\"").append(ownSubtotal.toPlainString()).append("\",");
        json.append("\"thirdPartyTotal\":\"").append(thirdPartyTotal.toPlainString()).append("\",");
        json.append("\"total\":\"").append(total.toPlainString()).append('"');
        json.append('}');
        return json.toString();
    }

    private static StringBuilder appendText(StringBuilder json, String field, String value) {
        json.append('"').append(field).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return json;
    }
}
