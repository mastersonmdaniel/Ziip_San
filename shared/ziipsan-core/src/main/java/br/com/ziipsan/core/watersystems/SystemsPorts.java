package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.certification.governance.RiskRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-79 para as tarefas vizinhas — consumo por interface,
 * sem duplicar motor alheio (padrao das ED-50/ED-89):
 *
 * <ul>
 *   <li>{@link FactoryCatalogPort} — catalogo de fabrica da P1.27
 *       (GIS-02). A P1.27 NAO tem pacote local executavel nesta VM
 *       (spec lida no JSON do plano): a porta segue o contrato da
 *       spec — tipo existe / tipo esta ativo — e a fiacao com o
 *       {@code R__gis_factory_catalog.sql} e pendencia nomeada.</li>
 *   <li>{@link GisFeaturePort} — feicoes da ED-53 (GIS-09): a ED-79 so
 *       confere a existencia da feicao referenciada pelo componente;
 *       geometria e edicao sao da ED-53.</li>
 *   <li>{@link RiskRegistryPort} — registro de riscos da P3.31
 *       (GES-20): devolve o {@link RiskRecord} REAL da P3.31 (mesmo
 *       modulo core); incidente operacional e resiliencia climatica
 *       sao as categorias dele, nao um vocabulario novo.</li>
 *   <li>{@link LegalMessagePort} — motor de mensagens legais da
 *       P2.32 (CAD-96, RAS-04). FRONTEIRA DURA do aceite: a ED-79 NAO
 *       tem motor proprio — nao seleciona, nao ordena, nao redige
 *       mensagem legal. Ela pede a emissao e guarda a PROVA que a
 *       P2.32 devolve. A P2.32 vive no modulo billing (que depende do
 *       core, nunca o contrario): a porta e a fronteira arquitetural
 *       correta, e o adaptador da integracao delega a
 *       {@code BillContentService}/{@code LegalMessageEngine}.</li>
 *   <li>{@link NotificationPort} — notificacoes da P3.5 (prova de
 *       entrega): a ED-79 pede a entrega do relatorio e registra o
 *       resultado; canal, preferencia, supressao e prova sao da P3.5.
 *       Vive no modulo platform — mesma fronteira por porta.</li>
 *   <li>{@link PortalPublicationPort} — portal do consumidor da
 *       P3.20: a Carta de Servicos so e PUBLICADA com a referencia
 *       que o portal devolve; a ED-79 nao publica por conta propria.</li>
 * </ul>
 */
public final class SystemsPorts {
    private SystemsPorts() {}

    /** Catalogo de fabrica da P1.27 (GIS-02): existencia e atividade do tipo. */
    public interface FactoryCatalogPort {
        boolean typeExists(UUID tenantId, String typeKey);
        boolean typeActive(UUID tenantId, String typeKey);
    }

    /** Feicoes GIS da ED-53: existencia da feicao referenciada. */
    public interface GisFeaturePort {
        boolean featureExists(UUID tenantId, UUID featureId);
    }

    /** Registro de riscos da P3.31 (GES-20): o risco REAL, do tenant. */
    public interface RiskRegistryPort {
        Optional<RiskRecord> findRisk(UUID tenantId, UUID riskId);
    }

    // ---------- P2.32: motor de mensagens legais (sem motor proprio) ----------

    /**
     * Pedido de emissao a P2.32. {@code catalogKey} e a chave da
     * mensagem no catalogo versionado da P2.32: para o bloco mensal de
     * qualidade a chave REAL e {@code legal.water_quality_block}
     * (catalogo v1 da P2.32); para o relatorio anual a chave e
     * PROPOSTA ({@link #ANNUAL_REPORT_CATALOG_KEY_PROPOSED}) — o
     * catalogo v1 da P2.32 nao a contem, e registra-la e ato da dona
     * do catalogo (P2.32) na integracao, nao desta tarefa.
     */
    public record LegalEmissionRequest(
            UUID tenantId,
            UUID reportId,
            UUID billId,
            int billVersion,
            String catalogKey,
            Instant requestedAt) {
        public LegalEmissionRequest {
            java.util.Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            java.util.Objects.requireNonNull(reportId, "relatorio e obrigatorio");
            if (catalogKey == null || catalogKey.isBlank())
                throw new IllegalArgumentException("chave do catalogo da P2.32 e obrigatoria");
            java.util.Objects.requireNonNull(requestedAt, "instante do pedido e obrigatorio");
        }
    }

