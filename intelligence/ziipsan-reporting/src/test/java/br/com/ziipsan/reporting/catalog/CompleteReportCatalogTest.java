package br.com.ziipsan.reporting.catalog;

import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Completude do catalogo da ED-46 contra o escopo da matriz viva:
 * TR-191..TR-199 e TR-664..TR-835 (VISAN §19), SG-193..SG-200 e
 * SG-890..SG-1062 (SAMAE 2.28). Fora das faixas numericas, com razao
 * documentada: TR-777/778/779 e SG-1004/1005/1006 NAO sao relatorios -
 * sao o termo de adesao (18d linha 34; 18e E1), o termo de confissao de
 * divida e o parcelamento em si (18e §2.12, P4.1). Item 2.28 do SAMAE
 * "sem falha" = nenhuma linha SG da faixa sem destino neste catalogo.
 *
 * <p>Contagens fixadas na geracao de 07/11/2026 (196 itens): regenerar
 * o catalogo a partir de levantamento novo muda os dois lados juntos
 * (este teste e a seed da migration, conferida no teste de consistencia).
 */
class CompleteReportCatalogTest {

    private final List<CatalogEntry> all = CompleteReportCatalog.all();

    @Test
    void catalogoTem196ItensComContagemPorStatusConferida() {
        assertThat(all).hasSize(196);
        Map<CatalogStatus, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(CatalogEntry::status, Collectors.counting()));
        assertThat(byStatus)
                .containsEntry(CatalogStatus.EXECUTABLE, 7L)
                .containsEntry(CatalogStatus.DELIVERED_P322, 26L)
                .containsEntry(CatalogStatus.DELIVERED_BY_SOURCE_TASK, 3L)
                .containsEntry(CatalogStatus.DEPENDENT_SOURCE, 122L)
                .containsEntry(CatalogStatus.EXCLUDED_OWN_SOURCE, 38L);
    }

    @Test
    void idsSaoUnicosETodaEntradaDeclaraFonte() {
        assertThat(all.stream().map(CatalogEntry::id).toList()).doesNotHaveDuplicates();
        assertThat(all).allSatisfy(e -> {
            assertThat(e.sourceRef()).isNotBlank();
            assertThat(e.title()).isNotBlank();
            assertThat(e.editalRefs()).isNotBlank();
        });
    }

    @Test
    void cobreTodasAsLinhasDeRelatorioDasSecoes19e228() {
        Set<Integer> naoRelatorios = Set.of(777, 778, 779);
        for (int tr = 664; tr <= 835; tr++) {
            if (naoRelatorios.contains(tr)) {
                continue;
            }
            String ref = "TR-" + tr;
            assertThat(all.stream().anyMatch(e -> hasRef(e, ref)))
                    .as("linha %s sem destino no catalogo", ref).isTrue();
        }
        for (int tr = 191; tr <= 199; tr++) {
            String ref = "TR-" + tr;
            assertThat(all.stream().anyMatch(e -> hasRef(e, ref)))
                    .as("linha %s (18d/E8) sem destino no catalogo", ref).isTrue();
        }
        Set<Integer> naoRelatoriosSg = Set.of(1004, 1005, 1006);
        for (int sg = 890; sg <= 1062; sg++) {
            if (naoRelatoriosSg.contains(sg)) {
                continue;
            }
            String ref = "SG-" + sg;
            assertThat(all.stream().anyMatch(e -> hasRef(e, ref)))
                    .as("linha %s (SAMAE 2.28) sem destino no catalogo", ref).isTrue();
        }
    }

    @Test
    void termosQueNaoSaoRelatoriosNaoViramItemDeCatalogo() {
        // TR-777/SG-1004 termo de adesao; TR-778/SG-1005 termo de confissao;
        // TR-779/SG-1006 parcelar dividas (funcionalidade da P4.1).
        assertThat(all.stream().map(CatalogEntry::id))
                .doesNotContain("TR-777", "TR-778", "TR-779");
    }

    @Test
    void executaveisNovosTemDefinicaoCorrespondente() {
        Set<String> newCodes = Ed46ReportDefinitions.all().stream()
                .map(d -> d.code()).collect(Collectors.toSet());
        assertThat(newCodes).containsExactlyInAnyOrder(
                "RPT-101", "RPT-102", "RPT-103", "RPT-104", "RPT-105");
        assertThat(all.stream()
                .filter(e -> e.status() == CatalogStatus.EXECUTABLE)
                .filter(e -> e.viewName() != null))
                .allSatisfy(e -> assertThat(newCodes).contains(e.reportCode()));
    }

    @Test
    void entreguesPelaP322ReferenciamCodigosReaisDoRegistro() {
        Set<String> builtins = ReportRegistry.builtins().stream()
                .map(d -> d.code()).collect(Collectors.toSet());
        assertThat(all.stream()
                .filter(e -> e.status() == CatalogStatus.DELIVERED_P322))
                .allSatisfy(e -> assertThat(builtins).contains(e.reportCode()));
    }

    @Test
    void dependentesEExcluidosNaoDeclaramViewNemCodigo() {
        assertThat(all.stream()
                .filter(e -> e.status() == CatalogStatus.DEPENDENT_SOURCE
                        || e.status() == CatalogStatus.EXCLUDED_OWN_SOURCE))
                .allSatisfy(e -> {
                    assertThat(e.viewName()).isNull();
                    assertThat(e.reportCode()).isNull();
                });
    }

    @Test
    void itensDa23dS52EstaoPresentesComDestinoHonesto() {
        // Compensacoes executavel (le a P3.4); almoxarifado dependente da ED-58.
        CatalogEntry compensacoes = byId("TR-665");
        assertThat(compensacoes.status()).isEqualTo(CatalogStatus.EXECUTABLE);
        assertThat(compensacoes.reportCode()).isEqualTo("RPT-103");
        assertThat(all.stream().filter(e -> e.id().startsWith("S52-ALMOX-")))
                .hasSize(5)
                .allSatisfy(e -> {
                    assertThat(e.status()).isEqualTo(CatalogStatus.DEPENDENT_SOURCE);
                    assertThat(e.sourceRef()).contains("ED-58");
                });
        assertThat(byId("S52-PRODUTIVIDADE-ATENDENTE").reportCode()).isEqualTo("RPT-101");
        assertThat(byId("S52-RECLAMACOES-ORIGEM-EXTERNA").reportCode()).isEqualTo("RPT-102");
        assertThat(byId("S52-ACESSOS-ATIVIDADE").reportCode()).isEqualTo("RPT-105");
        assertThat(byId("S52-ACESSOS-INATIVOS").status()).isEqualTo(CatalogStatus.DEPENDENT_SOURCE);
        assertThat(byId("S52-ACESSOS-PERFIS-SENSIVEIS").status()).isEqualTo(CatalogStatus.DEPENDENT_SOURCE);
    }

    @Test
    void poc56a68TemDestinoItemAItem() {
        // Entregues (P3.22 ou ED-46):
        assertPoc("POC-56", CatalogStatus.DELIVERED_P322);
        assertPoc("POC-57", CatalogStatus.DELIVERED_P322);
        assertPoc("POC-58", CatalogStatus.DELIVERED_P322);
        assertPoc("POC-59", CatalogStatus.DELIVERED_P322);
        assertPoc("POC-60", CatalogStatus.DELIVERED_P322);
        assertPoc("POC-62", CatalogStatus.EXECUTABLE);
        assertPoc("POC-64", CatalogStatus.DELIVERED_P322);
        assertPoc("POC-65", CatalogStatus.DELIVERED_P322);
        // Fonte propria (ED-32 reivindica POC-66..68):
        assertPoc("POC-66", CatalogStatus.DELIVERED_BY_SOURCE_TASK);
        assertPoc("POC-67", CatalogStatus.DELIVERED_BY_SOURCE_TASK);
        assertPoc("POC-68", CatalogStatus.DELIVERED_BY_SOURCE_TASK);
        // NAO entregues - dependencia nomeada, sem fingimento:
        assertPoc("POC-61", CatalogStatus.DEPENDENT_SOURCE);
        assertPoc("POC-63", CatalogStatus.DEPENDENT_SOURCE);
        assertThat(byPoc("POC-61").sourceRef()).contains("P4.1");
        assertThat(byPoc("POC-63").note()).contains("ED-18");
    }

    private void assertPoc(String poc, CatalogStatus status) {
        assertThat(byPoc(poc).status()).as(poc).isEqualTo(status);
    }

    private CatalogEntry byPoc(String poc) {
        return all.stream().filter(e -> poc.equals(e.pocRef())).findFirst()
                .orElseThrow(() -> new AssertionError("Sem entrada para " + poc));
    }

    private CatalogEntry byId(String id) {
        return all.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Sem entrada " + id));
    }

    private static boolean hasRef(CatalogEntry e, String ref) {
        // Fronteira de token: "TR-66" nao pode casar dentro de "TR-664".
        String refs = e.editalRefs();
        int i = refs.indexOf(ref);
        while (i >= 0) {
            int after = i + ref.length();
            if (after >= refs.length() || !Character.isDigit(refs.charAt(after))) {
                return true;
            }
            i = refs.indexOf(ref, i + 1);
        }
        return false;
    }
}
