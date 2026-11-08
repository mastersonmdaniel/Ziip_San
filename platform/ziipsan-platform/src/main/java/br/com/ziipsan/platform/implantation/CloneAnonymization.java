package br.com.ziipsan.platform.implantation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Anonimização do clone mascarado do tenant (ED-09, PLT-39) pelo roteiro da
 * P0.45 (ADR-0033).
 *
 * <p>O roteiro, literal: <b>sem nome, documento, endereço nem contato</b>;
 * <b>código substituto no lugar da matrícula</b>, com a correspondência só no
 * piloto (quem chama guarda a correspondência FORA do clone — esta classe
 * recebe o código substituto pronto e nunca o deriva da matrícula, porque
 * derivação reversível ou previsível recriaria o vínculo); no cadastro
 * técnico, rede, peças e estruturas reais, e ligações, ramais, cavaletes e
 * imóveis com <b>coordenada deslocada dentro da quadra, sem coincidir com o
 * lote de origem, ou agregada no centroide da quadra</b>, com o método
 * registrado por camada.
 *
 * <p>Desenho: o registro de entrada é o retrato mínimo de uma unidade do
 * tenant de produção (só os campos que o roteiro toca); a saída é o mesmo
 * retrato anonimizado e marcado como sintético. O deslocamento da coordenada
 * chega calculado por quem chama (o deslocamento "dentro da quadra" depende
 * da quadra real, dado do GIS da P3.25 — esta classe não tem a quadra e não
 * chuta metros): ela EXIGE a coordenada deslocada ou o centroide, confere
 * que ela não coincide com a de origem e registra o método. Sem coordenada
 * de destino, a anonimização recusa — coordenada de origem no clone é dado
 * pessoal de localização vazando.
 *
 * <p>A varredura ({@link #sweep}) é o teste da P0.45 aplicado ao clone: ela
 * procura resíduo de dado pessoal (nome/documento/endereço/contato não
 * nulos, matrícula de origem presente, coordenada igual à de origem,
 * registro sem a marca de sintético) e devolve TODAS as violações. Clone só
 * é válido com a varredura vazia.
 */
public final class CloneAnonymization {

    private CloneAnonymization() {
    }

    /** Como a coordenada foi tratada (o método é registrado por camada, ADR-0033). */
    public enum CoordinateMethod {
        /** Deslocada dentro da quadra, sem coincidir com o lote de origem. */
        DISPLACED_WITHIN_BLOCK,
        /** Agregada no centroide da quadra. */
        BLOCK_CENTROID
    }

    public enum Purpose { HOMOLOGATION, TRAINING }

    /** Retrato de origem (produção). Campos pessoais só existem aqui, na entrada. */
    public record SourceUnit(String originalEnrollment, String holderName, String holderDocument,
                             String address, String contact, BigDecimal latitude,
                             BigDecimal longitude, String technicalNetworkRef) {
        public SourceUnit {
            if (originalEnrollment == null || originalEnrollment.isBlank()) {
                throw new IllegalArgumentException("A matrícula de origem é obrigatória.");
            }
        }
    }

    /** Retrato do clone: sem nenhum campo pessoal; marcado como sintético. */
    public record ClonedUnit(String substituteCode, String technicalNetworkRef,
                             BigDecimal latitude, BigDecimal longitude,
                             CoordinateMethod coordinateMethod, boolean synthetic) {
    }

    /**
     * Anonimiza uma unidade.
     *
     * @param substituteCode código substituto da matrícula, fornecido por quem
     *                       detém a correspondência (só no piloto); obrigatório
     *                       e diferente da matrícula de origem
     * @param newLatitude    latitude deslocada ou do centroide (obrigatória se
     *                       a origem tem coordenada; proibida coincidir)
     * @param newLongitude   idem
     * @param method         o método aplicado à coordenada (obrigatório com coordenada)
     */
    public static ClonedUnit anonymize(SourceUnit source, String substituteCode,
                                       BigDecimal newLatitude, BigDecimal newLongitude,
                                       CoordinateMethod method) {
        if (source == null) {
            throw new IllegalArgumentException("A unidade de origem é obrigatória.");
        }
        if (substituteCode == null || substituteCode.isBlank()) {
            throw new IllegalArgumentException(
                    "O código substituto é obrigatório: o clone nunca carrega a matrícula de origem.");
        }
        if (substituteCode.equals(source.originalEnrollment())) {
            throw new IllegalArgumentException(
                    "O código substituto não pode ser a própria matrícula de origem.");
        }
        boolean hasOriginCoord = source.latitude() != null || source.longitude() != null;
        if (hasOriginCoord) {
            if (source.latitude() == null || source.longitude() == null) {
                throw new IllegalArgumentException(
                        "Coordenada de origem incompleta (só latitude ou só longitude).");
            }
            if (newLatitude == null || newLongitude == null || method == null) {
                throw new IllegalArgumentException(
                        "Unidade com coordenada exige coordenada deslocada/centroide e o método "
                                + "registrado (P0.45): coordenada de origem não atravessa para o clone.");
            }
            if (newLatitude.compareTo(source.latitude()) == 0
                    && newLongitude.compareTo(source.longitude()) == 0) {
                throw new IllegalArgumentException(
                        "A coordenada do clone coincide com a do lote de origem (P0.45 veda).");
            }
        } else if (newLatitude != null || newLongitude != null) {
            throw new IllegalArgumentException(
                    "Coordenada de destino sem coordenada de origem: nada a deslocar.");
        } else if (method != null) {
            throw new IllegalArgumentException(
                    "Método de coordenada declarado sem coordenada alguma: a declaração "
                            + "de quem chama não é descartada em silêncio.");
        }
        // Cadastro técnico (rede/peças/estruturas) é real por roteiro: atravessa intacto.
        return new ClonedUnit(substituteCode, source.technicalNetworkRef(),
                newLatitude, newLongitude, method, true);
    }

    public enum ViolationKind {
        PERSONAL_FIELD_PRESENT,
        ORIGINAL_ENROLLMENT_PRESENT,
        COORDINATE_EQUALS_ORIGIN,
        NOT_MARKED_SYNTHETIC
    }

    /**
     * Linha bruta do clone, como a varredura a lê do banco do clone (antes de
     * qualquer tipagem): se um campo pessoal tiver QUALQUER conteúdo, a
     * varredura acusa — ela não confia na anonimização, ela a confere.
     */
    public record RawCloneRow(String substituteCode, String holderName, String holderDocument,
                              String address, String contact, String originalEnrollment,
                              BigDecimal latitude, BigDecimal longitude,
                              BigDecimal originLatitude, BigDecimal originLongitude,
                              boolean synthetic) {
    }

    public record Violation(ViolationKind kind, String substituteCode, String detail) {
    }

    /** Varredura da P0.45 sobre as linhas do clone: vazia = clone limpo. */
    public static List<Violation> sweep(List<RawCloneRow> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("As linhas do clone são obrigatórias para a varredura.");
        }
        List<Violation> violations = new ArrayList<>();
        for (RawCloneRow r : rows) {
            String id = r.substituteCode();
            if (notBlank(r.holderName()) || notBlank(r.holderDocument())
                    || notBlank(r.address()) || notBlank(r.contact())) {
                violations.add(new Violation(ViolationKind.PERSONAL_FIELD_PRESENT, id,
                        "Campo pessoal (nome, documento, endereço ou contato) presente no clone."));
            }
            if (notBlank(r.originalEnrollment())) {
                violations.add(new Violation(ViolationKind.ORIGINAL_ENROLLMENT_PRESENT, id,
                        "A matrícula de origem está presente no clone; só o código substituto pode existir."));
            }
            if (r.latitude() != null && r.originLatitude() != null
                    && r.longitude() != null && r.originLongitude() != null
                    && r.latitude().compareTo(r.originLatitude()) == 0
                    && r.longitude().compareTo(r.originLongitude()) == 0) {
                violations.add(new Violation(ViolationKind.COORDINATE_EQUALS_ORIGIN, id,
                        "A coordenada do clone é igual à do lote de origem."));
            }
            if (!r.synthetic()) {
                violations.add(new Violation(ViolationKind.NOT_MARKED_SYNTHETIC, id,
                        "Registro do clone sem a marca de sintético."));
            }
        }
        return List.copyOf(violations);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
