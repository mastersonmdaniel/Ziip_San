package br.com.ziipsan.intelligence.gis.catalog.application;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ActiveInput;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ClassView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ItemInput;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ItemView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.MaterialView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ReferenceReport;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.TypeInput;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.TypeView;
import br.com.ziipsan.intelligence.gis.catalog.domain.GisCatalogItem;
import br.com.ziipsan.intelligence.gis.catalog.domain.GisFeatureType;
import br.com.ziipsan.intelligence.gis.catalog.repository.GisCatalogItemRepository;
import br.com.ziipsan.intelligence.gis.catalog.repository.GisFeatureClassRepository;
import br.com.ziipsan.intelligence.gis.catalog.repository.GisFeatureTypeRepository;
import br.com.ziipsan.intelligence.gis.catalog.repository.GisMaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administração do catálogo GIS pelo tenant (P1.27, GIS-CADASTRO-TECNICO §4.1).
 *
 * <ul>
 *   <li>Criar tipo/item do tenant: nasce PENDING + inativo, sem código nem migration.</li>
 *   <li>Aprovar: só outra pessoa (approved_by diferente de proposed_by, 403 caso
 *   contrário, e CHECK da V13_4 no banco); aprovar ativa o registro.</li>
 *   <li>Ativar/desativar: só registro APPROVED (de fábrica ou do tenant). Desativar
 *   nunca apaga: o item some da criação (listas {@code usable}) e continua
 *   referenciado pelas feições existentes.</li>
 *   <li>Linha de fábrica: o tenant só muda o active. A R__ também nunca altera
 *   active, origin, factory_code, standard_ref ou reference_status na reaplicação.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GisCatalogService {

    private final GisFeatureClassRepository classes;
    private final GisFeatureTypeRepository types;
    private final GisCatalogItemRepository items;
    private final GisMaterialRepository materials;

    public GisCatalogService(GisFeatureClassRepository classes, GisFeatureTypeRepository types,
                             GisCatalogItemRepository items, GisMaterialRepository materials) {
        this.classes = classes;
        this.types = types;
        this.items = items;
        this.materials = materials;
    }

    // ------------------------------------------------------------------ leitura

    public List<ClassView> listClasses() {
        return classes.findByTenantIdOrderByCodeAsc(GisCatalogContext.tenantId()).stream()
                .map(c -> new ClassView(c.getId(), c.getCode(), c.getDomain(), c.getTopologyRole(),
                        c.getGeometryType(), c.getOrigin(), c.isActive()))
                .toList();
    }

    public List<TypeView> listTypes(String q, Boolean active, Boolean usable) {
        UUID tenantId = GisCatalogContext.tenantId();
        Map<UUID, String> classCodes = classes.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getCode()));
        return types.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .filter(t -> q == null || q.isBlank()
                        || t.getCode().toLowerCase().contains(q.toLowerCase())
                        || t.getLabel().toLowerCase().contains(q.toLowerCase()))
                .filter(t -> active == null || t.isActive() == active)
                .filter(t -> usable == null || !usable || (t.isActive() && "APPROVED".equals(t.getApprovalStatus())))
                .map(t -> view(t, classCodes.getOrDefault(t.getClassId(), "")))
                .toList();
    }

    public TypeView getType(UUID id) {
        UUID tenantId = GisCatalogContext.tenantId();
        GisFeatureType t = types.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Tipo de catálogo não encontrado."));
        String classCode = classes.findByIdAndTenantId(t.getClassId(), tenantId)
                .map(c -> c.getCode()).orElse("");
        return view(t, classCode);
    }

    public List<ItemView> listItems(UUID featureTypeId, Boolean active, Boolean usable,
                                    String referenceStatus) {
        UUID tenantId = GisCatalogContext.tenantId();
        List<GisCatalogItem> all = featureTypeId == null
                ? items.findByTenantIdOrderByFeatureTypeIdAsc(tenantId)
                : items.findByTenantIdAndFeatureTypeIdOrderByDnMmAsc(tenantId, featureTypeId);
        Map<UUID, GisFeatureType> typeById = types.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .collect(Collectors.toMap(t -> t.getId(), Function.identity()));
        Map<UUID, String> materialCodes = materials.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m.getCode()));
        return all.stream()
                .filter(i -> active == null || i.isActive() == active)
                .filter(i -> usable == null || !usable || (i.isActive() && "APPROVED".equals(i.getApprovalStatus())))
                .filter(i -> referenceStatus == null || referenceStatus.equals(i.getReferenceStatus()))
                .map(i -> {
                    GisFeatureType t = typeById.get(i.getFeatureTypeId());
                    return view(i, t == null ? "" : t.getCode(),
                            i.getMaterialId() == null ? null : materialCodes.get(i.getMaterialId()));
                })
                .toList();
    }

    public List<MaterialView> listMaterials() {
        return materials.findByTenantIdOrderByCodeAsc(GisCatalogContext.tenantId()).stream()
                .map(m -> new MaterialView(m.getId(), m.getCode(), m.getFamily(), m.getStandardRef(),
                        m.getHazenWilliamsC(), m.getManningN(), m.isLegacyOnly(), m.getOrigin(), m.isActive()))
                .toList();
    }

    /**
     * Relatório dos itens a validar (§4.1). A compra das normas da P1.28 está
     * pendente de aprovação do dono: nenhum item de fábrica é STANDARD, e o
     * relatório diz isso explicitamente em vez de apresentar citação como
     * conferência.
     */
    public ReferenceReport referenceReport() {
        UUID tenantId = GisCatalogContext.tenantId();
        return new ReferenceReport(
                types.countByTenantIdAndOrigin(tenantId, "FACTORY"),
                items.countByTenantIdAndOrigin(tenantId, "FACTORY"),
                items.countByTenantIdAndOriginAndReferenceStatus(tenantId, "FACTORY", "REFERENCE_TO_VALIDATE"),
                items.countByTenantIdAndOriginAndReferenceStatus(tenantId, "FACTORY", "STANDARD"),
                types.countByTenantIdAndOriginAndFactoryRetiredOnIsNotNull(tenantId, "FACTORY"),
                items.countByTenantIdAndOriginAndFactoryRetiredOnIsNotNull(tenantId, "FACTORY"),
                types.countByTenantIdAndOriginAndApprovalStatus(tenantId, "TENANT", "PENDING"),
                items.countByTenantIdAndOriginAndApprovalStatus(tenantId, "TENANT", "PENDING"),
                true,
                "Compra das 33 normas da P1.28 pendente de aprovação do dono: nenhum item de "
                        + "fábrica foi conferido no texto de norma alguma, logo nenhum é STANDARD. "
                        + "O standard_ref do catálogo é citação bibliográfica (número e edição como "
                        + "citados no §4 do projeto), não verificação. Quando a compra for aprovada "
                        + "e feita, a curadoria passa os itens conferidos para STANDARD.");
    }

    // ------------------------------------------------------------------ tipos

    @Transactional
    public TypeView createType(TypeInput in) {
        UUID tenantId = GisCatalogContext.tenantId();
        UUID author = GisCatalogContext.userId();
        var clazz = classes.findByIdAndTenantId(in.classId(), tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Classe de feição não encontrada."));
        if (!clazz.isActive()) {
            throw new ApiException.Conflict("classe-inativa",
                    "Não é possível criar tipo em classe desativada.");
        }
        String code = in.code().trim().toUpperCase();
        if (types.existsByTenantIdAndClassIdAndCode(tenantId, in.classId(), code)) {
            throw new ApiException.Conflict("tipo-duplicado",
                    "Já existe um tipo com este code nesta classe.");
        }
        GisFeatureType t = new GisFeatureType();
        t.setTenantId(tenantId);
        t.setTenantSchema(TenantContextHolder.getTenant());
        t.setClassId(in.classId());
        t.setCode(code);
        t.setLabel(in.label().trim());
        t.setAttributesSchema(validSchema(in.attributesSchema()));
        t.setRequiresCatalogItem(Boolean.TRUE.equals(in.requiresCatalogItem()));
        t.setOrigin("TENANT");
        t.setActive(false);
        t.setApprovalStatus("PENDING");
        t.setProposedBy(author);
        t.setCreatedBy(author);
        t.setUpdatedBy(author);
        return view(types.saveAndFlush(t), clazz.getCode());
    }

    @Transactional
    public TypeView approveType(UUID id) {
        GisFeatureType t = approve(types.findByIdAndTenantId(id, GisCatalogContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Tipo de catálogo não encontrado.")));
        return view(t, classCodeOf(t));
    }

    @Transactional
    public TypeView rejectType(UUID id) {
        GisFeatureType t = types.findByIdAndTenantId(id, GisCatalogContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Tipo de catálogo não encontrado."));
        if (!"PENDING".equals(t.getApprovalStatus())) {
            throw new ApiException.Conflict("tipo-nao-pendente",
                    "Só é possível recusar um tipo pendente de aprovação.");
        }
        t.setApprovalStatus("REJECTED");
        t.setActive(false);
        t.setApprovedBy(null);
        t.setApprovedAt(null);
        t.setUpdatedBy(GisCatalogContext.userId());
        return view(types.saveAndFlush(t), classCodeOf(t));
    }

    @Transactional
    public TypeView setTypeActive(UUID id, ActiveInput in) {
        GisFeatureType t = types.findByIdAndTenantId(id, GisCatalogContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Tipo de catálogo não encontrado."));
        if (!"APPROVED".equals(t.getApprovalStatus())) {
            throw new ApiException.Conflict("tipo-nao-aprovado",
                    "Só é possível ativar ou desativar um tipo aprovado.");
        }
        t.setActive(in.active());
        t.setUpdatedBy(GisCatalogContext.userId());
        return view(types.saveAndFlush(t), classCodeOf(t));
    }

    // ------------------------------------------------------------------ itens

    @Transactional
    public ItemView createItem(ItemInput in) {
        UUID tenantId = GisCatalogContext.tenantId();
        UUID author = GisCatalogContext.userId();
        GisFeatureType type = types.findByIdAndTenantId(in.featureTypeId(), tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Tipo de catálogo não encontrado."));
        if (!"APPROVED".equals(type.getApprovalStatus()) || !type.isActive()) {
            throw new ApiException.Conflict("tipo-nao-usavel",
                    "Item só pode ser criado em tipo aprovado e ativo.");
        }
        if (in.materialId() != null) {
            materials.findByIdAndTenantId(in.materialId(), tenantId)
                    .orElseThrow(() -> new ApiException.NotFound("Material não encontrado."));
        }
        if (items.existsDuplicate(tenantId, in.featureTypeId(), in.materialId(), in.dnMm(), in.dn2Mm(),
                in.angleDeg(), trim(in.pressureClass()), trim(in.jointType()), trim(in.sectionShape()),
                in.geom1(), in.geom2())) {
            throw new ApiException.Conflict("item-duplicado",
                    "Já existe um item igual para este tipo (mesmo material, DN, ângulo, classe e seção).");
        }
        GisCatalogItem item = new GisCatalogItem();
        item.setTenantId(tenantId);
        item.setTenantSchema(TenantContextHolder.getTenant());
        item.setFeatureTypeId(in.featureTypeId());
        item.setMaterialId(in.materialId());
        item.setDnMm(in.dnMm());
        item.setDn2Mm(in.dn2Mm());
        item.setAngleDeg(in.angleDeg());
        item.setPressureClass(trim(in.pressureClass()));
        item.setStiffnessClass(trim(in.stiffnessClass()));
        item.setJointType(trim(in.jointType()));
        item.setInternalDiameterMm(in.internalDiameterMm());
        item.setExternalDiameterMm(in.externalDiameterMm());
        item.setSdr(in.sdr());
        item.setSectionShape(trim(in.sectionShape()));
        item.setGeom1(in.geom1());
        item.setGeom2(in.geom2());
        item.setGeom3(in.geom3());
        item.setGeom4(in.geom4());
        item.setManufacturer(trim(in.manufacturer()));
        item.setModel(trim(in.model()));
        item.setReferenceStatus("TENANT");
        item.setOrigin("TENANT");
        item.setActive(false);
        item.setApprovalStatus("PENDING");
        item.setProposedBy(author);
        item.setCreatedBy(author);
        item.setUpdatedBy(author);
        return view(items.saveAndFlush(item), type.getCode(), materialCodeOf(item));
    }

    @Transactional
    public ItemView approveItem(UUID id) {
        GisCatalogItem item = approve(items.findByIdAndTenantId(id, GisCatalogContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Item de catálogo não encontrado.")));
        return view(item, typeCodeOf(item), materialCodeOf(item));
    }

    @Transactional
    public ItemView rejectItem(UUID id) {
        GisCatalogItem item = items.findByIdAndTenantId(id, GisCatalogContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Item de catálogo não encontrado."));
        if (!"PENDING".equals(item.getApprovalStatus())) {
            throw new ApiException.Conflict("item-nao-pendente",
                    "Só é possível recusar um item pendente de aprovação.");
        }
        item.setApprovalStatus("REJECTED");
        item.setActive(false);
        item.setApprovedBy(null);
        item.setApprovedAt(null);
        item.setUpdatedBy(GisCatalogContext.userId());
        return view(items.saveAndFlush(item), typeCodeOf(item), materialCodeOf(item));
    }

    @Transactional
    public ItemView setItemActive(UUID id, ActiveInput in) {
        GisCatalogItem item = items.findByIdAndTenantId(id, GisCatalogContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Item de catálogo não encontrado."));
        if (!"APPROVED".equals(item.getApprovalStatus())) {
            throw new ApiException.Conflict("item-nao-aprovado",
                    "Só é possível ativar ou desativar um item aprovado.");
        }
        item.setActive(in.active());
        item.setUpdatedBy(GisCatalogContext.userId());
        return view(items.saveAndFlush(item), typeCodeOf(item), materialCodeOf(item));
    }

    // ------------------------------------------------------------------ apoio

    private GisFeatureType approve(GisFeatureType t) {
        UUID approver = GisCatalogContext.userId();
        if (!"PENDING".equals(t.getApprovalStatus())) {
            throw new ApiException.Conflict("tipo-nao-pendente",
                    "Só é possível aprovar um tipo pendente de aprovação.");
        }
        if (approver.equals(t.getProposedBy())) {
            throw new ApiException.Forbidden("Quem propôs não pode aprovar o próprio tipo.");
        }
        t.setApprovalStatus("APPROVED");
        t.setApprovedBy(approver);
        t.setApprovedAt(OffsetDateTime.now());
        t.setActive(true);
        t.setUpdatedBy(approver);
        return types.saveAndFlush(t);
    }

    private GisCatalogItem approve(GisCatalogItem item) {
        UUID approver = GisCatalogContext.userId();
        if (!"PENDING".equals(item.getApprovalStatus())) {
            throw new ApiException.Conflict("item-nao-pendente",
                    "Só é possível aprovar um item pendente de aprovação.");
        }
        if (approver.equals(item.getProposedBy())) {
            throw new ApiException.Forbidden("Quem propôs não pode aprovar o próprio item.");
        }
        item.setApprovalStatus("APPROVED");
        item.setApprovedBy(approver);
        item.setApprovedAt(OffsetDateTime.now());
        item.setActive(true);
        item.setUpdatedBy(approver);
        return items.saveAndFlush(item);
    }

    private static String validSchema(String schema) {
        String json = schema == null || schema.isBlank() ? "{}" : schema.trim();
        try {
            if (!Json.readTree(json).isObject()) {
                throw ApiException.Validation.of(new ApiException.FieldError("attributesSchema", "INVALID",
                        "O attributes_schema deve ser um objeto JSON."));
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("attributesSchema", "INVALID",
                    "O attributes_schema não é um JSON válido."));
        }
        return json;
    }

    private String classCodeOf(GisFeatureType t) {
        return classes.findByIdAndTenantId(t.getClassId(), GisCatalogContext.tenantId())
                .map(c -> c.getCode()).orElse("");
    }

    private String typeCodeOf(GisCatalogItem item) {
        return types.findByIdAndTenantId(item.getFeatureTypeId(), GisCatalogContext.tenantId())
                .map(t -> t.getCode()).orElse("");
    }

    private String materialCodeOf(GisCatalogItem item) {
        if (item.getMaterialId() == null) {
            return null;
        }
        return materials.findByIdAndTenantId(item.getMaterialId(), GisCatalogContext.tenantId())
                .map(m -> m.getCode()).orElse(null);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static TypeView view(GisFeatureType t, String classCode) {
        return new TypeView(t.getId(), t.getClassId(), classCode, t.getCode(), t.getLabel(),
                t.getAttributesSchema(), t.getOrigin(), t.getFactoryCode(), t.getFactoryRetiredOn(),
                t.isActive(), t.getApprovalStatus(), t.getProposedBy(), t.getApprovedBy(),
                t.getApprovedAt(), t.isRequiresCatalogItem());
    }

    private static ItemView view(GisCatalogItem i, String typeCode, String materialCode) {
        return new ItemView(i.getId(), i.getFeatureTypeId(), typeCode, i.getMaterialId(), materialCode,
                i.getDnMm(), i.getDn2Mm(), i.getAngleDeg(), i.getPressureClass(), i.getStiffnessClass(),
                i.getJointType(), i.getInternalDiameterMm(), i.getExternalDiameterMm(), i.getSdr(),
                i.getSectionShape(), i.getGeom1(), i.getGeom2(), i.getGeom3(), i.getGeom4(),
                i.getManufacturer(), i.getModel(), i.getStandardRef(), i.getReferenceStatus(),
                i.getOrigin(), i.getFactoryCode(), i.getFactoryRetiredOn(), i.isActive(),
                i.getApprovalStatus(), i.getProposedBy(), i.getApprovedBy(), i.getApprovedAt());
    }
}
