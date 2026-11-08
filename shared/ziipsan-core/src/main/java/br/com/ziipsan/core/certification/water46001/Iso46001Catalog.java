package br.com.ziipsan.core.certification.water46001;

import java.util.List;

/**
 * Acompanhamento de edicoes da ISO 46001 no catalogo de normas
 * (ED-81; aceite: "ISO 46001:2019 (em revisao; projeto da edicao 2
 * acompanhado no catalogo)").
 *
 * <p>Este registro e a foto fiel do que o aceite declara — nada alem:
 * a edicao de 2019 e a publicada vigente e esta EM REVISAO; a
 * edicao 2 existe como PROJETO acompanhado. Para o projeto NAO ha
 * requisitos, clausulas nem texto neste pacote: prescrever requisito
 * de edicao nao publicada seria inventar norma. Quando a edicao 2
 * for publicada e incorporada ao parametro
 * {@code cert.iso46001.edition} (core V3) e ao catalogo da P1.32, a
 * adocao passa pela regua de {@link Iso46001Activation} — ate la,
 * {@code adoptable} do projeto e {@code false} por construcao.
 */
public final class Iso46001Catalog {

    public enum EditionStatus {
        /** Publicada, vigente e em revisao (ISO 46001:2019). */
        PUBLISHED_UNDER_REVISION,
        /** Projeto acompanhado no catalogo; nao publicado, nao adotavel. */
        DRAFT_TRACKED
    }

    public record EditionEntry(String edition, EditionStatus status, boolean adoptable,
                               List<String> prescribedRequirements) {
        public EditionEntry {
            if (edition == null || edition.isBlank())
                throw new IllegalArgumentException("edicao do catalogo obrigatoria");
            if (status == null)
                throw new IllegalArgumentException("situacao da edicao no catalogo obrigatoria");
            if (status == EditionStatus.DRAFT_TRACKED && adoptable)
                throw new IllegalArgumentException("projeto de edicao acompanhado NAO e adotavel "
                        + "antes da publicacao: " + edition);
            if (status == EditionStatus.DRAFT_TRACKED
                    && prescribedRequirements != null && !prescribedRequirements.isEmpty())
                throw new IllegalArgumentException("nenhum requisito pode ser prescrito de "
                        + "edicao nao publicada (projeto acompanhado): " + edition);
            prescribedRequirements = prescribedRequirements == null
                    ? List.of() : List.copyOf(prescribedRequirements);
        }
    }

    /** Foto do catalogo declarada no aceite da ED-81. */
    public static List<EditionEntry> trackedEditions() {
        return List.of(
                new EditionEntry("2019", EditionStatus.PUBLISHED_UNDER_REVISION, true, List.of()),
                new EditionEntry("2 (projeto)", EditionStatus.DRAFT_TRACKED, false, List.of()));
    }

    public static boolean isAdoptable(String edition) {
        return trackedEditions().stream()
                .anyMatch(e -> e.edition().equals(edition) && e.adoptable());
    }

    private Iso46001Catalog() {}
}
