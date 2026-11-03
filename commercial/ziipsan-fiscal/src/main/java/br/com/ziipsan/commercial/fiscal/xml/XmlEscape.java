package br.com.ziipsan.commercial.fiscal.xml;

/**
 * Escape XML do porte (equivalente ao {@code SecurityElement.Escape} do C#,
 * FISCAL-PORTE-JAVA §1, {@code EventosService}). Sem parser, sem XXE: o builder monta
 * texto e escapa todo valor interpolado; nenhum valor chega cru ao XML.
 */
public final class XmlEscape {

    private XmlEscape() {
    }

    public static String escape(String value) {
        if (value == null) {
            throw new IllegalArgumentException("O valor a escapar e obrigatorio");
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(value.charAt(i));
            }
        }
        return out.toString();
    }
}
