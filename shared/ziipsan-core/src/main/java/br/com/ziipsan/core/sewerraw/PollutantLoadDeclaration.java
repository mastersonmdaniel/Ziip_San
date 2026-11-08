package br.com.ziipsan.core.sewerraw;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Declaracao de Carga Poluidora (DCP) gerada a partir do
 * automonitoramento da ED-105 (aceite ED-80). A declaracao guarda:
 * a versao do catalogo de padrao de efluente da ED-105 usada, as
 * referencias normativas DESSE catalogo (a base normativa e a do
 * catalogo, conferida nele — ver {@link DcpService}), a eficiencia
 * de DBO da ED-105 do periodo e as linhas de carga calculadas dos
 * resultados reais do automonitoramento. Nenhum limite vive aqui.
 */
public record PollutantLoadDeclaration(
        UUID id,
        UUID tenantId,
        UUID wastewaterUnitId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String effluentCatalogVersion,
        List<String> normativeBasisRefs,
        boolean successorBasisConfirmedInCatalog,
        BigDecimal effluentVolumeM3,
        int bodPairCount,
        BigDecimal bodRemovalPercent,
        List<LoadLine> lines,
        BigDecimal totalLoadKg,
        String declarantRef,
        LocalDate declaredOn) {

    /**
     * Linha de carga de um parametro. Formula dimensional declarada
     * (identidade de unidades, nao norma): carga (kg) =
     * concentracao media (mg/L) x volume efluente (m3) / 1000,
     * porque 1 mg/L = 1 g/m3. Concentracao e volume sao os valores
     * reais do automonitoramento e da operacao — nunca constantes.
     */
    public record LoadLine(
            String parameterKey,
            BigDecimal meanConcentration,
            String concentrationUnit,
            BigDecimal loadKg,
            String normReference) {
        public LoadLine {
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parametro da linha de carga e obrigatorio");
            Objects.requireNonNull(meanConcentration, "concentracao media da linha e obrigatoria");
            if (meanConcentration.signum() < 0)
                throw new IllegalArgumentException("concentracao media nao pode ser negativa");
            if (concentrationUnit == null || concentrationUnit.isBlank())
                throw new IllegalArgumentException("unidade da concentracao e obrigatoria (a do resultado do laboratorio)");
            Objects.requireNonNull(loadKg, "carga da linha e obrigatoria");
            if (normReference == null || normReference.isBlank())
                throw new IllegalArgumentException("referencia normativa da linha (a do catalogo da ED-105) e obrigatoria");
        }
    }

    public PollutantLoadDeclaration {
        Objects.requireNonNull(id, "id da DCP e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant da DCP e obrigatorio");
        Objects.requireNonNull(wastewaterUnitId, "ETE da DCP e obrigatoria");
        Objects.requireNonNull(periodStart, "inicio do periodo da DCP e obrigatorio");
        Objects.requireNonNull(periodEnd, "fim do periodo da DCP e obrigatorio");
        if (periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo da DCP invalido");
        if (effluentCatalogVersion == null || effluentCatalogVersion.isBlank())
            throw new IllegalArgumentException("versao do catalogo da ED-105 usada na DCP e obrigatoria");
        normativeBasisRefs = normativeBasisRefs == null ? List.of() : List.copyOf(normativeBasisRefs);
        if (normativeBasisRefs.isEmpty())
            throw new IllegalArgumentException("base normativa da DCP (a conferida no catalogo) nao pode ser vazia");
        Objects.requireNonNull(effluentVolumeM3, "volume efluente do periodo e obrigatorio");
        if (effluentVolumeM3.signum() <= 0)
            throw new IllegalArgumentException("volume efluente do periodo deve ser maior que zero");
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (lines.isEmpty())
            throw new IllegalArgumentException("DCP sem linha de carga nao se gera: sem automonitoramento nao ha declaracao");
        Objects.requireNonNull(totalLoadKg, "carga total da DCP e obrigatoria");
        if (declarantRef == null || declarantRef.isBlank())
            throw new IllegalArgumentException("declarante da DCP e obrigatorio");
        Objects.requireNonNull(declaredOn, "data da declaracao e obrigatoria");
    }
}
