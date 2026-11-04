package br.com.ziipsan.platform.contract;

import br.com.ziipsan.platform.security.PlatformRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras puras da cobranca do SaaS por contrato (P2.12, ADR-0019), sem
 * banco, para que a matriz de restricao, os limiares de alerta, a
 * margem e o hash da fotografia sejam testaveis sem PostgreSQL. O banco
 * continua sendo a trava final (CHECKs da V10_1) e o servico aplica
 * estas mesmas regras antes de escrever.
 *
 * <p>Regra de ouro, em codigo: nenhum metodo desta classe suspende,
 * bloqueia ou restringe um tenant. A restricao so existe como ato
 * manual do servico ({@code SaaSContractService}), com fatura vencida,
 * aviso previo, MFA, step-up e justificativa — e a avaliacao de
 * alertas nao escreve em {@code tenant_lifecycle} em nenhum caminho.
 */
public final class SaaSContractRules {

    /** Motivo unico da restricao por inadimplencia contratual. */
    public static final String CONTRACT_DEFAULT_REASON = "CONTRACT_DEFAULT";

    /** Alertas de vencimento do contrato (ADR-0019, item 7), em dias. */
    public static final int EXPIRY_WARN_180 = 180;
    public static final int EXPIRY_WARN_90 = 90;
    public static final int EXPIRY_CRITICAL_30 = 30;

    /** Limiares de cota (ADR-0019, item 7): 80% e 100%. */
    public static final BigDecimal QUOTA_WARN = new BigDecimal("0.8");
    public static final BigDecimal QUOTA_CRITICAL = BigDecimal.ONE;

    /** Limiar de saldo de empenho (ADR-0019, item 7): abaixo de 2 faturas. */
    public static final BigDecimal COMMITMENT_LOW_INVOICES = new BigDecimal("2");

    /** Chaves de metrica do usage_ledger, sem dado pessoal (so contagens). */
    public static final Set<String> LEDGER_METRICS = Set.of(
            "active_connections", "active_users", "storage_mb", "reports_generated",
            "api_calls", "llm_tokens", "batch_minutes");

    private static final Pattern MESSAGE_METRIC =
            Pattern.compile("^messages\\.[a-z0-9_]+\\.[a-z0-9_]+$");
    private static final Pattern COMPETENCE = Pattern.compile("^[0-9]{4}-[0-9]{2}$");

    private SaaSContractRules() {
    }

    /**
     * Quem pode pedir e ativar a restricao por inadimplencia (ADR-0019,
     * item 5; CENTRAL-ZIIP §2): PLATFORM_OWNER ou PLATFORM_FINANCE, e
     * sempre com o motivo inadimplencia contratual. Qualquer outro papel
     * ou motivo, nunca. A MFA vive no filtro e o step-up e confirmado na
     * chamada do servico — nenhum dos dois e decidido aqui.
     */
    public static boolean canRestrict(String role, String reason) {
        if (!CONTRACT_DEFAULT_REASON.equals(reason)) {
            return false;
        }
        return PlatformRole.OWNER.equals(role) || PlatformRole.FINANCE.equals(role);
    }

    /** Fatura vencida: nao paga (OPEN ou PARTIALLY_PAID) e vencimento passado. */
    public static boolean isOverdue(String invoiceStatus, LocalDate dueDate, LocalDate today) {
        if (dueDate == null || today == null) {
            return false;
        }
        return ("OPEN".equals(invoiceStatus) || "PARTIALLY_PAID".equals(invoiceStatus))
                && dueDate.isBefore(today);
    }

    /** Dias ate o fim da vigencia (negativo quando ja vencido). */
    public static long daysToExpiry(LocalDate endsAt, LocalDate today) {
        if (endsAt == null || today == null) {
            throw new IllegalArgumentException("Vigencia e data de referencia sao obrigatorias");
        }
        return endsAt.toEpochDay() - today.toEpochDay();
    }

    /** Severidade do alerta de vencimento, ou null quando fora das janelas. */
    public static String expirySeverity(long daysToExpiry) {
        if (daysToExpiry < 0 || daysToExpiry > EXPIRY_WARN_180) {
            return null;
        }
        if (daysToExpiry <= EXPIRY_CRITICAL_30) {
            return "CRITICAL";
        }
        return "WARNING";
    }

