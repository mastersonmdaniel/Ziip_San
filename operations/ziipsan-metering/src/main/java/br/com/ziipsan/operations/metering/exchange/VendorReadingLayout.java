package br.com.ziipsan.operations.metering.exchange;

import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Leiaute de arquivo do fornecedor de leitura do piloto (P2.7): o mesmo
 * leiaute na ida (tarefas) e na volta (leituras), para o arquivo que o
 * fornecedor recebe ser o arquivo que ele devolve preenchido.
 *
 * <p><b>Suposicao declarada (ver RELATORIO-P2.7.md):</b> nenhum leiaute
 * concreto do fornecedor do piloto esta definido no plano v3.0, no
 * levantamento nem em tarefa anterior -- o aceite da P2.7 diz apenas
 * "no leiaute dele". Este leiaute e, portanto, a proposta implementada ate o
 * leiaute real ser entregue pelo piloto: CSV delimitado por {@code ;}
 * (o separador das planilhas em pt-BR), UTF-8, uma linha por tarefa/registro,
 * com a primeira linha de cabecalho fixa. Trocar o leiaute quando o real
 * chegar e trocar esta classe e o {@code layoutCode} do fornecedor; servico,
 * entidades e idempotencia nao mudam.
 *
 * <p>Colunas (ida preenche as de tarefa e deixa as de leitura vazias; a volta
 * devolve a mesma linha com as de leitura preenchidas):
 * <pre>
 * SEQ;FORNECEDOR;ROTA;REFERENCIA;UNIDADE;INSTALACAO;TIPO;DATA_PREVISTA;LEITURA_ANTERIOR;DATA_LEITURA;LEITURA;COD_ANOMALIA;CONSUMO_INFORMADO
 * </pre>
 * {@code REFERENCIA} e {@code yyyy-MM}; datas sao ISO ({@code yyyy-MM-dd});
 * {@code UNIDADE} e {@code INSTALACAO} sao os UUIDs que a propria ida
 * exportou -- a volta so e aceita para a tarefa que a ida criou.
 *
 * <p>Erro de cabecalho e erro explicito ({@link MeteringInputException}):
 * arquivo de outro leiaute nao e "interpretado" por aproximacao. Erro numa
 * linha de dados nao derruba o arquivo: a linha vira um {@link ParsedLine}
 * com {@code error} e quem importa a grava como registro {@code ERROR}.
 */
public final class VendorReadingLayout {

    /** Codigo deste leiaute, gravado em {@code reading_vendors.layout_code} e no lote. */
    public static final String LAYOUT_CODE = "PILOTO_CSV_V1";

    public static final String DELIMITER = ";";

    public static final List<String> COLUMNS = List.of(
            "SEQ", "FORNECEDOR", "ROTA", "REFERENCIA", "UNIDADE", "INSTALACAO", "TIPO",
            "DATA_PREVISTA", "LEITURA_ANTERIOR", "DATA_LEITURA", "LEITURA", "COD_ANOMALIA",
            "CONSUMO_INFORMADO");

    public static final String HEADER = String.join(DELIMITER, COLUMNS);

    private VendorReadingLayout() {
    }

    /** Uma linha do leiaute: os campos da tarefa (ida) e os da leitura (volta), como estao no arquivo. */
    public record VendorLine(int sequenceNo, String vendorCode, String routeCode, YearMonth reference,
                             UUID unitId, UUID installationId, MeasurementKind kind,
                             LocalDate plannedOn, Integer previousReading,
                             LocalDate readOn, Integer reading, String anomalyCode,
                             Integer informedConsumption) {
    }

    /** Linha lida do arquivo: ou a {@link VendorLine}, ou o motivo de a linha nao ser uma. */
    public record ParsedLine(int lineNo, VendorLine line, String error) {

        public boolean isError() {
            return error != null;
        }
    }

    /** Referencia na chave: {@code yyyyMM} (6 caracteres, sem o hifen de {@code yyyy-MM}). */
    private static final DateTimeFormatter REFERENCE_IN_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * Chave de idempotencia de um registro: fornecedor, rota, referencia,
     * unidade e tipo -- a mesma identidade em qualquer arquivo e em qualquer
     * reenvio, por isso o reenvio do mesmo registro cai nela.
     *
     * <p>Formato compacto, sem redundancia:
     * {@code <fornecedor>:<rota>:<yyyyMM>:<unidade sem hifens>:<tipo em 2 letras>},
     * com {@code WATER -> WA} e {@code WELL -> WE}. Conta do pior caso, com os
     * codigos NOS LIMITES das suas colunas: fornecedor 30
     * ({@code reading_vendors.code}) + rota 40 ({@code reading_tasks.route_code})
     * + referencia 6 + unidade 32 (UUID sem os 4 hifens) + tipo 2
     * + 4 separadores = <b>114 caracteres</b> -- dentro dos 120 de
     * {@code reading_import_records.record_key} (V5_3) e de
     * {@code meter_readings.idempotency_key} (V5_4), com folga de 6. (O formato
     * anterior, com prefixo {@code VND:}, referencia {@code yyyy-MM}, UUID com
     * hifens e o nome inteiro do tipo, somava 126 no pior caso e estourava os
     * dois limites -- corrigido na revisao da P2.7.)
     */
    public static String recordKey(String vendorCode, String routeCode, YearMonth reference, UUID unitId,
                                   MeasurementKind kind) {
        return vendorCode + ":" + routeCode + ":" + REFERENCE_IN_KEY.format(reference)
                + ":" + unitId.toString().replace("-", "") + ":" + kindCode(kind);
    }

