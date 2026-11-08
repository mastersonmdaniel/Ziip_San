package br.com.ziipsan.core.quality;

import java.time.LocalDate;
import java.util.List;

/**
 * Fonte do bloco mensal de qualidade da agua da conta (Decreto
 * 5.440/2005) quando {@code bill.water_quality_source = QUALITY_MODULE}
 * (parametro da P2.24): entrega as linhas no MESMO formato textual que
 * a P2.24 consome ({@code WaterQualityResult}: chave, nome de
 * exibicao, unidade, resultado como texto e referencia como texto) —
 * sem importar o modulo billing: o adaptador da integracao converte
 * {@link BillBlockLine} em {@code WaterQualityResult} com
 * {@code source = QUALITY_MODULE}.
 *
 * <p>Regras:
 * <ul>
 *   <li>so de fechamento mensal FECHADO — mes aberto nao alimenta
 *       conta;</li>
 *   <li>o "resultado" de cada parametro e o fato do fechamento:
 *       quantas analises validadas e quantas conformes — texto
 *       factual montado do agregado, nunca um valor de laboratorio
 *       inventado nem media presumida;</li>
 *   <li>a referencia e o {@code referenceText} da linha REVISADA do
 *       catalogo; parametro sem linha revisada vigente sai SEM
 *       referencia (campo nulo, como a P2.24 admite) e com o fato
 *       "conformidade indeterminada" quando houver indeterminados —
 *       nunca com limite presumido.</li>
 * </ul>
 */
public final class BillQualityBlockSource {

    public record BillBlockLine(
            LocalDate referenceMonth,
            String supplySystem,
            String parameterKey,
            String parameterName,
            String unit,
            String resultText,
            String referenceText) {}

    private BillQualityBlockSource() {}

    public static List<BillBlockLine> qualityModuleLines(MonthlyClosing closing, NormativeCatalog catalog) {
        if (closing == null) throw new IllegalArgumentException("fechamento e obrigatorio");
        if (!closing.isClosed())
            throw new IllegalStateException("bloco da conta exige fechamento mensal FECHADO ("
                    + closing.month() + " ainda aberto)");
        if (catalog == null) throw new IllegalArgumentException("catalogo e obrigatorio");
        LocalDate monthStart = closing.month().atDay(1);
        return closing.lines().stream().map(line -> {
            var standard = catalog.reviewedStandardOn(line.parameterKey(), monthStart);
            String name = standard.map(ParameterStandard::displayName).orElse(line.parameterKey());
            String unit = standard.map(ParameterStandard::unit).orElse(null);
            String reference = standard.map(ParameterStandard::referenceText).orElse(null);
            String result = "conformes " + line.conforming() + " de "
                    + (line.conforming() + line.nonConforming()) + " analises determinadas"
                    + " (" + line.analyzed() + " validadas no mes)";
            if (line.indeterminate() > 0) {
                result += "; " + line.indeterminate() + " com conformidade indeterminada";
            }
            return new BillBlockLine(monthStart, closing.supplySystem(), line.parameterKey(),
                    name, unit, result, reference);
        }).toList();
    }
}
