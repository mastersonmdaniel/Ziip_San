package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.CatalogDtos.CategoryInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.CategoryView;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SewerStatusInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SewerStatusView;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SubcategoryInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SubcategoryView;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.WaterStatusInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.WaterStatusView;
import br.com.ziipsan.commercial.registry.domain.Category;
import br.com.ziipsan.commercial.registry.domain.SewerStatus;
import br.com.ziipsan.commercial.registry.domain.Subcategory;
import br.com.ziipsan.commercial.registry.domain.WaterStatus;
import br.com.ziipsan.commercial.registry.repository.CategoryRepository;
import br.com.ziipsan.commercial.registry.repository.SewerStatusRepository;
import br.com.ziipsan.commercial.registry.repository.SubcategoryRepository;
import br.com.ziipsan.commercial.registry.repository.WaterStatusRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.application.JpqlQuery.LIKE_ESCAPE;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.blankToNull;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.contains;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.exists;

/**
 * Catálogos do tenant: listar, ler, criar e alterar. Nenhum é semeado (V3_1): os valores e os flags
 * de cada situação são política do prestador. Não há exclusão: um item que sai de uso é desativado
 * ({@code active=false}) e continua resolvendo os imóveis que já o referenciam.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final UUID NONE = new UUID(0, 0);
    private static final Map<String, List<String>> SORT = Map.of("code", List.of("x.code"), "name", List.of("x.name"));

    @PersistenceContext
    private EntityManager em;

    private final CategoryRepository categories;
    private final SubcategoryRepository subcategories;
    private final WaterStatusRepository waterStatuses;
    private final SewerStatusRepository sewerStatuses;

    public CatalogService(CategoryRepository categories, SubcategoryRepository subcategories,
                          WaterStatusRepository waterStatuses, SewerStatusRepository sewerStatuses) {
        this.categories = categories;
        this.subcategories = subcategories;
        this.waterStatuses = waterStatuses;
        this.sewerStatuses = sewerStatuses;
    }

    // ------------------------------------------------------------------ categorias

    public PageResult<CategoryView> listCategories(String q, Boolean active, PageSpec spec) {
        return list("Category", Category.class, q, active, null, spec).map(CatalogService::view);
    }

    public CategoryView getCategory(UUID id) {
        return view(category(id));
    }

    @Transactional
    public CategoryView createCategory(CategoryInput in) {
        checkCode("Category", in.code(), NONE, "categoria");
        Category c = RegistryContext.forCreate(new Category());
        apply(c, in);
        return view(categories.saveAndFlush(c));
    }

    @Transactional
    public CategoryView updateCategory(UUID id, CategoryInput in) {
        Category c = category(id);
        RegistryContext.checkVersion(c, in.version());
        checkCode("Category", in.code(), id, "categoria");
        apply(c, in);
        RegistryContext.touch(c);
        return view(categories.saveAndFlush(c));
    }

    private static void apply(Category c, CategoryInput in) {
        c.setCode(in.code().trim());
        c.setName(in.name().trim());
        c.setPublicEntity(Boolean.TRUE.equals(in.publicEntity()));
        c.setActive(in.active() == null || in.active());
    }

    Category category(UUID id) {
        return RegistryContext.require(categories.findById(id), () -> "Categoria não encontrada.");
    }

    static CategoryView view(Category c) {
        return new CategoryView(c.getId(), c.getVersion(), c.getCode(), c.getName(), c.isPublicEntity(), c.isActive());
    }

    static Ref ref(Category c) {
        return c == null ? null : new Ref(c.getId(), c.getCode(), c.getName());
    }

    // ------------------------------------------------------------------ subcategorias

    public PageResult<SubcategoryView> listSubcategories(String q, UUID categoryId, Boolean active, PageSpec spec) {
        return list("Subcategory", Subcategory.class, q, active, categoryId, spec).map(this::view);
    }

    public SubcategoryView getSubcategory(UUID id) {
        return view(subcategory(id));
    }

    @Transactional
    public SubcategoryView createSubcategory(SubcategoryInput in) {
        Checks checks = new Checks();
        checkCategory(checks, in.categoryId());
        codeInUse(checks, "Subcategory", in.code(), NONE, "subcategoria");
        checks.throwIfAny();
        Subcategory s = RegistryContext.forCreate(new Subcategory());
        apply(s, in);
        return view(subcategories.saveAndFlush(s));
    }

    @Transactional
    public SubcategoryView updateSubcategory(UUID id, SubcategoryInput in) {
        Subcategory s = subcategory(id);
        RegistryContext.checkVersion(s, in.version());
        Checks checks = new Checks();
        checkCategory(checks, in.categoryId());
        codeInUse(checks, "Subcategory", in.code(), id, "subcategoria");
        checks.throwIfAny();
        apply(s, in);
        RegistryContext.touch(s);
        return view(subcategories.saveAndFlush(s));
    }

    private void checkCategory(Checks checks, UUID categoryId) {
        if (categories.findById(categoryId).filter(c -> RegistryContext.tenantId().equals(c.getTenantId())).isEmpty()) {
            checks.error("categoryId", "NOT_FOUND", "Categoria não encontrada.");
        }
    }

    private static void apply(Subcategory s, SubcategoryInput in) {
        s.setCategoryId(in.categoryId());
        s.setCode(in.code().trim());
        s.setName(in.name().trim());
        s.setActive(in.active() == null || in.active());
    }

    Subcategory subcategory(UUID id) {
        return RegistryContext.require(subcategories.findById(id), () -> "Subcategoria não encontrada.");
    }

    private SubcategoryView view(Subcategory s) {
        return new SubcategoryView(s.getId(), s.getVersion(), s.getCategoryId(), ref(em.find(Category.class, s.getCategoryId())),
                s.getCode(), s.getName(), s.isActive());
    }

    static Ref ref(Subcategory s) {
        return s == null ? null : new Ref(s.getId(), s.getCode(), s.getName());
    }

    // ------------------------------------------------------------------ situação de água

    public PageResult<WaterStatusView> listWaterStatuses(String q, Boolean active, PageSpec spec) {
        return list("WaterStatus", WaterStatus.class, q, active, null, spec).map(CatalogService::view);
    }

    public WaterStatusView getWaterStatus(UUID id) {
        return view(waterStatus(id));
    }

    @Transactional
    public WaterStatusView createWaterStatus(WaterStatusInput in) {
        checkCode("WaterStatus", in.code(), NONE, "situação de água");
        WaterStatus w = RegistryContext.forCreate(new WaterStatus());
        apply(w, in);
        return view(waterStatuses.saveAndFlush(w));
    }

    @Transactional
    public WaterStatusView updateWaterStatus(UUID id, WaterStatusInput in) {
        WaterStatus w = waterStatus(id);
        RegistryContext.checkVersion(w, in.version());
        checkCode("WaterStatus", in.code(), id, "situação de água");
        apply(w, in);
        RegistryContext.touch(w);
        return view(waterStatuses.saveAndFlush(w));
    }

    private static void apply(WaterStatus w, WaterStatusInput in) {
        w.setCode(in.code().trim());
        w.setName(in.name().trim());
        w.setBills(Boolean.TRUE.equals(in.billable()));
        w.setRequiresNetwork(Boolean.TRUE.equals(in.requiresNetwork()));
        w.setConnected(Boolean.TRUE.equals(in.connected()));
        w.setSupplies(Boolean.TRUE.equals(in.supplies()));
        w.setMinimumConsumption(Boolean.TRUE.equals(in.minimumConsumption()));
        w.setActive(in.active() == null || in.active());
    }

    WaterStatus waterStatus(UUID id) {
        return RegistryContext.require(waterStatuses.findById(id), () -> "Situação de água não encontrada.");
    }

    static WaterStatusView view(WaterStatus w) {
        return new WaterStatusView(w.getId(), w.getVersion(), w.getCode(), w.getName(), w.isBills(), w.isRequiresNetwork(),
                w.isConnected(), w.isSupplies(), w.isMinimumConsumption(), w.isActive());
    }

    static Ref ref(WaterStatus w) {
        return w == null ? null : new Ref(w.getId(), w.getCode(), w.getName());
    }

    // ------------------------------------------------------------------ situação de esgoto

    public PageResult<SewerStatusView> listSewerStatuses(String q, Boolean active, PageSpec spec) {
        return list("SewerStatus", SewerStatus.class, q, active, null, spec).map(CatalogService::view);
    }

    public SewerStatusView getSewerStatus(UUID id) {
        return view(sewerStatus(id));
    }

    @Transactional
    public SewerStatusView createSewerStatus(SewerStatusInput in) {
        checkCode("SewerStatus", in.code(), NONE, "situação de esgoto");
        SewerStatus s = RegistryContext.forCreate(new SewerStatus());
        apply(s, in);
        return view(sewerStatuses.saveAndFlush(s));
    }

    @Transactional
    public SewerStatusView updateSewerStatus(UUID id, SewerStatusInput in) {
        SewerStatus s = sewerStatus(id);
        RegistryContext.checkVersion(s, in.version());
        checkCode("SewerStatus", in.code(), id, "situação de esgoto");
        apply(s, in);
        RegistryContext.touch(s);
        return view(sewerStatuses.saveAndFlush(s));
    }

    private static void apply(SewerStatus s, SewerStatusInput in) {
        s.setCode(in.code().trim());
        s.setName(in.name().trim());
        s.setBills(Boolean.TRUE.equals(in.billable()));
        s.setRequiresNetwork(Boolean.TRUE.equals(in.requiresNetwork()));
        s.setConnected(Boolean.TRUE.equals(in.connected()));
        s.setMinimumConsumption(Boolean.TRUE.equals(in.minimumConsumption()));
        s.setActive(in.active() == null || in.active());
    }

    SewerStatus sewerStatus(UUID id) {
        return RegistryContext.require(sewerStatuses.findById(id), () -> "Situação de esgoto não encontrada.");
    }

    static SewerStatusView view(SewerStatus s) {
        return new SewerStatusView(s.getId(), s.getVersion(), s.getCode(), s.getName(), s.isBills(), s.isRequiresNetwork(),
                s.isConnected(), s.isMinimumConsumption(), s.isActive());
    }

    static Ref ref(SewerStatus s) {
        return s == null ? null : new Ref(s.getId(), s.getCode(), s.getName());
    }

    // ------------------------------------------------------------------ comum

    /** Lista de catálogo: {@code q} casa com código ou nome (contém, sem caixa). O nome da entidade é fixo. */
    private <T> PageResult<T> list(String entity, Class<T> type, String q, Boolean active, UUID categoryId, PageSpec spec) {
        JpqlQuery query = new JpqlQuery("x", entity + " x")
                .where("x.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("x.active = :active", "active", active)
                .whereIfPresent("x.categoryId = :category", "category", categoryId);
        String text = blankToNull(q);
        if (text != null) {
            query.where("(lower(x.code) like :q" + LIKE_ESCAPE + " or lower(x.name) like :q" + LIKE_ESCAPE + ")",
                    "q", contains(text));
        }
        return query.page(em, type, spec, spec.orderBy(SORT, "code", "x.id"));
    }

    private void checkCode(String entity, String code, UUID self, String label) {
        Checks checks = new Checks();
        codeInUse(checks, entity, code, self, label);
        checks.throwIfAny();
    }

    private void codeInUse(Checks checks, String entity, String code, UUID self, String label) {
        if (exists(em, "select count(x) from " + entity + " x where x.tenantId = :t and x.code = :code and x.id <> :self",
                "t", RegistryContext.tenantId(), "code", code.trim(), "self", self)) {
            checks.error("code", "CODE_IN_USE", "Já existe " + label + " com o código " + code.trim() + ".");
        }
    }
}
