package br.com.ziipsan.core.fieldos;

import java.util.List;

/**
 * Cobertura dos itens 2.18 e 2.12.5 do SAMAE no recorte da ED-20 (OS em
 * campo). MARCADOR HONESTO (padrao ED-30/ED-41): os codigos SG individuais
 * destes itens NAO foram confirmados nas fontes disponiveis nesta
 * entrega — nenhum codigo SG e inventado; as linhas usam as exigencias do
 * aceite da ED-20 e a atribuicao de codigos SG fica PENDENTE de
 * conferencia contra o roteiro do SAMAE (pendencia no relatorio).
 */
public final class SamaeFieldCoverage {

    private SamaeFieldCoverage() {}

    public record Entry(String item, String requirement, String feature) {}

    public static final List<Entry> ED20_SLICE = List.of(
            new Entry("2.18", "App de OS offline com fotos, GPS e anexos sobre a base da ED-18",
                    "FieldExecution + CutPackage + sincronizacao da ED-18 (task_id)"),
            new Entry("2.18", "Revalidacao do corte no app (offline) e no servidor (sincronizacao); falha cancela sem efeito na ligacao",
                    "CutRevalidation + FieldOsSyncService"),
            new Entry("2.18", "Trilha GPS so na jornada, retencao configuravel (so reduz), acesso so da chefia",
                    "Tracking + FieldOsParameters"),
            new Entry("2.12.5", "Janela combinada, aviso 'a caminho' pelo canal da ED-41 e estados visiveis ao usuario",
                    "EnRouteNotice + FieldExecution.VisibleState"),
            new Entry("2.12.5", "Foto antes/depois, comprovante, aceite do usuario por tipo e pesquisa de satisfacao na ED-35",
                    "FieldExecution.Receipt + CustomerAcceptance + SatisfactionSurvey"),
            new Entry("2.12.5", "Material usado por tarefa com task_id; baixa pela ED-58 (ADR-0032)",
                    "MaterialUse + FieldOsPorts.StockWriteOffPort"));

    public static List<String> items() {
        return ED20_SLICE.stream().map(Entry::item).distinct().toList();
    }
}
