package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;

import java.util.Objects;
import java.util.Set;

/**
 * Politica de calculabilidade em campo (Arquitetura Tarifaria
 * Unificada, Camada 4 — desenho da Camada 0, seccao 4.1 item 1, na
 * forma da fronteira D-06 do adendo de desenho do municipio): decide,
 * para uma cobranca do catalogo (Camada 1), se ela pode ser calculada
 * no aparelho de campo pelo calculador vigente da ED-19
 * ({@code FieldBillCalculator}, modulo
 * {@code ziipsan-billing-rules}).
 *
 * <p>Uma cobranca e calculavel em campo se e so se as DUAS condicoes
 * se verificam:
 * <ol>
 *   <li>o modelo de calculo e reproduzivel pelo calculador vigente —
 *       {@link CalculationModel#PROGRESSIVE_BY_CONSUMPTION} (faixas
 *       progressivas), {@link CalculationModel#DERIVED_FROM_VOLUME}
 *       (esgoto derivado, percentual sobre agua) e
 *       {@link CalculationModel#FIXED_PER_UNIT} (parcela fixa por
 *       economia) sao exactamente os tres comportamentos que o
 *       retrato vigente ({@code FieldTariffSnapshot}) e o calculador
 *       da ED-19 executam; e</li>
 *   <li>a definicao esta marcada {@code fieldCalculable} no catalogo
 *       (Camada 1) — a marcacao e condicao necessaria, nunca
 *       suficiente: marcacao sem modelo reproduzivel nao entra.</li>
 * </ol>
 *
 * <p><b>Exclusoes estruturais (independentes de qualquer
 * marcacao):</b>
 * <ul>
 *   <li>{@link CalculationModel#EXTERNAL_VALUE} esta SEMPRE fora:
 *       e o modelo do convenio/cofaturamento — o valor vem de fonte
 *       externa, o aparelho nunca o calcula nem o recebe
 *       (INV-C4-02: convenio nunca no aparelho);</li>
 *   <li>dono {@link ChargeOwner#THIRD_PARTY} esta SEMPRE fora, pelo
 *       mesmo invariante: cobranca de terceiro e cobranca de
 *       convenio, qualquer que seja o modelo declarado — a guarda
 *       por dono fecha a porta que a guarda por modelo deixaria
 *       aberta a uma definicao de terceiro mal modelada.</li>
 * </ul>
 *
 * <p><b>Fora por nao reproducao — {@code [A DEFINIR]}:</b>
 * {@link CalculationModel#PERCENT_OF_BASE} e
 * {@link CalculationModel#CATALOG_PRICE} ficam FORA desta politica:
 * o calculador vigente da ED-19 nao reproduz nenhum dos dois (o
 * retrato vigente nao tem base percentual generica nem preco de
 * catalogo). Eles so entram se e quando o artefacto de campo os
 * reproduzir com prova — ate la, {@code [A DEFINIR]} ate a O7-6 ou
 * extensao provada do artefacto, como o desenho regista. Marcar
 * {@code fieldCalculable=true} numa dessas cobrancas NAO a torna
 * calculavel em campo: a resolucao de campo omite-a (ver
 * {@link FieldChargeResolver}) e o projector recusa-a se ela lhe
 * chegar por fora do resolvedor (ver
 * {@link FieldSnapshotProjector}, INV-C4-05).
 *
 * <p>Politica pura e sem estado: nao le catalogo, nao le parametro,
 * nao tem valor de fabrica — decide so sobre a definicao recebida.
 */
public final class FieldCalculablePolicy {

    /** Modelos que o calculador vigente da ED-19 reproduz. */
    private static final Set<CalculationModel> FIELD_REPRODUCIBLE_MODELS = Set.of(
            CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
            CalculationModel.DERIVED_FROM_VOLUME,
            CalculationModel.FIXED_PER_UNIT);

    private FieldCalculablePolicy() {
    }

    /**
     * O calculador vigente reproduz este modelo? Verdadeiro apenas
     * para progressiva, derivada e fixa; falso para
     * {@code PERCENT_OF_BASE}/{@code CATALOG_PRICE} (fora,
     * {@code [A DEFINIR]}) e para {@code EXTERNAL_VALUE}
     * (estruturalmente excluido).
     */
    public static boolean isReproducibleByFieldCalculator(CalculationModel model) {
        Objects.requireNonNull(model, "modelo de calculo e obrigatorio");
        return FIELD_REPRODUCIBLE_MODELS.contains(model);
    }

    /**
     * A cobranca desta definicao e calculavel em campo? Exige modelo
     * reproduzivel E marcacao {@code fieldCalculable} na definicao;
     * {@code EXTERNAL_VALUE} e dono {@code THIRD_PARTY} sao excluidos
     * estruturalmente, independentemente da marcacao.
     */
    public static boolean isFieldCalculable(ChargeDefinition definition) {
        Objects.requireNonNull(definition, "definicao da cobranca e obrigatoria");
        if (definition.calculationModel() == CalculationModel.EXTERNAL_VALUE) {
            return false;
        }
        if (definition.owner() == ChargeOwner.THIRD_PARTY) {
            return false;
        }
        return definition.fieldCalculable()
                && FIELD_REPRODUCIBLE_MODELS.contains(definition.calculationModel());
    }

    /** Atalho sobre o par resolvido (definicao, versao) da Camada 1. */
    public static boolean isFieldCalculable(ChargeResolution resolution) {
        Objects.requireNonNull(resolution, "resolucao da cobranca e obrigatoria");
        return isFieldCalculable(resolution.definition());
    }
}
