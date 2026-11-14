package br.com.ziipsan.commercial.billing.relief;

import java.util.EnumSet;
import java.util.Set;

/**
 * Checklist documental do processo de desoneracao (NT 40/2026, item 6.10.1),
 * como referencia do fluxo de publicacao (Passo 5): sem o processo
 * documentado completo, o programa nao avanca para publicacao.
 *
 * <ol>
 *   <li>regime tributario do prestador + fonte da aliquota efectiva;</li>
 *   <li>memoria de calculo do factor;</li>
 *   <li>tabelas vigente e desonerada;</li>
 *   <li>tratamento dos demais precos e das sancoes.</li>
 * </ol>
 */
public enum PublicationChecklist {
    TAX_REGIME_AND_RATE_SOURCE,
    FACTOR_CALCULATION_MEMO,
    CURRENT_AND_RELIEVED_TABLES,
    OTHER_PRICES_AND_SANCTIONS_TREATMENT;

    /** Itens em falta no conjunto apresentado (vazio = processo completo). */
    public static Set<PublicationChecklist> missing(Set<PublicationChecklist> presented) {
        EnumSet<PublicationChecklist> missing = EnumSet.allOf(PublicationChecklist.class);
        if (presented != null) {
            missing.removeAll(presented);
        }
        return missing;
    }

    public static boolean isComplete(Set<PublicationChecklist> presented) {
        return missing(presented).isEmpty();
    }
}
