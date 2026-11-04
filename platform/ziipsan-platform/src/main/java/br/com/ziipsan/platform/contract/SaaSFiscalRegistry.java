package br.com.ziipsan.platform.contract;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto fiscal da plataforma e aliquotas da NFS-e do SaaS (P2.13, ADR-0003, ADR-0019,
 * core V10_1_1).
 *
 * <p>Este servico vive no modulo {@code platform} e, por isso, NAO importa o motor fiscal
 * (fronteira P0.47: platform so depende do nucleo e de si mesma). Ele guarda tres coisas:
 * o contexto do prestador (a propria plataforma), as aliquotas com vigencia e a leitura
 * da NFS-e emitida. A emissao em si e orquestrada pelo modulo {@code app}
 * ({@code SaaSNfseService}), sobre o motor portado.
 *
 * <p>Contexto: um unico contexto ACTIVE por vez (indice unico parcial da V10_1_1). Registrar
 * um novo contexto inativa o anterior — ato do financeiro da plataforma, auditado em
 * {@code platform.audit_event} como as demais mutacoes da P2.12. Nenhum valor real e
 * presumido aqui: sem contexto ativo, a emissao e recusada; o CNPJ, o municipio, a serie
 * e o ambiente chegam por parametro, validados por {@link DocumentNumber} (inclusive
 * CNPJ alfanumerico, P1.39). Um contexto de producao nunca pode ser {@code test_only}:
 * producao com flag de teste e recusada, e SIMULATED forca {@code test_only=true}.
 *
 * <p>Aliquotas: cadastradas com vigencia, como {@code tax_rates} da V4_5 — nunca constantes
 * e nunca seed. Sobreposicao de vigencias do mesmo tributo e recusada no registro; o motor
 * tambem recusa emitir com vigencia sobreposta ou ausente, como segunda barreira.
 */
@Service
public class SaaSFiscalRegistry {

    public record ContextInput(String legalName, String cnpj, String municipalityIbge, String series,
                               String environment, String taxRegime, String certificateVaultRef,
                               Boolean testOnly) {
    }

    public record ContextView(UUID id, String legalName, String cnpj, String municipalityIbge,
                              String series, String environment, String taxRegime,
                              String certificateVaultRef, boolean testOnly, String status) {
    }

    public record TaxRateInput(String taxCode, String validFrom, String validTo, BigDecimal rate,
                               String legalBasis) {
    }

    public record TaxRateView(UUID id, String taxCode, String validFrom, String validTo,
                              BigDecimal rate, String legalBasis) {
    }

    public record NfseTaxView(String taxCode, BigDecimal rate, BigDecimal amount, boolean informative) {
    }

    /** Leitura da NFS-e de uma cobrança, para a emissao e para a area do tenant (P2.14). */
    public record NfseView(UUID id, UUID invoiceId, UUID tenantId, String series, long number,
                           String dpsId, String accessKey, String protocol, String status,
                           String environment, BigDecimal gross, BigDecimal net,
                           String takerName, String takerCnpj, String xmlSha256,
                           List<NfseTaxView> taxes) {
    }

    private static final Set<String> ENVIRONMENTS = Set.of("SIMULATED", "HOMOLOGATION", "PRODUCTION");
    private static final Set<String> TAX_CODES =
            Set.of("CBS", "IBS", "ISS", "IRRF", "CSRF", "PIS", "COFINS", "INSS");
    private static final Set<String> TAX_REGIMES =
            Set.of("SIMPLES_NACIONAL", "LUCRO_PRESUMIDO", "LUCRO_REAL", "EXEMPT");

    private final JdbcTemplate jdbc;

