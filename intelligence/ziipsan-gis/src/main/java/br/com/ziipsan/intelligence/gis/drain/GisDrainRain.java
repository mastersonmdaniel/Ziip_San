package br.com.ziipsan.intelligence.gis.drain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitoramento pluviométrico da GIS-13 (aceite da ED-64): fonte
 * configurável pelo parâmetro {@code drain.rain_source} —
 * {@code CEMADEN_IMPORT} (importação do download mensal do Cemaden,
 * com a fonte citada em cada lote) ou {@code OWN_GAUGE}
 * (pluviômetro próprio do tenant). <b>Sem depender de API do
 * Cemaden</b>, não confirmada: nenhum cliente HTTP existe nesta
 * entrega; a entrada é sempre ARQUIVO importado ou lançamento.
 *
 * <p>Formato do download mensal do Cemaden: CSV delimitado por
 * {@code ;} com cabeçalho nomeando as colunas de instante e de chuva.
 * O importador localiza as colunas por NOME (aceita os rótulos
 * usuais do arquivo do Cemaden e do lançamento próprio), nunca por
 * posição fixa — e o layout exato do arquivo vigente deve ser
 * conferido no primeiro download real da implantação (registrado no
 * relatório): se um rótulo novo aparecer, ele entra nas listas
 * {@link #TIMESTAMP_HEADERS}/{@link #RAIN_HEADERS} como DADO.
 * Linhas de metadado anteriores ao cabeçalho são ignoradas até a
 * linha que contém os dois rótulos; linha de dado inválida aborta a
 * importação inteira (sem lote parcial silencioso).
 *
 * <p>Unidade: milímetros de chuva por registro. O acumulado de um
 * período é a SOMA dos registros dentro dele — calculado só sobre
 * leituras importadas de fato: sem leitura no período, o acumulado é
 * {@code null} (desconhecido), nunca zero inventado. É esse acumulado
 * que liga a ocorrência de alagamento à chuva do período.
 */
public final class GisDrainRain {

    /** Rótulos aceitos da coluna de instante (minúsculas, sem acento). */
    public static final List<String> TIMESTAMP_HEADERS = List.of(
            "datahora", "data_hora", "data hora", "datetime", "timestamp",
            "data", "date");

    /** Rótulos aceitos da coluna de chuva em mm. */
    public static final List<String> RAIN_HEADERS = List.of(
            "chuva", "precipitacao", "precipitacao_mm", "chuva_mm",
            "rain", "rain_mm", "valor", "value");

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    /** Uma leitura pluviométrica importada/lançada. */
    public record RainReading(LocalDateTime readAt, double rainMm) {
    }

    /** Resultado da importação de um arquivo mensal. */
    public record RainImportResult(String sourceKind, String sourceRef,
                                   List<RainReading> readings) {
        public double totalMm() {
            return readings.stream().mapToDouble(RainReading::rainMm).sum();
        }
    }

    private GisDrainRain() {
    }

    /** A fonte informada é uma das configuráveis (drain.rain_source)? */
    public static void ensureRainSource(String sourceKind) {
        if (sourceKind == null || !GisDrainCatalog.RAIN_SOURCES.contains(sourceKind)) {
            throw new IllegalArgumentException(
                    "fonte pluviometrica fora de drain.rain_source: " + sourceKind
                            + " (esperado CEMADEN_IMPORT ou OWN_GAUGE)");
        }
    }

    /**
     * Importa o CSV mensal. {@code sourceRef} é a CITAÇÃO da fonte
     * exigida pelo aceite (ex.: o nome do arquivo/URL do download do
     * Cemaden ou a identificação do pluviômetro próprio): sem citação,
     * sem importação — chuva sem fonte não entra no cadastro.
     */
    public static RainImportResult importCsv(String sourceKind, String sourceRef,
                                             String csvContent) {
        ensureRainSource(sourceKind);
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException(
                    "importacao pluviometrica exige a fonte citada (source_ref)");
        }
        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("arquivo de chuva vazio");
        }
        String[] lines = csvContent.replace("\r\n", "\n").split("\n");
        int headerIdx = -1;
        int tsCol = -1;
        int rainCol = -1;
        for (int i = 0; i < lines.length; i++) {
            String[] cols = split(lines[i]);
            int t = indexOfHeader(cols, TIMESTAMP_HEADERS);
            int r = indexOfHeader(cols, RAIN_HEADERS);
            if (t >= 0 && r >= 0) {
                headerIdx = i;
                tsCol = t;
                rainCol = r;
                break;
            }
        }
        if (headerIdx < 0) {
            throw new IllegalArgumentException(
                    "cabecalho do CSV de chuva nao localizado (colunas de instante e chuva)");
        }
        List<RainReading> readings = new ArrayList<>();
        for (int i = headerIdx + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cols = split(lines[i]);
            if (cols.length <= Math.max(tsCol, rainCol)) {
                throw new IllegalArgumentException(
                        "linha " + (i + 1) + " do CSV de chuva com colunas insuficientes");
            }
            LocalDateTime at = parseTimestamp(cols[tsCol].trim(), i + 1);
            double mm = parseRain(cols[rainCol].trim(), i + 1);
            readings.add(new RainReading(at, mm));
        }
        if (readings.isEmpty()) {
            throw new IllegalArgumentException("CSV de chuva sem nenhuma leitura");
        }
        return new RainImportResult(sourceKind, sourceRef, List.copyOf(readings));
    }

    /** Totais diários (mm por dia) das leituras, em ordem de data. */
    public static Map<LocalDate, Double> dailyTotals(List<RainReading> readings) {
        Map<LocalDate, Double> out = new LinkedHashMap<>();
        readings.stream()
                .sorted((a, b) -> a.readAt().compareTo(b.readAt()))
                .forEach(r -> out.merge(r.readAt().toLocalDate(), r.rainMm(), Double::sum));
        return Map.copyOf(out);
    }

    /**
     * Acumulado (mm) no período fechado [from, to] — a ligação da
     * ocorrência de alagamento com a chuva do período. Devolve null
     * quando NÃO há leitura no período (desconhecido ≠ zero).
     */
    public static Double accumulatedMm(List<RainReading> readings,
                                       LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("periodo de chuva invalido");
        }
        double sum = 0;
        boolean any = false;
        for (RainReading r : readings) {
            if (!r.readAt().isBefore(from) && !r.readAt().isAfter(to)) {
                sum += r.rainMm();
                any = true;
            }
        }
        return any ? sum : null;
    }

    // ------------------------------------------------------------- helpers

    private static String[] split(String line) {
        String delimiter = line.contains(";") ? ";" : ",";
        return line.split(delimiter, -1);
    }

    private static int indexOfHeader(String[] cols, List<String> accepted) {
        for (int i = 0; i < cols.length; i++) {
            String normalized = cols[i].trim().toLowerCase()
                    .replace("á", "a").replace("ã", "a").replace("ç", "c")
                    .replace("é", "e").replace("í", "i");
            if (accepted.contains(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private static LocalDateTime parseTimestamp(String raw, int line) {
        for (DateTimeFormatter f : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(raw, f);
            } catch (RuntimeException ignored) {
                // tenta o proximo formato declarado
            }
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "linha " + line + ": instante de chuva invalido: " + raw);
        }
    }

    private static double parseRain(String raw, int line) {
        try {
            double v = Double.parseDouble(raw.replace(",", "."));
            if (v < 0) {
                throw new IllegalArgumentException(
                        "linha " + line + ": chuva negativa nao e leitura valida: " + raw);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "linha " + line + ": valor de chuva invalido: " + raw);
        }
    }
}