    /**
     * Prova de emissao DEVOLVIDA pela P2.32 (espelho de forma do
     * {@code LegalMessageEngine.EmissionProof} dela: qual mensagem,
     * em qual versao da conta, em qual versao dos catalogos). A ED-79
     * guarda a prova; quem a produz e a P2.32.
     *
     * <p><b>Correcao D1 (revisao da ED-79, 08/11/2026 — padrao da
     * correcao M-1 da ED-50):</b> era um {@code record} publico de
     * {@code String} — a sonda A10 do revisor forjou uma "prova" com
     * qualquer texto (nao ha hash nem verificacao possivel sobre uma
     * string solta). Correcao: classe final SELADA, construtor
     * PRIVADO; o unico nascimento e {@link #fromAdapter}, de ACESSO
     * DE PACOTE, chamado pelo adaptador da P2.32 fiado neste pacote
     * (fiacao da P-ED79-03) e pelas portas de teste do pacote. Alem
     * da selagem, o servico de emissao CONFERE o vinculo da prova com
     * o pedido (conta, versao da conta e a chave pedida entre as
     * mensagens emitidas) — prova de outra conta/versao/chave e
     * recusada com {@code LEGAL_EMISSION_PROOF_MISMATCH}. O residuo
     * que a selagem nao alcanca (uma implementacao da porta que minta
     * deliberadamente dentro do processo) esta coberto pela
     * declaracao formal da pendencia P-ED79-03, como prescreveu a
     * revisao.
     */
    public static final class LegalEmissionProof {
        private final String proofRef;
        private final UUID billId;
        private final int billVersion;
        private final int contentCatalogVersion;
        private final int messageCatalogVersion;
        private final List<String> emittedMessageKeys;

        private LegalEmissionProof(String proofRef, UUID billId, int billVersion,
                                   int contentCatalogVersion, int messageCatalogVersion,
                                   List<String> emittedMessageKeys) {
            if (proofRef == null || proofRef.isBlank())
                throw new IllegalArgumentException("prova de emissao da P2.32 exige a referencia da prova");
            this.proofRef = proofRef;
            this.billId = billId;
            this.billVersion = billVersion;
            this.contentCatalogVersion = contentCatalogVersion;
            this.messageCatalogVersion = messageCatalogVersion;
            this.emittedMessageKeys = emittedMessageKeys == null ? List.of() : List.copyOf(emittedMessageKeys);
        }

        /**
         * Nascimento UNICO da prova (acesso de pacote): chamado pelo
         * adaptador da P2.32 ao converter a {@code EmissionProof} real
         * dela — nunca por codigo de consumo. Ver P-ED79-03.
         */
        static LegalEmissionProof fromAdapter(String proofRef, UUID billId, int billVersion,
                                              int contentCatalogVersion, int messageCatalogVersion,
                                              List<String> emittedMessageKeys) {
            return new LegalEmissionProof(proofRef, billId, billVersion,
                    contentCatalogVersion, messageCatalogVersion, emittedMessageKeys);
        }

