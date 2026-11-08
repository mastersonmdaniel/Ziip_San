package br.com.ziipsan.commercial.billingrules.fixtures;

import br.com.ziipsan.commercial.billingrules.AmountLine;
import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.TariffBracket;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vetores de contrato {@code billing-contract-fixtures} (ED-19): o MESMO
 * arquivo JSON roda no teste JVM do servidor e no teste instrumentado do
 * emulador Android em todo build — resultado diferente em qualquer dos dois
 * lados BLOQUEIA o merge (a divergencia e do artefato/contrato, nao "do
 * aparelho"). O teste diferencial noturno com 10 mil ligacoes reusa esta
 * mesma entrada, gerando variacoes deterministicas dos vetores-base
 * (ver {@link #differentialCases(int, long)}).
 *
 * <p>Formato do vetor: {@code id}, {@code description}, {@code input}
 * (leituras, economias e o retrato de tarifa) e {@code expected}
 * (consumo, faturado e os componentes em 2 casas). UUIDs dos vetores sao
 * fixos e ficticios — sao vetores de aritmetica, nao dados de cliente.
 */
public final class ContractFixtures {

    /** Caminho do recurso dentro do artefato (o mesmo JAR nos dois lados). */
    public static final String RESOURCE_PATH = "/billing-contract-fixtures/billing-contract-fixtures.json";

    private ContractFixtures() {
    }

    /** Um vetor: entrada montada + esperado declarado no arquivo. */
    public record FixtureCase(String id, String description, FieldBillInput input,
                              int expectedConsumption, int expectedBilled,
                              BigDecimal expectedWater, BigDecimal expectedFixed,
                              BigDecimal expectedSewer, BigDecimal expectedDebits,
                              BigDecimal expectedCreditsApplied, BigDecimal expectedTotal) {
    }

    /** Carrega os vetores do recurso do artefato. */
    public static List<FixtureCase> load() {
        try (InputStream in = ContractFixtures.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Vetores de contrato nao encontrados no artefato: " + RESOURCE_PATH);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler os vetores de contrato", e);
        }
    }

    /** Carrega os vetores de um texto JSON (o teste Android le o arquivo gemeo). */
    public static List<FixtureCase> parse(String json) {
        Object root = MiniJson.parse(json);
        Map<String, Object> doc = asObject(root, "raiz");
        List<Object> cases = asArray(doc.get("cases"), "cases");
        List<FixtureCase> out = new ArrayList<>();
        for (Object raw : cases) {
            out.add(parseCase(asObject(raw, "case")));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de fixtures sem nenhum vetor");
        }
        return List.copyOf(out);
    }

    /**
     * Executa todos os vetores e devolve as divergencias (vazia = contrato
     * verde). Nao lanca na primeira divergencia: o relatorio do build lista
     * todas.
     */
    public static List<String> runAll(List<FixtureCase> cases) {
        List<String> failures = new ArrayList<>();
        for (FixtureCase c : cases) {
            FieldBillResult actual = FieldBillCalculator.calculate(c.input());
            compare(failures, c, "consumptionM3", c.expectedConsumption(), actual.consumptionM3());
            compare(failures, c, "billedM3", c.expectedBilled(), actual.billedM3());
            compareMoney(failures, c, "waterValue", c.expectedWater(), actual.waterValue());
            compareMoney(failures, c, "fixedValue", c.expectedFixed(), actual.fixedValue());
            compareMoney(failures, c, "sewerValue", c.expectedSewer(), actual.sewerValue());
            compareMoney(failures, c, "debitsValue", c.expectedDebits(), actual.debitsValue());
            compareMoney(failures, c, "creditsApplied", c.expectedCreditsApplied(), actual.creditsApplied());
            compareMoney(failures, c, "total", c.expectedTotal(), actual.total());
            // O fingerprint tambem e contrato: mesma entrada, mesmo hash, em qualquer JVM.
            FieldBillResult again = FieldBillCalculator.calculate(c.input());
            if (!actual.fingerprint().equals(again.fingerprint())) {
                failures.add(c.id() + ": fingerprint nao deterministico");
            }
        }
        return List.copyOf(failures);
    }

    /**
     * Casos do teste diferencial noturno: variacoes deterministicas (semente
     * fixa por noite) dos vetores-base — leituras e consumos derivados por
     * aritmetica inteira, sem aleatoriedade de relogio. 10 mil ligacoes =
     * {@code differentialCases(10_000, seed)}; aqui so se GERAM os casos: a
     * comparacao JVM x Android roda no job noturno (pendencia de CI/emulador
     * declarada no relatorio).
     */
    public static List<FieldBillInput> differentialCases(int count, long seed) {
        if (count < 0) {
            throw new IllegalArgumentException("Quantidade de casos negativa: " + count);
        }
        List<FixtureCase> base = load();
        List<FieldBillInput> out = new ArrayList<>(count);
        long state = seed;
        for (int i = 0; i < count; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            FixtureCase source = base.get((int) ((state >>> 33) % base.size()));
            FieldBillInput in = source.input();
            int delta = (int) ((state >>> 40) % 400);
            int capacity = 1;
            for (int d = 0; d < in.meterDigits(); d++) {
                capacity *= 10;
            }
            int current = (in.previousReading() + delta) % capacity;
            out.add(new FieldBillInput(in.taskId(), in.unitId(), in.installationId(),
                    in.previousReading(), current, in.meterDigits(), in.economies(),
                    in.snapshot(), in.debits(), in.credits(), in.dueDate()));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------
    // Parse dos vetores
    // ------------------------------------------------------------------

    private static FixtureCase parseCase(Map<String, Object> node) {
        String id = asString(node.get("id"), "id");
        String description = asString(node.get("description"), "description");
        Map<String, Object> input = asObject(node.get("input"), "input");
        Map<String, Object> snap = asObject(input.get("snapshot"), "snapshot");

        List<TariffBracket> brackets = new ArrayList<>();
        for (Object raw : asArray(snap.get("brackets"), "brackets")) {
            Map<String, Object> b = asObject(raw, "bracket");
            Object width = b.get("widthM3");
            brackets.add(new TariffBracket(
                    width == null ? null : asInt(width, "widthM3"),
                    asDecimal(b.get("pricePerM3"), "pricePerM3")));
        }
        Map<String, String> params = new java.util.LinkedHashMap<>();
        Object rawParams = snap.get("parameterVersions");
        if (rawParams != null) {
            for (Map.Entry<String, Object> e : asObject(rawParams, "parameterVersions").entrySet()) {
                params.put(e.getKey(), asString(e.getValue(), "parameterVersions." + e.getKey()));
            }
        }
        Object sewerPct = snap.get("sewerPct");
        FieldTariffSnapshot snapshot = new FieldTariffSnapshot(
                UUID.fromString(asString(snap.get("tariffVersionId"), "tariffVersionId")),
                UUID.fromString(asString(snap.get("categoryId"), "categoryId")),
                asInt(snap.get("minimumM3PerEconomy"), "minimumM3PerEconomy"),
                asDecimal(snap.get("fixedChargePerEconomy"), "fixedChargePerEconomy"),
                brackets,
                Boolean.TRUE.equals(snap.get("sewerEnabled")),
                sewerPct == null ? null : asDecimal(sewerPct, "sewerPct"),
                params);

        FieldBillInput billInput = new FieldBillInput(
                UUID.fromString(asString(input.get("taskId"), "taskId")),
                UUID.fromString(asString(input.get("unitId"), "unitId")),
                UUID.fromString(asString(input.get("installationId"), "installationId")),
                asInt(input.get("previousReading"), "previousReading"),
                asInt(input.get("currentReading"), "currentReading"),
                asInt(input.get("meterDigits"), "meterDigits"),
                asInt(input.get("economies"), "economies"),
                snapshot,
                parseLines(input.get("debits")),
                parseLines(input.get("credits")),
                LocalDate.parse(asString(input.get("dueDate"), "dueDate")));

        Map<String, Object> expected = asObject(node.get("expected"), "expected");
        return new FixtureCase(id, description, billInput,
                asInt(expected.get("consumptionM3"), "consumptionM3"),
                asInt(expected.get("billedM3"), "billedM3"),
                asDecimal(expected.get("waterValue"), "waterValue"),
                asDecimal(expected.get("fixedValue"), "fixedValue"),
                asDecimal(expected.get("sewerValue"), "sewerValue"),
                asDecimal(expected.get("debitsValue"), "debitsValue"),
                asDecimal(expected.get("creditsApplied"), "creditsApplied"),
                asDecimal(expected.get("total"), "total"));
    }

    private static List<AmountLine> parseLines(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<AmountLine> out = new ArrayList<>();
        for (Object item : asArray(raw, "lines")) {
            Map<String, Object> line = asObject(item, "line");
            out.add(new AmountLine(asString(line.get("code"), "code"),
                    asDecimal(line.get("amount"), "amount")));
        }
        return List.copyOf(out);
    }

    private static void compare(List<String> failures, FixtureCase c, String field, int expected, int actual) {
        if (expected != actual) {
            failures.add(c.id() + ": " + field + " esperado " + expected + ", calculado " + actual);
        }
    }

    private static void compareMoney(List<String> failures, FixtureCase c, String field,
                                     BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            failures.add(c.id() + ": " + field + " esperado " + expected.toPlainString()
                    + ", calculado " + actual.toPlainString());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Fixture: '" + field + "' deveria ser objeto JSON");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object value, String field) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("Fixture: '" + field + "' deveria ser lista JSON");
    }

    private static String asString(Object value, String field) {
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Fixture: '" + field + "' deveria ser texto");
    }

    private static int asInt(Object value, String field) {
        if (value instanceof BigDecimal number) {
            return number.intValueExact();
        }
        throw new IllegalArgumentException("Fixture: '" + field + "' deveria ser inteiro");
    }

    private static BigDecimal asDecimal(Object value, String field) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        throw new IllegalArgumentException("Fixture: '" + field + "' deveria ser numero");
    }
}
