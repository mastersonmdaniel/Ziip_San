package br.com.ziipsan.commercial.collection.cession;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-103/E3 (decisao 62) — registo da trilha de auditoria de UMA
 * exportacao da carteira: quem exportou, quando, com que ambito/filtros
 * (data de referencia e faixas declaradas), para que destino declarado e
 * o hash SHA-256 do conteudo gerado. Produtor (execucao ED-103,
 * decisao 62): sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>Sem destino declarado nao ha exportacao (recusa tipificada em
 * {@link PortfolioExporter}) e sem exportacao nao ha registo — a trilha
 * so nasce de exportacoes efectivamente geradas.
 */
public record ExportAudit(UUID id, UUID tenantId, String actor, Instant exportedAt,
                          String scope, String destination, String format,
                          String contentHash, int lineCount) {

    public ExportAudit {
        Objects.requireNonNull(id, "id da auditoria e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (actor == null || actor.isBlank())
            throw new ReceivablesCessionException("EXPORT_ACTOR_REQUIRED", "auditoria de exportacao exige o actor");
        Objects.requireNonNull(exportedAt, "instante da exportacao e obrigatorio");
        if (scope == null || scope.isBlank())
            throw new ReceivablesCessionException("EXPORT_SCOPE_REQUIRED", "auditoria de exportacao exige o ambito/filtros");
        if (destination == null || destination.isBlank())
            throw new ReceivablesCessionException("EXPORT_DESTINATION_REQUIRED",
                    "exportacao exige destino declarado — sem destino, a exportacao e recusada");
        Objects.requireNonNull(format, "formato e obrigatorio");
        if (contentHash == null || contentHash.isBlank())
            throw new ReceivablesCessionException("EXPORT_HASH_REQUIRED", "auditoria exige o hash do conteudo gerado");
        if (lineCount < 0)
            throw new ReceivablesCessionException("EXPORT_LINE_COUNT_INVALID", "contagem de linhas nao pode ser negativa");
    }
}