        public String proofRef() { return proofRef; }
        public UUID billId() { return billId; }
        public int billVersion() { return billVersion; }
        public int contentCatalogVersion() { return contentCatalogVersion; }
        public int messageCatalogVersion() { return messageCatalogVersion; }
        public List<String> emittedMessageKeys() { return emittedMessageKeys; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LegalEmissionProof other)) return false;
            return billVersion == other.billVersion
                    && contentCatalogVersion == other.contentCatalogVersion
                    && messageCatalogVersion == other.messageCatalogVersion
                    && java.util.Objects.equals(proofRef, other.proofRef)
                    && java.util.Objects.equals(billId, other.billId)
                    && java.util.Objects.equals(emittedMessageKeys, other.emittedMessageKeys);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(proofRef, billId, billVersion,
                    contentCatalogVersion, messageCatalogVersion, emittedMessageKeys);
        }

        @Override public String toString() {
            return "LegalEmissionProof[proofRef=" + proofRef + ", billId=" + billId
                    + ", billVersion=" + billVersion + ", contentCatalogVersion=" + contentCatalogVersion
                    + ", messageCatalogVersion=" + messageCatalogVersion
                    + ", emittedMessageKeys=" + emittedMessageKeys + "]";
        }
    }

    /**
     * Chave PROPOSTA para a mensagem do relatorio anual no catalogo
     * da P2.32 — marcada como proposta: NAO existe no catalogo v1
     * entregue pela P2.32 (que tem social_tariff_loss, annual_discharge,
     * water_quality_block e benefit_expiry). Sem o registro dela pela
     * P2.32, a emissao e recusada pelo motor dela e esta tarefa nao
     * emite (ver pendencia P-ED79-04 no relatorio).
     */
    public static final String ANNUAL_REPORT_CATALOG_KEY_PROPOSED = "legal.annual_water_quality_report [PROPOSTA]";

    /** Chave REAL do bloco mensal de qualidade da agua no catalogo v1 da P2.32. */
    public static final String WATER_QUALITY_BLOCK_CATALOG_KEY = "legal.water_quality_block";

    public interface LegalMessagePort {
        /** Devolve a prova da P2.32; {@code empty} = o motor nao emitiu (sem prova, sem envio). */
        Optional<LegalEmissionProof> emit(LegalEmissionRequest request);
    }

    // ---------- P3.5: entrega com prova ----------

    /** Finalidade declarada do envio do relatorio anual [a registrar no catalogo de finalidades da P1.3 na fiacao]. */
    public static final String ANNUAL_REPORT_PURPOSE = "ANNUAL_QUALITY_REPORT";

    public record DeliveryRequest(
            UUID tenantId,
            UUID consumerUnitId,
            UUID reportId,
            String purpose,
            Instant requestedAt) {
        public DeliveryRequest {
            java.util.Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            java.util.Objects.requireNonNull(consumerUnitId, "ligacao destinataria e obrigatoria");
            java.util.Objects.requireNonNull(reportId, "relatorio e obrigatorio");
            if (purpose == null || purpose.isBlank())
                throw new IllegalArgumentException("finalidade da entrega (P3.5) e obrigatoria");
            java.util.Objects.requireNonNull(requestedAt, "instante do pedido e obrigatorio");
        }
    }

    /** Resultado espelhando o {@code DispatchResult} da P3.5: enviado tem tentativa; nao enviado tem motivo nomeado. */
    public record DeliveryOutcome(UUID attemptId, boolean sent, String reason) {
        public DeliveryOutcome {
            if (sent && attemptId == null)
                throw new IllegalArgumentException("entrega enviada exige o id da tentativa da P3.5");
            if (!sent && (reason == null || reason.isBlank()))
                throw new IllegalArgumentException("entrega nao enviada exige o motivo nomeado da P3.5");
        }
        public static DeliveryOutcome sent(UUID attemptId) { return new DeliveryOutcome(attemptId, true, null); }
        public static DeliveryOutcome notSent(String reason) { return new DeliveryOutcome(null, false, reason); }
    }

    public interface NotificationPort {
        DeliveryOutcome deliver(DeliveryRequest request);
    }

    // ---------- P3.20: publicacao no portal ----------

    public record PortalPublicationRef(String portalRef, int publishedVersion, Instant publishedAt) {
        public PortalPublicationRef {
            if (portalRef == null || portalRef.isBlank())
                throw new IllegalArgumentException("publicacao no portal exige a referencia devolvida pela P3.20");
            java.util.Objects.requireNonNull(publishedAt, "instante da publicacao e obrigatorio");
        }
    }

    public interface PortalPublicationPort {
        /** Publica a versao da Carta no portal do consumidor (P3.20) e devolve a referencia. */
        PortalPublicationRef publishServiceCharter(UUID tenantId, UUID charterId, int version);
    }
}
