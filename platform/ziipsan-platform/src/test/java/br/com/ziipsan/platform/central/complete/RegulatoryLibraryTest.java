package br.com.ziipsan.platform.central.complete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.15: aba Regulatório sobre a biblioteca da P1.34, na projeção FIEL
 * da V9_8 (correções B2/M1/M5): datas anuláveis aceitas, transição pela
 * data DA NORMA (regra única do NormEffectivenessService), obrigatória
 * derivada da classe REGULATORY e consulta pela situação derivada.
 * Fixtures sintéticas, como nas frentes da P1.32/P1.34.
 */
class RegulatoryLibraryTest {

    private static final LocalDate REF = LocalDate.of(2026, 11, 7);

    /** Norma em vigência futura COM data da norma (2027-02-01) e uma edição por norma (P1.34). */
    private static RegulatoryLibrary.NormEntry futureNorm() {
        return new RegulatoryLibrary.NormEntry("nr-exemplo-futura", "Norma de referência futura (exemplo sintético)",
                "REGULATORY", "VIGENCIA_FUTURA", "OFICIAL", "saneamento", "ANA", "billing, collection",
                "https://www.gov.br/ana", "Resumo em palavras próprias do exemplo.",
                LocalDate.of(2027, 2, 1), null,
                List.of(new RegulatoryLibrary.NormEdition("2027", LocalDate.of(2027, 2, 1), null)));
    }

    /** Norma vigente REGULATORY: obrigatória POR CLASSE (antes a fixture dizia mandatory=false — contradição do M1). */
    private static RegulatoryLibrary.NormEntry currentNorm() {
        return new RegulatoryLibrary.NormEntry("lei-exemplo-vigente", "Lei exemplo vigente (sintética)",
                "REGULATORY", "VIGENTE", "OFICIAL", "saneamento", "ANA", "collection",
                "https://www.planalto.gov.br", "Resumo em palavras próprias do exemplo.",
                LocalDate.of(2020, 7, 15), null,
                List.of(new RegulatoryLibrary.NormEdition("2020", LocalDate.of(2020, 7, 15), null)));
    }

    private static RegulatoryLibrary.NormEntry otherAgencyNorm() {
        return new RegulatoryLibrary.NormEntry("res-exemplo-agencia", "Resolução de agência estadual (sintética)",
                "REGULATORY", "VIGENTE", "SECUNDARIA", "saneamento", "ARESC", "billing",
                "https://example.invalid/fonte", "Resumo em palavras próprias do exemplo.",
                LocalDate.of(2024, 1, 1), null,
                List.of(new RegulatoryLibrary.NormEdition("2024", LocalDate.of(2024, 1, 1), null)));
    }

    /** Norma VOLUNTARY da ANA (para o perfil: exclusão de não obrigatória funciona). */
    private static RegulatoryLibrary.NormEntry voluntaryNorm() {
        return new RegulatoryLibrary.NormEntry("guia-exemplo-voluntario", "Guia voluntário (sintético)",
                "VOLUNTARY", "VIGENTE", "SECUNDARIA", "saneamento", "ANA", "billing",
                "https://example.invalid/guia", "Resumo em palavras próprias do exemplo.",
                LocalDate.of(2023, 5, 1), null,
                List.of(new RegulatoryLibrary.NormEdition("2023", LocalDate.of(2023, 5, 1), null)));
    }

    /**
     * B2: norma em vigência futura SEM effective_from — o caso real da
     * carga da P1.34 (nt-2026-001-rtc-nfag): a projeção ACEITA o null,
     * a norma permanece VIGENCIA_FUTURA e nada quebra. A edição também
     * vem sem datas (anuláveis na V9_8).
     */
    private static RegulatoryLibrary.NormEntry undatedFutureNorm() {
        return new RegulatoryLibrary.NormEntry("nt-exemplo-sem-data", "Norma técnica sem data fixada (sintética)",
                "REGULATORY", "VIGENCIA_FUTURA", "NAO_CONFIRMADA", "saneamento", "ANA", "billing",
                "https://example.invalid/nt", "Resumo em palavras próprias do exemplo.",
                null, null,
                List.of(new RegulatoryLibrary.NormEdition("s/e", null, null)));
    }

    private static RegulatoryLibrary library() {
        return new RegulatoryLibrary(List.of(futureNorm(), currentNorm(), otherAgencyNorm(), voluntaryNorm()));
    }

