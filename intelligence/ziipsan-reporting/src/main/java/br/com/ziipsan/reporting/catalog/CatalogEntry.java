package br.com.ziipsan.reporting.catalog;

/**
 * Um item do catalogo completo de relatorios (ED-46).
 *
 * @param id         chave estavel: o codigo TR/SG principal do edital, ou a
 *                   chave do item novo (S52-* para a 23d §5.2, POC-* para o roteiro)
 * @param editalRefs referencias de edital como lidas no levantamento (18g/18d/23d)
 * @param title      titulo do relatorio, verbatim do levantamento quando ha linha de edital
 * @param sourceRef  dependencia textual: a tarefa/tabela que produz a fonte deste relatorio
 * @param status     situacao honesta do item (ver {@link CatalogStatus})
 * @param reportCode codigo RPT executavel associado, ou {@code null} quando nao ha view
 * @param viewName   view {@code rpt_*} quando o relatorio e novo desta tarefa, senao {@code null}
 * @param pocRef     item do roteiro de POC da VISAN (POC-56..POC-68) coberto por esta entrada
 * @param note       interpretacao declarada, parcialidade ou pendencia nomeada; {@code null} se nao ha
 */
public record CatalogEntry(
        String id,
        String editalRefs,
        String title,
        String sourceRef,
        CatalogStatus status,
        String reportCode,
        String viewName,
        String pocRef,
        String note) {

    public CatalogEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item de catalogo sem id");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Item de catalogo sem titulo: " + id);
        }
        if (sourceRef == null || sourceRef.isBlank()) {
            // O acceptance exige a dependencia textual de CADA relatorio:
            // item sem fonte declarada nao entra no catalogo.
            throw new IllegalArgumentException("Item sem dependencia de fonte declarada: " + id);
        }
        if (status == null) {
            throw new IllegalArgumentException("Item sem status: " + id);
        }
        if (status == CatalogStatus.EXECUTABLE && viewName != null
                && (reportCode == null || !reportCode.matches("RPT-[0-9]{3}"))) {
            throw new IllegalArgumentException("Item executavel com codigo invalido: " + id);
        }
        if ((status == CatalogStatus.DEPENDENT_SOURCE || status == CatalogStatus.EXCLUDED_OWN_SOURCE)
                && viewName != null) {
            // Relatorio dependente/excluido NAO tem view: prometer view sem
            // fonte entregue e exatamente a fraude que esta tarefa evita.
            throw new IllegalArgumentException("Item " + status + " nao pode declarar view: " + id);
        }
    }

    /** Verdadeiro quando ha um relatorio executavel por tras desta entrada. */
    public boolean executable() {
        return status == CatalogStatus.EXECUTABLE || status == CatalogStatus.DELIVERED_P322;
    }
}
