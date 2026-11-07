package br.com.ziipsan.platform.work;

/**
 * Evento do catálogo único da P1.38 como a caixa o enxerga: nome e versão declarados e o
 * EFEITO sobre a caixa — abrir um item ({@code WORK_ITEM_OPENED}) ou encerrar um item
 * ({@code WORK_ITEM_CLOSED}). A carga do catálogo, por regra da P1.38, não tem dado
 * pessoal (só identificadores e códigos).
 */
public record WorkCatalogEvent(String name, int version, Effect effect) {

    public enum Effect { WORK_ITEM_OPENED, WORK_ITEM_CLOSED }

    public WorkCatalogEvent {
        if (name == null || name.isBlank() || version < 1 || effect == null) {
            throw new IllegalArgumentException("evento do catálogo exige nome, versão >= 1 e efeito");
        }
    }
}
