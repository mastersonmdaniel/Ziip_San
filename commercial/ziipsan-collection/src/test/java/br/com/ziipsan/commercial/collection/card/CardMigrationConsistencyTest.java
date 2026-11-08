package br.com.ziipsan.commercial.collection.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas do SQL PENDENTE da ED-28 (sem numero de versao — ver o
 * cabecalho do arquivo): CHECKs = enums do codigo, tabelas registradas
 * para RLS, chaves de parametro do codigo presentes nas definicoes,
 * indices de idempotencia — e a perna "banco" da prova do aceite:
 * NENHUMA coluna de dado de cartao existe no esquema.
 *
 * <p>Correcao D1 da revisao de 08/11/2026: a lista de proibidos NAO
 * vive mais aqui — cada identificador do DDL passa por
 * {@link CardDataGuard#isForbiddenFieldName(String)}, a MESMA fonte
 * unica (PT + EN) da guarda e do teste de arquitetura. Uma coluna
 * {@code numero_cartao} ou {@code validade} no SQL morde neste teste.
 */
class CardMigrationConsistencyTest {

    private static final String PATH = "/db/pendente/PENDENTE__card_acquirer_checkout_settlement.sql";
    private static final String SQL = readResource(PATH);
    /** SQL sem comentarios de linha: a varredura de colunas le so o DDL. */
    private static final String DDL = SQL.lines()
            .map(line -> {
                int at = line.indexOf("--");
                return at >= 0 ? line.substring(0, at) : line;
            })
            .collect(Collectors.joining("\n"));

    private static String readResource(String path) {
        try (InputStream in = CardMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> checkLiterals(String sql, String marker) {
        int at = sql.indexOf(marker);
        assertThat(at).as("marcador %s", marker).isGreaterThanOrEqualTo(0);
        int inAt = sql.indexOf("IN", at);
        int open = sql.indexOf('(', inAt);
        int depth = 0;
        int close = -1;
        for (int i = open; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                close = i;
                break;
            }
        }
        Matcher m = Pattern.compile("'([A-Z0-9_]+)'").matcher(sql.substring(open, close));
        Set<String> literals = new HashSet<>();
        while (m.find()) {
            literals.add(m.group(1));
        }
        return literals;
    }

    @Test
    @DisplayName("CHECKs de modo e de situacao da sessao = enums do codigo")
    void checksMirrorEnums() {
        assertThat(checkLiterals(SQL, "checkout_mode VARCHAR"))
                .isEqualTo(enumNames(CardEnums.CheckoutMode.class));
        assertThat(checkLiterals(SQL, "status VARCHAR"))
                .isEqualTo(enumNames(CardEnums.SessionStatus.class));
    }

    @Test
    @DisplayName("as 5 tabelas registradas para RLS e os indices de idempotencia presentes")
    void tablesRegisteredAndIdempotencyIndexes() {
        for (String table : new String[]{"card_acquirer_profiles", "card_checkout_sessions",
                "card_acquirer_events", "card_settlements", "card_statement_lines"}) {
            assertThat(SQL).as("register_tenant_table de %s", table)
                    .contains("register_tenant_table('" + table + "')");
        }
        assertThat(SQL).contains("idx_card_events_idempotency")
                .contains("(tenant_id, acquirer_code, event_id)")
                .contains("idx_card_settlements_transaction")
                .contains("fk_card_settlements_payment");
    }

    @Test
    @DisplayName("todas as chaves de parametro do codigo estao nas definicoes do SQL")
    void parameterKeysDeclared() {
        for (String key : new String[]{CardParameters.ENABLED, CardParameters.ACQUIRER_CODE,
                CardParameters.CHECKOUT_MODE, CardParameters.STATEMENT_TOLERANCE}) {
            assertThat(SQL).as("definicao de %s", key).contains("'" + key + "'");
        }
    }

    @Test
    @DisplayName("liquido = bruto - taxa conferido no banco nas duas tabelas de valores")
    void netEqualsGrossMinusFeeInDatabase() {
        long checks = DDL.lines().filter(l -> l.contains("net_amount = gross_amount - fee_amount")).count();
        assertThat(checks).isEqualTo(2);
    }

    @Test
    @DisplayName("BANCO: nenhuma coluna de dado de cartao existe no esquema (prova do aceite)")
    void noCardDataColumnsInSchema() {
        // Cada identificador do DDL (nomes com _ preservados) passa pelo
        // vocabulario UNICO da guarda — o mesmo da arquitetura e do runtime.
        java.util.List<String> offenders = new java.util.ArrayList<>();
        Matcher identifiers = Pattern.compile("[a-z0-9_]+").matcher(DDL.toLowerCase());
        while (identifiers.find()) {
            String identifier = identifiers.group();
            if (CardDataGuard.isForbiddenFieldName(identifier)) {
                offenders.add(identifier);
            }
        }
        assertThat(offenders).as("identificadores de dado de cartao no DDL").isEmpty();
        // E o payload bruto do retorno nao e persistido: so o hash dele
        // (que o CardReturnProcessor calcula de verdade — correcao D4).
        assertThat(DDL.toLowerCase()).contains("payload_hash").doesNotContain("payload_json");
    }
}
