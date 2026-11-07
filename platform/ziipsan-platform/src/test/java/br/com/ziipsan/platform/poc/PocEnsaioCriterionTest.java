package br.com.ziipsan.platform.poc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mesmos casos e mesmos esperados do avaliador de script
 * (scripts/demo/testes/test_demo.py): as duas implementações da régua
 * do aceite da P3.23 são conferidas com as contas feitas à mão.
 */
class PocEnsaioCriterionTest {

    private static PocEnsaioCriterion.Item item(String id, String roteiro, String secao,
                                                int max, int obt,
                                                PocEnsaioCriterion.ItemResultado res) {
        return new PocEnsaioCriterion.Item(id, roteiro, secao, max, obt, res,
                "REQ-1", "P3.23", "TesteX");
    }

    /** 81 itens VISAN de 2 pontos (162) + 4 itens SAMAE 2.4/2.5 de 1 ponto. */
    private static List<PocEnsaioCriterion.Item> cenario(int aprovadosVisan,
                                                         PocEnsaioCriterion.ItemResultado... samae) {
        List<PocEnsaioCriterion.Item> itens = new ArrayList<>();
        for (int i = 0; i < 81; i++) {
            boolean ok = i < aprovadosVisan;
            itens.add(item("VISAN-" + (i + 1), "VISAN", "", 2, ok ? 2 : 0,
                    ok ? PocEnsaioCriterion.ItemResultado.APROVADO
                            : PocEnsaioCriterion.ItemResultado.FALHOU));
        }
        for (int j = 0; j < samae.length; j++) {
            boolean ok = samae[j] == PocEnsaioCriterion.ItemResultado.APROVADO;
            itens.add(item("SAMAE-2." + (4 + (j % 2)) + "." + (j + 1), "SAMAE",
                    j % 2 == 0 ? "2.4" : "2.5", 1, ok ? 1 : 0, samae[j]));
        }
        return itens;
    }

    private static final PocEnsaioCriterion.ItemResultado OK =
            PocEnsaioCriterion.ItemResultado.APROVADO;

    @Test
    @DisplayName("exatos 50 pontos no VISAN aprovam o critério, com SAMAE limpo e 5 dias úteis")
    void cinquentaPontosAprova() {
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(cenario(25, OK, OK, OK, OK), 5.0);
        assertThat(a.visanPontosObtidos()).isEqualTo(50);
        assertThat(a.visan()).isEqualTo(PocEnsaioCriterion.Veredito.APROVADO);
        assertThat(a.samae()).isEqualTo(PocEnsaioCriterion.Veredito.APROVADO);
        assertThat(a.aprovado()).isTrue();
    }

    @Test
    @DisplayName("48 pontos reprovam o VISAN")
    void quarentaEOitoReprova() {
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(cenario(24, OK, OK, OK, OK), 5.0);
        assertThat(a.visanPontosObtidos()).isEqualTo(48);
        assertThat(a.visan()).isEqualTo(PocEnsaioCriterion.Veredito.REPROVADO);
        assertThat(a.aprovado()).isFalse();
    }

    @Test
    @DisplayName("uma falha nas seções 2.4/2.5 do SAMAE reprova mesmo com VISAN cheio")
    void falhaNoSamaeReprova() {
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(
                cenario(81, OK, PocEnsaioCriterion.ItemResultado.FALHOU, OK, OK), 5.0);
        assertThat(a.samae()).isEqualTo(PocEnsaioCriterion.Veredito.REPROVADO);
        assertThat(a.aprovado()).isFalse();
    }

    @Test
    @DisplayName("item do SAMAE não executado deixa o critério não comprovado, não aprovado")
    void samaeNaoExecutadoNaoComprova() {
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(
                cenario(81, OK, PocEnsaioCriterion.ItemResultado.NAO_EXECUTADO, OK, OK), 5.0);
        assertThat(a.samae()).isEqualTo(PocEnsaioCriterion.Veredito.NAO_COMPROVADO);
        assertThat(a.aprovado()).isFalse();
    }

