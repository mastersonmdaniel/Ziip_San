package br.com.ziipsan.intelligence.gis.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regras puras do campo GIS no SERVIDOR (ED-66 / GIS-15) — o espelho
 * definitivo das regras do aparelho (campo/src/gis/*.ts): o que o
 * aparelho pre-confere offline, o servidor confere de novo na
 * sincronizacao, porque topologia e validacao definitivas sao do
 * servidor (§8). Nenhum numero de negocio vive aqui: a meta de
 * exatidao vem de gis.accuracy_targets do tenant (V9_6) e chega como
 * parametro; teto de PDOP nao tem fonte no desenho e NAO e inventado
 * (o aceite exige guardar tipo de solucao e PDOP, nao um teto).
 */
public final class GisFieldRules {

    /** Tipo de solucao GNSS (§8). Deriva o capture_method da V13_2. */
    public enum Solution { RTK_FIXED, RTK_FLOAT, AUTONOMOUS }

    public static String captureMethodFor(Solution solution) {
        Objects.requireNonNull(solution, "tipo de solucao e obrigatorio");
        return switch (solution) {
            case RTK_FIXED -> "RTK_FIXED";
            case RTK_FLOAT -> "RTK_FLOAT";
            case AUTONOMOUS -> "GNSS_NAVIGATION";
        };
    }

    /** Fix GNSS como o servidor o recebe (altitude ainda elipsoidal, em transito). */
    public record GnssFix(Solution solution, double latitude, double longitude,
                          Double ellipsoidalHeightM, Double hAccuracyM, Double vAccuracyM,
                          Double pdop, Integer satellites, Double antennaHeightM) {
        public GnssFix {
            Objects.requireNonNull(solution, "tipo de solucao e obrigatorio");
        }
    }

    /** Meta de exatidao do tenant para a classe (gis.accuracy_targets). */
    public record AccuracyTarget(double hTargetM, Double vTargetM) {
        public AccuracyTarget {
            if (!(hTargetM > 0)) throw new IllegalArgumentException("meta horizontal deve ser positiva");
            if (vTargetM != null && !(vTargetM > 0)) throw new IllegalArgumentException("meta vertical deve ser positiva");
        }
    }

    /** Problemas de FORMA do fix: tipo de solucao e PDOP sao exigidos pelo aceite. */
    public static List<String> fixProblems(GnssFix fix) {
        Objects.requireNonNull(fix, "fix e obrigatorio");
        List<String> problems = new ArrayList<>();
        if (fix.latitude() < -90 || fix.latitude() > 90) problems.add("LATITUDE_OUT_OF_RANGE");
        if (fix.longitude() < -180 || fix.longitude() > 180) problems.add("LONGITUDE_OUT_OF_RANGE");
        if (fix.pdop() == null) problems.add("PDOP_MISSING");
        else if (!(fix.pdop() > 0)) problems.add("PDOP_NOT_POSITIVE");
        if (fix.satellites() == null) problems.add("SATELLITES_MISSING");
        else if (fix.satellites() <= 0) problems.add("SATELLITES_NOT_POSITIVE");
        if (fix.hAccuracyM() == null) problems.add("H_ACCURACY_MISSING");
        if (fix.vAccuracyM() == null) problems.add("V_ACCURACY_MISSING");
        if ((fix.hAccuracyM() != null && !(fix.hAccuracyM() > 0))
                || (fix.vAccuracyM() != null && !(fix.vAccuracyM() > 0))) problems.add("ACCURACY_NOT_POSITIVE");
        if (fix.antennaHeightM() != null && fix.antennaHeightM() < 0) problems.add("ANTENNA_HEIGHT_NEGATIVE");
        return List.copyOf(problems);
    }

    /**
     * Portao do as-built (§8/§6.4): SO RTK fixo dentro da meta do tenant.
     * Devolve as recusas nomeadas (vazio = as-built aceito na forma).
     *
     * <p>Correcao m3 da revisao: meta ausente e recusa NOMEADA
     * ({@code AS_BUILT_TARGET_MISSING}, IllegalArgumentException) —
     * simetrica a recusa nomeada do aparelho (TS) —, nunca um
     * NullPointerException cru de requireNonNull.
     */
    public static List<String> asBuiltRefusals(GnssFix fix, AccuracyTarget target) {
        if (fix == null) throw new IllegalArgumentException(
                "FIX_MISSING: fix GNSS e obrigatorio para o portao do as-built");
        if (target == null) throw new IllegalArgumentException(
                "AS_BUILT_TARGET_MISSING: meta do tenant (gis.accuracy_targets) e obrigatoria: sem meta, sem as-built");
        List<String> refusals = new ArrayList<>();
        if (!fixProblems(fix).isEmpty()) refusals.add("FIX_INVALID");
        if (fix.solution() != Solution.RTK_FIXED) refusals.add("NOT_RTK_FIXED");
        if (fix.hAccuracyM() != null && fix.hAccuracyM() > target.hTargetM()) refusals.add("H_OUT_OF_TARGET");
        if (target.vTargetM() != null && fix.vAccuracyM() != null && fix.vAccuracyM() > target.vTargetM()) {
            refusals.add("V_OUT_OF_TARGET");
        }
        return List.copyOf(refusals);
    }

    public enum PhotoScan { QUARANTINE, CLEARED, INFECTED }

    /** Foto de campo (§8): posicao, azimute, hash e estado da varredura (P1.20). */
    public record FieldPhoto(String sha256Hex, double latitude, double longitude,
                             double azimuthDeg, PhotoScan scan, boolean facesBlurred) {
        public FieldPhoto { Objects.requireNonNull(scan, "estado da varredura e obrigatorio"); }
    }

    /**
     * Porta de desfoque de rostos da ED-20 (FaceBlurPort): quem APLICA
     * o desfoque e esta porta (servico de imagem do tenant); devolve
     * true se o desfoque foi aplicado a foto referenciada.
     */
    public interface FaceBlurPort {
        boolean blurFaces(String photoRef);
    }

    /**
     * Captura de foto COM o elo do desfoque (correcao m1 da revisao,
     * espelho da correcao D-2 da ED-20 em FieldExecution.registerPhoto):
     * a flag {@code facesBlurred} NUNCA e autodeclarada pelo chamador —
     * ela DERIVA da invocacao da {@link FaceBlurPort}:
     * <ul>
     *   <li>parametro ausente (null) -&gt; recusa nomeada
     *       BLUR_PARAM_MISSING (contrato D-3), a porta nem e invocada;</li>
     *   <li>desfoque exigido sem porta (null) -&gt; recusa nomeada
     *       BLUR_PORT_MISSING: registrar foto pulando o desfoque nao e
     *       caminho legitimo;</li>
     *   <li>desfoque exigido com porta -&gt; facesBlurred = retorno da
     *       porta (false da porta segue recusado em photoProblems);</li>
     *   <li>desfoque nao exigido -&gt; a porta NAO e invocada e a foto
     *       nasce com facesBlurred = false, como na ED-20.</li>
     * </ul>
     */
    public static FieldPhoto capturePhoto(String sha256Hex, double latitude, double longitude,
                                          double azimuthDeg, PhotoScan scan,
                                          Boolean blurFacesParam, FaceBlurPort blurPort,
                                          String photoRef) {
        Objects.requireNonNull(scan, "estado da varredura e obrigatorio");
        if (blurFacesParam == null) throw new IllegalStateException(
                "BLUR_PARAM_MISSING: field.photo_blur_faces ausente no tenant: "
                        + "ausencia nao e falso (contrato D-3 da ED-20)");
        if (!blurFacesParam) {
            return new FieldPhoto(sha256Hex, latitude, longitude, azimuthDeg, scan, false);
        }
        if (blurPort == null) throw new IllegalStateException(
                "BLUR_PORT_MISSING: porta de desfoque (FaceBlurPort) obrigatoria para registrar "
                        + "foto com field.photo_blur_faces ligado no tenant: "
                        + "sem a porta, o desfoque nao pode ser aplicado nem provado");
        boolean blurred = blurPort.blurFaces(photoRef);
        return new FieldPhoto(sha256Hex, latitude, longitude, azimuthDeg, scan, blurred);
    }

    /**
     * Foto utilizavel: CLEARED na varredura, hash SHA-256 bem formado,
     * azimute em [0,360) e a privacidade da ED-20 satisfeita — com
     * field.photo_blur_faces ligado, exige o desfoque aplicado; o
     * parametro chega como Boolean anulavel e AUSENCIA RECUSA (contrato
     * D-3 da ED-20: ausencia nao e falso).
     */
    public static List<String> photoProblems(FieldPhoto photo, Boolean blurFacesParam) {
        Objects.requireNonNull(photo, "foto e obrigatoria");
        List<String> problems = new ArrayList<>();
        if (photo.sha256Hex() == null || !photo.sha256Hex().matches("[0-9a-f]{64}")) problems.add("HASH_INVALID");
        if (!(photo.azimuthDeg() >= 0 && photo.azimuthDeg() < 360)) problems.add("AZIMUTH_OUT_OF_RANGE");
        if (photo.latitude() < -90 || photo.latitude() > 90
                || photo.longitude() < -180 || photo.longitude() > 180) problems.add("FIX_INVALID");
        if (photo.scan() != PhotoScan.CLEARED) problems.add("SCAN_NOT_CLEARED");
        if (blurFacesParam == null) problems.add("BLUR_PARAM_MISSING");
        else if (blurFacesParam && !photo.facesBlurred()) problems.add("BLUR_REQUIRED_NOT_APPLIED");
        return List.copyOf(problems);
    }

    /**
     * Camada pessoal no pacote offline (§8): feicao ligada a imovel ou
     * ligacao so entra no pacote de quem tem gis.view-personal.
     */
    public static boolean personalLayerAllowed(boolean featurePersonal, boolean hasViewPersonal) {
        return !featurePersonal || hasViewPersonal;
    }

    private GisFieldRules() {}
}
