package br.com.ziipsan.intelligence.gis.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Inventario completo de ativos da GIS-19 (ED-56) SOBRE o cadastro
 * minimo e a hierarquia de seis niveis da P1.35 (COE-05): a entidade
 * {@code asset} e da P1.35 — esta entrega NAO cria segunda entidade
 * nem segunda fonte do codigo patrimonial (DAD-26); ela modela o
 * RETRATO do ativo com os campos que o aceite da ED-56 acrescenta
 * (ficha tecnica completa, vida util, custo historico, indicadores
 * de bem compartilhado/reversivel, codigos MCPSB, criticidade e
 * ciclo de vida) e as regras puras do inventario. Registros puros,
 * sem banco e sem Spring — quem le o banco e a fiacao (portas de
 * {@link AssetPorts}); quem decide sao os motores desta entrega.
 *
 * <p>Seis niveis (P1.35): 1 municipio/localidade, 2 sistema,
 * 3 natureza e subdivisao do servico (etapa), 4 instalacao,
 * 5 equipamento, 6 componente (opcional, so sob equipamento).
 *
 * <p><b>Nenhum dado inventado:</b> vida util NAO tem valor de fabrica
 * nesta entrega — ela vem do perfil de agencia conferido na tabela
 * original do MCPSB ({@link McpsbCodes.UsefulLifeProfile}); ativo sem
 * perfil aplicavel fica com vida util AUSENTE (nula), nunca com um
 * numero presumido. Custo historico e fato do tenant (documento de
 * aquisicao/incorporacao), nunca estimado aqui.
 */
public final class AssetInventoryModel {

    private AssetInventoryModel() {
    }

    /** Nivel hierarquico do ativo (P1.35, seis niveis). */
    public enum HierarchyLevel {
        MUNICIPIO(1), SISTEMA(2), ETAPA_SERVICO(3), INSTALACAO(4), EQUIPAMENTO(5), COMPONENTE(6);

        private final int depth;

        HierarchyLevel(int depth) { this.depth = depth; }

        public int depth() { return depth; }
    }

    /** Situacao do ciclo de vida do ativo (registrada aqui; lida pela ED-72). */
    public enum LifecycleStatus { PLANNED, IN_OPERATION, UNDER_MAINTENANCE, DECOMMISSIONED, DISPOSED }

    /** Modo de controle patrimonial do MCPSB (P1.35). */
    public enum ControlMode { INDIVIDUAL, MASS }