    @Test
    @DisplayName("roteiro VISAN que não soma 162 é inconclusivo por estrutura")
    void estruturaDivergenteInconclusiva() {
        List<PocEnsaioCriterion.Item> itens = List.of(
                item("VISAN-01", "VISAN", "", 100, 100, OK),
                item("SAMAE-2.4.1", "SAMAE", "2.4", 1, 1, OK));
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(itens, 5.0);
        assertThat(a.visan()).isEqualTo(PocEnsaioCriterion.Veredito.INCONCLUSIVO_ESTRUTURA);
        assertThat(a.aprovado()).isFalse();
    }

    @Test
    @DisplayName("10 dias úteis estouram os dois orçamentos (5 e 7)")
    void tempoEstouradoReprova() {
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(cenario(81, OK, OK, OK, OK), 10.0);
        assertThat(a.dentroDoMaiorOrcamento()).isFalse();
        assertThat(a.aprovado()).isFalse();
    }

    @Test
    @DisplayName("item executado sem teste ligado torna a cobertura incompleta")
    void coberturaIncompletaReprova() {
        List<PocEnsaioCriterion.Item> itens = new ArrayList<>(cenario(81, OK, OK, OK, OK));
        PocEnsaioCriterion.Item primeiro = itens.getFirst();
        itens.set(0, new PocEnsaioCriterion.Item(primeiro.item(), primeiro.roteiro(),
                primeiro.secao(), primeiro.pontosMaximos(), primeiro.pontosObtidos(),
                primeiro.resultado(), primeiro.requisito(), primeiro.tarefa(), ""));
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(itens, 5.0);
        assertThat(a.coberturaCompleta()).isFalse();
        assertThat(a.aprovado()).isFalse();
    }

    @Test
    @DisplayName("pontuação parcial em item aprovado é erro de formato, não jeitinho")
    void parcialEmAprovadoEhErro() {
        assertThatThrownBy(() -> item("VISAN-01", "VISAN", "", 2, 1, OK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pontuação parcial");
    }

    @Test
    @DisplayName("exatos 7 dias úteis cabem no maior orçamento; 7,5 estouram (revisão cruzada)")
    void fronteiraSeteDiasUteis() {
        PocEnsaioCriterion.Avaliacao dentro = PocEnsaioCriterion.avaliar(
                cenario(81, OK, OK, OK, OK), 7.0);
        assertThat(dentro.dentroDoMaiorOrcamento()).isTrue();
        assertThat(dentro.aprovado()).isTrue();
        PocEnsaioCriterion.Avaliacao fora = PocEnsaioCriterion.avaliar(
                cenario(81, OK, OK, OK, OK), 7.5);
        assertThat(fora.dentroDoMaiorOrcamento()).isFalse();
        assertThat(fora.aprovado()).isFalse();
    }

    @Test
    @DisplayName("item SAMAE sem seção não explode: critério não comprovado (revisão cruzada)")
    void samaeSemSecaoNaoExplode() {
        List<PocEnsaioCriterion.Item> itens = new ArrayList<>(cenario(81, OK, OK, OK, OK));
        itens.add(new PocEnsaioCriterion.Item("SAMAE-X", "SAMAE", null, 1, 1, OK,
                "REQ-1", "P3.23", "TesteX"));
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(itens, 5.0);
        assertThat(a.samae()).isEqualTo(PocEnsaioCriterion.Veredito.APROVADO);
        assertThat(a.aprovado()).isTrue();
    }

    @Test
    @DisplayName("roteiro nulo é erro de formato, não item ignorado (revisão cruzada)")
    void roteiroNuloEhErro() {
        assertThatThrownBy(() -> new PocEnsaioCriterion.Item(
                "X-1", null, "", 1, 1, OK, "REQ-1", "P3.23", "TesteX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Roteiro");
    }

    @Test
    @DisplayName("JSON do veredito sai pela classe Json do core, com os campos do critério")
    void jsonDoVeredito() {
        PocEnsaioCriterion.Avaliacao a = PocEnsaioCriterion.avaliar(cenario(25, OK, OK, OK, OK), 5.0);
        assertThat(a.toJson()).contains("\"visanPontosObtidos\":50")
                .contains("\"visan\":\"APROVADO\"")
                .contains("\"aprovado\":true");
    }
}