    /** Codigo de 2 letras do tipo na chave: os dois valores de {@link MeasurementKind}, sem colisao. */
    private static String kindCode(MeasurementKind kind) {
        return switch (kind) {
            case WATER -> "WA";
            case WELL -> "WE";
        };
    }

    /** SHA-256 (hex minusculo) do conteudo exato do arquivo: a identidade do arquivo para a idempotencia. */
    public static String sha256Hex(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    /** Arquivo de ida: cabecalho e uma linha por tarefa, na ordem de leitura da rota. */
    public static String formatTasks(List<VendorLine> tasks) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (VendorLine task : tasks) {
            out.append(formatLine(task)).append('\n');
        }
        return out.toString();
    }

    private static String formatLine(VendorLine line) {
        return String.join(DELIMITER,
                Integer.toString(line.sequenceNo()),
                text(line.vendorCode()),
                text(line.routeCode()),
                line.reference() == null ? "" : line.reference().toString(),
                line.unitId() == null ? "" : line.unitId().toString(),
                line.installationId() == null ? "" : line.installationId().toString(),
                line.kind() == null ? "" : line.kind().name(),
                line.plannedOn() == null ? "" : line.plannedOn().toString(),
                line.previousReading() == null ? "" : line.previousReading().toString(),
                line.readOn() == null ? "" : line.readOn().toString(),
                line.reading() == null ? "" : line.reading().toString(),
                text(line.anomalyCode()),
                line.informedConsumption() == null ? "" : line.informedConsumption().toString());
    }

    /**
     * Le o arquivo (ida ou volta). Cabecalho diferente do leiaute lanca
     * {@link MeteringInputException}; linhas em branco no fim sao ignoradas;
     * cada outra linha vira um {@link ParsedLine}, com erro nomeado quando um
     * campo nao pode ser lido (nunca um valor presumido).
     */
    public static List<ParsedLine> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "Arquivo do fornecedor vazio: nada a importar");
        }
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String header = lines[0].trim();
        if (!HEADER.equals(header)) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "Cabecalho fora do leiaute " + LAYOUT_CODE + ": esperado \"" + HEADER
                            + "\", recebido \"" + header + "\"");
        }
        List<ParsedLine> result = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.isBlank()) {
                continue;
            }
            int lineNo = i + 1;
            result.add(parseLine(lineNo, raw));
        }
        if (result.isEmpty()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "Arquivo do fornecedor so tem o cabecalho: nenhuma linha de leitura");
        }
        return result;
    }

    private static ParsedLine parseLine(int lineNo, String raw) {
        String[] f = raw.split(DELIMITER, -1);
        if (f.length != COLUMNS.size()) {
            return new ParsedLine(lineNo, null,
                    "linha com " + f.length + " colunas; o leiaute tem " + COLUMNS.size());
        }
        try {
            VendorLine line = new VendorLine(
                    parseInt(f[0], "SEQ"),
                    blankToNull(f[1]),
                    blankToNull(f[2]),
                    f[3].isBlank() ? null : YearMonth.parse(f[3].trim()),
                    f[4].isBlank() ? null : UUID.fromString(f[4].trim()),
                    f[5].isBlank() ? null : UUID.fromString(f[5].trim()),
                    f[6].isBlank() ? null : MeasurementKind.valueOf(f[6].trim()),
                    parseDate(f[7], "DATA_PREVISTA"),
                    parseIntOrNull(f[8], "LEITURA_ANTERIOR"),
                    parseDate(f[9], "DATA_LEITURA"),
                    parseIntOrNull(f[10], "LEITURA"),
                    blankToNull(f[11]),
                    parseIntOrNull(f[12], "CONSUMO_INFORMADO"));
            return new ParsedLine(lineNo, line, null);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return new ParsedLine(lineNo, null, "linha ilegivel no leiaute: " + e.getMessage());
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseInt(String value, String column) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("coluna " + column + " nao e um inteiro: \"" + value + "\"");
        }
    }

    private static Integer parseIntOrNull(String value, String column) {
        return value == null || value.isBlank() ? null : parseInt(value, column);
    }

    private static LocalDate parseDate(String value, String column) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("coluna " + column + " nao e uma data ISO: \"" + value + "\"");
        }
    }
}