    /**
     * Ficha tecnica do ativo. Os campos minimos sao os da P1.35
     * (fabricante, modelo, numero de serie, ano de fabricacao,
     * potencia, capacidade); potencia/capacidade carregam a unidade
     * declarada pelo cadastro — nenhuma unidade e presumida.
     */
    public record TechnicalSheet(String manufacturer, String model, String serialNumber,
                                 Integer manufactureYear, BigDecimal power, String powerUnit,
                                 BigDecimal capacity, String capacityUnit) {
        public TechnicalSheet {
            if (power != null && (powerUnit == null || powerUnit.isBlank()))
                throw new IllegalArgumentException("potencia sem unidade declarada");
            if (capacity != null && (capacityUnit == null || capacityUnit.isBlank()))
                throw new IllegalArgumentException("capacidade sem unidade declarada");
            if (manufactureYear != null && (manufactureYear < 1800 || manufactureYear > 2200))
                throw new IllegalArgumentException("ano de fabricacao fora da faixa plausivel: " + manufactureYear);
        }

        public static TechnicalSheet empty() {
            return new TechnicalSheet(null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Ativo do inventario (retrato da entidade da P1.35 + campos da
     * ED-56). {@code patrimonialCode} e o codigo patrimonial UNICO —
     * ele so existe aqui (DAD-26): feicao do GIS e hidrometro guardam
     * apenas a FK para o ativo ({@link AssetLinkRules}).
     * {@code usefulLifeYears} nulo = sem perfil de agencia conferido
     * (pendencia nomeada, nunca numero inventado).
     * {@code gisFeatureId} nulo = ativo nao linear/sem feicao (ex.:
     * bomba de elevatoria e ativo cuja instalacao-pai tem a feicao).
     */
    public record AssetRecord(UUID id, UUID parentId, HierarchyLevel level,
                              String patrimonialCode, String name,
                              String equipmentClassCode, String typeCode,
                              McpsbCodes codes, TechnicalSheet sheet,
                              Integer usefulLifeYears, BigDecimal historicalCost,
                              boolean sharedAsset, boolean reversibleAsset,
                              LocalDate reversibleOriginDate, String reversibleOrigin,
                              LocalDate inOperationSince, LifecycleStatus lifecycle,
                              String criticalityClass, String regionCode,
                              UUID gisFeatureId, boolean confinedSpace) {
        public AssetRecord {
            Objects.requireNonNull(id, "ativo sem id");
            Objects.requireNonNull(level, "ativo sem nivel hierarquico");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("ativo sem nome");
            Objects.requireNonNull(codes, "ativo sem codigos MCPSB (podem ser parciais, nunca nulos)");
            Objects.requireNonNull(sheet, "ativo sem ficha tecnica (pode ser vazia)");
            Objects.requireNonNull(lifecycle, "ativo sem situacao de ciclo de vida");
            if (level == HierarchyLevel.MUNICIPIO && parentId != null)
                throw new IllegalArgumentException("municipio e a raiz da hierarquia: nao tem pai");
            if (level != HierarchyLevel.MUNICIPIO && parentId == null)
                throw new IllegalArgumentException("ativo de nivel " + level + " exige o pai");
            if (usefulLifeYears != null && usefulLifeYears <= 0)
                throw new IllegalArgumentException("vida util deve ser positiva quando informada");
            if (historicalCost != null && historicalCost.signum() < 0)
                throw new IllegalArgumentException("custo historico negativo");
            if (reversibleAsset && (reversibleOrigin == null || reversibleOrigin.isBlank()))
                throw new IllegalArgumentException("bem reversivel exige a origem do investimento (NR 3/2023)");
            if (!reversibleAsset && reversibleOriginDate != null)
                throw new IllegalArgumentException("data de origem de bem reversivel em bem nao reversivel");
        }

        public boolean isEquipmentOrComponent() {
            return level == HierarchyLevel.EQUIPAMENTO || level == HierarchyLevel.COMPONENTE;
        }
    }

    /** Retrato validado do inventario de um tenant. */
    public record InventorySnapshot(List<AssetRecord> assets) {
        public InventorySnapshot {
            assets = List.copyOf(assets);
            Map<UUID, AssetRecord> byId = new HashMap<>();
            for (AssetRecord a : assets) {
                if (byId.put(a.id(), a) != null)
                    throw new IllegalArgumentException("ativo repetido no retrato: " + a.patrimonialCode());
            }
            Set<String> codes = new HashSet<>();
            for (AssetRecord a : assets) {
                if (a.patrimonialCode() != null && !codes.add(a.patrimonialCode()))
                    throw new IllegalArgumentException("codigo patrimonial repetido (DAD-26): " + a.patrimonialCode());
                if (a.parentId() != null) {
                    AssetRecord parent = byId.get(a.parentId());
                    if (parent == null)
                        throw new IllegalArgumentException("pai fora do retrato: " + a.name());
                    // Nivel coerente com o do pai: exatamente um abaixo.
                    if (a.level().depth() != parent.level().depth() + 1)
                        throw new IllegalArgumentException("nivel incoerente com o pai: " + a.name()
                                + " (" + a.level() + ") sob " + parent.name() + " (" + parent.level() + ")");
                }
            }
            // Sem ciclo: subir a cadeia de cada ativo nao pode revisita-lo.
            for (AssetRecord a : assets) {
                Set<UUID> seen = new HashSet<>();
                UUID cur = a.id();
                while (cur != null) {
                    if (!seen.add(cur))
                        throw new IllegalArgumentException("ciclo na hierarquia a partir de: " + a.name());
                    AssetRecord node = byId.get(cur);
                    cur = node == null ? null : node.parentId();
                }
            }
        }

        public Optional<AssetRecord> byId(UUID id) {
            return assets.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        public List<AssetRecord> childrenOf(UUID parentId) {
            return assets.stream().filter(a -> Objects.equals(a.parentId(), parentId)).toList();
        }

        /** Descendentes (transitivo) de um ativo, na ordem do retrato. */
        public List<AssetRecord> descendantsOf(UUID rootId) {
            List<AssetRecord> out = new java.util.ArrayList<>();
            java.util.Deque<UUID> queue = new java.util.ArrayDeque<>(List.of(rootId));
            while (!queue.isEmpty()) {
                UUID cur = queue.removeFirst();
                for (AssetRecord child : childrenOf(cur)) {
                    out.add(child);
                    queue.addLast(child.id());
                }
            }
            return List.copyOf(out);
        }
    }
}
