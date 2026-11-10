package br.com.ziipsan.commercial.collection.cession;

import br.com.ziipsan.commercial.collection.cession.ReceivablesPortfolioSource.PortfolioLine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-103/E2+E3 (decisao 62) — exportacao estruturada GENERICA da carteira
 * (CSV ou JSON), apta a ser entregue a cessao, fundo de recebiveis ou
 * conta vinculada. Produtor (execucao ED-103, decisao 62): sessao
 * 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p><b>Nenhum leiaute oficial e presumido:</b> leiautes de banco, fundo
 * ou registradora sao [SEM FONTE] neste ciclo (pendencia
 * P-ED103-INT-02). Este formato e o formato generico declarado do
 * sistema: uma linha por recebivel com saldo em aberto, SO com os campos
 * da whitelist da porta (identificador do recebivel, chave
 * pseudonimizada do devedor, valor devido, vencimento, faixa de aging).
 * O perfil por omissao NAO tem nome, CPF/CNPJ nem endereco — esses campos
 * nem existem no modelo, logo nao podem vazar.
 *
 * <p>Toda a exportacao exige destino declarado e grava a trilha
 * ({@link ExportAudit}) com o hash SHA-256 do conteudo gerado, ANTES de
 * devolver o conteudo ao chamador.
 */
public final class PortfolioExporter {

    public enum Format { CSV, JSON }

    /** Resultado: conteudo gerado + o registo de auditoria gravado. */
    public record ExportResult(String content, ExportAudit audit) {}

    private final CessionStore store;

    public PortfolioExporter(CessionStore store) {
        this.store = Objects.requireNonNull(store, "loja da cessao e obrigatoria");
    }

    public ExportResult export(PortfolioReport report, Format format, String destination,
                               String actor, Instant at) {
        Objects.requireNonNull(report, "relatorio e obrigatorio");
        Objects.requireNonNull(format, "formato e obrigatorio");
        if (destination == null || destination.isBlank())
            throw new ReceivablesCessionException("EXPORT_DESTINATION_REQUIRED",
                    "exportacao da carteira exige destino declarado (cessao, fundo ou conta vinculada sao destinos declarados pelo cliente — decisao 62)");
        if (actor == null || actor.isBlank())
            throw new ReceivablesCessionException("EXPORT_ACTOR_REQUIRED", "exportacao exige o actor");

        String content = format == Format.CSV ? toCsv(report) : toJson(report);
        String hash = sha256(content);
        String scope = "referenceDate=" + report.referenceDate()
                + "; bands=" + String.join(",", report.bands().bands().stream().map(AgingBands.Band::label).toList());
        var audit = new ExportAudit(UUID.randomUUID(), report.tenantId(), actor, at,
                scope, destination, format.name(), hash, openLines(report));
        store.appendExportAudit(audit);
        return new ExportResult(content, audit);
    }

    private static int openLines(PortfolioReport report) {
        return (int) report.lines().stream().filter(l -> l.outstanding().signum() > 0).count();
    }

    /** So recebiveis com saldo em aberto entram na exportacao de cessao. */
    private static java.util.List<PortfolioLine> open(PortfolioReport report) {
        return report.lines().stream().filter(l -> l.outstanding().signum() > 0).toList();
    }

    private String bandOf(PortfolioReport report, PortfolioLine l) {
        long overdue = ChronoUnit.DAYS.between(l.dueDate(), report.referenceDate());
        return report.bands().labelFor(overdue);
    }

    String toCsv(PortfolioReport report) {
        var sb = new StringBuilder("receivable_id,debtor_key,amount_due,due_date,aging_band\n");
        for (PortfolioLine l : open(report)) {
            sb.append(csv(l.receivableId())).append(',')
              .append(csv(l.debtorKey())).append(',')
              .append(l.outstanding().toPlainString()).append(',')
              .append(l.dueDate()).append(',')
              .append(csv(bandOf(report, l))).append('\n');
        }
        return sb.toString();
    }

    String toJson(PortfolioReport report) {
        var sb = new StringBuilder();
        sb.append("{\"tenantId\":\"").append(report.tenantId())
          .append("\",\"referenceDate\":\"").append(report.referenceDate())
          .append("\",\"lines\":[");
        boolean first = true;
        for (PortfolioLine l : open(report)) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"receivableId\":\"").append(json(l.receivableId()))
              .append("\",\"debtorKey\":\"").append(json(l.debtorKey()))
              .append("\",\"amountDue\":").append(l.outstanding().toPlainString())
              .append(",\"dueDate\":\"").append(l.dueDate())
              .append("\",\"agingBand\":\"").append(json(bandOf(report, l))).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String csv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    private static String json(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