    @Test
    @DisplayName("M5 — consulta por situação usa a DERIVADA em statusAt: norma gravada VIGENCIA_FUTURA com data chegada é achada por VIGENTE")
    void queryUsesDerivedStatus() {
        // Norma cuja data DA NORMA (01/10/2026) já chegou em 07/11/2026,
        // ainda gravada VIGENCIA_FUTURA: o filtro VIGENTE a encontra e o
        // filtro VIGENCIA_FUTURA não — a consulta é consistente com a aba.
        RegulatoryLibrary lib = new RegulatoryLibrary(List.of(
                new RegulatoryLibrary.NormEntry("nr-transicao", "Norma em transição (sintética)",
                        "REGULATORY", "VIGENCIA_FUTURA", "OFICIAL", "saneamento", "ANA", "billing",
                        "https://example.invalid/t", "Resumo próprio.", LocalDate.of(2026, 10, 1), null,
                        List.of(new RegulatoryLibrary.NormEdition("2026", LocalDate.of(2026, 10, 1), null))),
                futureNorm()));
        assertThat(lib.query(new RegulatoryLibrary.NormFilter(null, null, null, "VIGENTE", null), REF))
                .extracting(RegulatoryLibrary.NormEntry::catalogKey).containsExactly("nr-transicao");
        assertThat(lib.query(new RegulatoryLibrary.NormFilter(null, null, null, "VIGENCIA_FUTURA", null), REF))
                .extracting(RegulatoryLibrary.NormEntry::catalogKey).containsExactly("nr-exemplo-futura");
        assertThat(library().query(new RegulatoryLibrary.NormFilter("REGULATORY", null, null, null, "ARESC"), REF))
                .extracting(RegulatoryLibrary.NormEntry::catalogKey).containsExactly("res-exemplo-agencia");
    }

    @Test
    @DisplayName("módulo é token inteiro: 'bill' não casa 'billing'; 'billing' casa as normas do módulo")
    void moduleFilterIsWholeToken() {
        assertThat(library().query(new RegulatoryLibrary.NormFilter(null, null, "bill", null, null), REF)).isEmpty();
        assertThat(library().query(new RegulatoryLibrary.NormFilter(null, null, "billing", null, null), REF))
                .extracting(RegulatoryLibrary.NormEntry::catalogKey)
                .containsExactlyInAnyOrder("nr-exemplo-futura", "res-exemplo-agencia", "guia-exemplo-voluntario");
    }

    @Test
    @DisplayName("vigência futura: a edição 2027 é futura em 07/11/2026; a norma entra em withFutureEffect")
    void futureEditionsAndFutureEffect() {
        RegulatoryLibrary.NormEntry n = futureNorm();
        assertThat(n.futureEditions(REF)).extracting(RegulatoryLibrary.NormEdition::edition).containsExactly("2027");
        assertThat(library().withFutureEffect(REF)).extracting(RegulatoryLibrary.NormEntry::catalogKey)
                .containsExactly("nr-exemplo-futura");
    }

    @Test
    @DisplayName("B2 — fronteira da regra real (data DA NORMA): effective_from == hoje → VIGENTE; véspera → VIGENCIA_FUTURA")
    void statusTransitionBoundaryOnNormDate() {
        RegulatoryLibrary.NormEntry n = futureNorm(); // effective_from da norma = 2027-02-01
        assertThat(RegulatoryLibrary.statusAt(n, LocalDate.of(2027, 2, 1)))
                .as("na data de efeito a norma já vigora (effective_from <= hoje)").isEqualTo("VIGENTE");
        assertThat(RegulatoryLibrary.statusAt(n, LocalDate.of(2027, 1, 31)))
                .as("na véspera ainda é vigência futura").isEqualTo("VIGENCIA_FUTURA");
        assertThat(RegulatoryLibrary.statusAt(currentNorm(), REF)).isEqualTo("VIGENTE");
    }

    @Test
    @DisplayName("B2 — norma sem effective_from (caso real da P1.34): projeção aceita o null e ela permanece VIGENCIA_FUTURA, sem erro")
    void undatedNormStaysFutureWithoutError() {
        RegulatoryLibrary.NormEntry n = undatedFutureNorm();
        assertThat(n.effectiveFrom()).isNull();
        assertThat(RegulatoryLibrary.statusAt(n, REF)).isEqualTo("VIGENCIA_FUTURA");
        assertThat(RegulatoryLibrary.statusAt(n, LocalDate.of(2030, 1, 1)))
                .as("sem data não há transição, nem em data distante").isEqualTo("VIGENCIA_FUTURA");
        assertThat(new RegulatoryLibrary(List.of(n))
                .query(new RegulatoryLibrary.NormFilter(null, null, null, "VIGENCIA_FUTURA", null), REF))
                .hasSize(1);
    }

