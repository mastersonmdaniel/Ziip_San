package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Leiaute de arquivo de importação de leituras AMR (ED-17), no mesmo
 * espírito do leiaute da P2.7: CSV delimitado por {@code ;} (o separador das
 * planilhas em pt-BR), UTF-8, uma linha por leitura, primeira linha de
 * cabeçalho fixa.
 *
 * <p><b>Suposição declarada (ver relatório da entrega):</b> nenhum leiaute
 * concreto de arquivo AMR está definido no plano v3.0 — o aceite diz apenas
 * "importação de arquivo de leituras AMR". Este leiaute é, portanto, o
 * contrato publicado desta entrega; quando o integrador do piloto tiver
 * leiaute próprio, troca-se esta classe e o {@code layoutCode}, sem tocar
 * ingestão, VEE nem idempotência.
 *
 * <pre>
 * REF_EXTERNA;PONTO;CANAL;INSTANTE;LEITURA;ESTADO
 * </pre>
 *
 * {@code PONTO} e {@code CANAL} são os códigos cadastrados do ponto e do
 * canal; {@code INSTANTE} é ISO-8601 com fuso (ex.:
 * {@code 2026-11-05T08:30:00-03:00}); {@code LEITURA} pode vir vazia (a VEE
 * estima ou rejeita); {@code ESTADO} é um {@link DataState}.
 *
 * <p>Erro de cabeçalho é erro explícito. Erro numa linha de dados não
 * derruba o arquivo: a linha vira um {@link ParsedLine} com {@code error}.
 */
public final class AmrFileLayout {

    public static final String LAYOUT_CODE = "AMR_CSV_V1";

    public static final String DELIMITER = ";";

    public static final List<String> COLUMNS = List.of(
            "REF_EXTERNA", "PONTO", "CANAL", "INSTANTE", "LEITURA", "ESTADO");

    public static final String HEADER = String.join(DELIMITER, COLUMNS);

    private AmrFileLayout() {
    }

    /** Uma linha do arquivo já interpretada, ainda com os códigos do ponto e do canal. */
    public record AmrLine(String externalRef, String pointCode, String channelCode,
                          OffsetDateTime readAt, Integer dialReading, DataState state) {
    }

    /** Linha lida: ou a {@link AmrLine}, ou o motivo de a linha não ser uma. */
    public record ParsedLine(int lineNo, AmrLine line, String error) {

        public boolean isError() {
            return error != null;
        }
    }

    public static List<ParsedLine> parse(String content) {
        TelemetryInput.notNull(content, "content");
        String[] lines = content.split("\\R");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "Arquivo fora do leiaute " + LAYOUT_CODE + ": cabeçalho esperado '" + HEADER + "'.");
        }
        List<ParsedLine> parsed = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.isBlank()) {
                continue;
            }
            parsed.add(parseLine(i + 1, raw));
        }
        return parsed;
    }

    private static ParsedLine parseLine(int lineNo, String raw) {
        String[] fields = raw.split(DELIMITER, -1);
        if (fields.length != COLUMNS.size()) {
            return new ParsedLine(lineNo, null,
                    "esperadas " + COLUMNS.size() + " colunas, encontradas " + fields.length);
        }
        try {
            String externalRef = fields[0].trim();
            if (externalRef.isBlank()) {
                return new ParsedLine(lineNo, null, "REF_EXTERNA vazia");
            }
            OffsetDateTime readAt = OffsetDateTime.parse(fields[3].trim());
            Integer dial = fields[4].isBlank() ? null : Integer.valueOf(fields[4].trim());
            DataState state = DataState.valueOf(fields[5].trim());
            return new ParsedLine(lineNo, new AmrLine(externalRef, fields[1].trim(), fields[2].trim(),
                    readAt, dial, state), null);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return new ParsedLine(lineNo, null, "linha inválida: " + e.getMessage());
        }
    }
}
