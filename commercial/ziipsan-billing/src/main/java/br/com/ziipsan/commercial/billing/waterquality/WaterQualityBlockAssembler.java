package br.com.ziipsan.commercial.billing.waterquality;

import br.com.ziipsan.commercial.billing.parameters.StatementParameters.WaterQualitySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Montador do bloco mensal de qualidade da agua (P2.24,
 * Decreto 5.440/2005): escolhe a fonte pelo parametro
 * {@code bill.water_quality_source} e monta o bloco do mes/sistema.
 *
 * <ul>
 *   <li>Fonte {@code LAB_SPREADSHEET}: valem os resultados da planilha
 *       mensal do laboratorio (importador generico da P1.40, esquema
 *       {@code billing.water_quality_lab}); os do modulo sao ignorados.</li>
 *   <li>Fonte {@code QUALITY_MODULE}: valem os resultados do modulo de
 *       qualidade da ED-50; os da planilha sao ignorados. Enquanto a
 *       ED-50 nao estiver ativa, o chamador nao tem lista do modulo para
 *       entregar — e o bloco fica indisponivel, sem cair para a planilha
 *       em silencio.</li>
 *   <li>Sem nenhum resultado da fonte escolhida para o mes/sistema:
 *       {@code Optional.empty()} — o chamador marca
 *       {@code waterQualityAvailable = false} no contexto da P2.32 e a
 *       mensagem legal do bloco nao dispara. Bloco parcial ou de outro
 *       mes NUNCA e aproveitado.</li>
 *   <li>Resultado da fonte escolhida com mes, sistema ou fonte
 *       divergentes do pedido e erro explicito: dado trocado nao entra
 *       no bloco por aproximacao.</li>
 * </ul>
 */
public final class WaterQualityBlockAssembler {

    private WaterQualityBlockAssembler() {
    }

    public static Optional<WaterQualityBlock> assemble(WaterQualitySource source, LocalDate referenceMonth,
                                                       String supplySystem,
                                                       List<WaterQualityResult> spreadsheetResults,
                                                       List<WaterQualityResult> qualityModuleResults) {
        if (source == null) {
            throw new IllegalArgumentException("source (bill.water_quality_source) e obrigatoria");
        }
        if (referenceMonth == null || referenceMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException(
                    "referenceMonth deve ser o primeiro dia do mes: " + referenceMonth);
        }
        if (supplySystem == null || supplySystem.isBlank()) {
            throw new IllegalArgumentException("supplySystem e obrigatorio");
        }
        List<WaterQualityResult> chosen = switch (source) {
            case LAB_SPREADSHEET -> spreadsheetResults;
            case QUALITY_MODULE -> qualityModuleResults;
        };
        if (chosen == null || chosen.isEmpty()) {
            return Optional.empty();
        }
        List<WaterQualityResult> sorted = new ArrayList<>(chosen);
        for (WaterQualityResult result : sorted) {
            if (result.source() != source) {
                throw new IllegalArgumentException("resultado " + result.parameterKey() + " veio marcado da fonte "
                        + result.source() + " mas a fonte vigente e " + source);
            }
            if (!result.referenceMonth().equals(referenceMonth)) {
                throw new IllegalArgumentException("resultado " + result.parameterKey() + " e de "
                        + result.referenceMonth() + ", nao do mes do bloco " + referenceMonth);
            }
            if (!result.supplySystem().equals(supplySystem)) {
                throw new IllegalArgumentException("resultado " + result.parameterKey() + " e do sistema "
                        + result.supplySystem() + ", nao do sistema do bloco " + supplySystem);
            }
        }
        sorted.sort(Comparator.comparing(WaterQualityResult::parameterKey));
        return Optional.of(new WaterQualityBlock(referenceMonth, supplySystem, sorted));
    }

    /** O bloco esta disponivel para o contexto de emissao da P2.32? (disparo WATER_QUALITY_AVAILABLE) */
    public static boolean available(WaterQualitySource source, LocalDate referenceMonth, String supplySystem,
                                    List<WaterQualityResult> spreadsheetResults,
                                    List<WaterQualityResult> qualityModuleResults) {
        return assemble(source, referenceMonth, supplySystem, spreadsheetResults, qualityModuleResults).isPresent();
    }
}
