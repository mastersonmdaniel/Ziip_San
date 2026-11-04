package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.certification.CertificationService;
import br.com.ziipsan.core.certification.NormEffectivenessService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.34 sobre o PostgreSQL real: a carga da R__norm_catalog.sql, a
 * consulta filtrada e a transicao de vigencia futura.
 *
 * <p>A carga roda na migration de core do ProvisionedTenant (banco
 * virgem), portanto estes testes leem o catalogo real de 1.640 linhas
 * — nao linhas sinteticas. As unicas linhas sinteticas aqui sao as da
 * transicao (prefixo syn-norm-, removidas no finally de cada teste),
 * para a data simulada nao tocar o catalogo real: rodar a transicao
 * com data futura sobre o catalogo real viraria normas de verdade e
 * contaminaria os demais testes da JVM.
 *
 * <p>Contagens dependentes da data de execucao (a transicao de carga
 * ja vira as normas cuja data chegou) sao calculadas no teste a partir
 * de LocalDate.now(), nunca fixadas: o que e invariante — total,
 * classes, selos, REVOGADA, SUBSTITUIDA e a soma
 * VIGENTE + VIGENCIA_FUTURA — e fixo.
 */
class NormCatalogLoadIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    private static CertificationService service;
    private static NormEffectivenessService normService;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
        service = new CertificationService(ProvisionedTenant.appDs, SCHEMA, TENANT);
        normService = new NormEffectivenessService(ProvisionedTenant.appDs, "platform");
    }

    private static int queryInt(String sql) throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String queryString(String sql, String param) throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static Set<String> keysOf(List<CertificationService.StandardView> views) {
        Set<String> keys = new HashSet<>();
        for (CertificationService.StandardView v : views) {
            keys.add(v.catalogKey());
        }
        return keys;
    }

    @Test
    @DisplayName("carga: 1640 normas e 1640 edicoes, com classes, selos e situacoes mapeadas")
    void loadCounts() throws SQLException {
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard WHERE catalog_key NOT LIKE 'syn-%'"))
                .as("total carregado (JSON 1676 menos 36 excluidas da P1.28)").isEqualTo(1640);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard_edition e"
                        + " JOIN platform.cert_standard s ON s.id = e.standard_id"
                        + " WHERE s.catalog_key NOT LIKE 'syn-%'"))
                .as("uma edicao por norma carregada").isEqualTo(1640);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE standard_class = 'REGULATORY' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(795);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE standard_class = 'VOLUNTARY' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(67);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE standard_class = 'REFERENCE' AND catalog_key NOT LIKE 'syn-%'"))
                .as("REFERENCE: 774 do JSON menos as 36 excluidas (todas REFERENCE)").isEqualTo(738);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE standard_class = 'EXPORT_PROFILE' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(40);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE verification_seal = 'OFICIAL' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(1322);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE verification_seal = 'SECUNDARIA' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(305);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE verification_seal = 'NAO_CONFIRMADA' AND catalog_key NOT LIKE 'syn-%'"))
                .as("selo de revisao: exatamente os 13 A_CONFIRMAR / needs_review").isEqualTo(13);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE status = 'REVOGADA' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(105);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE status = 'SUBSTITUIDA' AND catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(90);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE status IN ('VIGENTE', 'VIGENCIA_FUTURA') AND catalog_key NOT LIKE 'syn-%'"))
                .as("VIGENTE + VIGENCIA_FUTURA e invariante; a reparticao move com a data de carga")
                .isEqualTo(1445);
    }

    @Test
    @DisplayName("carga: copia fiel de titulo, resumo, fonte oficial e datas de efeito curadas")
    void loadFaithfulCopy() throws SQLException {
        CertificationService.StandardView decree = service.getStandard("decreto-10046-2019");
        assertThat(decree.title()).isEqualTo("Dispõe sobre a governança no compartilhamento de dados"
                + " no âmbito da administração pública federal e institui o Cadastro Base do Cidadão"
                + " e o Comitê Central de Governança de Dados");
        assertThat(decree.summary()).startsWith("Define categorias de compartilhamento de dados entre órgãos federais");
        assertThat(decree.summary()).contains("Nota de situação:");
        assertThat(decree.standardClass()).isEqualTo(CertificationService.StandardClass.REFERENCE);
        assertThat(decree.verificationSeal()).isEqualTo("OFICIAL");
        assertThat(decree.status()).isEqualTo("VIGENTE");
        assertThat(queryString(
                "SELECT official_source_url FROM platform.cert_standard WHERE catalog_key = ?",
                "decreto-10046-2019"))
                .isEqualTo("https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2019/decreto/d10046.htm");

        CertificationService.StandardView bcb = service.getStandard("bcb-in-777-2026");
        assertThat(bcb.status()).isEqualTo("VIGENCIA_FUTURA");
        assertThat(bcb.effectiveFrom()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(bcb.standardClass()).isEqualTo(CertificationService.StandardClass.REGULATORY);

        CertificationService.StandardView nr10 = service.getStandard("mte-nr-10");
        assertThat(nr10.status()).isEqualTo("VIGENCIA_FUTURA");
        assertThat(nr10.effectiveFrom()).isEqualTo(LocalDate.of(2027, 6, 1));

        // Sem data fixada na fonte: nunca transiciona por palpite.
        CertificationService.StandardView nt = service.getStandard("nt-2026-001-rtc-nfag");
        assertThat(nt.status()).isEqualTo("VIGENCIA_FUTURA");
        assertThat(nt.effectiveFrom()).isNull();
        assertThat(nt.verificationSeal()).isEqualTo("SECUNDARIA");

        // A transicao de carga ja virou as normas cuja data chegou antes
        // da execucao; a expectativa acompanha a data de hoje.
        LocalDate today = LocalDate.now();
        CertificationService.StandardView arsesp = service.getStandard("arsesp-deliberacao-1836-2026");
        assertThat(arsesp.effectiveFrom()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(arsesp.status())
                .isEqualTo(today.isBefore(LocalDate.of(2026, 10, 23)) ? "VIGENCIA_FUTURA" : "VIGENTE");
        CertificationService.StandardView decree13135 = service.getStandard("decreto-13135-2026");
        assertThat(decree13135.effectiveFrom()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(decree13135.status())
                .isEqualTo(today.isBefore(LocalDate.of(2026, 11, 2)) ? "VIGENCIA_FUTURA" : "VIGENTE");

        // Selo de revisao nos itens nao confirmados.
        assertThat(service.getStandard("conama-cnrh-resolucao-conjunta-reuso").verificationSeal())
                .isEqualTo("NAO_CONFIRMADA");
        assertThat(service.getStandard("caer-rr-res-005-2024").verificationSeal())
                .isEqualTo("NAO_CONFIRMADA");

        // Edicao derivada: edition_year, senao year, senao 's/e'.
        assertThat(queryString(
                "SELECT edition FROM platform.cert_standard_edition e"
                        + " JOIN platform.cert_standard s ON s.id = e.standard_id WHERE s.catalog_key = ?",
                "mte-nr-10")).isEqualTo("2004");
        assertThat(queryString(
                "SELECT edition FROM platform.cert_standard_edition e"
                        + " JOIN platform.cert_standard s ON s.id = e.standard_id WHERE s.catalog_key = ?",
                "ogc-api-features")).isEqualTo("s/e");
    }

    @Test
    @DisplayName("carga: as 36 linhas da P1.28 (compra pendente) estao fora do catalogo")
    void abntPurchasePendingExcluded() throws SQLException {
        String[] excluded = {
            "abnt-nbr-12208-2020", "abnt-nbr-12218-2017", "abnt-nbr-5647-1-2025",
            "abnt-nbr-5667-1-2006", "abnt-nbr-5667-2-2006", "abnt-nbr-5667-3-2006",
            "abnt-nbr-7362-2025", "abnt-nbr-10004-1-2024", "abnt-nbr-10004-2-2024",
            "abnt-nbr-10004-2024", "abnt-nbr-12212", "abnt-nbr-12212-2017",
            "abnt-nbr-8890", "abnt-nbr-8890-2020", "abnt-nbr-iso-21138-1-2021",
        };
        for (String key : excluded) {
            assertThat(queryString(
                    "SELECT catalog_key FROM platform.cert_standard WHERE catalog_key = ?", key))
                    .as("excluido da P1.28 nao pode estar no catalogo: %s", key).isNull();
            assertThatThrownBy(() -> service.getStandard(key))
                    .as("consulta por codigo excluido e 404: %s", key)
                    .isInstanceOf(ApiException.NotFound.class);
        }
        // As demais referencias ABNT entram como metadados (ex.: partes
        // da NBR 7362 que NAO sao o item 2025 a comprar).
        assertThat(service.getStandard("abnt-nbr-7362-1-2005").title()).isNotBlank();
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard"
                        + " WHERE catalog_key LIKE 'abnt-%' AND catalog_key NOT LIKE 'syn-%'"))
                .as("ABNT carregadas como metadados: 264 do JSON menos 36 excluidas")
                .isEqualTo(228);
    }

    @Test
    @DisplayName("consulta: filtros por classe, area, modulo, situacao e agencia (P1.34)")
    void filteredQuery() {
        Set<String> regulatory = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(
                        CertificationService.StandardClass.REGULATORY, null, null, null, null)));
        assertThat(regulatory).contains("bcb-in-777-2026", "mte-nr-10");
        assertThat(regulatory).doesNotContain("decreto-10046-2019");

        Set<String> future = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(null, null, null, "VIGENCIA_FUTURA", null)));
        assertThat(future).contains("bcb-in-777-2026", "mte-nr-10", "nt-2026-001-rtc-nfag");
        assertThat(future).doesNotContain("decreto-10046-2019");

        Set<String> area = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(null,
                        "Legislação federal: leis, leis complementares e decretos", null, null, null)));
        assertThat(area).contains("decreto-10046-2019", "decreto-13135-2026");

        Set<String> lgpd = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(null, null, "lgpd", null, null)));
        assertThat(lgpd).contains("decreto-10046-2019");

        // Modulo casa por token inteiro, nunca por substring.
        Set<String> lgp = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(null, null, "lgp", null, null)));
        assertThat(lgp).doesNotContain("decreto-10046-2019");

        Set<String> agency = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(null, null, null, null, "Presidência da República")));
        assertThat(agency).contains("decreto-10046-2019", "decreto-13135-2026");

        Set<String> combined = keysOf(service.listStandardsFiltered(
                new CertificationService.StandardFilter(
                        CertificationService.StandardClass.REGULATORY, null, null, "VIGENCIA_FUTURA", null)));
        assertThat(combined).contains("bcb-in-777-2026");
        assertThat(combined).doesNotContain("nt-2026-001-rtc-nfag");

        assertThatThrownBy(() -> service.listStandardsFiltered(
                new CertificationService.StandardFilter(null, null, null, "EM_REVISAO", null)))
                .as("situacao fora dos 4 valores do CHECK e erro de validacao, nao lista vazia")
                .isInstanceOf(ApiException.Validation.class);

        Set<String> unfiltered = keysOf(service.listStandardsFiltered(null));
        assertThat(unfiltered).contains("decreto-10046-2019", "bcb-in-777-2026");
    }

    @Test
    @DisplayName("carga: reexecutar a R__ nao duplica nem altera o total")
    void repeatableRerunIsIdempotent() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/core/R__norm_catalog.sql")) {
            assertThat(in).as("R__norm_catalog.sql deve estar no classpath do core").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute(sql);
            st.execute(sql);
        }
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard WHERE catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(1640);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM platform.cert_standard_edition e"
                        + " JOIN platform.cert_standard s ON s.id = e.standard_id"
                        + " WHERE s.catalog_key NOT LIKE 'syn-%'"))
                .isEqualTo(1640);
    }

    @Test
    @DisplayName("transicao: data simulada vira so o vencido, sem tocar futuro, sem data ou vigente")
    void transitionWithSimulatedDate() throws SQLException {
        insertSyn("A", "VIGENCIA_FUTURA", LocalDate.of(2026, 6, 1));
        insertSyn("B", "VIGENCIA_FUTURA", LocalDate.of(2026, 11, 4));
        insertSyn("C", "VIGENCIA_FUTURA", LocalDate.of(2026, 11, 5));
        insertSyn("D", "VIGENCIA_FUTURA", null);
        insertSyn("E", "VIGENTE", LocalDate.of(2026, 6, 1));
        try {
            NormEffectivenessService.TransitionReport report =
                    normService.transitionDue(LocalDate.of(2026, 11, 4));
            assertThat(report.lockAcquired()).isTrue();
            assertThat(report.asOf()).isEqualTo(LocalDate.of(2026, 11, 4));
            List<String> moved = report.transitioned().stream()
                    .map(NormEffectivenessService.TransitionedView::catalogKey).toList();
            assertThat(moved).contains("syn-norm-a", "syn-norm-b");
            assertThat(moved).doesNotContain("syn-norm-c", "syn-norm-d", "syn-norm-e");

            assertThat(synStatus("syn-norm-a")).isEqualTo("VIGENTE");
            assertThat(synStatus("syn-norm-b")).isEqualTo("VIGENTE");
            assertThat(synStatus("syn-norm-c")).isEqualTo("VIGENCIA_FUTURA");
            assertThat(synStatus("syn-norm-d")).isEqualTo("VIGENCIA_FUTURA");
            assertThat(synStatus("syn-norm-e")).isEqualTo("VIGENTE");

            // Segunda execucao no mesmo dia: nada mais a transicionar.
            NormEffectivenessService.TransitionReport again =
                    normService.transitionDue(LocalDate.of(2026, 11, 4));
            assertThat(again.transitioned().stream()
                    .map(NormEffectivenessService.TransitionedView::catalogKey).toList())
                    .doesNotContain("syn-norm-a", "syn-norm-b");

            // Alerta antecipado: janela (hoje, hoje+30] contem C, nao
            // contem A/B (ja vigentes) nem D (sem data).
            List<String> upcoming = normService.upcomingEffective(LocalDate.of(2026, 11, 4), 30)
                    .stream().map(NormEffectivenessService.UpcomingView::catalogKey).toList();
            assertThat(upcoming).contains("syn-norm-c");
            assertThat(upcoming).doesNotContain("syn-norm-a", "syn-norm-b", "syn-norm-d");
            assertThat(normService.upcomingEffective(LocalDate.of(2026, 11, 4), 0)).isEmpty();

            // Trava: com outra sessao detendo o advisory lock, o
            // servico desiste sem escrever nada.
            try (Connection holder = ProvisionedTenant.migratorDs.getConnection()) {
                holder.setAutoCommit(false);
                try (PreparedStatement ps = holder.prepareStatement(
                        "SELECT pg_try_advisory_xact_lock(?)")) {
                    ps.setLong(1, NormEffectivenessService.LOCK_KEY);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getBoolean(1)).isTrue();
                    }
                }
                NormEffectivenessService.TransitionReport skipped =
                        normService.transitionDue(LocalDate.of(2026, 11, 5));
                assertThat(skipped.lockAcquired()).isFalse();
                assertThat(skipped.transitioned()).isEmpty();
                assertThat(synStatus("syn-norm-c")).isEqualTo("VIGENCIA_FUTURA");
                holder.rollback();
            }
        } finally {
            try (Connection c = ProvisionedTenant.migratorDs.getConnection();
                 Statement st = c.createStatement()) {
                st.execute("DELETE FROM platform.cert_standard WHERE catalog_key LIKE 'syn-norm-%'");
            }
        }
    }

    private static void insertSyn(String suffix, String status, LocalDate effectiveFrom) throws SQLException {
        String key = "syn-norm-" + suffix.toLowerCase();
        String code = "SYN-NORM-" + suffix;
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO platform.cert_standard (catalog_key, code, title, standard_class,"
                             + " verification_seal, status, effective_from)"
                             + " VALUES (?, ?, ?, 'REFERENCE', 'NAO_CONFIRMADA', ?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, code);
            ps.setString(3, "Norma sintetica de teste de transicao (" + code + ")");
            ps.setString(4, status);
            if (effectiveFrom == null) {
                ps.setNull(5, java.sql.Types.DATE);
            } else {
                ps.setDate(5, java.sql.Date.valueOf(effectiveFrom));
            }
            ps.executeUpdate();
        }
    }

    private static String synStatus(String catalogKey) throws SQLException {
        return queryString(
                "SELECT status FROM platform.cert_standard WHERE catalog_key = ?", catalogKey);
    }
}
