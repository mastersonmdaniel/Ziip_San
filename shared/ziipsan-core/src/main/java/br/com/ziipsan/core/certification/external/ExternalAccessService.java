package br.com.ziipsan.core.certification.external;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Servico de leitura do observador externo (ED-75): a porta unica de
 * leitura externa sobre o mecanismo. SOMENTE LEITURA — nao existe
 * operacao de escrita; {@link #attemptWrite} recusa sempre.
 *
 * <p>Cada leitura (e cada tentativa de LEITURA negada) e registrada em
 * {@link ExternalReadLogEntry} e fica disponivel em
 * {@link #readLog()}. {@link #attemptWrite} e a excecao por desenho
 * (dobra R6-A, R6-A2): recusa a escrita SEM registrar, pois escrita
 * nao e leitura — o log registra leituras. A ordem de verificacao de
 * uma leitura e:
 * concessao ativa (parametro ligado + dentro da validade) -&gt;
 * escopo por perfil
 * ({@link ExternalScope}) -&gt; escopo fino (no pacote de evidencia:
 * normas do pacote dentro do escopo da concessao E ligadas no
 * instante da leitura). Nao ha conferencia de tenant nesta regua em
 * memoria: {@code EvidencePack} nao carrega {@code tenantId} e
 * nenhuma porta compara tenant — o isolamento por tenant e do
 * SQL/RLS das tabelas do pacote (a fiacao que trouxer o tenant do
 * pacote e a pendencia P-ED75-05).</p>
 *
 * <p>Esta classe e a regua em memoria do mecanismo, como as classes
 * dos pacotes irmaos ED-69..ED-74 sao reguas puras: a fiacao de
 * persistencia (tabelas do SQL pendente desta tarefa), de HTTP e de
 * identidade (Keycloak, ED-06) e da integracao, declarada no
 * relatorio. O CONTEUDO regulatorio concreto (quais dados e
 * relatorios o regulador le) e da ED-97 sobre este mecanismo; aqui a
 * leitura regulatoria devolve o recibo da leitura registrada, sem
 * conteudo inventado.</p>
 */
public final class ExternalAccessService {

    private final List<ExternalReadLogEntry> log = new ArrayList<>();

    /** Registro de leituras, em ordem (copia imutavel). */
    public List<ExternalReadLogEntry> readLog() {
        return List.copyOf(log);
    }

    /**
     * Leitura do pacote de evidencia por um auditor de certificacao.
     * Devolve o manifesto canonico do pacote. Recusa (registrando a
     * negacao) quando: a concessao nao esta ativa,
     * o perfil nao tem o recurso no escopo, ou o pacote contem norma
     * fora do escopo da concessao ou desligada no instante da leitura.
     */
    public String readEvidencePack(ExternalAccessGrant grant, EvidencePack pack,
                                   Set<CertificationStandard> currentlyEnabledStandards,
                                   Instant now) {
        Objects.requireNonNull(grant, "grant e obrigatorio");
        Objects.requireNonNull(pack, "pack e obrigatorio");
        Objects.requireNonNull(now, "now e obrigatorio");
        String ref = "evidence-pack:" + pack.manifestHash();
        requireActive(grant, ExternalResource.EVIDENCE_PACK, ref, now);
        requireScope(grant, ExternalResource.EVIDENCE_PACK, ref, now);
        Set<CertificationStandard> enabledNow =
                CertificationStandard.subsetOf(currentlyEnabledStandards);
        for (CertificationStandard standard : pack.enabledStandards()) {
            if (!grant.evidenceStandards().contains(standard)) {
                throw deny(grant, ExternalResource.EVIDENCE_PACK, ref, now,
                        "norma " + standard + " fora do escopo de evidencia da concessao");
            }
            if (!enabledNow.contains(standard)) {
                throw deny(grant, ExternalResource.EVIDENCE_PACK, ref, now,
                        "norma " + standard + " desligada no instante da leitura: o pacote "
                                + "de evidencia e por norma ligada");
            }
        }
        log.add(new ExternalReadLogEntry(grant.grantId(), grant.tenantId(), grant.profile(),
                ExternalResource.EVIDENCE_PACK, ref, now, ExternalReadLogEntry.Outcome.GRANTED,
                null));
        return pack.canonicalManifest();
    }

    /**
     * Leitura de um recurso REGULATORIO (dados, relatorios ou o
     * minimo de dado pessoal) por regulador/titular. O conteudo
     * concreto e da ED-97; esta regua autoriza e registra a leitura e
     * devolve o recibo {@code READ:<recurso>:<referencia>}.
     */
    public String readRegulatory(ExternalAccessGrant grant, ExternalResource resource,
                                 String resourceRef, Instant now) {
        Objects.requireNonNull(grant, "grant e obrigatorio");
        Objects.requireNonNull(now, "now e obrigatorio");
        if (resource == null || resource == ExternalResource.EVIDENCE_PACK
                || resource == ExternalResource.PERSONAL_DATA) {
            // O recurso nao e leitura regulatoria valida e e recusado
            // pela matriz — mas a TENTATIVA e registrada como negada
            // (DENIED com motivo) antes da recusa, no mesmo padrao
            // para EVIDENCE_PACK e PERSONAL_DATA (correcao D2: a
            // recusa de PERSONAL_DATA era silenciosa, sem registro).
            if (resource == ExternalResource.EVIDENCE_PACK) {
                requireActive(grant, resource, resourceRef, now);
                throw deny(grant, resource, resourceRef, now,
                        "perfil " + grant.profile() + " nao le " + resource
                                + " (matriz de escopo da ED-75, GES-22/JOR-23)");
            }
            if (resource == ExternalResource.PERSONAL_DATA) {
                requireActive(grant, resource, resourceRef, now);
                throw denyRegulatoryRefusal(grant, resource, resourceRef, now,
                        "readRegulatory so atende REGULATORY_DATA, "
                                + "REGULATORY_REPORT e PERSONAL_DATA_MINIMUM: " + resource
                                + " — dado pessoal nao e legivel por nenhum perfil"
                                + " (matriz de escopo da ED-75, GES-22/JOR-23)");
            }
            // Recurso nulo: nao ha recurso identificavel para registrar
            // contra (ExternalReadLogEntry exige recurso nao nulo),
            // logo a recusa permanece IllegalArgumentException sem
            // registro — ver RELATORIO-CORRECAO-ED-75.
            throw new IllegalArgumentException("readRegulatory so atende REGULATORY_DATA, "
                    + "REGULATORY_REPORT e PERSONAL_DATA_MINIMUM: " + resource);
        }
        requireActive(grant, resource, resourceRef, now);
        requireScope(grant, resource, resourceRef, now);
        // Dobra R5-A (F1-B1): o caminho concedido passava o ref cru ao
        // registro e ao recibo — ref nulo/em branco lancava
        // IllegalArgumentException no construtor de ExternalReadLogEntry
        // SEM deixar registro, e resourceRef.trim() no recibo daria NPE
        // com ref nulo. Sanitiza-se antes, no mesmo padrao do deny()
        // ("(sem referencia)").
        String safeRef = safeRef(resourceRef);
        log.add(new ExternalReadLogEntry(grant.grantId(), grant.tenantId(), grant.profile(),
                resource, safeRef, now, ExternalReadLogEntry.Outcome.GRANTED, null));
        return "READ:" + resource.name() + ":" + safeRef.trim();
    }

    /**
     * Leitura generica por recurso (a porta que o teste de escopo por
     * perfil exercita para TODOS os recursos, inclusive os negados):
     * aplica concessao ativa + matriz de escopo e registra o desfecho.
     * Para EVIDENCE_PACK exige-se a leitura completa via
     * {@link #readEvidencePack} — esta porta generica recusa o pacote
     * sem o objeto, porque o escopo fino por norma nao pode ser
     * conferido sem ele.
     */
    public void read(ExternalAccessGrant grant, ExternalResource resource, String resourceRef,
                     Instant now) {
        Objects.requireNonNull(grant, "grant e obrigatorio");
        Objects.requireNonNull(resource, "resource e obrigatorio");
        Objects.requireNonNull(now, "now e obrigatorio");
        requireActive(grant, resource, resourceRef, now);
        requireScope(grant, resource, resourceRef, now);
        if (resource == ExternalResource.EVIDENCE_PACK) {
            throw deny(grant, resource, resourceRef, now,
                    "pacote de evidencia se le por readEvidencePack (escopo fino por norma)");
        }
        // Dobra R5-A (F1-B1): mesmo caso do readRegulatory — o caminho
        // concedido passava o ref cru ao registro.
        log.add(new ExternalReadLogEntry(grant.grantId(), grant.tenantId(), grant.profile(),
                resource, safeRef(resourceRef), now, ExternalReadLogEntry.Outcome.GRANTED, null));
    }

    /**
     * Escrita por observador externo: RECUSADA sempre, SEM registrar.
     * O papel e somente leitura (aceite da ED-75); nao ha concessao,
     * perfil ou validade que autorize escrita por esta porta. A recusa
     * de escrita nao deixa entrada em {@link #readLog()}: o log
     * registra leituras, e escrita nao e leitura (dobra R6-A, R6-A2).
     * Registrar exigiria {@code Instant now} e {@code requireNonNull}
     * de grant/resource — mudanca de assinatura do metodo (mudanca de
     * contrato) ou uso de {@code Instant.now()} com null-checks, ou
     * seja, mudanca de comportamento num pacote ja aprovado duas
     * vezes; por isso o Javadoc e ajustado e o codigo nao muda.
     */
    public void attemptWrite(ExternalAccessGrant grant, ExternalResource resource,
                             String resourceRef) {
        throw new SecurityException("observador externo e SOMENTE LEITURA (aceite da ED-75): "
                + "escrita recusada para o recurso " + resource);
    }

    private void requireActive(ExternalAccessGrant grant, ExternalResource resource,
                               String resourceRef, Instant now) {
        if (!grant.accessParameterEnabled()) {
            throw deny(grant, resource, resourceRef, now,
                    "acesso externo DESLIGADO por parametro ("
                            + ExternalAccessPolicy.REG_EXTERNAL_ACCESS_PARAM + " / "
                            + ExternalAccessPolicy.CERT_EXTERNAL_AUDITOR_ACCESS_PARAM
                            + ", classe b, padrao desligado)");
        }
        if (grant.isExpiredAt(now)) {
            throw deny(grant, resource, resourceRef, now,
                    "concessao EXPIRADA em " + grant.expiresAt() + ": acesso externo sem "
                            + "validade vigente nao le");
        }
        if (!grant.isActiveAt(now)) {
            throw deny(grant, resource, resourceRef, now,
                    "concessao inativa no instante da leitura (fora da validade ou parametro "
                            + "desligado)");
        }
    }

    private void requireScope(ExternalAccessGrant grant, ExternalResource resource,
                              String resourceRef, Instant now) {
        if (!ExternalScope.canRead(grant.profile(), resource)) {
            throw deny(grant, resource, resourceRef, now,
                    "perfil " + grant.profile() + " nao le " + resource
                            + " (matriz de escopo da ED-75, GES-22/JOR-23)");
        }
    }

    /**
     * Referencia segura para o registro: ref nulo ou em branco vira o
     * sentinela "(sem referencia)" — o mesmo padrao do deny() — para
     * que a leitura concedida seja registrada em vez de lancar
     * IllegalArgumentException sem registro (dobra R5-A, F1-B1).
     * O trim fica por conta de {@link ExternalReadLogEntry}.
     */
    private static String safeRef(String resourceRef) {
        return resourceRef == null || resourceRef.isBlank() ? "(sem referencia)" : resourceRef;
    }

    private SecurityException deny(ExternalAccessGrant grant, ExternalResource resource,
                                   String resourceRef, Instant now, String reason) {
        String safeRef = safeRef(resourceRef);
        log.add(new ExternalReadLogEntry(grant.grantId(), grant.tenantId(), grant.profile(),
                resource, safeRef, now, ExternalReadLogEntry.Outcome.DENIED, reason));
        return new SecurityException("leitura externa negada: " + reason);
    }

    /**
     * Como {@link #deny}, mas preservando o contrato de recusa da
     * porta regulatoria: registra a negacao (DENIED com motivo) e
     * devolve a {@link IllegalArgumentException} que o chamador lanca
     * (correcao D2 — antes esta recusa nao registrava nada).
     */
    private IllegalArgumentException denyRegulatoryRefusal(ExternalAccessGrant grant,
            ExternalResource resource, String resourceRef, Instant now, String reason) {
        String safeRef = safeRef(resourceRef);
        log.add(new ExternalReadLogEntry(grant.grantId(), grant.tenantId(), grant.profile(),
                resource, safeRef, now, ExternalReadLogEntry.Outcome.DENIED, reason));
        return new IllegalArgumentException(reason);
    }
}
