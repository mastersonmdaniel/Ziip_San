package br.com.ziipsan.commercial.collection.dunning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portas da P4.1 (padrao das P3.4/P3.5/P3.36): as superficies das tarefas
 * vizinhas sao consumidas por interfaces, sem assinatura inventada. A
 * integracao liga as implementacoes reais; porta nao ligada = a operacao
 * que a exige recusa explicitamente, nunca finge o efeito.
 */
public final class DunningPorts {

    private DunningPorts() {
    }

    /**
     * Motor unico de modelos da P2.30 (JOR-06): gera o demonstrativo e o
     * termo do parcelamento e a mensagem da regua. O documento emitido
     * grava codigo e versao do modelo (regra da P2.30: documento nao muda
     * quando o modelo muda); a renderizacao e do motor, nao desta tarefa.
     */
    public interface TemplatePort {
        RenderedDocument render(UUID tenantId, String templateCode, java.util.Map<String, Object> variables);

        record RenderedDocument(String templateCode, String templateVersion, String documentRef) {
            public RenderedDocument {
                if (templateCode == null || templateCode.isBlank()
                        || templateVersion == null || templateVersion.isBlank()
                        || documentRef == null || documentRef.isBlank()) {
                    throw new IllegalArgumentException(
                            "documento gerado exige codigo, versao do modelo e referencia");
                }
            }
        }
    }

    /**
     * Politica de nivel minimo de assinatura da P3.34 (correcao D1 da
     * revisao cruzada): resolve, DENTRO do fluxo desta tarefa, o nivel
     * exigido para um tipo de documento signatario. Na integracao, o
     * adaptador delega a {@code MinLevelPolicy} da P3.34, que le
     * {@code sign.min_level.<document_type>} (classe d, do tenant) — para
     * o termo de parcelamento, {@code sign.min_level.installment_term}
     * (tipo {@code INSTALLMENT_TERM} de {@code SignableDocumentType}).
     * Como na P3.34, a AUSENCIA de nivel definido e RECUSA (excecao),
     * nunca um minimo presumido: a Lei 14.063/2020 atribui a definicao a
     * cada ente. O nivel NUNCA vem do chamador do servico.
     */
    public interface MinimumSignatureLevelPort {
        /** Nome do nivel minimo exigido (SIMPLE, ADVANCED ou QUALIFIED). */
        String requiredLevelName(UUID tenantId, String documentTypeCode);
    }

    /**
     * Assinatura eletronica da P3.34 (Lei 14.063/2020): assina o DOCUMENTO
     * GERADO pelo motor de modelos da P2.30 — o {@code documentRef} do
     * termo daquele acordo, gravado no acordo na criacao —, nunca o codigo
     * do modelo. Recebe o nivel minimo ja resolvido pela
     * {@link MinimumSignatureLevelPort} e devolve o nivel EFETIVAMENTE
     * aplicado, que o servico confere contra o minimo antes de ativar o
     * acordo. A execucao (simples, avancada ou qualificada, com PSC/ACT)
     * e da P3.34.
     */
    public interface SignaturePort {
        SignatureRef signInstallmentTerm(UUID tenantId, UUID agreementId, String documentRef,
                                         String requiredLevelName, UUID signerUserId);

        record SignatureRef(String levelName, String signatureRef, Instant signedAt) {
            public SignatureRef {
                if (levelName == null || levelName.isBlank()
                        || signatureRef == null || signatureRef.isBlank() || signedAt == null) {
                    throw new IllegalArgumentException(
                            "assinatura exige nivel, referencia e instante");
                }
            }
        }
    }

    /**
     * Contas do billing (P2.5): ao desfazer um parcelamento, as contas
     * incluidas voltam ao estado anterior ao acordo, cada uma pelo seu
     * valor original gravado em {@code included_bills}. Quem escreve na
     * conta e o billing; esta tarefa apenas ordena a restauracao.
     */
    public interface BillPort {
        void restoreBills(UUID tenantId, UUID agreementId, List<BillRestore> bills);

        record BillRestore(UUID billId, BigDecimal originalAmount) {
            public BillRestore {
                if (billId == null || originalAmount == null || originalAmount.signum() < 0) {
                    throw new IllegalArgumentException("restauracao exige conta e valor original");
                }
            }
        }
    }

    /**
     * Parecer juridico favoravel registrado (quem emitiu, quando e a
     * referencia do registro). Usado pela cessao federal (MP 1.393/2026,
     * RAS-11) e pelas linhas de prescricao marcadas como dependentes de
     * analise juridica (Decreto 20.910/1932, RAS-15). Parecer nao se
     * presume: a porta devolve a decisao REGISTRADA, com proveniencia.
     */
    public interface LegalOpinionPort {
        java.util.Optional<Opinion> favorableOpinion(UUID tenantId, String subjectCode);

        record Opinion(String issuedBy, Instant issuedAt, String reference) {
            public Opinion {
                if (issuedBy == null || issuedBy.isBlank() || issuedAt == null
                        || reference == null || reference.isBlank()) {
                    throw new IllegalArgumentException(
                            "parecer exige emissor, instante e referencia do registro (proveniencia)");
                }
            }
        }
    }

    /**
     * Link Pix de cobranca da conta (P3.2): a mensagem da regua carrega o
     * link gerado pela cobranca Pix do tenant. Sem link, a etapa de
     * mensagem nao e emitida (o aceite exige link Pix NA mensagem).
     */
    @FunctionalInterface
    public interface PixLinkPort {
        String pixLinkFor(UUID tenantId, UUID unitId, UUID billId);
    }
}
