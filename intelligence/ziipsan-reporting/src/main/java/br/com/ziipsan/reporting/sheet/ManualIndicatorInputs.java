package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;

import java.util.UUID;

/**
 * Energia, emissoes e pessoal (GES-21/OPE-08): nesta tarefa entram SO por
 * entrada manual com grau de confianca BAIXO forcado - a fonte automatica
 * e da ED-90, que substitui estas variaveis quando for executada. A fabrica
 * aqui devolve a variavel MANUAL da P3.41 ja carimbada para a ficha com
 * LOW; tentar registrar esses temas com grau maior e decisao da ficha do
 * tenant na ED-90, nao desta entrega.
 */
public final class ManualIndicatorInputs {

    public static final ConfidenceGrade FORCED_GRADE = ConfidenceGrade.LOW;

    private ManualIndicatorInputs() {
    }

    public static IndicatorVariable energy(String name, UUID responsibleId) {
        return IndicatorVariable.manual(name, responsibleId);
    }

    public static IndicatorVariable emissions(String name, UUID responsibleId) {
        return IndicatorVariable.manual(name, responsibleId);
    }

    public static IndicatorVariable personnel(String name, UUID responsibleId) {
        return IndicatorVariable.manual(name, responsibleId);
    }
}
