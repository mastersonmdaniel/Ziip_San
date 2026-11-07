package br.com.ziipsan.commercial.registry.legacyxml;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Motor da P2.19 — importador dos documentos fiscais autorizados
 * ANTERIORES à primeira produção (sistema antigo do piloto ou emissor
 * usado no intervalo), construído com uma declaração própria NOS MOLDES
 * do framework declarativo de migração da P1.2 (PLT-16) — precisão da
 * correção A5 da revisão: o framework da P1.2 NÃO é reutilizado, porque
 * ele é orientado a linhas/cadastro e o {@code source_kind} dos seus
 * lotes só admite CSV/SQL_DUMP/LEGACY_DB, nunca documentos XML. O que se
 * mantém do desenho do PLT-16 é: a origem entra como declaração
 * ({@link LegacyXmlMapping}), o lote tem a mesma identidade (sistema +
 * chave externa, com idempotência de recarga) e cada arquivo vira um
 * registro com resultado — o relatório por lote.
 *
 * <p><b>Ordem das validações de cada arquivo</b> (a primeira que falha
 * define o motivo da recusa; nenhuma recusa interrompe o lote):
 *
 * <ol>
 *   <li>estrutura: XML bem-formado no parser seguro e chave de acesso de
 *       44 dígitos com DV módulo 11 correto
 *       ({@code XML_INVALID}/{@code ACCESS_KEY_INVALID});</li>
 *   <li>assinatura XMLDSig válida pela porta
 *       {@link XmlSignatureValidatorPort} ({@code SIGNATURE_INVALID});</li>
 *   <li>protocolo de autorização presente E provado: {@code infProt/nProt}
 *       presente e {@code cStat} presente e de autorizado (100/150) —
 *       {@code nProt} sem {@code cStat} é alegação de protocolo, não
 *       protocolo de autorização (correção A4 da revisão)
 *       ({@code MISSING_AUTHORIZATION_PROTOCOL});</li>
 *   <li>emitente: {@code emit/CNPJ} igual ao CNPJ da chave, certificado do
 *       assinante (quando o {@code KeyInfo} o traz) igual ao emitente, e
 *       emitente igual ao esperado do tenant, declarado no comando
 *       ({@code ISSUER_MISMATCH});</li>
 *   <li>modelo dentro dos aceitos do mapeamento
 *       ({@code MODEL_NOT_ACCEPTED});</li>
 *   <li>duplicidade: chave já aceita neste lote ou em lote anterior do
 *       tenant ({@code DUPLICATE}).</li>
 * </ol>
 *
 * <p><b>Aceito:</b> os bytes EXATOS recebidos vão para o armazenamento
 * imutável da P2.18 pela porta {@link LegacyXmlRetentionPort}, com a
 * origem {@code LEGACY_IMPORT} e o SHA-256 calculado aqui — e o hash que a
 * retenção devolve tem de ser igual ao calculado, senão a importação do
 * arquivo falha com erro explícito (nunca se registra um hash que não é o
 * do arquivo). O registro persistido é o da V14_3; <b>nenhum dado do
 * destinatário é persistido</b>.
 *
 * <p><b>Conclusão do lote (correção A2 da revisão):</b> o marcador de
 * conclusão de um lote é o {@code report_json} gravado
 * ({@link LegacyXmlBatch#reportJson()} não nulo). Se um erro aborta o laço
 * no meio (ex.: hash divergente da retenção), o lote fica persistido com
 * documentos parciais e SEM relatório — e uma nova chamada com a mesma
 * chave de lote NÃO o promove a "já importado": ela falha com
 * {@code IllegalStateException} explícita. Não há retomada automática
 * segura nesta camada: os documentos são imutáveis (INSERT-only na V14_3),
 * os aceitos parciais já podem estar na retenção e regravar duplicaria a
 * chave aceita. A conduta é a do órfão da P2.18: erro nomeado e
 * diagnosticável, para a integração decidir (conferir o lote parcial e
 * reprocessar o restante sob nova chave de lote, onde os já aceitos caem
 * como {@code DUPLICATE} — comportamento declarado, nunca silêncio).
 *
 * <p><b>Ambiente local (ADR-0033):</b> aqui só se testa com lotes de XML
 * sintéticos assinados com certificado/chave de teste. Os XML reais do
 * piloto — que trazem dado pessoal do destinatário — são importados só na
 * produção, pela LC.10, executando este mesmo importador.
 */
public final class LegacyXmlImportService {

    /** Um arquivo do lote: nome original + bytes exatos recebidos. */
    public record XmlFileEntry(String fileName, byte[] content) {
        public XmlFileEntry {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("Arquivo do lote sem nome.");
            }
            Objects.requireNonNull(content, "Conteúdo do arquivo do lote é obrigatório");
        }
    }

    /**
     * @param expectedIssuerCnpj CNPJ do emitente esperado (o prestador do
     *                           tenant): é dado do tenant informado por quem
     *                           importa — nunca presumido pelo importador
     */
    public record ImportCommand(UUID tenantId, LegacyXmlMapping mapping, String externalBatchKey,
                                String expectedIssuerCnpj, UUID actorId, List<XmlFileEntry> files) {
    }

    /** Resultado de um arquivo dentro do relatório do lote. */
    public record FileResult(String fileName, String status, String rejection,
                             String accessKey, String xmlSha256) {
    }

    /**
     * Relatório por lote: totais, recusas por motivo, aceitos por
     * competência e o resultado arquivo a arquivo. É o relatório que a
     * LC.10 apresenta ao piloto sobre o dado real.
     */
    public record BatchReport(UUID batchId, boolean alreadyExisted, int totalReceived,
                              int totalAccepted, int totalRejected,
                              Map<String, Long> rejectedByReason,
                              Map<YearMonth, Long> acceptedByCompetence,
                              List<FileResult> files) {

        /** Serialização do relatório com o {@code Json} do core. */
        public String toJson() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("tarefa", "P2.19");
            root.put("batchId", batchId.toString());
            root.put("alreadyExisted", alreadyExisted);
            root.put("totalReceived", totalReceived);
            root.put("totalAccepted", totalAccepted);
            root.put("totalRejected", totalRejected);
            root.put("rejectedByReason", rejectedByReason);
            Map<String, Long> porCompetencia = new TreeMap<>();
            acceptedByCompetence.forEach((k, v) -> porCompetencia.put(k.toString(), v));
            root.put("acceptedByCompetence", porCompetencia);
            root.put("files", files.stream().map(f -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fileName", f.fileName());
                item.put("status", f.status());
                if (f.rejection() != null) {
                    item.put("rejection", f.rejection());
                }
                if (f.accessKey() != null) {
                    item.put("accessKey", f.accessKey());
                }
                item.put("xmlSha256", f.xmlSha256());
                return item;
            }).toList());
            return Json.write(root);
        }
    }

    /** Linha da conferência de contagem por competência contra o legado. */
    public record CompetenceCountLine(YearMonth competence, long expectedFromLegacy,
                                      long importedAccepted, boolean matches) {
    }

    /** Conferência do aceite: contagem por competência importada × contagem declarada pelo legado. */
    public record CompetenceConference(List<CompetenceCountLine> lines) {
        public boolean allMatch() {
            return lines.stream().allMatch(CompetenceCountLine::matches);
        }
    }

    private final Clock clock;
    private final LegacyXmlImportStore store;
    private final XmlSignatureValidatorPort signatureValidator;
    private final LegacyXmlRetentionPort retention;
    private final LegacyXmlParser parser = new LegacyXmlParser();

    public LegacyXmlImportService(Clock clock, LegacyXmlImportStore store,
                                  XmlSignatureValidatorPort signatureValidator,
                                  LegacyXmlRetentionPort retention) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.store = Objects.requireNonNull(store, "Porta de persistência da V14_3 é obrigatória");
        this.signatureValidator = Objects.requireNonNull(signatureValidator,
                "Validador de assinatura é obrigatório (sem ele, nada se aceita)");
        this.retention = Objects.requireNonNull(retention,
                "Armazenamento imutável da P2.18 é obrigatório (sem ele, nada se aceita)");
    }

    public BatchReport importar(ImportCommand command) {
        Objects.requireNonNull(command, "Comando de importação é obrigatório");
        if (command.tenantId() == null || command.actorId() == null) {
            throw new IllegalArgumentException("tenantId e actorId são obrigatórios para importar.");
        }
        LegacyXmlMapping mapping = Objects.requireNonNull(command.mapping(), "Mapeamento declarativo é obrigatório");
        if (command.externalBatchKey() == null || command.externalBatchKey().isBlank()) {
            throw new IllegalArgumentException("externalBatchKey é obrigatório (identidade do lote no framework).");
        }
        if (command.files() == null || command.files().isEmpty()) {
            throw new IllegalArgumentException("Lote sem arquivos: nada a importar.");
        }
        String expectedIssuer = DocumentNumber.normalize(command.expectedIssuerCnpj());
        if (!DocumentNumber.isValidCnpj(expectedIssuer)) {
            throw new IllegalArgumentException("CNPJ do emitente esperado inválido (validação P1.39):"
                    + " sem emitente esperado válido, não há contra o que conferir os documentos.");
        }

        var existing = store.findBatch(command.tenantId(), mapping.sourceSystem(), command.externalBatchKey());
        if (existing.isPresent()) {
            LegacyXmlBatch lote = existing.get();
            if (lote.reportJson() == null) {
                // Correção A2: lote sem relatório é lote INTERROMPIDO no
                // meio — nunca é devolvido como relatório final.
                throw new IllegalStateException("Lote " + lote.id() + " (sistema "
                        + mapping.sourceSystem() + ", chave externa '" + command.externalBatchKey()
                        + "') existe mas NÃO foi concluído (report_json nulo): a importação foi"
                        + " interrompida no meio e pode haver documentos parciais persistidos,"
                        + " com aceitos já gravados na retenção. Reimportar a mesma chave NÃO"
                        + " retoma nem promove o lote parcial a relatório final — é necessária"
                        + " a intervenção da integração (conferir os documentos do lote parcial"
                        + " e reprocessar o restante sob nova chave de lote).");
            }
            return reportOf(lote, true);
        }

        LegacyXmlBatch batch = store.createBatch(command.tenantId(), mapping.sourceSystem(),
                mapping.name(), mapping.version(), command.externalBatchKey(), expectedIssuer, command.actorId());
        Set<String> acceptedInBatch = new LinkedHashSet<>();
        for (XmlFileEntry file : command.files()) {
            LegacyXmlImportedDocument document = processar(command.tenantId(), batch, mapping,
                    expectedIssuer, file, acceptedInBatch);
            store.insertDocument(document);
            if (document.accepted()) {
                acceptedInBatch.add(document.accessKey());
            }
        }
        BatchReport report = reportOf(batch, false);
        store.saveReport(batch.id(), report.toJson());
        return report;
    }

    /**
     * Conferência do aceite: compara a contagem de aceitos por competência
     * do lote com a contagem que o LEGADO declara (entrada de quem confere,
     * vinda do sistema do piloto — nunca inventada aqui). Competência
     * presente de um lado só entra na conferência com zero do outro lado.
     *
     * <p><b>Escopo (correção A10 da revisão):</b> a conferência é POR LOTE
     * — conta só os aceitos daquele {@code batchId}, porque a contagem do
     * legado é a contagem daquela carga. Lote inexistente para o tenant é
     * erro nomeado ({@code IllegalArgumentException}), não um relatório de
     * divergências contra zero; lote existente mas NÃO concluído (sem
     * {@code report_json}, correção A2) também é erro explícito, porque
     * conferir contagem parcial como se fosse a carga inteira mentiria o
     * resultado.
     *
     * @throws IllegalArgumentException se a contagem do legado vier vazia:
     *                                  ausência de dado é erro explícito,
     *                                  nunca "confere por falta de termo";
     *                                  ou se o lote não existir para o tenant
     * @throws IllegalStateException    se o lote existir mas não tiver sido
     *                                  concluído (importação interrompida)
     */
    public CompetenceConference conferirContagem(UUID tenantId, UUID batchId,
                                                 Map<YearMonth, Long> expectedFromLegacy) {
        if (expectedFromLegacy == null || expectedFromLegacy.isEmpty()) {
            throw new IllegalArgumentException("Contagem por competência do legado é obrigatória"
                    + " para conferir: sem ela, não há conferência.");
        }
        LegacyXmlBatch lote = store.findBatchById(tenantId, batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lote não encontrado para o tenant: " + batchId));
        if (lote.reportJson() == null) {
            throw new IllegalStateException("Lote " + batchId + " não foi concluído"
                    + " (importação interrompida): não há contagem final a conferir.");
        }
        Map<YearMonth, Long> imported = new TreeMap<>();
        for (LegacyXmlImportedDocument doc : store.listDocuments(tenantId, batchId)) {
            if (doc.accepted()) {
                imported.merge(doc.competence(), 1L, Long::sum);
            }
        }
        Set<YearMonth> competences = new LinkedHashSet<>();
        competences.addAll(expectedFromLegacy.keySet());
        competences.addAll(imported.keySet());
        List<CompetenceCountLine> lines = new ArrayList<>();
        for (YearMonth competence : competences) {
            long expected = expectedFromLegacy.getOrDefault(competence, 0L);
            long actual = imported.getOrDefault(competence, 0L);
            lines.add(new CompetenceCountLine(competence, expected, actual, expected == actual));
        }
        lines.sort(java.util.Comparator.comparing(CompetenceCountLine::competence));
        return new CompetenceConference(List.copyOf(lines));
    }

    // ------------------------------------------------------------------
    // Um arquivo
    // ------------------------------------------------------------------

    private LegacyXmlImportedDocument processar(UUID tenantId, LegacyXmlBatch batch, LegacyXmlMapping mapping,
                                                String expectedIssuer, XmlFileEntry file,
                                                Set<String> acceptedInBatch) {
        String sha256 = sha256Hex(file.content());
        LegacyXmlParser.ParsedXml parsed;
        try {
            parsed = parser.parse(file.content());
        } catch (LegacyXmlParser.LegacyXmlParseException e) {
            return rejected(tenantId, batch, file, sha256, null,
                    e.rejection(), e.getMessage());
        }

        XmlSignatureValidatorPort.SignatureResult signature = signatureValidator.validate(file.content());
        if (!signature.valida()) {
            return rejected(tenantId, batch, file, sha256, parsed, LegacyXmlRejection.SIGNATURE_INVALID,
                    signature.detail());
        }
        if (parsed.protocol() == null || parsed.protocolStatus() == null
                || (!parsed.protocolStatus().equals("100") && !parsed.protocolStatus().equals("150"))) {
            return rejected(tenantId, batch, file, sha256, parsed,
                    LegacyXmlRejection.MISSING_AUTHORIZATION_PROTOCOL,
                    parsed.protocol() == null
                            ? "XML sem protocolo de autorização (infProt/nProt ausente)"
                            : parsed.protocolStatus() == null
                                ? "protocolo sem cStat: nProt presente, sozinho, não prova"
                                  + " autorização (correção A4 da revisão)"
                                : "cStat do protocolo não é de documento autorizado: "
                                  + parsed.protocolStatus());
        }
        if (parsed.issuerFromBody() != null && !parsed.issuerFromBody().equals(parsed.issuerFromKey())) {
            return rejected(tenantId, batch, file, sha256, parsed, LegacyXmlRejection.ISSUER_MISMATCH,
                    "emit/CNPJ diverge do CNPJ embutido na chave de acesso");
        }
        if (signature.signerDocument().isPresent()
                && !signature.signerDocument().get().equals(parsed.issuerFromKey())) {
            return rejected(tenantId, batch, file, sha256, parsed, LegacyXmlRejection.ISSUER_MISMATCH,
                    "certificado do assinante não é do emitente do documento");
        }
        if (!parsed.issuerFromKey().equals(expectedIssuer)) {
            return rejected(tenantId, batch, file, sha256, parsed, LegacyXmlRejection.ISSUER_MISMATCH,
                    "emitente do documento difere do emitente esperado do tenant");
        }
        if (!mapping.acceptedModels().contains(parsed.model())) {
            return rejected(tenantId, batch, file, sha256, parsed, LegacyXmlRejection.MODEL_NOT_ACCEPTED,
                    "modelo " + parsed.model() + " fora dos aceitos do mapeamento " + mapping.acceptedModels());
        }
        if (acceptedInBatch.contains(parsed.accessKey()) || store.alreadyAccepted(tenantId, parsed.accessKey())) {
            return rejected(tenantId, batch, file, sha256, parsed, LegacyXmlRejection.DUPLICATE,
                    "chave de acesso já aceita neste lote ou em lote anterior do tenant");
        }

        LegacyXmlRetentionPort.RetentionResult retained =
                retention.arquivarLegado(tenantId, parsed.accessKey(), file.content(), sha256);
        Objects.requireNonNull(retained, "Porta de retenção devolveu resultado nulo");
        if (!sha256.equals(retained.xmlSha256())) {
            // O hash registrado na retenção tem de ser o hash DESTE arquivo:
            // divergência é defeito da ligação, nunca um registro a gravar.
            throw new IllegalStateException("Retenção da P2.18 registrou hash divergente do arquivo importado"
                    + " (chave " + parsed.accessKey() + "): importação abortada sem registrar o documento");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tarefa", "P2.19");
        detail.put("origem", LegacyXmlRetentionPort.ORIGIN_LEGACY_IMPORT);
        detail.put("sistemaOrigem", mapping.sourceSystem());
        detail.put("arquivo", file.fileName());
        detail.put("importadoEm", clock.instant().toString());
        return new LegacyXmlImportedDocument(UUID.randomUUID(), tenantId, batch.id(), file.fileName(),
                parsed.accessKey(), parsed.competence(), parsed.issuerFromKey(), parsed.protocol(),
                sha256, LegacyXmlImportedDocument.Status.ACCEPTED, null,
                LegacyXmlRetentionPort.ORIGIN_LEGACY_IMPORT,
                retained.chaveObjeto(), retained.uriRetencao(), retained.hashChain(), Json.write(detail));
    }

    private LegacyXmlImportedDocument rejected(UUID tenantId, LegacyXmlBatch batch, XmlFileEntry file,
                                               String sha256, LegacyXmlParser.ParsedXml parsed,
                                               LegacyXmlRejection rejection, String detailMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tarefa", "P2.19");
        detail.put("arquivo", file.fileName());
        detail.put("motivo", rejection.name());
        detail.put("detalhe", detailMessage);
        detail.put("importadoEm", clock.instant().toString());
        return new LegacyXmlImportedDocument(UUID.randomUUID(), tenantId, batch.id(), file.fileName(),
                parsed == null ? null : parsed.accessKey(),
                parsed == null ? null : parsed.competence(),
                parsed == null ? null : parsed.issuerFromKey(),
                parsed == null ? null : parsed.protocol(),
                sha256, LegacyXmlImportedDocument.Status.REJECTED, rejection,
                LegacyXmlRetentionPort.ORIGIN_LEGACY_IMPORT, null, null, null, Json.write(detail));
    }

    private BatchReport reportOf(LegacyXmlBatch batch, boolean alreadyExisted) {
        List<LegacyXmlImportedDocument> documents = store.listDocuments(batch.tenantId(), batch.id());
        Map<String, Long> byReason = new TreeMap<>();
        Map<YearMonth, Long> byCompetence = new TreeMap<>();
        List<FileResult> files = new ArrayList<>();
        int accepted = 0;
        for (LegacyXmlImportedDocument doc : documents) {
            if (doc.accepted()) {
                accepted++;
                byCompetence.merge(doc.competence(), 1L, Long::sum);
            } else {
                byReason.merge(doc.rejection().name(), 1L, Long::sum);
            }
            files.add(new FileResult(doc.fileName(), doc.status().name(),
                    doc.rejection() == null ? null : doc.rejection().name(),
                    doc.accessKey(), doc.xmlSha256()));
        }
        return new BatchReport(batch.id(), alreadyExisted, documents.size(), accepted,
                documents.size() - accepted, byReason, byCompetence, List.copyOf(files));
    }

    static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JRE", e);
        }
    }
}