    @Test
    @DisplayName("b1 — effectiveTo da edição: igual a effectiveFrom é aceito (CHECK da V9_8); anterior é recusado; statusAt NÃO usa effectiveTo")
    void editionEffectiveToBoundary() {
        RegulatoryLibrary.NormEdition sameDay = new RegulatoryLibrary.NormEdition(
                "2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
        assertThat(sameDay.effectiveTo()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> new RegulatoryLibrary.NormEdition(
                "2026", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("effective_to");
        // A regra real não tem condição de effectiveTo: norma com
        // effective_to já passado mas effective_from chegado e status
        // gravado VIGENCIA_FUTURA lê-se VIGENTE pela data da norma.
        RegulatoryLibrary.NormEntry withTo = new RegulatoryLibrary.NormEntry(
                "nr-com-effective-to", "Norma com effective_to (sintética)", "REGULATORY", "VIGENCIA_FUTURA",
                "OFICIAL", "saneamento", "ANA", "billing", "https://example.invalid/x", "Resumo próprio.",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                List.of(new RegulatoryLibrary.NormEdition("2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))));
        assertThat(RegulatoryLibrary.statusAt(withTo, REF)).isEqualTo("VIGENTE");
    }

    @Test
    @DisplayName("M1 — obrigatória deriva da CLASSE: REGULATORY é mandatory, VOLUNTARY não; perfil da ANA tem 2 obrigatórias")
    void mandatoryDerivesFromClass() {
        assertThat(currentNorm().mandatory()).isTrue();
        assertThat(futureNorm().mandatory()).isTrue();
        assertThat(voluntaryNorm().mandatory()).isFalse();
        RegulatoryLibrary.AgencyProfile p = library().profileForAgency("ANA");
        assertThat(p.norms()).hasSize(3);
        assertThat(p.mandatoryNorms()).extracting(RegulatoryLibrary.NormEntry::catalogKey)
                .containsExactlyInAnyOrder("nr-exemplo-futura", "lei-exemplo-vigente");
        assertThat(p.retainedMandatoryKeys()).isEmpty();
        assertThat(library().profileForAgency("AGENCIA-INEXISTENTE").norms()).isEmpty();
    }

    @Test
    @DisplayName("M1 — tentar excluir REGULATORY do perfil é recusado: ela PERMANECE e a tentativa é sinalizada; excluir VOLUNTARY funciona")
    void regulatoryCannotBeExcludedFromProfile() {
        RegulatoryLibrary.AgencyProfile p = library().profileForAgency("ANA",
                List.of("lei-exemplo-vigente", "guia-exemplo-voluntario"));
        // A voluntária saiu; a REGULATORY ficou, sinalizada em retainedMandatoryKeys.
        assertThat(p.norms()).extracting(RegulatoryLibrary.NormEntry::catalogKey)
                .containsExactlyInAnyOrder("nr-exemplo-futura", "lei-exemplo-vigente");
        assertThat(p.retainedMandatoryKeys()).containsExactly("lei-exemplo-vigente");
        assertThat(p.mandatoryNorms()).extracting(RegulatoryLibrary.NormEntry::catalogKey)
                .containsExactlyInAnyOrder("nr-exemplo-futura", "lei-exemplo-vigente");
        assertThatThrownBy(() -> library().profileForAgency("ANA", List.of("chave-inexistente")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora do perfil");
    }

    @Test
    @DisplayName("situação fora dos 4 valores da V9_8 e selo desconhecido são recusados")
    void refusesUnknownStatusAndSeal() {
        assertThatThrownBy(() -> new RegulatoryLibrary.NormEntry("x", "t", "REGULATORY", "EM_REVISAO", "OFICIAL",
                "a", "ANA", "billing", "https://example.invalid", "r", null, null,
                List.of(new RegulatoryLibrary.NormEdition("2026", LocalDate.of(2026, 1, 1), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("V9_8");
        assertThatThrownBy(() -> new RegulatoryLibrary.NormFilter(null, null, null, "EM_REVISAO", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
