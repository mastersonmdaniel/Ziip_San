package br.com.ziipsan.platform.ai;

import java.util.Locale;

/** ADR-0042/GES-08: NENHUMA decisao exclusivamente automatizada sobre corte, negativacao ou tarifa social.
 * A IA nao decide, nao recomenda como decisao e nao executa: tema detectado => transbordo humano obrigatorio
 * e revisao humana travada (ai.human_review_required, classe a). */
public enum ForbiddenDecision {
    CORTE, NEGATIVACAO, TARIFA_SOCIAL;
    public static boolean isForbiddenTopic(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        // CORRECAO ED-43 (m3): parafrases usuais dos mesmos temas entram na regua —
        // "desligamento (do fornecimento)" = corte; "nome sujo" = negativacao.
        return t.contains("corte") || t.contains("cortar") || t.contains("desligamento") || t.contains("negativ") || t.contains("serasa")
            || t.contains("spc") || t.contains("nome sujo") || t.contains("tarifa social") || t.contains("baixa renda");
    }
}
