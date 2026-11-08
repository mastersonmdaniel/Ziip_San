package br.com.ziipsan.core.fieldos;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Execucao da OS em campo (ED-20), sobre o motor da P1.31 e a base offline
 * da ED-18: os estados VISIVEIS AO USUARIO (a caminho, no local, em
 * execucao, concluida) mapeiam para os estados do motor — nenhum estado
 * novo e inventado:
 * <pre>
 *   EN_ROUTE     -> SCHEDULED (equipe despachada, aviso da ED-41 disparado)
 *   ON_SITE      -> SCHEDULED (chegada registrada com GPS; o motor nao tem
 *                    estado de chegada — a chegada e evento desta entrega)
 *   IN_EXECUTION -> IN_EXECUTION
 *   COMPLETED    -> EXECUTED
 * </pre>
 * Foto ANTES e foto DEPOIS sao obrigatorias e precisam estar liberadas
 * (varredura da P1.20/ADR-0020, mesmo criterio do anexo da ED-36) e, com
 * {@code field.photo_blur_faces} ligado, com o desfoque aplicado. O
 * comprovante (recibo da execucao) nasce na conclusao.
 *
 * <p><b>Elo do desfoque (correcao D-2):</b> quem APLICA o desfoque e a
 * {@link FieldOsPorts.FaceBlurPort}; o ponto de producao que registra a
 * foto e {@link #registerPhoto} (e os atalhos {@link #startApplyingBlur}/
 * {@link #completeApplyingBlur}): com o desfoque exigido, eles INVOCAM a
 * porta e a foto nasce com {@code facesBlurred} = resultado da porta —
 * a semantica da flag nao muda (ela continua sendo a prova de que o
 * desfoque foi aplicado; foto com a porta devolvendo falso segue
 * recusada por {@link #start}/{@link #complete}).
 *
 * <p><b>Ausencia do parametro (correcao D-3):</b> o construtor recebe o
 * interruptor como {@code Optional<Boolean>} e o resolve por
 * {@link FieldOsParameters#requirePhotoBlurFaces(Optional)}: sem valor
 * explicito do tenant a execucao RECUSA nascer — ausencia nunca vira
 * {@code false} silencioso.
 */
public final class FieldExecution {

    public enum VisibleState { EN_ROUTE, ON_SITE, IN_EXECUTION, COMPLETED }

    public enum PhotoScan { QUARANTINE, CLEARED, INFECTED }

    public record Photo(UUID documentId, PhotoScan scan, boolean facesBlurred) {
        public Photo {
            Objects.requireNonNull(documentId, "documento da foto (P1.20) e obrigatorio");
            Objects.requireNonNull(scan, "estado da varredura da foto e obrigatorio");
        }
        boolean usable(boolean blurRequired) {
            return scan == PhotoScan.CLEARED && (!blurRequired || facesBlurred);
        }
    }

    public record Receipt(UUID orderId, Instant completedAt, UUID beforePhotoId, UUID afterPhotoId) {}

    private final UUID orderId;
    private final boolean blurRequired;
    private VisibleState state;
    private Photo beforePhoto;
    private Photo afterPhoto;

    /**
     * @param blurFacesParam leitura crua de {@code field.photo_blur_faces}
     *        do tenant; vazia = ausencia recusada (D-3), nunca default falso.
     */
    public FieldExecution(UUID orderId, ServiceOrderStatus engineStatus, Optional<Boolean> blurFacesParam) {
        this.orderId = Objects.requireNonNull(orderId, "OS e obrigatoria");
        Objects.requireNonNull(engineStatus, "estado do motor (P1.31) e obrigatorio");
        this.blurRequired = FieldOsParameters.requirePhotoBlurFaces(blurFacesParam);
        this.state = switch (engineStatus) {
            case SCHEDULED -> VisibleState.EN_ROUTE;
            case IN_EXECUTION -> VisibleState.IN_EXECUTION;
            case EXECUTED, CLOSED_EXECUTED -> VisibleState.COMPLETED;
            default -> throw new IllegalStateException(
                    "execucao de campo exige OS SCHEDULED/IN_EXECUTION/EXECUTED no motor da P1.31, nao " + engineStatus);
        };
    }

    public VisibleState state() { return state; }

    /** Exigencia de desfoque ja resolvida (ausencia foi recusada no construtor). */
    public boolean blurRequired() { return blurRequired; }

    public ServiceOrderStatus engineStatus() {
        return switch (state) {
            case EN_ROUTE, ON_SITE -> ServiceOrderStatus.SCHEDULED;
            case IN_EXECUTION -> ServiceOrderStatus.IN_EXECUTION;
            case COMPLETED -> ServiceOrderStatus.EXECUTED;
        };
    }

    /** Chegada ao local (GPS registrado pelo chamador): EN_ROUTE -> ON_SITE. */
    public void arriveOnSite() {
        require(VisibleState.EN_ROUTE, "registrar chegada");
        state = VisibleState.ON_SITE;
    }

    /** Inicio do trabalho, com a foto de ANTES ja utilizavel. */
    public void start(Photo before) {
        require(VisibleState.ON_SITE, "iniciar execucao");
        this.beforePhoto = requireUsable(before, "foto de antes");
        state = VisibleState.IN_EXECUTION;
    }

    /**
     * Registro de foto COM o elo do desfoque (D-2): com
     * {@code blurRequired}, INVOCA a {@link FieldOsPorts.FaceBlurPort} do
     * tenant para o documento e a foto nasce com {@code facesBlurred} =
     * resultado da porta. Sem a porta (nula) e com desfoque exigido,
     * recusa nomeada — registrar foto pulando o desfoque nao e caminho
     * legitimo. Com desfoque nao exigido, a porta nao e invocada e a
     * foto nasce sem desfoque, como antes.
     */
    public static Photo registerPhoto(UUID tenantId, UUID documentId, PhotoScan scan,
                                      boolean blurRequired, FieldOsPorts.FaceBlurPort blurPort) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(documentId, "documento da foto (P1.20) e obrigatorio");
        Objects.requireNonNull(scan, "estado da varredura da foto e obrigatorio");
        if (!blurRequired) {
            return new Photo(documentId, scan, false);
        }
        if (blurPort == null) throw new IllegalStateException(
                "porta de desfoque (FaceBlurPort) obrigatoria para registrar foto com "
                        + FieldOsParameters.PHOTO_BLUR_FACES + " ligado no tenant: "
                        + "sem a porta, o desfoque nao pode ser aplicado nem provado");
        boolean blurred = blurPort.blurFaces(tenantId, documentId);
        return new Photo(documentId, scan, blurred);
    }

    /** Variante de instancia: usa a exigencia de desfoque ja resolvida desta execucao. */
    public Photo capturePhoto(UUID tenantId, UUID documentId, PhotoScan scan, FieldOsPorts.FaceBlurPort blurPort) {
        return registerPhoto(tenantId, documentId, scan, this.blurRequired, blurPort);
    }

    /** Inicio do trabalho registrando a foto de ANTES atraves do elo do desfoque. */
    public void startApplyingBlur(UUID tenantId, UUID documentId, PhotoScan scan, FieldOsPorts.FaceBlurPort blurPort) {
        start(capturePhoto(tenantId, documentId, scan, blurPort));
    }

    /** Conclusao registrando a foto de DEPOIS atraves do elo do desfoque: devolve o comprovante. */
    public Receipt completeApplyingBlur(UUID tenantId, UUID documentId, PhotoScan scan,
                                        FieldOsPorts.FaceBlurPort blurPort, Instant completedAt) {
        return complete(capturePhoto(tenantId, documentId, scan, blurPort), completedAt);
    }

    /** Conclusao, com a foto de DEPOIS utilizavel: devolve o comprovante. */
    public Receipt complete(Photo after, Instant completedAt) {
        require(VisibleState.IN_EXECUTION, "concluir execucao");
        Objects.requireNonNull(completedAt, "instante da conclusao e obrigatorio");
        this.afterPhoto = requireUsable(after, "foto de depois");
        state = VisibleState.COMPLETED;
        return new Receipt(orderId, completedAt, beforePhoto.documentId(), afterPhoto.documentId());
    }

    private void require(VisibleState expected, String action) {
        if (state != expected) throw new IllegalStateException(
                action + " exige estado visivel " + expected + "; estado atual: " + state);
    }

    private Photo requireUsable(Photo photo, String label) {
        Objects.requireNonNull(photo, label + " e obrigatoria");
        if (photo.scan() != PhotoScan.CLEARED) throw new IllegalStateException(
                label + " nao liberada pela varredura (" + photo.scan() + ", ADR-0020): nao vira evidencia da OS");
        if (blurRequired && !photo.facesBlurred()) throw new IllegalStateException(
                label + " sem desfoque de rostos aplicado, com field.photo_blur_faces ligado no tenant");
        return photo;
    }
}
