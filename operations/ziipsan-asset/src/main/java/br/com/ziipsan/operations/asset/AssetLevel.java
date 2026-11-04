package br.com.ziipsan.operations.asset;

/**
 * Hierarquia de seis niveis do cadastro de ativos (P1.35;
 * ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS §2, ISO 14224 como analogia tecnica).
 *
 * <p>O nivel do pai e sempre exatamente um acima do filho (regra do servico
 * e do gatilho {@code trg_asset_hierarchy}); por isso ciclo e impossivel e
 * componente (6) so existe sob equipamento (5). A peca (nivel 9 da ISO
 * 14224) nao e ativo: e material consumido pela OS (ED-58).
 */
public enum AssetLevel {
    MUNICIPALITY(1, "Municipio e localidade"),
    SYSTEM(2, "Sistema"),
    SERVICE_NATURE(3, "Natureza e subdivisao do servico"),
    INSTALLATION(4, "Instalacao"),
    EQUIPMENT(5, "Equipamento"),
    COMPONENT(6, "Componente");

    private final int level;
    private final String label;

    AssetLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    public static AssetLevel of(int level) {
        for (AssetLevel l : values()) {
            if (l.level == level) {
                return l;
            }
        }
        throw new IllegalArgumentException("nivel de ativo invalido: " + level);
    }

    /** Nivel exigido do pai de um ativo deste nivel. */
    public AssetLevel requiredParentLevel() {
        if (level == 1) {
            throw new IllegalStateException("a raiz (nivel 1) nao tem pai");
        }
        return of(level - 1);
    }
}
