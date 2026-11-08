package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.inventory.InventoryItem;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Produto quimico de tratamento (ED-89, OPE-06) SOBRE o almoxarifado da
 * ED-58: o produto REFERENCIA o item de estoque da ED-58 pelo codigo
 * ({@code inventoryItemCode}) — saldo, lote/validade e custo medio
 * continuam na ED-58; nada de estoque e duplicado aqui.
 *
 * <p>O item referenciado tem de ser da ED-58 um item {@code CHEMICAL}
 * controlado por lote (reagentes/quimicos com lote e validade sao regra
 * da ED-58); a ficha de dados de seguranca (FDS) e obrigatoria no
 * cadastro: produto sem FDS nao recebe nem consome (ver ChemService).
 *
 * <p>Local de armazenamento: silo, tanque ou cilindro — os tres tipos
 * nomeados no acceptance — ligados ao codigo do local da ED-58.
 */
public record ChemProduct(
        java.util.UUID tenantId,
        String inventoryItemCode,
        String name,
        StorageKind storageKind,
        String storageLocationCode,
        String safetyDataSheetRef,
        LocalDate safetyDataSheetRevisionOn,
        boolean active) {

    /** Tipos de armazenamento nomeados no acceptance da ED-89. */
    public enum StorageKind { SILO, TANK, CYLINDER }

    public ChemProduct {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (inventoryItemCode == null || inventoryItemCode.isBlank())
            throw new IllegalArgumentException("codigo do item de estoque (ED-58) e obrigatorio");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("nome do produto quimico e obrigatorio: " + inventoryItemCode);
        Objects.requireNonNull(storageKind, "tipo de armazenamento (silo, tanque, cilindro) e obrigatorio: " + inventoryItemCode);
        if (storageLocationCode == null || storageLocationCode.isBlank())
            throw new IllegalArgumentException("local de armazenamento (ED-58) e obrigatorio: " + inventoryItemCode);
        if (safetyDataSheetRef == null || safetyDataSheetRef.isBlank())
            throw new ChemExceptions.ValidationException("SDS_REQUIRED",
                    "produto quimico exige a ficha de dados de seguranca (FDS): " + inventoryItemCode);
        Objects.requireNonNull(safetyDataSheetRevisionOn, "data de revisao da FDS e obrigatoria: " + inventoryItemCode);
    }

    /**
     * Confere o produto contra o item REAL da ED-58: tem de ser CHEMICAL
     * e controlado por lote (lote/validade sao do aceite das duas tarefas).
     */
    public static ChemProduct requireMatchingItem(ChemProduct product, InventoryItem.Item item) {
        Objects.requireNonNull(product, "produto e obrigatorio");
        Objects.requireNonNull(item, "item da ED-58 e obrigatorio");
        if (!product.inventoryItemCode().equals(item.code()))
            throw new ChemExceptions.ValidationException("ITEM_CODE_MISMATCH",
                    "produto referencia o item " + product.inventoryItemCode() + " mas o item lido e " + item.code());
        if (item.kind() != InventoryItem.Kind.CHEMICAL)
            throw new ChemExceptions.ValidationException("ITEM_NOT_CHEMICAL",
                    "item " + item.code() + " nao e CHEMICAL na ED-58 (e " + item.kind() + "): produto quimico de tratamento exige item quimico");
        if (!item.lotTracked())
            throw new ChemExceptions.ValidationException("ITEM_NOT_LOT_TRACKED",
                    "item " + item.code() + " nao e controlado por lote/validade na ED-58: o aceite da ED-89 exige lote e validade");
        return product;
    }
}
