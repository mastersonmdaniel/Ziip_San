package br.com.sigsan.commercial.registry.rules;

/**
 * Inscrição do imóvel: localização cadastral {@code LLL.SSS.QQQ.LLLL.SSS}
 * (localidade, setor, quadra, lote, sublote).
 *
 * <p>Os números são guardados como inteiros: o zero à esquerda é só de exibição. A largura de cada
 * parte é configurável por tenant ({@link Layout}); o padrão é 3.3.3.4.3.
 *
 * <p>A inscrição identifica o <b>lugar</b>, não o cliente: muda se o imóvel é remembrado, e por
 * isso a chave estável do imóvel é a matrícula. A unicidade da inscrição (entre imóveis ativos) é
 * garantida por índice único no banco.
 */
public record Inscription(int locality, int sector, int block, int lot, int sublot) {

    /** Largura em dígitos de cada parte. */
    public record Layout(int locality, int sector, int block, int lot, int sublot) {

        public static final Layout DEFAULT = new Layout(3, 3, 3, 4, 3);

        public Layout {
            for (int w : new int[]{locality, sector, block, lot, sublot}) {
                if (w < 1 || w > 9) {
                    throw new IllegalArgumentException("Largura de inscrição fora de 1 a 9: " + w);
                }
            }
        }

        public int totalDigits() {
            return locality + sector + block + lot + sublot;
        }
    }

    public Inscription {
        if (locality < 0 || sector < 0 || block < 0 || lot < 0 || sublot < 0) {
            throw new IllegalArgumentException("As partes da inscrição não podem ser negativas");
        }
    }

    public String format() {
        return format(Layout.DEFAULT);
    }

    /** Formato com pontos e zeros à esquerda; falha se alguma parte não cabe na largura. */
    public String format(Layout layout) {
        return pad(locality, layout.locality(), "localidade") + "."
                + pad(sector, layout.sector(), "setor") + "."
                + pad(block, layout.block(), "quadra") + "."
                + pad(lot, layout.lot(), "lote") + "."
                + pad(sublot, layout.sublot(), "sublote");
    }

    /** Aceita com ou sem pontos, desde que tenha exatamente a quantidade de dígitos do layout. */
    public static Inscription parse(String text, Layout layout) {
        if (text == null) {
            throw new IllegalArgumentException("Inscrição obrigatória");
        }
        String digits = text.replace(".", "").trim();
        if (digits.length() != layout.totalDigits() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("Inscrição deve ter " + layout.totalDigits() + " dígitos: " + text);
        }
        int pos = 0;
        int[] widths = {layout.locality(), layout.sector(), layout.block(), layout.lot(), layout.sublot()};
        int[] parts = new int[5];
        for (int i = 0; i < 5; i++) {
            parts[i] = Integer.parseInt(digits.substring(pos, pos + widths[i]));
            pos += widths[i];
        }
        return new Inscription(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    public static Inscription parse(String text) {
        return parse(text, Layout.DEFAULT);
    }

    private static String pad(int value, int width, String name) {
        String s = Integer.toString(value);
        if (s.length() > width) {
            throw new IllegalArgumentException("O valor de " + name + " (" + value + ") não cabe em " + width + " dígitos");
        }
        return "0".repeat(width - s.length()) + s;
    }
}
