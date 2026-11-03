package br.com.ziipsan.platform.documents;

import br.com.ziipsan.platform.documents.DocumentRecords.DataClassification;
import br.com.ziipsan.platform.documents.DocumentRecords.Document;
import br.com.ziipsan.platform.documents.DocumentRecords.DocumentType;
import br.com.ziipsan.platform.documents.DocumentRecords.RetentionClass;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEvent;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEventType;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanStatus;
import br.com.ziipsan.platform.documents.DocumentRecords.SignatureFreshness;
import br.com.ziipsan.platform.documents.DocumentRecords.SignatureStatus;
import br.com.ziipsan.platform.infra.scan.ContentTypeDetector;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P1.20: pipeline de arquivos enviados.
 *
 * <p>Fluxo, sem excecao:
 * <ol>
 *   <li>upload: tipo por catalogo + classe de retencao + classificacao, SHA-256
 *       calculado, tipo detectado pelo conteudo (nunca pela extensao), bytes
 *       gravados na chave de quarentena e linha criada QUARANTINED;</li>
 *   <li>scan: o varredor (interface {@link FileScanner} — ClamAV ou o servico
 *       da hospedagem) examina os bytes; CLEAN move o objeto para a chave de
 *       liberados e libera o download; INFECTED bloqueia e registra o alerta;
 *       varredor indisponivel deixa NOT_SCANNED — nunca CLEAN;</li>
 *   <li>download: somente CLEAN e servido; qualquer outro estado recusa.</li>
 * </ol>
 *
 * <p>Imutabilidade: documento imutavel recusa {@link #replace} e
 * {@link #regenerate}. A regeneracao em si (re-renderizar o conteudo a partir
 * dos dados de origem — emissor fiscal, modelos de documento) vive fora do
 * pipeline; aqui esta o portao: o conteudo regerado so entra quando o documento
 * nao e imutavel, e volta para a quarentena para nova varredura. E por isso
 * que um XML autorizado — sempre criado imutavel — nunca e regerado.
 *
 * <p>Seguranca fail-closed: toda falha entre armazenamento e banco deixa o
 * documento em quarentena (ou sem linha, logo inacessivel), nunca liberado.
 * Objetos orfaos de uma falha no meio do caminho nao sao servidos: o download
 * exige linha CLEAN e le a chave de liberados dessa linha.
 *
 * <p>Sem anotacoes Spring de proposito: servico simples, construido
 * explicitamente pelas frentes consumidoras (P1.19, P1.23, P1.24) com o
 * armazenamento e o varredor do ambiente.
 */
public final class DocumentService {

    private final DocumentRepository repository;
    private final ObjectStorage storage;
    private final String bucket;
    private final FileScanner scanner;
    private final Clock clock;
    private final Duration signatureMaxAge;

    /**
     * @param bucket bucket do armazenamento de objetos para os documentos
     * @param signatureMaxAge idade maxima aceita para as assinaturas do varredor;
     *                        acima dela, checkSignatureFreshness alerta STALE.
     *                        E um padrao operacional desta frente (nao uma
     *                        decisao do dono nem um piso regulatorio): o
     *                        chamador o informa explicitamente.
     */
    public DocumentService(DocumentRepository repository, ObjectStorage storage, String bucket,
                           FileScanner scanner, Clock clock, Duration signatureMaxAge) {
        if (repository == null || storage == null || scanner == null || clock == null
                || signatureMaxAge == null) {
            throw new IllegalArgumentException("repository, storage, scanner, clock e signatureMaxAge sao obrigatorios");
        }
        if (bucket == null || bucket.isBlank() || signatureMaxAge.isNegative()) {
            throw new IllegalArgumentException("bucket obrigatorio e signatureMaxAge nao-negativa");
        }
        this.repository = repository;
        this.storage = storage;
        this.bucket = bucket;
        this.scanner = scanner;
        this.clock = clock;
        this.signatureMaxAge = signatureMaxAge;
    }

    public DocumentType createDocumentType(UUID tenantId, String code, String label) {
        requireTenant(tenantId);
        requireCode(code);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label do tipo de documento e obrigatorio");
        }
        return repository.createDocumentType(tenantId, code.strip(), label.strip());
    }

    public RetentionClass createRetentionClass(UUID tenantId, String code, String label,
                                               int retentionMonths) {
        requireTenant(tenantId);
        requireCode(code);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label da classe de retencao e obrigatorio");
        }
        if (retentionMonths < 0) {
            throw new IllegalArgumentException("retentionMonths nao pode ser negativo");
        }
        return repository.createRetentionClass(tenantId, code.strip(), label.strip(), retentionMonths);
    }

    /**
     * Recebe o arquivo e o deixa em quarentena. Nao varre aqui: a varredura e
     * {@link #scan}, para o upload nao depender da disponibilidade do daemon.
     */
    public Document upload(UUID tenantId, UUID documentTypeId, UUID retentionClassId,
                           DataClassification dataClassification, String originalFilename,
                           String declaredContentType, byte[] content, boolean immutable) {
        requireTenant(tenantId);
        if (documentTypeId == null || retentionClassId == null || dataClassification == null) {
            throw new IllegalArgumentException("tipo, classe de retencao e classificacao sao obrigatorios");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename e obrigatorio");
        }
        if (content == null) {
            throw new IllegalArgumentException("conteudo e obrigatorio");
        }
        DocumentType type = repository.findDocumentType(tenantId, documentTypeId)
                .orElseThrow(() -> new IllegalArgumentException("tipo de documento inexistente: "
                        + documentTypeId));
        if (!type.active()) {
            throw new IllegalArgumentException("tipo de documento inativo: " + documentTypeId);
        }
        RetentionClass retention = repository.findRetentionClass(tenantId, retentionClassId)
                .orElseThrow(() -> new IllegalArgumentException("classe de retencao inexistente: "
                        + retentionClassId));
        if (!retention.active()) {
            throw new IllegalArgumentException("classe de retencao inativa: " + retentionClassId);
        }
        UUID id = UUID.randomUUID();
        String quarantineKey = quarantineKey(tenantId, id);
        storage.put(bucket, quarantineKey, new ByteArrayInputStream(content),
                ContentTypeDetector.detect(content));
        Document document = new Document(id, tenantId, documentTypeId, retentionClassId,
                dataClassification, ScanStatus.QUARANTINED, originalFilename.strip(),
                declaredContentType, ContentTypeDetector.detect(content), content.length,
                sha256Hex(content), bucket, quarantineKey, immutable,
                null, null, null, null, null, null);
        Document saved = repository.insertDocument(document);
        repository.insertEvent(tenantId, Optional.of(id), ScanEventType.SCAN_QUARANTINED,
                null, "upload em quarentena, aguardando varredura");
        return saved;
    }

    /**
     * Varre o documento. CLEAN libera; INFECTED bloqueia com alerta;
     * varredor indisponivel marca NOT_SCANNED e relanca
     * {@link ScannerUnavailableException} (para o chamador/worker reagendar) —
     * o documento continua em quarentena.
     */
    public Document scan(UUID tenantId, UUID documentId) throws ScannerUnavailableException {
        Document document = load(tenantId, documentId);
        byte[] content = readStored(document);
        FileScanner.ScanResult result;
        try {
            result = scanner.scan(new ByteArrayInputStream(content));
        } catch (IOException e) {
            Document notScanned = withScan(document, ScanStatus.NOT_SCANNED, null, null, null, null);
            repository.updateDocument(notScanned);
            repository.insertEvent(tenantId, Optional.of(documentId), ScanEventType.SCAN_UNAVAILABLE,
                    null, "varredor indisponivel: " + e.getMessage());
            if (e instanceof ScannerUnavailableException unavailable) {
                throw unavailable;
            }
            throw new ScannerUnavailableException("varredor indisponivel", e);
        }
        Instant now = clock.instant();
        if (result.verdict() == FileScanner.ScanVerdict.INFECTED) {
            String threat = result.threat().orElse("ameaca sem nome");
            Document infected = withScan(document, ScanStatus.INFECTED, now,
                    result.scanner(), result.avDbUpdatedAt().orElse(null), threat);
            Document saved = repository.updateDocument(infected);
            repository.insertEvent(tenantId, Optional.of(documentId), ScanEventType.SCAN_INFECTED,
                    threat, "bloqueado pelo varredor " + result.scanner());
            return saved;
        }
        String releasedKey = releasedKey(tenantId, documentId);
        storage.put(bucket, releasedKey, new ByteArrayInputStream(content),
                document.detectedContentType());
        Document clean = withScan(document, ScanStatus.CLEAN, now,
                result.scanner(), result.avDbUpdatedAt().orElse(null), null);
        clean = new Document(clean.id(), clean.tenantId(), clean.documentTypeId(),
                clean.retentionClassId(), clean.dataClassification(), clean.scanStatus(),
                clean.originalFilename(), clean.declaredContentType(), clean.detectedContentType(),
                clean.sizeBytes(), clean.sha256(), clean.storageBucket(), releasedKey,
                clean.immutable(), clean.scannedAt(), clean.scanner(), clean.avDbUpdatedAt(),
                clean.threat(), clean.createdAt(), clean.updatedAt());
        Document saved = repository.updateDocument(clean);
        deleteBestEffort(document.storageKey());
        repository.insertEvent(tenantId, Optional.of(documentId), ScanEventType.SCAN_CLEAN,
                null, "liberado pelo varredor " + result.scanner());
        return saved;
    }

    /** Somente CLEAN e servido; qualquer outro estado recusa. */
    public byte[] download(UUID tenantId, UUID documentId) {
        Document document = load(tenantId, documentId);
        if (!document.released()) {
            throw new DocumentNotReleasedException("documento " + documentId
                    + " nao liberado (scan_status=" + document.scanStatus() + ")");
        }
        return readStored(document);
    }

    /** Substitui o conteudo; recusada quando imutavel. Volta para quarentena. */
    public Document replace(UUID tenantId, UUID documentId, byte[] newContent,
                            String newOriginalFilename, String newDeclaredContentType) {
        Document document = load(tenantId, documentId);
        if (document.immutable()) {
            throw new ImmutableDocumentException("documento imutavel nao aceita substituicao: "
                    + documentId);
        }
        return applyNewContent(tenantId, document, newContent, newOriginalFilename,
                newDeclaredContentType, "substituido; volta para quarentena");
    }

    /**
     * Aplica conteudo regerado; recusada quando imutavel. O renderizador vive
     * fora do pipeline — aqui esta o portao de imutabilidade e a re-quarentena.
     */
    public Document regenerate(UUID tenantId, UUID documentId, byte[] regeneratedContent,
                               String regeneratedFilename, String regeneratedDeclaredContentType) {
        Document document = load(tenantId, documentId);
        if (document.immutable()) {
            throw new ImmutableDocumentException("documento imutavel nao aceita regeneracao: "
                    + documentId);
        }
        return applyNewContent(tenantId, document, regeneratedContent, regeneratedFilename,
                regeneratedDeclaredContentType, "regerado; volta para quarentena");
    }

    /**
     * Idade das assinaturas do varredor. STALE registra um alerta
     * SIGNATURE_STALE no tenant — no maximo um por janela de signatureMaxAge,
     * para o monitor nao inundar a trilha. UNKNOWN nao registra alerta e nunca
     * e apresentado como atual.
     */
    public SignatureFreshness checkSignatureFreshness(UUID tenantId) {
        requireTenant(tenantId);
        Optional<Instant> updatedAt = scanner.signatureUpdatedAt();
        if (updatedAt.isEmpty()) {
            return new SignatureFreshness(SignatureStatus.UNKNOWN, Optional.empty(),
                    Optional.empty(), signatureMaxAge);
        }
        Instant now = clock.instant();
        Duration age = Duration.between(updatedAt.get(), now);
        if (age.isNegative()) {
            age = Duration.ZERO;
        }
        if (age.compareTo(signatureMaxAge) <= 0) {
            return new SignatureFreshness(SignatureStatus.FRESH, updatedAt, Optional.of(age),
                    signatureMaxAge);
        }
        boolean alreadyAlerted = repository.latestEventByType(tenantId, ScanEventType.SIGNATURE_STALE)
                .map(event -> Duration.between(event.createdAt(), now)
                        .compareTo(signatureMaxAge) <= 0)
                .orElse(false);
        if (!alreadyAlerted) {
            repository.insertEvent(tenantId, Optional.empty(), ScanEventType.SIGNATURE_STALE,
                    null, "assinaturas do varredor desatualizadas ha " + age.toDays() + " dia(s)");
        }
        return new SignatureFreshness(SignatureStatus.STALE, updatedAt, Optional.of(age),
                signatureMaxAge);
    }

    public Optional<Document> find(UUID tenantId, UUID documentId) {
        requireTenant(tenantId);
        return repository.findDocument(tenantId, documentId);
    }

    public List<ScanEvent> events(UUID tenantId, UUID documentId) {
        requireTenant(tenantId);
        return repository.listEvents(tenantId, documentId);
    }

    private Document applyNewContent(UUID tenantId, Document document, byte[] newContent,
                                     String newOriginalFilename, String newDeclaredContentType,
                                     String eventDetail) {
        if (newContent == null) {
            throw new IllegalArgumentException("conteudo e obrigatorio");
        }
        if (newOriginalFilename == null || newOriginalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename e obrigatorio");
        }
        String quarantineKey = quarantineKey(tenantId, document.id());
        storage.put(bucket, quarantineKey, new ByteArrayInputStream(newContent),
                ContentTypeDetector.detect(newContent));
        String previousKey = document.storageKey();
        Document replaced = new Document(document.id(), tenantId, document.documentTypeId(),
                document.retentionClassId(), document.dataClassification(), ScanStatus.QUARANTINED,
                newOriginalFilename.strip(), newDeclaredContentType,
                ContentTypeDetector.detect(newContent), newContent.length, sha256Hex(newContent),
                bucket, quarantineKey, document.immutable(),
                null, null, null, null, document.createdAt(), document.updatedAt());
        Document saved = repository.updateDocument(replaced);
        if (!previousKey.equals(quarantineKey)) {
            deleteBestEffort(previousKey);
        }
        repository.insertEvent(tenantId, Optional.of(document.id()), ScanEventType.SCAN_QUARANTINED,
                null, eventDetail);
        return saved;
    }

    private Document load(UUID tenantId, UUID documentId) {
        requireTenant(tenantId);
        if (documentId == null) {
            throw new IllegalArgumentException("documentId e obrigatorio");
        }
        return repository.findDocument(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("documento inexistente: " + documentId));
    }

    private byte[] readStored(Document document) {
        Optional<InputStream> stored = storage.get(document.storageBucket(), document.storageKey());
        if (stored.isEmpty()) {
            throw new IllegalStateException("objeto ausente no armazenamento: "
                    + document.storageBucket() + "/" + document.storageKey());
        }
        try (InputStream in = stored.get()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler objeto no armazenamento", e);
        }
    }

    private void deleteBestEffort(String key) {
        try {
            storage.delete(bucket, key);
        } catch (RuntimeException ignored) {
            // Orfao de quarentena apos liberacao: nunca servido (o download le a
            // chave de liberados da linha CLEAN). Falha de limpeza nao reverte
            // uma liberacao nem vaza o objeto.
        }
    }

    private static Document withScan(Document document, ScanStatus status, Instant scannedAt,
                                     String scanner, Instant avDbUpdatedAt, String threat) {
        return new Document(document.id(), document.tenantId(), document.documentTypeId(),
                document.retentionClassId(), document.dataClassification(), status,
                document.originalFilename(), document.declaredContentType(),
                document.detectedContentType(), document.sizeBytes(), document.sha256(),
                document.storageBucket(), document.storageKey(), document.immutable(),
                scannedAt, scanner, avDbUpdatedAt, threat,
                document.createdAt(), document.updatedAt());
    }

    private static String quarantineKey(UUID tenantId, UUID documentId) {
        return "quarantine/" + tenantId + "/" + documentId;
    }

    private static String releasedKey(UUID tenantId, UUID documentId) {
        return "released/" + tenantId + "/" + documentId;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId e obrigatorio");
        }
    }

    private static void requireCode(String code) {
        // Codigo de catalogo nunca opcional (@NotBlank mantido): vazio ou em
        // branco e recusado aqui e pelo CHECK da migration.
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code do catalogo e obrigatorio");
        }
    }
}