    /** Uso sobre a cota (1 = 100%); cota zero ou negativa nao gera razao. */
    public static BigDecimal quotaRatio(BigDecimal used, BigDecimal quota) {
        if (used == null || quota == null || quota.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return used.divide(quota, 4, RoundingMode.HALF_UP);
    }

    /** Niveis de alerta de cota atingidos: vazio, 80% ou 80%+100%. */
    public static Set<String> quotaAlertLevels(BigDecimal ratio) {
        if (ratio == null) {
            return Set.of();
        }
        if (ratio.compareTo(QUOTA_CRITICAL) >= 0) {
            return Set.of("80", "100");
        }
        if (ratio.compareTo(QUOTA_WARN) >= 0) {
            return Set.of("80");
        }
        return Set.of();
    }

    /**
     * Saldo do empenho em faturas mensais. Valor mensal zero: saldo
     * positivo e folga (sem alerta) e saldo zero ou negativo e alerta —
     * nunca divisao por zero.
     */
    public static BigDecimal commitmentBalanceInInvoices(BigDecimal balance, BigDecimal monthlyValue) {
        if (balance == null) {
            return null;
        }
        if (monthlyValue == null || monthlyValue.compareTo(BigDecimal.ZERO) <= 0) {
            return balance.compareTo(BigDecimal.ZERO) > 0
                    ? COMMITMENT_LOW_INVOICES : BigDecimal.ZERO;
        }
        return balance.divide(monthlyValue, 4, RoundingMode.HALF_UP);
    }

    public static boolean commitmentLow(BigDecimal balanceInInvoices) {
        return balanceInInvoices != null
                && balanceInInvoices.compareTo(COMMITMENT_LOW_INVOICES) < 0;
    }

    /** Margem realizada: (faturado - custo) / faturado; null sem faturamento. */
    public static BigDecimal margin(BigDecimal invoiced, BigDecimal providerCost) {
        if (invoiced == null || invoiced.compareTo(BigDecimal.ZERO) <= 0 || providerCost == null) {
            return null;
        }
        return invoiced.subtract(providerCost).divide(invoiced, 4, RoundingMode.HALF_UP);
    }

    /** Status da fatura apos conciliar: pago quando o pago cobre o total. */
    public static String reconciledStatus(BigDecimal total, BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "OPEN";
        }
        if (total != null && paidAmount.compareTo(total) >= 0) {
            return "PAID";
        }
        return "PARTIALLY_PAID";
    }

    /** Metrica valida do usage_ledger: lista fechada, ou messages.canal.categoria. */
    public static boolean isLedgerMetric(String metricKey) {
        if (metricKey == null) {
            return false;
        }
        return LEDGER_METRICS.contains(metricKey) || MESSAGE_METRIC.matcher(metricKey).matches();
    }

    /** Competencia no formato AAAA-MM. */
    public static boolean isCompetence(String competence) {
        return competence != null && COMPETENCE.matcher(competence).matches();
    }

    /** Chave de cota do contrato (quota.*) para a metrica do ledger. */
    public static String quotaKeyFor(String metricKey) {
        return "quota." + metricKey;
    }

    /** Primeiro dia do mes da competencia AAAA-MM. */
    public static LocalDate monthOf(String competence) {
        if (!isCompetence(competence)) {
            throw new IllegalArgumentException("Competencia fora do formato AAAA-MM");
        }
        return LocalDate.parse(competence + "-01");
    }

    /**
     * Conteudo canonico da fotografia mensal: tenant, mes e quantidade
     * de ligacoes ativas, um por linha. O hash detecta qualquer alteracao
     * posterior de um desses campos sem recalcular o hash.
     */
    public static String snapshotCanonical(String tenantId, LocalDate periodMonth, long activeConnections) {
        return tenantId + "\n" + periodMonth + "\n" + activeConnections;
    }

    /** Hash da fotografia (SHA-256 hex). */
    public static String snapshotHash(String tenantId, LocalDate periodMonth, long activeConnections) {
        return sha256Hex(snapshotCanonical(tenantId, periodMonth, activeConnections));
    }

    /** Confere a fotografia: o hash guardado precisa ser o do conteudo atual. */
    public static boolean snapshotValid(String tenantId, LocalDate periodMonth,
                                        long activeConnections, String storedHash) {
        return storedHash != null
                && snapshotHash(tenantId, periodMonth, activeConnections).equals(storedHash);
    }

    /** Reajuste so vale com apostilamento ou aditivo: sem ele, fica proposta. */
    public static boolean readjustmentMayApply(boolean hasAddendum) {
        return hasAddendum;
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
