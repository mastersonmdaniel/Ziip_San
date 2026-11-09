package br.com.ziipsan.core.regulatory.agenda;

import java.text.Normalizer;
import java.util.Locale;

/**
 * ED-98 — normalizacao de texto para casamento (correcao D4 da
 * REVISAO-ED-98): dobra de acentos (NFD sem marcas combinantes) e de
 * caixa. O catalogo real da P3.15/P1.34 tem titulos acentuados e
 * chaves em outro padrao (ex.: {@code nt-2026-001-rtc-nfag}); casar
 * token por substring crua, sem dobrar acentos, mandava regra valida
 * para {@code unresolvedKinds} (sonda S4 do revisor). O casamento
 * continua pelo lado seguro: sem casamento, a regra NAO vira item —
 * e reportada como nao resolvida, nunca inventada.
 */
final class TextFolding {

    private TextFolding() { }

    /** Dobra acentos e caixa: "Declaração" -&gt; "DECLARACAO". */
    static String fold(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    /** Dobra acentos/caixa e remove tudo que nao e letra/digito:
     *  "TC-28/2021" -&gt; "TC282021"; "TARIFA SOCIAL" -&gt; "TARIFASOCIAL". */
    static String squash(String s) {
        return fold(s).replaceAll("[^A-Z0-9]+", "");
    }
}
