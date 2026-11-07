package br.com.ziipsan.commercial.collection.persistence;

import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Guardas textuais da V6_5 (sem banco), no padrao das guardas da P3.1/P3.2. */
class RefundMigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = RefundMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V6_5__payment_refunds_and_unidentified_queue.sql")) {
            assertThat(in).as("migration V6_5 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    void devolucaoLigadaAoPagamentoDeOrigemComPendenciaMotivada() {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS refund_requests");
        assertThat(sql).contains("REFERENCES payments (tenant_id, id)");
        assertThat(sql).contains("'MISSING_ORIGIN_PAYMENT', 'AMOUNT_DIVERGENCE'");
        assertThat(sql).contains("status <> 'PENDENTE' OR pending_reason IS NOT NULL");
        assertThat(sql).contains("'DUPLICATE_PAYMENT', 'UNDUE_CHARGE'");
        assertThat(sql).contains("'P3_2_HYBRID_SECOND_PAYMENT'");
    }

    @Test
    void destinoSomenteCifradoComRetencaoENenhumDadoSemeado() {
        assertThat(sql).contains("destination_ciphertext TEXT");
        assertThat(sql).contains("destination_retention_until");
        assertThat(sql).contains("destination_ciphertext IS NULL OR destination_retention_until IS NOT NULL");
        assertThat(sql).doesNotContain("INSERT INTO");
        // Nenhuma coluna de destino em claro.
        assertThat(sql).doesNotContain("pix_key VARCHAR");
        assertThat(sql).doesNotContain("bank_account VARCHAR");
    }

    @Test
    void idempotenciaDoReenvioNosIndicesUnicos() {
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_refund_requests_os "
                + "ON refund_requests(tenant_id, service_order_id)");
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_refund_executions_request "
                + "ON refund_executions(tenant_id, refund_request_id)");
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_refund_executions_os "
                + "ON refund_executions(tenant_id, service_order_id)");
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_unidentified_payments_payment "
                + "ON unidentified_payments(tenant_id, payment_id)");
    }

    @Test
    void filaSemClienteFicticioEFatosParaP322() {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS unidentified_payments");
        assertThat(sql).doesNotContain("customer_id");
        assertThat(sql).doesNotContain("holder_name");
        assertThat(sql).contains("'REFUND_APPLIED', 'PAYMENT_IDENTIFIED'");
        assertThat(sql).contains("'PIX_RETURN_TO_PAYER', 'PIX_KEY', 'BANK_ACCOUNT'");
        assertThat(sql).contains("'USER_CHOICE', 'CREDIT_NEXT_BILL', 'BANK_TRANSFER'");
    }

    @Test
    void aprovacaoDuplaTemGuardasNoBancoETabelasRegistradasParaRls() {
        assertThat(sql).contains("first_approver_id <> requested_by");
        assertThat(sql).contains("second_approver_id <> first_approver_id");
        for (String table : new String[]{"refund_requests", "refund_executions", "unidentified_payments"}) {
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    void entidadeDoDestinoNaoTemCampoEmClaro() throws Exception {
        for (var field : br.com.ziipsan.commercial.collection.domain.RefundRequestEntity.class
                .getDeclaredFields()) {
            assertThat(field.getName()).as("campo de destino em claro proibido")
                    .isNotIn("pixKey", "bankAccount", "bankBranch", "destinationPlaintext");
        }
        var cipherField = br.com.ziipsan.commercial.collection.domain.RefundRequestEntity.class
                .getDeclaredField("destinationCiphertext");
        assertThat(cipherField.getType()).isEqualTo(String.class);
    }

    // =====================================================================
    // Correcao pos-revisao B1: LARGURA x LITERAL, medido - nao so contains.
    // A versao original declarava VARCHAR(24) para um literal de 26 e
    // VARCHAR(20) para um de 23, e nenhum teste media comprimento.
    // =====================================================================

    /** Bloco de uma CREATE TABLE (do nome ate o fechamento ");"). */
    private static String tableBlock(String table) {
        int start = sql.indexOf("CREATE TABLE IF NOT EXISTS " + table + " (");
        assertThat(start).as("tabela %s na V6_5", table).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf("\n);", start);
        assertThat(end).as("fechamento da tabela %s", table).isGreaterThan(start);
        return sql.substring(start, end);
    }

    /** Largura declarada de {@code coluna VARCHAR(n)} dentro do bloco. */
    private static int columnWidth(String block, String column) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(column) + "\\s+VARCHAR\\((\\d+)\\)")
                .matcher(block);
        assertThat(m.find()).as("coluna %s VARCHAR na V6_5", column).isTrue();
        return Integer.parseInt(m.group(1));
    }

    /** Literais do CHECK (coluna IN ('A', 'B', ...)) dentro do bloco. */
    private static List<String> checkLiterals(String block, String column) {
        // O "IN (" pode quebrar de linha na formatacao da migration.
        Matcher m = Pattern.compile("CHECK \\(" + Pattern.quote(column) + " IN\\s*\\(([^)]*)\\)")
                .matcher(block);
        assertThat(m.find()).as("CHECK de %s na V6_5", column).isTrue();
        List<String> literals = new ArrayList<>();
        Matcher lit = Pattern.compile("'([^']+)'").matcher(m.group(1));
        while (lit.find()) {
            literals.add(lit.group(1));
        }
        assertThat(literals).as("literais de %s", column).isNotEmpty();
        return literals;
    }

    /**
     * Para CADA coluna com CHECK de literais das tres tabelas, CADA literal
     * tem de caber na largura declarada. Se algum literal exceder a coluna,
     * este teste FALHA (era exatamente o defeito B1).
     */
    @Test
    void larguraDeCadaColunaComportaCadaLiteralDoSeuCheck() {
        String[][] columns = {
                {"refund_requests", "refund_type"}, {"refund_requests", "source"},
                {"refund_requests", "refund_mode"}, {"refund_requests", "chosen_mode"},
                {"refund_requests", "refund_destination"}, {"refund_requests", "status"},
                {"refund_requests", "pending_reason"},
                {"refund_executions", "effect"},
                {"unidentified_payments", "queue_reason"}, {"unidentified_payments", "status"},
        };
        for (String[] tc : columns) {
            String block = tableBlock(tc[0]);
            int width = columnWidth(block, tc[1]);
            for (String literal : checkLiterals(block, tc[1])) {
                assertThat(literal.length())
                        .as("literal %s (%d caracteres) excede %s.%s VARCHAR(%d)",
                                literal, literal.length(), tc[0], tc[1], width)
                        .isLessThanOrEqualTo(width);
            }
        }
    }

    /** Regressao nomeada dos dois estouros medidos na revisao. */
    @Test
    void literaisQueEstouravamCabemAposOAlargamento() {
        // 'P3_2_HYBRID_SECOND_PAYMENT' tem 26 caracteres; a coluna tinha 24.
        assertThat("P3_2_HYBRID_SECOND_PAYMENT".length()).isEqualTo(26);
        assertThat(columnWidth(tableBlock("refund_requests"), "source")).isGreaterThanOrEqualTo(26);
        // 'DEVOLVIDO_CLASSIFICACAO' tem 23 caracteres; a coluna tinha 20.
        assertThat("DEVOLVIDO_CLASSIFICACAO".length()).isEqualTo(23);
        assertThat(columnWidth(tableBlock("unidentified_payments"), "status")).isGreaterThanOrEqualTo(23);
    }

    /** O length da entidade acompanha a largura da migration nas duas colunas. */
    @Test
    void lengthDasEntidadesAcompanhaALarguraDaMigration() throws Exception {
        var source = br.com.ziipsan.commercial.collection.domain.RefundRequestEntity.class
                .getDeclaredField("source").getAnnotation(Column.class);
        assertThat(source.length())
                .isEqualTo(columnWidth(tableBlock("refund_requests"), "source"))
                .isGreaterThanOrEqualTo("P3_2_HYBRID_SECOND_PAYMENT".length());
        var status = br.com.ziipsan.commercial.collection.domain.UnidentifiedPaymentEntity.class
                .getDeclaredField("status").getAnnotation(Column.class);
        assertThat(status.length())
                .isEqualTo(columnWidth(tableBlock("unidentified_payments"), "status"))
                .isGreaterThanOrEqualTo("DEVOLVIDO_CLASSIFICACAO".length());
    }

    // =====================================================================
    // Correcao pos-revisao B3: pendencia PERSISTIVEL (valores anulaveis com
    // CHECK explicito), na migration e na entidade.
    // =====================================================================

    @Test
    void pendenciaSemValorPersisteEDevolucaoCalculadaSemValorERecusadaPeloCheck() {
        String block = tableBlock("refund_requests");
        // Sem NOT NULL seco: pendencia sem base/valor precisa persistir.
        assertThat(block).doesNotContain("base_amount NUMERIC(14,2) NOT NULL");
        assertThat(block).doesNotContain("refund_amount NUMERIC(14,2) NOT NULL");
        assertThat(block).contains("base_amount NUMERIC(14,2),");
        assertThat(block).contains("refund_amount NUMERIC(14,2),");
        // CHECK explicito: em pendencia os valores PODEM ser nulos...
        assertThat(block).contains("status = 'PENDENTE'");
        assertThat(block).contains("base_amount IS NULL OR base_amount > 0");
        assertThat(block).contains("refund_amount IS NULL OR refund_amount > 0");
        // ...fora de pendencia sao obrigatorios e > 0 (sem valor = recusada).
        assertThat(block).contains("status <> 'PENDENTE'");
        assertThat(block).contains("base_amount IS NOT NULL AND base_amount > 0");
        assertThat(block).contains("refund_amount IS NOT NULL AND refund_amount > 0");
    }

    @Test
    void entidadeEspelhaValoresAnulaveisNaPendencia() throws Exception {
        var base = br.com.ziipsan.commercial.collection.domain.RefundRequestEntity.class
                .getDeclaredField("baseAmount").getAnnotation(Column.class);
        var refund = br.com.ziipsan.commercial.collection.domain.RefundRequestEntity.class
                .getDeclaredField("refundAmount").getAnnotation(Column.class);
        assertThat(base.nullable()).as("baseAmount anulavel na pendencia").isTrue();
        assertThat(refund.nullable()).as("refundAmount anulavel na pendencia").isTrue();
    }

    // =====================================================================
    // Correcao pos-revisao B2: o pom do modulo declara ziipsan-security.
    // =====================================================================

    @Test
    void pomDoModuloDeclaraZiipsanSecurityParaOFieldCipher() throws IOException {
        // O teste roda com diretorio de trabalho na raiz do repositorio ou
        // do modulo, conforme o runner; tenta os dois caminhos do arquivo
        // entregue junto a esta entrega.
        String pom = null;
        for (String candidate : new String[]{
                "commercial/ziipsan-collection/pom.xml", "pom.xml"}) {
            var path = java.nio.file.Path.of(candidate);
            if (java.nio.file.Files.exists(path)) {
                pom = java.nio.file.Files.readString(path);
                break;
            }
        }
        assertThat(pom).as("pom.xml do ziipsan-collection acessivel ao teste").isNotNull();
        assertThat(pom).contains("<artifactId>ziipsan-security</artifactId>");
        assertThat(pom).contains("RefundDestinationProtector");
    }

    // =====================================================================
    // Correcao pos-revisao b2: ADD CONSTRAINT protegido por pg_constraint.
    // =====================================================================

    @Test
    void addConstraintDePaymentEventsTemGuardaDeExistencia() {
        assertThat(sql).contains("FROM pg_constraint");
        assertThat(sql).contains("conname = 'payment_events_event_type_check'");
        assertThat(sql).contains("IF NOT EXISTS (SELECT 1 FROM pg_constraint");
        assertThat(sql).contains("END $$;");
    }
}