    @Autowired
    public SaaSFiscalRegistry(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    SaaSFiscalRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Contexto fiscal da plataforma
    // ------------------------------------------------------------------

    @Transactional
    public ContextView registerContext(ContextInput in, UUID actorId, String actorRole) {
        if (in == null || in.legalName() == null || in.legalName().isBlank()) {
            throw validation("A razao social do prestador e obrigatoria.");
        }
        String cnpj = in.cnpj() == null ? "" : DocumentNumber.normalize(in.cnpj());
        if (!DocumentNumber.isValidCnpj(cnpj)) {
            throw validation("O CNPJ do prestador precisa ser valido (14 posicoes, com digitos verificadores).");
        }
        if (in.municipalityIbge() == null || !in.municipalityIbge().matches("^[0-9]{7}$")) {
            throw validation("O codigo IBGE do municipio do prestador precisa ter 7 digitos.");
        }
        String series = in.series() == null || in.series().isBlank() ? "00001" : in.series();
        if (!series.matches("^[0-9]{5}$")) {
            throw validation("A serie fiscal precisa ter 5 digitos.");
        }
        String environment = in.environment() == null || in.environment().isBlank()
                ? "SIMULATED" : in.environment();
        if (!ENVIRONMENTS.contains(environment)) {
            throw validation("O ambiente fiscal precisa ser SIMULATED, HOMOLOGATION ou PRODUCTION.");
        }
        if (in.taxRegime() != null && !TAX_REGIMES.contains(in.taxRegime())) {
            throw validation("O regime tributario precisa ser um dos valores do dominio.");
        }
        boolean testOnly = in.testOnly() == null || in.testOnly();
        if ("SIMULATED".equals(environment)) {
            testOnly = true;
        }
        if ("PRODUCTION".equals(environment) && testOnly) {
            throw validation("Um contexto de producao nao pode ser marcado como de teste.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("UPDATE platform.platform_fiscal_context SET status = 'INACTIVE', updated_at = NOW()"
                + " WHERE status = 'ACTIVE'");
        jdbc.update("""
                INSERT INTO platform.platform_fiscal_context (id, legal_name, cnpj, document_hash,
                    municipality_ibge, series, environment, tax_regime, certificate_vault_ref,
                    test_only, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                id, in.legalName(), cnpj, sha256Hex(cnpj), in.municipalityIbge(), series,
                environment, in.taxRegime(), in.certificateVaultRef(), testOnly);
        audit(null, actorId, actorRole, "SAAS_FISCAL_CONTEXT_REGISTERED",
                Map.of("environment", environment, "series", series,
                        "test_only", Boolean.toString(testOnly)));
        return context(id);
    }

    public ContextView activeContext() {
        List<ContextView> rows = jdbc.query(
                "SELECT id, legal_name, cnpj, municipality_ibge, series, environment, tax_regime,"
                        + " certificate_vault_ref, test_only, status"
                        + " FROM platform.platform_fiscal_context WHERE status = 'ACTIVE'",
                (rs, i) -> new ContextView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getBoolean(9), rs.getString(10)));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ContextView context(UUID contextId) {
        List<ContextView> rows = jdbc.query(
                "SELECT id, legal_name, cnpj, municipality_ibge, series, environment, tax_regime,"
                        + " certificate_vault_ref, test_only, status"
                        + " FROM platform.platform_fiscal_context WHERE id = ?",
                (rs, i) -> new ContextView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getBoolean(9), rs.getString(10)),
                contextId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O contexto fiscal da plataforma nao existe.");
        }
        return rows.get(0);
    }

    // ------------------------------------------------------------------
    // Aliquotas da plataforma
    // ------------------------------------------------------------------

    @Transactional
    public TaxRateView registerTaxRate(TaxRateInput in, UUID actorId, String actorRole) {
        if (in == null || !TAX_CODES.contains(in.taxCode())) {
            throw validation("O codigo do tributo precisa ser um dos valores do dominio.");
        }
        if (in.validFrom() == null || in.validFrom().isBlank()) {
            throw validation("A vigencia da aliquota precisa ter inicio.");
        }
        if (in.rate() == null || in.rate().compareTo(BigDecimal.ZERO) < 0
                || in.rate().compareTo(BigDecimal.ONE) > 0) {
            throw validation("A aliquota precisa estar entre 0 e 1.");
        }
        Date from;
        Date to = null;
        try {
            from = Date.valueOf(in.validFrom());
            if (in.validTo() != null && !in.validTo().isBlank()) {
                to = Date.valueOf(in.validTo());
            }
        } catch (IllegalArgumentException e) {
            throw validation("As datas de vigencia da aliquota precisam estar no formato AAAA-MM-DD.");
        }
        if (to != null && to.before(from)) {
            throw validation("A vigencia da aliquota precisa ter fim apos o inicio.");
        }
        // Sem "? IS NULL" com parametro nulo: o Postgres nao infere o
        // tipo do parametro sem tipo e recusa a consulta (PSQLException
        // "could not determine data type of parameter"). O caso de fim
        // aberto (to == null) usa outra consulta, sem a condicao sobre
        // valid_from — equivalente, pois fim aberto nao limita o inicio.
        Integer overlapping;
        if (to == null) {
            overlapping = jdbc.queryForObject(
                    "SELECT count(*) FROM platform.saas_fiscal_tax_rate WHERE tax_code = ? AND scope = 'NATIONAL'"
                            + " AND (valid_to IS NULL OR valid_to >= ?)",
                    Integer.class, in.taxCode(), from);
        } else {
            overlapping = jdbc.queryForObject(
                    "SELECT count(*) FROM platform.saas_fiscal_tax_rate WHERE tax_code = ? AND scope = 'NATIONAL'"
                            + " AND valid_from <= ? AND (valid_to IS NULL OR valid_to >= ?)",
                    Integer.class, in.taxCode(), to, from);
        }
        // Sobreposicao de intervalos com fim aberto (NULL = aberta): o
        // motor recusa de novo na emissao, se alguma escapar daqui.
        if (overlapping != null && overlapping > 0) {
            throw validation("Ja existe aliquota vigente para " + in.taxCode() + " no intervalo informado.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_fiscal_tax_rate (id, tax_code, scope, valid_from, valid_to,"
                + " rate, legal_basis) VALUES (?, ?, 'NATIONAL', ?, ?, ?, ?)",
                id, in.taxCode(), from, to, in.rate(), in.legalBasis());
        audit(null, actorId, actorRole, "SAAS_FISCAL_TAX_RATE_REGISTERED",
                Map.of("tax_code", in.taxCode(), "valid_from", in.validFrom()));
        return new TaxRateView(id, in.taxCode(), in.validFrom(), in.validTo(), in.rate(), in.legalBasis());
    }

    // ------------------------------------------------------------------
    // Leitura da NFS-e
    // ------------------------------------------------------------------

    public NfseView nfseByInvoice(UUID invoiceId) {
        List<NfseView> rows = jdbc.query(
                "SELECT id, invoice_id, tenant_id, series, number, dps_id, access_key, protocol, status,"
                        + " environment, gross, net, taker_name, taker_cnpj, xml_sha256"
                        + " FROM platform.saas_nfse WHERE invoice_id = ?",
                (rs, i) -> new NfseView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getLong(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getBigDecimal(11), rs.getBigDecimal(12),
                        rs.getString(13), rs.getString(14), rs.getString(15), List.of()),
                invoiceId);
        if (rows.isEmpty()) {
            return null;
        }
        NfseView base = rows.get(0);
        List<NfseTaxView> taxes = jdbc.query(
                "SELECT tax_code, rate, amount, informative FROM platform.saas_nfse_tax"
                        + " WHERE nfse_id = ? ORDER BY tax_code",
                (rs, i) -> new NfseTaxView(rs.getString(1), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getBoolean(4)),
                base.id());
        return new NfseView(base.id(), base.invoiceId(), base.tenantId(), base.series(), base.number(),
                base.dpsId(), base.accessKey(), base.protocol(), base.status(), base.environment(),
                base.gross(), base.net(), base.takerName(), base.takerCnpj(), base.xmlSha256(), taxes);
    }

    private void audit(UUID tenantId, UUID actorId, String actorRole, String action,
                       Map<String, String> details) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : details.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append("\"");
        }
        json.append("}");
        jdbc.update("INSERT INTO platform.audit_event (tenant_id, actor_id, actor_role, action, details)"
                + " VALUES (?, ?, ?, ?, ?::jsonb)", tenantId, actorId, actorRole, action, json.toString());
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** SHA-256 hexadecimal da forma normalizada (indice cego, sem segredo). */
    static String sha256Hex(String normalized) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }
}
