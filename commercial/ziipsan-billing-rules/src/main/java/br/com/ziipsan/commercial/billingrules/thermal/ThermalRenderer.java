package br.com.ziipsan.commercial.billingrules.thermal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderizador termico (ED-19): transforma o MODELO da P2.30 + o documento
 * em comandos da impressora Zebra ZQ — ZPL ou CPCL conforme o canal do
 * modelo. O renderizador nao conhece "conta": conhece linhas, codigo de
 * barras (Code 128) e QR — por isso o mesmo codigo imprime a conta e os
 * comunicados de debito e de excesso (modelos diferentes, motor unico).
 *
 * <h2>Sanitizacao</h2>
 * Valores com caracteres de comando da linguagem ({@code ^ ~ \} no ZPL;
 * quebra de linha nos dois) sao recusados/limpos ANTES de montar o comando:
 * dado do cadastro nunca vira comando de impressora.
 *
 * <h2>Geometria</h2>
 * Papel de 80 mm a 203 dpi: linhas de texto com passo fixo de 36 dots (ZPL)
 * ou altura calculada pelas linhas (CPCL). Sao constantes DESTE leiaute
 * termico generico — o ajuste fino por modelo de impressora e do ensaio com
 * a ZQ real (pendencia de hardware declarada).
 */
public final class ThermalRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)}}");
    private static final int ZPL_LEFT = 40;
    private static final int ZPL_TOP = 30;
    private static final int ZPL_LINE_STEP = 36;

    private ThermalRenderer() {
    }

    /** Renderiza o documento no canal do modelo. Saida pronta para o Bluetooth. */
    public static String render(ThermalTemplate template, ThermalDocument document) {
        Objects.requireNonNull(template, "modelo termico e obrigatorio");
        Objects.requireNonNull(document, "documento termico e obrigatorio");
        List<String> lines = new ArrayList<>();
        for (String bodyLine : template.bodyLines()) {
            lines.add(substitute(bodyLine, document));
        }
        return switch (template.channel()) {
            case ZPL -> renderZpl(lines, document);
            case CPCL -> renderCpcl(lines, document);
        };
    }

    /**
     * Pagina de teste do cabecote (ED-19): faixas solidas e texto de prova.
     * O envio fisico e a leitura do resultado sao do plugin/ensaio de
     * hardware; o comando e deterministico e testavel aqui.
     */
    public static String headTestPattern(ThermalChannel channel) {
        Objects.requireNonNull(channel, "canal do teste de cabecote e obrigatorio");
        return switch (channel) {
            case ZPL -> "^XA^FO40,30^GB720,60,60^FS^FO40,120^GB720,10,10^FS"
                    + "^FO40,160^A0N,36,36^FDTESTE DE CABECOTE ZIIPSAN^FS"
                    + "^FO40,210^A0N,28,28^FD1234567890 ABCDEFGHIJKLMNOPQRSTUVWXYZ^FS^XZ";
            case CPCL -> "! 0 200 200 320 1\r\nBOX 40 30 760 90 6\r\n"
                    + "TEXT 4 0 40 120 TESTE DE CABECOTE ZIIPSAN\r\n"
                    + "TEXT 4 0 40 180 1234567890 ABCDEFGHIJKLMNOPQRSTUVWXYZ\r\nFORM\r\nPRINT\r\n";
        };
    }

    private static String renderZpl(List<String> lines, ThermalDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append("^XA");
        sb.append("^PW832");
        int y = ZPL_TOP;
        for (String line : lines) {
            sb.append("^FO").append(ZPL_LEFT).append(',').append(y)
                    .append("^A0N,30,30^FD").append(zplText(line)).append("^FS");
            y += ZPL_LINE_STEP;
        }
        if (document.hasBarcode()) {
            y += 10;
            sb.append("^FO").append(ZPL_LEFT).append(',').append(y)
                    .append("^BY2^BCN,90,Y,N,N^FD").append(document.barcodeValue().replace(" ", "")).append("^FS");
            y += 130;
        }
        if (document.hasPixQr()) {
            y += 10;
            sb.append("^FO").append(ZPL_LEFT).append(',').append(y)
                    .append("^BQN,2,6^FDQA,").append(zplText(document.pixPayload())).append("^FS");
        }
        sb.append("^XZ");
        return sb.toString();
    }

    private static String renderCpcl(List<String> lines, ThermalDocument document) {
        int height = 80 + lines.size() * 36 + (document.hasBarcode() ? 140 : 0) + (document.hasPixQr() ? 240 : 0);
        StringBuilder sb = new StringBuilder();
        sb.append("! 0 200 200 ").append(height).append(" 1\r\n");
        sb.append("PAGE-WIDTH 832\r\n");
        int y = 30;
        for (String line : lines) {
            sb.append("TEXT 4 0 40 ").append(y).append(' ').append(cpclText(line)).append("\r\n");
            y += 36;
        }
        if (document.hasBarcode()) {
            y += 10;
            sb.append("BARCODE 128 2 2 90 40 ").append(y).append(' ')
                    .append(document.barcodeValue().replace(" ", "")).append("\r\n");
            y += 130;
        }
        if (document.hasPixQr()) {
            y += 10;
            sb.append("B QR 40 ").append(y).append(" M 2 U 6\r\nMA,")
                    .append(cpclText(document.pixPayload())).append("\r\nENDQR\r\n");
        }
        sb.append("FORM\r\nPRINT\r\n");
        return sb.toString();
    }

    private static String substitute(String line, ThermalDocument document) {
        Matcher matcher = PLACEHOLDER.matcher(line);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = document.values().get(name);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Modelo termico usa a variavel {{" + name + "}} sem valor no documento — "
                                + "imprimir marcador cru na conta e proibido");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** ZPL: ^ ~ \ sao comandos — nunca saem de um valor de cadastro. */
    private static String zplText(String value) {
        return value.replace("^", " ").replace("~", " ").replace("\\", " ")
                .replace("\r", " ").replace("\n", " ");
    }

    /** CPCL: quebra de linha dentro do valor quebraria o comando. */
    private static String cpclText(String value) {
        return value.replace("\r", " ").replace("\n", " ");
    }
}
