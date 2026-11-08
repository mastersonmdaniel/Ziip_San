package br.com.ziipsan.commercial.collection.dunning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Portas falsas em memoria (sem mocks), no padrao dos pacotes vizinhos. */
public final class DunningTestSupport {

    private DunningTestSupport() {
    }

    public static final class FakeTemplates implements DunningPorts.TemplatePort {
        public final List<String> renderedCodes = new ArrayList<>();

        @Override
        public RenderedDocument render(UUID tenantId, String templateCode, Map<String, Object> variables) {
            renderedCodes.add(templateCode);
            return new RenderedDocument(templateCode, "v3", "doc-" + templateCode);
        }
    }

    /**
     * Politica de nivel minimo FALSA mas NAO complacente (D1): devolve o
     * nivel configurado pelo teste como se viesse de
     * {@code sign.min_level.installment_term} — ou RECUSA, quando
     * configurada sem nivel (a P3.34 recusa sem parametro vigente).
     */
    public static final class FakeMinimumLevels implements DunningPorts.MinimumSignatureLevelPort {
        private final String level;
        public int consultations = 0;
        public String lastDocumentTypeCode;

        public FakeMinimumLevels(String level) {
            this.level = level;
        }

        @Override
        public String requiredLevelName(UUID tenantId, String documentTypeCode) {
            consultations++;
            lastDocumentTypeCode = documentTypeCode;
            if (level == null) {
                throw new IllegalStateException(
                        "Nivel minimo de assinatura nao definido para este tipo de documento neste tenant");
            }
            return level;
        }
    }

    /**
     * Assinatura FALSA que NAO ecoa o nivel pedido (correcao D1 — a fake
     * anterior devolvia {@code requiredLevelName} e escondia o defeito):
     * ela aplica SEMPRE o nivel com que foi construida (o nivel que a
     * "P3.34" aplicaria) e RECUSA documento que nao seja o termo gerado
     * esperado pelo teste. Registra as chamadas para as assercoes.
     */
    public static final class FakeSignatures implements DunningPorts.SignaturePort {
        private final String levelItApplies;
        public int calls = 0;
        public String lastDocumentRef;
        public String lastRequiredLevelName;
        public String expectedDocumentRef;

        public FakeSignatures(String levelItApplies) {
            this.levelItApplies = levelItApplies;
        }

        @Override
        public SignatureRef signInstallmentTerm(UUID tenantId, UUID agreementId, String documentRef,
                                                String requiredLevelName, UUID signerUserId) {
            calls++;
            lastDocumentRef = documentRef;
            lastRequiredLevelName = requiredLevelName;
            if (expectedDocumentRef != null && !expectedDocumentRef.equals(documentRef)) {
                throw new IllegalStateException(
                        "documento recusado pela fake: nao e o termo gerado esperado");
            }
            return new SignatureRef(levelItApplies, "sig-" + agreementId,
                    Instant.parse("2026-11-07T12:00:00Z"));
        }
    }

    public static final class FakeBills implements DunningPorts.BillPort {
        public final List<BillRestore> restored = new ArrayList<>();

        @Override
        public void restoreBills(UUID tenantId, UUID agreementId, List<BillRestore> bills) {
            restored.addAll(bills);
        }
    }

    public static final class FakeLegalOpinions implements DunningPorts.LegalOpinionPort {
        private final Opinion opinion;

        public FakeLegalOpinions(Opinion opinion) {
            this.opinion = opinion;
        }

        @Override
        public Optional<Opinion> favorableOpinion(UUID tenantId, String subjectCode) {
            return Optional.ofNullable(opinion);
        }
    }
}
