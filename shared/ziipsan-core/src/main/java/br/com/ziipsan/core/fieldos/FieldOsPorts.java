package br.com.ziipsan.core.fieldos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portas da ED-20 para os vizinhos — a ED-20 CONSUME por estas interfaces,
 * sem duplicar o que cada entrega ja faz:
 * <ul>
 *   <li>{@link EnRouteNotifier} — o canal multicanal da ED-41
 *       (MultichannelDispatchService): o aviso "tecnico a caminho" sai por
 *       ele; nenhum envio direto e implementado aqui;</li>
 *   <li>{@link CutFactsPort} — fatos ATUAIS do corte no servidor (debito,
 *       contestacao, aviso, vedacoes), lidos na sincronizacao;</li>
 *   <li>{@link OrderCancellationPort} — cancelamento da OS no motor da
 *       P1.31 SEM efeito na ligacao (a porta so cancela a OS);</li>
 *   <li>{@link StockWriteOffPort} — baixa no estoque pela ED-58 (ADR-0032):
 *       a ED-20 registra o material usado por tarefa e ENTREGA a baixa;</li>
 *   <li>{@link SacCasePort} — registro da pesquisa de satisfacao no
 *       atendimento da ED-35;</li>
 *   <li>{@link ImporterPort} — importador da P1.40 (materiais, funcionarios
 *       de campo e cadastros de consistencia);</li>
 *   <li>{@link FaceBlurPort} — desfoque de rostos no aparelho/servico de
 *       imagem do tenant (a tecnica de desfoque e do provedor; aqui so a
 *       exigencia e a prova do flag);</li>
 *   <li>{@link TrackStore} — persistencia da trilha (acesso restrito a
 *       chefia, ver {@link Tracking}).</li>
 * </ul>
 */
public final class FieldOsPorts {

    private FieldOsPorts() {}

    /** Canal da ED-41: devolve se o aviso foi efetivamente disparado. */
    public interface EnRouteNotifier {
        boolean notifyEnRoute(UUID tenantId, UUID orderId, UUID customerId, Instant windowStart, Instant windowEnd);
    }

    /** Fatos correntes de uma OS de corte, relidos pelo servidor na sincronizacao. */
    public interface CutFactsPort {
        CutPackage.Facts currentFacts(UUID tenantId, UUID orderId);
    }

    /** Cancela a OS (motor P1.31). NAO toca na ligacao: corte nao executado, nenhum efeito fisico. */
    public interface OrderCancellationPort {
        void cancelWithoutConnectionEffect(UUID tenantId, UUID orderId, String reason);
    }

    /** Baixa de estoque da ED-58 (ADR-0032), uma chamada por tarefa sincronizada. */
    public interface StockWriteOffPort {
        void writeOff(UUID tenantId, String taskId, List<MaterialUse.Line> lines);
    }

    /** Atendimento da ED-35: registra a pesquisa de satisfacao vinculada ao atendimento da OS. */
    public interface SacCasePort {
        void registerSatisfaction(UUID tenantId, UUID orderId, SatisfactionSurvey survey);
    }

    /** Importador da P1.40: devolve o id do lote importado. */
    public interface ImporterPort {
        String importBatch(UUID tenantId, FieldImport.Kind kind, List<String> externalRefs);
    }

    /**
     * Desfoque de rostos (field.photo_blur_faces): devolve true se o desfoque foi aplicado a foto.
     * INVOCADA pela producao em {@code FieldExecution.registerPhoto} /
     * {@code startApplyingBlur} / {@code completeApplyingBlur} (correcao D-2):
     * toda foto registrada com o parametro ligado passa por esta porta e o
     * retorno vira a flag {@code facesBlurred} da foto.
     */
    public interface FaceBlurPort {
        boolean blurFaces(UUID tenantId, UUID documentId);
    }

    /**
     * Trilha GPS persistida por trabalhador/equipe e jornada.
     * {@code append} so recebe ponto que ja passou por {@code Tracking.ingest}
     * (portao composto do aviso ao empregado, D-1) — persistir ponto sem
     * ingestao previa e proibido na integracao (P-ED20-02).
     */
    public interface TrackStore {
        void append(UUID tenantId, Tracking.TrackPoint point);
        List<Tracking.TrackPoint> points(UUID tenantId, UUID workerId, Instant from, Instant to);
    }
}
