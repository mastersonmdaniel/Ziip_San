package br.com.ziipsan.operations.metering.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mantém o SQL da ED-17 (pendente de numeração na sequência global — ver o
 * cabeçalho do arquivo) de acordo com o código, sem banco: os CHECKs listam
 * exatamente os enums, as tabelas-série são particionadas sem auditoria por
 * linha, a tabela de resumo e o índice por ponto e tempo existem, e nada é
 * semeado.
 *
 * <p><b>Integração:</b> quando o SQL receber o número da sequência global e
 * for para {@code db/migration/tenant/V<numero>__metering_telemetry.sql},
 * o caminho lido por este teste muda no mesmo commit.
 */
class TelemetryMigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = TelemetryMigrationConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-17__telemetry_and_supervisory.sql")) {
            assertThat(in).as("SQL da ED-17 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <E extends Enum<E>> Set<String> names(Class<E> type) {
        return EnumSet.allOf(type).stream().map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Todas as listas {@code CHECK (coluna IN (...))} do SQL, por coluna — uma coluna pode ter mais de uma tabela. */
    private static java.util.Map<String, java.util.List<Set<String>>> checkLists() {
        java.util.Map<String, java.util.List<Set<String>>> result = new java.util.LinkedHashMap<>();
        Matcher m = Pattern.compile("CHECK\\s*\\(\\s*(\\w+)\\s+IN\\s*\\(([^)]*)\\)\\s*\\)").matcher(SQL);
        while (m.find()) {
            Set<String> values = new LinkedHashSet<>();
            for (String v : m.group(2).split(",")) {
                values.add(v.trim().replace("'", ""));
            }
            result.computeIfAbsent(m.group(1), k -> new java.util.ArrayList<>()).add(values);
        }
        return result;
    }

    @Test
    @DisplayName("os CHECKs do SQL listam exatamente os valores dos enums do código")
    void checksMatchEnums() {
        java.util.Map<String, java.util.List<Set<String>>> checks = checkLists();
        assertThat(checks.get("data_state"))
                .allMatch(values -> values.equals(names(DataState.class)));
        assertThat(checks.get("vee_status")).containsExactly(names(VeeStatus.class));
        // 'kind' aparece no ponto de medição (água/poço) e no alarme (os seis tipos)
        assertThat(checks.get("kind")).containsExactlyInAnyOrder(
                names(br.com.ziipsan.operations.metering.rules.MeasurementKind.class),
                names(MeterAlarmKind.class));
        assertThat(checks.get("action")).containsExactly(names(AlarmAction.class));
        assertThat(checks.get("source")).containsExactly(Set.of("MANUAL", "SUPERVISORY"));
        assertThat(checks.get("status")).containsExactly(Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED"));
    }

    @Test
    @DisplayName("as três tabelas-série são particionadas por mês e registradas sem auditoria por linha")
    void seriesTablesArePartitionedWithoutRowAudit() {
        for (String table : Set.of("telemetry_readings", "telemetry_hourly_profile", "supervisory_samples")) {
            assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(SQL).contains("register_tenant_table('" + table + "', p_audit => false)");
        }
        assertThat(SQL).contains("PARTITION BY RANGE (reference_month)");
        assertThat(SQL).contains("PARTITION BY RANGE (bucket_month)");
        // Sem partição DEFAULT: mês sem partição falha, não cai em balde sem poda (padrão P2.3).
        assertThat(SQL).doesNotContainPattern("PARTITION OF [^;]*DEFAULT");
        // Idempotência dentro do índice único, com a chave de partição.
        assertThat(SQL).contains("idx_telemetry_readings_idempotency");
        assertThat(SQL).contains("idx_telemetry_hourly_point_hour");
        assertThat(SQL).contains("idx_supervisory_samples_key");
    }

    @Test
    @DisplayName("a tabela de resumo e o índice por ponto e tempo da curva existem")
    void summaryAndPointTimeIndexExist() {
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS telemetry_hourly_daily_summary");
        assertThat(SQL).contains("idx_telemetry_hourly_point_time");
        assertThat(SQL).contains("ON telemetry_hourly_profile(tenant_id, measuring_point_id, hour_start)");
    }

    @Test
    @DisplayName("nenhum dado é semeado: o SQL não tem INSERT nem COPY")
    void noSeedData() {
        String withoutComments = SQL.replaceAll("(?m)--.*$", "");
        assertThat(Pattern.compile("(?i)\\bINSERT\\s+INTO\\b").matcher(withoutComments).find()).isFalse();
        assertThat(withoutComments).doesNotContainIgnoringCase("COPY ");
    }

    @Test
    @DisplayName("o cabeçalho declara a pendência de numeração em vez de um número inventado")
    void numberingPendencyIsDeclared() {
        assertThat(SQL).contains("PENDÊNCIA DE NUMERAÇÃO");
        assertThat(SQL).contains("sequência global");
    }
}
