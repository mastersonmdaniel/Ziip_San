package br.com.ziipsan.commercial.billing.leakprotocol;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Portas da ED-23 (protocolo da revisao por vazamento). A ED-23 NAO
 * duplica nenhuma capacidade das dependencias: consome cada uma pela
 * sua fronteira, e a integracao liga os adaptadores reais.
 *
 * <ul>
 *   <li>{@link SigningPort} -&gt; P3.34 ({@code SignatureService}): assina
 *       o laudo como {@code LAB_REPORT} e a decisao como
 *       {@code SERVICE_ORDER_ACCEPTANCE} -- os dois tipos que o
 *       {@code SignableDocumentType} da P3.34 nomeia para a ED-23
 *       ("laudo e aceite da OS em campo (ED-19, ED-23)"). O nivel
 *       aplicado e o {@code sign.min_level.<tipo>} do tenant, resolvido
 *       DENTRO da P3.34 ({@code MinLevelPolicy}); esta entrega nao le
 *       nem presume nivel algum;</li>
 *   <li>{@link RectificationPort} -&gt; P2.20
 *       ({@code BillMaintenanceService.rectify}): UNICO caminho de
 *       alteracao da fatura. A ED-23 nao cria credito, nao versiona
 *       conta e nao recalcula tarifa: entrega os volumes revistos que a
 *       P3.28 calculou ({@code LeakMeterService.LeakDecision}) e a
 *       P2.20 aplica pelas suas reguas (limites, fila de analise e
 *       dupla aprovacao sao dela);</li>
 *   <li>{@link NotificationPort} -&gt; P3.5/ED-41: comunicacao das fases
 *       ao usuario pelos canais e modelos existentes. Nenhum envio e
 *       implementado aqui;</li>
 *   <li>{@link CutoffSuspensionPort} -&gt; P2.20/P3.4: enquanto a revisao
 *       esta aberta, a suspensao da cobranca/corte da conta em revisao
 *       e a da {@code ReviewSuspensionRule} da P2.20, sobre as reguas
 *       da P3.4. A ED-23 apenas CONSULTA o estado para exibi-lo no
 *       protocolo; nao suspende nem libera corte por conta propria.</li>
 * </ul>
 */
public final class LeakProtocolPorts {

    private LeakProtocolPorts() {
    }

    /** Tipos de documento da P3.34 usados por esta entrega (espelho dos codigos, nao dos niveis). */
    public enum SignableKind {
        /** Laudo da vistoria: {@code LAB_REPORT} da P3.34 ({@code sign.min_level.lab_report}). */
        LAB_REPORT("lab_report"),
        /** Decisao da revisao: {@code SERVICE_ORDER_ACCEPTANCE} da P3.34. */
        DECISION("service_order_acceptance");

        private final String p334Code;

        SignableKind(String p334Code) {
            this.p334Code = p334Code;
        }

        /** Codigo do tipo em {@code SignableDocumentType} da P3.34. */
        public String p334Code() {
            return p334Code;
        }
    }

    /** Resultado de uma assinatura na P3.34: codigo verificador e nivel EFETIVAMENTE aplicado. */
    public record SignatureReceipt(String verificationCode, String appliedLevel) {
        public SignatureReceipt {
            Objects.requireNonNull(verificationCode, "codigo verificador da assinatura (P3.34) e obrigatorio");
            Objects.requireNonNull(appliedLevel, "nivel aplicado da assinatura (P3.34) e obrigatorio");
        }
    }

    /** Assinatura eletronica (P3.34, Lei 14.063/2020). Ausencia de nivel no tenant = a P3.34 recusa. */
    public interface SigningPort {
        /**
         * Assina o documento logico identificado por {@code documentRef}
         * no nivel configurado do tenant para {@code kind}. A
         * implementacao real delega a {@code SignatureService.sign} da
         * P3.34 e devolve o recibo dela; qualquer recusa da P3.34
         * (nivel ausente, signatario, portao) propaga como excecao.
         */
        SignatureReceipt sign(UUID tenantId, SignableKind kind, String documentRef, UUID signerId);
    }

    /** Volumes revistos calculados pela P3.28 (nunca recalculados aqui). */
    public record GrantedVolumes(BigDecimal revisedWater, BigDecimal revisedSewer, String effectiveRule) {
        public GrantedVolumes {
            Objects.requireNonNull(revisedWater, "volume de agua revisto (P3.28) e obrigatorio no GRANT");
            Objects.requireNonNull(revisedSewer, "volume de esgoto revisto (P3.28) e obrigatorio no GRANT");
            Objects.requireNonNull(effectiveRule, "regra efetiva do desconto (P3.28) e obrigatoria no GRANT");
            // B1 (revisao): volume negativo e recusado aqui, na entrada --
            // a recusa nao fica delegada ao adaptador nem a P2.20.
            if (revisedWater.signum() < 0) {
                throw new IllegalArgumentException(
                        "volume de agua revisto (P3.28) nao pode ser negativo: " + revisedWater);
            }
            if (revisedSewer.signum() < 0) {
                throw new IllegalArgumentException(
                        "volume de esgoto revisto (P3.28) nao pode ser negativo: " + revisedSewer);
            }
        }
    }

    /** Recibo da retificacao aplicada pela P2.20: operacao e conta resultante. */
    public record RectificationReceipt(UUID operationRecordId, UUID resultBillId) {
        public RectificationReceipt {
            Objects.requireNonNull(operationRecordId, "operacao de manutencao (P2.20) e obrigatoria");
            Objects.requireNonNull(resultBillId, "conta resultante da retificacao (P2.20) e obrigatoria");
        }
    }

    /** Retificacao de conta -- SOMENTE pela P2.20. */
    public interface RectificationPort {
        /**
         * Pede a retificacao da fatura com os volumes revistos da
         * P3.28. A implementacao real constroi o
         * {@code RectifiedValues} da P2.20 e chama
         * {@code BillMaintenanceService.rectify} com a chave de
         * idempotencia do protocolo; limites contra o usuario, fila de
         * analise e dupla aprovacao sao aplicados LA.
         */
        RectificationReceipt rectifyByLeakReview(UUID tenantId, UUID billId, GrantedVolumes volumes,
                                                 UUID protocolId, String idempotencyKey, UUID actorId);
    }

    /** Comunicacao ao usuario pelas vias da P3.5/ED-41 (modelo e canal sao delas). */
    public interface NotificationPort {
        void notifyPhase(UUID tenantId, UUID protocolId, String phaseCode, UUID billId);
    }

    /** Estado de suspensao da cobranca da conta em revisao (P2.20 sobre P3.4): so consulta. */
    public interface CutoffSuspensionPort {
        boolean billSuspendedFromCutoff(UUID tenantId, UUID billId);
    }
}
