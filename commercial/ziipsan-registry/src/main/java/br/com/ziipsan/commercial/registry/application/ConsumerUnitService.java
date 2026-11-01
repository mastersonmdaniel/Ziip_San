package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.HistoryChange;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.HistoryEvent;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.InscriptionView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.SubcategoryInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitCustomerInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitCustomerLink;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitSearch;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitSubcategoryView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitSummary;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitView;
import br.com.ziipsan.commercial.registry.domain.Address;
import br.com.ziipsan.commercial.registry.domain.Block;
import br.com.ziipsan.commercial.registry.domain.Category;
import br.com.ziipsan.commercial.registry.domain.CommercialSector;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.commercial.registry.domain.Locality;
import br.com.ziipsan.commercial.registry.domain.ReadingRoute;
import br.com.ziipsan.commercial.registry.domain.SewerStatus;
import br.com.ziipsan.commercial.registry.domain.Subcategory;
import br.com.ziipsan.commercial.registry.domain.UnitCustomer;
import br.com.ziipsan.commercial.registry.domain.UnitStatusHistory;
import br.com.ziipsan.commercial.registry.domain.UnitSubcategory;
import br.com.ziipsan.commercial.registry.domain.WaterStatus;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.commercial.registry.repository.UnitCustomerRepository;
import br.com.ziipsan.commercial.registry.repository.UnitStatusHistoryRepository;
import br.com.ziipsan.commercial.registry.repository.UnitSubcategoryRepository;
import br.com.ziipsan.commercial.registry.rules.Inscription;
import br.com.ziipsan.commercial.registry.rules.Matricula;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.audit.AuditLog;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.core.json.Json;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.application.JpqlQuery.LIKE_ESCAPE;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.blankToNull;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.contains;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.exists;

/**
 * Imóvel (unidade consumidora): cadastro, busca, vínculos com clientes e histórico.
 *
 * <p>As regras de consistência são as de {@link UnitConsistency} (endereço físico, quadra x rede,
 * usuário vigente com documento, exatamente um nome na conta, economias); esta classe só monta o
 * retrato da unidade e traduz as violações para errors[]/warnings[]. Inscrição repetida e sequência de
 * leitura ocupada também são checadas aqui, antes do índice único do banco, para a resposta citar a
 * matrícula que já usa a inscrição.
 *
 * <p>Gravação em fases com flush: as chaves estrangeiras são colunas UUID sem associação JPA, e o
 * Hibernate (order_inserts) não sabe que o endereço precisa existir antes da unidade, nem que o nome na
 * conta antigo precisa sair antes de o novo entrar (índice único parcial).
 */
@Service
@Transactional(readOnly = true)
public class ConsumerUnitService {

    private static final String FROM = "ConsumerUnit u left join Block b on b.id = u.blockId "
            + "left join CommercialSector s on s.id = b.sectorId left join Locality l on l.id = s.localityId";

    private static final Map<String, List<String>> SORT = Map.of(
            "inscription", List.of("l.code", "s.code", "b.code", "u.lot", "u.sublot"),
            "registration", List.of("length(u.matricula)", "u.matricula"),
            "createdAt", List.of("u.createdAt"));

    /** Colunas técnicas que não interessam na linha do tempo. */
    private static final Set<String> TECHNICAL = Set.of("id", "tenant_id", "tenant_schema", "version", "created_at",
            "updated_at", "created_by", "updated_by");

    private static final String UNIT_ENTITY_TYPE = "consumer_units";

    @PersistenceContext
    private EntityManager em;

    private final ConsumerUnitRepository units;
    private final AddressRepository addresses;
    private final UnitCustomerRepository unitCustomers;
    private final UnitSubcategoryRepository unitSubcategories;
    private final UnitStatusHistoryRepository statusHistory;
    private final AuditLogRepository auditLog;
    private final CustomerDocumentService documents;
    private final RegistrySettingsService settings;

    public ConsumerUnitService(ConsumerUnitRepository units, AddressRepository addresses,
                               UnitCustomerRepository unitCustomers, UnitSubcategoryRepository unitSubcategories,
                               UnitStatusHistoryRepository statusHistory, AuditLogRepository auditLog,
                               CustomerDocumentService documents, RegistrySettingsService settings) {
        this.units = units;
        this.addresses = addresses;
        this.unitCustomers = unitCustomers;
        this.unitSubcategories = unitSubcategories;
        this.statusHistory = statusHistory;
        this.auditLog = auditLog;
        this.documents = documents;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ leitura

    /** Lista por GET: sem busca livre (pode conter nome ou documento do titular, que não vão na URL). */
    public PageResult<UnitSummary> list(String q, UnitSearch filters) {
        if (blankToNull(q) != null) {
            throw new ApiException.BadRequest("search-in-url",
                    "A busca de imóveis por texto vai no corpo de POST /api/registry/consumer-units/search, nunca na URL.");
        }
        return search(filters);
    }

    public PageResult<UnitSummary> search(UnitSearch f) {
        if (f.profileId() != null || blankToNull(f.numberFrom()) != null || blankToNull(f.numberTo()) != null) {
            throw new ApiException.BadRequest("filter-unavailable",
                    "Os filtros profileId, numberFrom e numberTo ainda não têm suporte no cadastro.");
        }
        PageSpec spec = PageSpec.of(f.page(), f.size(), f.sort());
        JpqlQuery query = new JpqlQuery("u", FROM)
                .where("u.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("s.localityId = :locality", "locality", f.localityId())
                .whereIfPresent("b.sectorId = :sector", "sector", f.sectorId())
                .whereIfPresent("u.blockId = :block", "block", f.blockId())
                .whereIfPresent("u.waterStatusId = :water", "water", f.waterStatusId())
                .whereIfPresent("u.sewerStatusId = :sewer", "sewer", f.sewerStatusId())
                .whereIfPresent("u.readingRouteId = :route", "route", f.routeId())
                .whereIfPresent("exists (select 1 from UnitSubcategory us join Subcategory sc on sc.id = us.subcategoryId "
                        + "where us.unitId = u.id and sc.categoryId = :category)", "category", f.categoryId());
        if (!Boolean.TRUE.equals(f.includeDeleted())) {
            query.where("u.deletedAt is null");
        }
        String cep = blankToNull(f.cep());
        if (cep != null) {
            query.where("exists (select 1 from Address a where a.id = u.addressId and a.cep = :cep)",
                    "cep", cep.replaceAll("\\D", ""));
        }
        freeText(query, blankToNull(f.q()));
        PageResult<ConsumerUnit> page = query.page(em, ConsumerUnit.class, spec, spec.orderBy(SORT, "inscription", "u.id"));
        return page.map(this::summary);
    }

    /**
     * Texto livre: inscrição formatada (com pontos), CPF/CNPJ válido do titular (índice cego), matrícula
     * (dígitos, com ou sem DV) ou parte do nome de um titular vigente.
     */
    private void freeText(JpqlQuery query, String text) {
        if (text == null) {
            return;
        }
        if (text.contains(".") && text.matches("[0-9.]+")) {
            Inscription i;
            try {
                i = Inscription.parse(text);
            } catch (IllegalArgumentException e) {
                throw new ApiException.BadRequest("invalid-inscription", "Inscrição no formato LLL.SSS.QQQ.LLLL.SSS.");
            }
            query.where("l.code = :il and s.code = :is and b.code = :ib and u.lot = :lot and u.sublot = :sublot")
                    .param("il", i.locality()).param("is", i.sector()).param("ib", i.block())
                    .param("lot", i.lot()).param("sublot", i.sublot());
            return;
        }
        Optional<String> hash = documents.searchIndex(text);
        if (hash.isPresent()) {
            query.where("exists (select 1 from UnitCustomer uc join Customer c on c.id = uc.customerId "
                    + "where uc.unitId = u.id and uc.endDate is null and c.documentHash = :hash)", "hash", hash.get());
        } else if (text.matches("\\d{1,18}(-\\d)?")) {
            String[] parts = text.split("-");
            query.where("u.matricula = :matricula", "matricula", parts[0]);
            if (parts.length > 1) {
                query.where("u.checkDigit = :dv", "dv", parts[1]);
            }
        } else {
            query.where("exists (select 1 from UnitCustomer uc join Customer c on c.id = uc.customerId "
                    + "where uc.unitId = u.id and uc.endDate is null and lower(c.name) like :name" + LIKE_ESCAPE + ")",
                    "name", contains(text));
        }
    }

    public UnitView get(UUID id) {
        return view(unit(id));
    }

    public List<UnitCustomerLink> customers(UUID id) {
        ConsumerUnit u = unit(id);
        return unitCustomers.findByTenantIdAndUnitIdOrderByStartDateDesc(RegistryContext.tenantId(), u.getId()).stream()
                .map(link -> {
                    Customer c = em.find(Customer.class, link.getCustomerId());
                    return new UnitCustomerLink(link.getId(), link.getCustomerId(), c == null ? null : c.getName(),
                            c == null ? null : c.getDocumentMasked(), link.getRole(), link.getStartDate(),
                            link.getEndDate(), link.getEndReason(), link.isShowOnBill());
                })
                .toList();
    }

    /**
     * Linha do tempo: mudanças de situação (unit_status_history) e a trilha de auditoria da unidade
     * campo a campo. A trilha já vem redigida pelo banco (ex.: observações aparecem como [redacted]).
     */
    public List<HistoryEvent> history(UUID id) {
        ConsumerUnit u = unit(id);
        UUID tenantId = RegistryContext.tenantId();
        List<HistoryEvent> events = new ArrayList<>();
        for (UnitStatusHistory h : statusHistory.findByTenantIdAndUnitIdOrderByChangedAtDesc(tenantId, u.getId())) {
            boolean water = h.getServiceKind() == UnitStatusHistory.ServiceKind.WATER;
            String from = statusName(water, h.getFromStatusId());
            String to = statusName(water, h.getToStatusId());
            events.add(new HistoryEvent(h.getId(), h.getChangedAt(), "STATUS",
                    water ? "Situação de água alterada" : "Situação de esgoto alterada",
                    h.getCreatedBy() == null ? null : h.getCreatedBy().toString(),
                    List.of(new HistoryChange(water ? "waterStatus" : "sewerStatus", from, to))));
        }
        for (AuditLog a : auditLog.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId, UNIT_ENTITY_TYPE,
                u.getId())) {
            List<HistoryChange> changes = new ArrayList<>();
            if (a.getAction() == AuditLog.AuditAction.UPDATE) {
                Map<String, Object> before = Json.readMap(a.getOldValues());
                Map<String, Object> after = Json.readMap(a.getNewValues());
                for (String key : Json.readMap(a.getChangedFields()).keySet()) {
                    if (!TECHNICAL.contains(key)) {
                        changes.add(new HistoryChange(key, text(before.get(key)), text(after.get(key))));
                    }
                }
                if (changes.isEmpty()) {
                    continue;
                }
            }
            String title = switch (a.getAction()) {
                case CREATE -> "Imóvel cadastrado";
                case DELETE -> "Imóvel removido";
                default -> "Cadastro alterado";
            };
            events.add(new HistoryEvent(a.getId(), a.getCreatedAt(), "AUDIT", title,
                    a.getCreatedBy() == null ? null : a.getCreatedBy().toString(), changes));
        }
        events.sort(Comparator.comparing(HistoryEvent::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    // ------------------------------------------------------------------ gravação

    @Transactional
    public UnitView create(UnitInput in) {
        Checks checks = new Checks(in.confirmTokens());
        RegistrySettingsService.Settings config = settings.current();
        Resolved r = resolve(checks, in, null, config);
        consistency(checks, in, r, config);
        checks.throwIfAny();

        ConsumerUnit u = RegistryContext.forCreate(new ConsumerUnit());
        u.setId(UUID.randomUUID());
        String matricula = nextMatricula();
        u.setMatricula(matricula);
        u.setCheckDigit(Integer.toString(Matricula.checkDigit(matricula)));

        Address a = RegistryContext.forCreate(new Address());
        a.setEntityType(UNIT_ENTITY_TYPE);
        a.setEntityId(u.getId());
        a.setType(Address.Type.PHYSICAL);
        a.setPrimary(true);
        apply(a, in.address());
        addresses.saveAndFlush(a);

        u.setAddressId(a.getId());
        apply(u, in, r);
        units.saveAndFlush(u);

        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.WATER, null, u.getWaterStatusId(), "Cadastro do imóvel");
        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.SEWER, null, u.getSewerStatusId(), "Cadastro do imóvel");
        syncSubcategories(u, in.subcategories());
        syncCustomers(u, in.customers());
        return view(u);
    }

    @Transactional
    public UnitView update(UUID id, UnitInput in) {
        ConsumerUnit u = unit(id);
        if (u.getDeletedAt() != null) {
            throw new ApiException.Conflict("record-deleted", "Imóvel excluído não pode ser alterado.");
        }
        RegistryContext.checkVersion(u, in.version());
        Checks checks = new Checks(in.confirmTokens());
        RegistrySettingsService.Settings config = settings.current();
        Resolved r = resolve(checks, in, u, config);
        consistency(checks, in, r, config);
        checks.throwIfAny();

        Address a = u.getAddressId() == null ? null : addresses.findById(u.getAddressId()).orElse(null);
        if (a == null) {
            a = RegistryContext.forCreate(new Address());
            a.setEntityType(UNIT_ENTITY_TYPE);
            a.setEntityId(u.getId());
            a.setType(Address.Type.PHYSICAL);
            a.setPrimary(true);
        } else {
            RegistryContext.touch(a);
        }
        apply(a, in.address());
        addresses.saveAndFlush(a);

        UUID waterBefore = u.getWaterStatusId();
        UUID sewerBefore = u.getSewerStatusId();
        u.setAddressId(a.getId());
        apply(u, in, r);
        RegistryContext.touch(u);
        // Toda gravação gera versão nova, inclusive quando só os vínculos ou as subcategorias mudam: a
        // versão do imóvel protege o conjunto, e não só a linha de consumer_units.
        u.setUpdatedAt(OffsetDateTime.now());
        units.saveAndFlush(u);

        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.WATER, waterBefore, u.getWaterStatusId(), "Alteração do cadastro");
        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.SEWER, sewerBefore, u.getSewerStatusId(), "Alteração do cadastro");
        syncSubcategories(u, in.subcategories());
        syncCustomers(u, in.customers());
        return view(u);
    }

    /** Exclusão lógica: libera a inscrição (índice parcial) e encerra os vínculos vigentes. */
    @Transactional
    public void delete(UUID id, String reason) {
        ConsumerUnit u = unit(id);
        if (u.getDeletedAt() != null) {
            throw new ApiException.Conflict("record-already-deleted", "O imóvel já está excluído.");
        }
        u.setDeletedAt(OffsetDateTime.now());
        u.setDeletedReason(reason.trim());
        RegistryContext.touch(u);
        units.saveAndFlush(u);
        LocalDate today = LocalDate.now();
        for (UnitCustomer link : unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(RegistryContext.tenantId(), id)) {
            link.setEndDate(today.isBefore(link.getStartDate()) ? link.getStartDate() : today);
            link.setEndReason("Imóvel excluído.");
            RegistryContext.touch(link);
        }
        unitCustomers.flush();
    }

    // ------------------------------------------------------------------ validação

    /** Referências do corpo já carregadas (e do tenant). Campos nulos: não informados ou não encontrados. */
    private record Resolved(Block block, CommercialSector sector, Locality locality, UUID routeId,
                            WaterStatus water, SewerStatus sewer, Map<UUID, Customer> customers) {
    }

    private Resolved resolve(Checks checks, UnitInput in, ConsumerUnit current, RegistrySettingsService.Settings config) {
        UUID self = current == null ? null : current.getId();
        Block block = null;
        CommercialSector sector = null;
        Locality locality = null;
        if (in.blockId() != null) {
            block = find(Block.class, in.blockId());
            if (block == null) {
                checks.error("blockId", "NOT_FOUND", "Quadra não encontrada.");
            } else {
                sector = em.find(CommercialSector.class, block.getSectorId());
                locality = em.find(Locality.class, sector.getLocalityId());
                if (in.sectorId() != null && !in.sectorId().equals(sector.getId())) {
                    checks.error("sectorId", "BLOCK_OUTSIDE_SECTOR", "A quadra não pertence ao setor informado.");
                }
                if (in.localityId() != null && !in.localityId().equals(locality.getId())) {
                    checks.error("localityId", "BLOCK_OUTSIDE_LOCALITY", "A quadra não pertence à localidade informada.");
                }
                boolean blockChanged = current == null || !block.getId().equals(current.getBlockId());
                if (blockChanged && (block.isBlocked() || sector.isBlocked() || locality.isBlocked())) {
                    checks.error("blockId", "BLOCK_BLOCKED",
                            "A quadra, o setor ou a localidade está bloqueada para novos imóveis.");
                }
                if (in.lot() == null) {
                    checks.error("lot", "REQUIRED", "Informe o lote.");
                }
                if (in.sublot() == null) {
                    checks.error("sublot", "REQUIRED", "Informe o sublote.");
                }
                if (in.lot() != null && in.sublot() != null) {
                    String inscription = new Inscription(locality.getCode(), sector.getCode(), block.getCode(),
                            in.lot(), in.sublot()).format();
                    units.findByTenantIdAndBlockIdAndLotAndSublotAndDeletedAtIsNull(
                                    RegistryContext.tenantId(), block.getId(), in.lot(), in.sublot())
                            .filter(other -> !other.getId().equals(self))
                            .ifPresent(other -> checks.error("lot", "INSCRIPTION_IN_USE", "A inscrição " + inscription
                                    + " já pertence à matrícula " + other.getMatricula() + "-" + other.getCheckDigit() + "."));
                }
            }
        } else if (in.lot() != null || in.sublot() != null) {
            checks.error("blockId", "REQUIRED", "Lote e sublote só existem dentro de uma quadra.");
        }

        UUID routeId = in.readingRouteId() != null ? in.readingRouteId() : block == null ? null : block.getReadingRouteId();
        if (in.readingRouteId() != null && find(ReadingRoute.class, in.readingRouteId()) == null) {
            checks.error("readingRouteId", "NOT_FOUND", "Rota de leitura não encontrada.");
        }
        if (config.requireReadingSequence() && routeId != null && in.readingSequence() == null) {
            checks.error("readingSequence", "REQUIRED", "Informe a sequência de leitura na rota.");
        }
        if (routeId != null && in.readingSequence() != null && exists(em,
                "select count(u) from ConsumerUnit u where u.tenantId = :t and u.readingRouteId = :route "
                        + "and u.readingSequence = :seq and u.deletedAt is null and u.id <> :self",
                "t", RegistryContext.tenantId(), "route", routeId, "seq", in.readingSequence(),
                "self", self == null ? new UUID(0, 0) : self)) {
            checks.warning("READING_SEQUENCE_IN_USE", "A sequência " + in.readingSequence()
                    + " já é usada por outro imóvel nesta rota. Confirme se está correto.");
        }

        WaterStatus water = null;
        if (in.waterStatusId() != null) {
            water = find(WaterStatus.class, in.waterStatusId());
            if (water == null) {
                checks.error("waterStatusId", "NOT_FOUND", "Situação de água não encontrada.");
            } else if (!water.isActive() && (current == null || !water.getId().equals(current.getWaterStatusId()))) {
                checks.error("waterStatusId", "INACTIVE", "A situação de água está desativada.");
            }
        }
        SewerStatus sewer = null;
        if (in.sewerStatusId() != null) {
            sewer = find(SewerStatus.class, in.sewerStatusId());
            if (sewer == null) {
                checks.error("sewerStatusId", "NOT_FOUND", "Situação de esgoto não encontrada.");
            } else if (!sewer.isActive() && (current == null || !sewer.getId().equals(current.getSewerStatusId()))) {
                checks.error("sewerStatusId", "INACTIVE", "A situação de esgoto está desativada.");
            }
        }

        Set<UUID> currentSubs = current == null ? Set.of() : Set.copyOf(unitSubcategories
                .findByTenantIdAndUnitId(RegistryContext.tenantId(), current.getId()).stream()
                .map(UnitSubcategory::getSubcategoryId).toList());
        for (int i = 0; i < in.subcategories().size(); i++) {
            SubcategoryInput line = in.subcategories().get(i);
            Subcategory sub = find(Subcategory.class, line.subcategoryId());
            if (sub == null) {
                checks.error("subcategories[" + i + "].subcategoryId", "NOT_FOUND", "Subcategoria não encontrada.");
            } else if (!sub.isActive() && !currentSubs.contains(sub.getId())) {
                checks.error("subcategories[" + i + "].subcategoryId", "INACTIVE", "A subcategoria está desativada.");
            }
        }

        Map<UUID, Customer> customers = new HashMap<>();
        for (int i = 0; i < in.customers().size(); i++) {
            UUID customerId = in.customers().get(i).customerId();
            Customer c = find(Customer.class, customerId);
            if (c == null || c.getDeletedAt() != null) {
                checks.error("customers[" + i + "].customerId", "NOT_FOUND", "Cliente não encontrado.");
            } else {
                customers.put(customerId, c);
            }
        }
        return new Resolved(block, sector, locality, routeId, water, sewer, customers);
    }

    /** Regras de {@link UnitConsistency}, com os campos traduzidos para os nomes do corpo. */
    private void consistency(Checks checks, UnitInput in, Resolved r, RegistrySettingsService.Settings config) {
        List<UnitConsistency.Relation> relations = in.customers().stream()
                .map(c -> {
                    Customer customer = r.customers().get(c.customerId());
                    // Cliente não encontrado já é erro; não acusar também "usuário sem documento".
                    boolean hasDocument = customer == null || customer.getDocumentHash() != null;
                    return new UnitConsistency.Relation(c.customerId(), UnitConsistency.Role.valueOf(c.relation().name()),
                            c.startDate(), null, Boolean.TRUE.equals(c.showOnBill()), hasDocument);
                })
                .toList();
        List<UnitConsistency.SubcategoryLine> lines = in.subcategories().stream()
                .map(s -> new UnitConsistency.SubcategoryLine(s.subcategoryId(), s.economies()))
                .toList();
        UnitConsistency.UnitSnapshot snapshot = new UnitConsistency.UnitSnapshot(
                "PROVISIONAL".equals(in.status()),
                in.address() == null ? 0 : 1,
                r.block() == null ? null : new UnitConsistency.BlockNetwork(r.block().isHasWaterNetwork(),
                        r.block().isHasSewerNetwork()),
                r.water() == null ? null : new UnitConsistency.Status(r.water().getCode(), r.water().isRequiresNetwork()),
                r.sewer() == null ? null : new UnitConsistency.Status(r.sewer().getCode(), r.sewer().isRequiresNetwork()),
                relations, lines, LocalDate.now());
        for (UnitConsistency.Violation v : UnitConsistency.check(snapshot, config.policy())) {
            // Quadra não encontrada já foi acusada em blockId; a regra veria "sem quadra".
            if (v.code().equals("UNIT_BLOCK_REQUIRED") && in.blockId() != null) {
                continue;
            }
            String field = v.field().equals("relations") ? "customers" : v.field();
            if (v.severity() == UnitConsistency.Severity.ERROR) {
                checks.error(field, v.code(), v.message());
            } else {
                checks.warning(v.code(), v.message());
            }
        }
    }

    // ------------------------------------------------------------------ gravação (apoio)

    /**
     * Próxima matrícula do tenant (maior numérica + 1). A trava consultiva da transação serializa a
     * geração por tenant; o índice único (tenant_id, matricula) continua sendo a garantia final.
     */
    private String nextMatricula() {
        UUID tenantId = RegistryContext.tenantId();
        em.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:k))) x")
                .setParameter("k", "registry.matricula." + tenantId)
                .getSingleResult();
        Number max = (Number) em.createNativeQuery("SELECT COALESCE(MAX(CAST(matricula AS bigint)), 0) FROM consumer_units "
                        + "WHERE tenant_id = :t AND matricula ~ '^[0-9]+$' AND length(matricula) <= 18")
                .setParameter("t", tenantId)
                .getSingleResult();
        return Long.toString(max.longValue() + 1);
    }

    private static void apply(Address a, AddressInput in) {
        a.setCep(in.cep() == null ? null : in.cep().replaceAll("\\D", ""));
        a.setStreet(in.streetName().trim());
        a.setNumber(blankToNull(in.number()));
        a.setComplement(blankToNull(in.complement()));
        a.setNeighborhood(blankToNull(in.neighborhood()));
        a.setCity(blankToNull(in.city()));
        a.setState(in.state() == null ? null : in.state().toUpperCase(java.util.Locale.ROOT));
        a.setCountry("BR");
    }

    private static void apply(ConsumerUnit u, UnitInput in, Resolved r) {
        u.setStatus(in.status() == null ? "ACTIVE" : in.status());
        u.setBlockId(in.blockId());
        u.setLot(in.blockId() == null ? null : in.lot());
        u.setSublot(in.blockId() == null ? null : in.sublot());
        u.setReadingRouteId(r.routeId());
        u.setReadingSequence(in.readingSequence());
        u.setWaterStatusId(in.waterStatusId());
        u.setSewerStatusId(in.sewerStatusId());
        u.setBuiltAreaM2(in.builtAreaM2());
        u.setDueDay(in.dueDay() == null ? null : in.dueDay().shortValue());
        u.setNotes(blankToNull(in.notes()));
    }

    private void recordStatus(UUID unitId, UnitStatusHistory.ServiceKind kind, UUID from, UUID to, String reason) {
        if (to == null || to.equals(from)) {
            return;
        }
        UnitStatusHistory h = RegistryContext.forCreate(new UnitStatusHistory());
        h.setUnitId(unitId);
        h.setServiceKind(kind);
        h.setFromStatusId(from);
        h.setToStatusId(to);
        h.setChangedAt(OffsetDateTime.now());
        h.setReason(reason);
        statusHistory.saveAndFlush(h);
    }

    /**
     * Subcategorias da unidade: a linha que sai é removida (é composição do imóvel, não um cadastro; a
     * trilha registra o DELETE), a que fica tem as economias atualizadas, a nova é criada.
     */
    private void syncSubcategories(ConsumerUnit u, List<SubcategoryInput> wanted) {
        Map<UUID, Integer> desired = new LinkedHashMap<>();
        wanted.forEach(s -> desired.put(s.subcategoryId(), s.economies()));
        Map<UUID, UnitSubcategory> kept = new HashMap<>();
        for (UnitSubcategory line : unitSubcategories.findByTenantIdAndUnitId(RegistryContext.tenantId(), u.getId())) {
            if (desired.containsKey(line.getSubcategoryId())) {
                kept.put(line.getSubcategoryId(), line);
            } else {
                unitSubcategories.delete(line);
            }
        }
        unitSubcategories.flush();
        List<UnitSubcategory> toSave = new ArrayList<>();
        desired.forEach((subId, economies) -> {
            UnitSubcategory line = kept.get(subId);
            if (line == null) {
                line = RegistryContext.forCreate(new UnitSubcategory());
                line.setUnitId(u.getId());
                line.setSubcategoryId(subId);
            } else if (line.getEconomies() == economies) {
                return;
            } else {
                RegistryContext.touch(line);
            }
            line.setEconomies(economies);
            toSave.add(line);
        });
        unitSubcategories.saveAllAndFlush(toSave);
    }

    /**
     * Vínculos vigentes: o corpo traz o conjunto desejado (cliente + papel). O que não veio é encerrado
     * hoje; o que veio e não existia é criado. Em duas fases por causa do índice único do nome na conta.
     */
    private void syncCustomers(ConsumerUnit u, List<UnitCustomerInput> wanted) {
        Map<String, UnitCustomerInput> desired = new LinkedHashMap<>();
        wanted.forEach(c -> desired.put(c.customerId() + "|" + c.relation(), c));
        LocalDate today = LocalDate.now();
        Map<String, UnitCustomer> kept = new HashMap<>();
        for (UnitCustomer link : unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(RegistryContext.tenantId(), u.getId())) {
            String key = link.getCustomerId() + "|" + link.getRole();
            UnitCustomerInput d = desired.get(key);
            if (d == null) {
                link.setEndDate(today.isBefore(link.getStartDate()) ? link.getStartDate() : today);
                link.setEndReason("Vínculo encerrado na alteração do cadastro.");
                RegistryContext.touch(link);
            } else {
                kept.put(key, link);
                if (link.isShowOnBill() && !Boolean.TRUE.equals(d.showOnBill())) {
                    link.setShowOnBill(false);
                    RegistryContext.touch(link);
                }
            }
        }
        unitCustomers.flush();
        List<UnitCustomer> toSave = new ArrayList<>();
        desired.forEach((key, d) -> {
            UnitCustomer link = kept.get(key);
            if (link == null) {
                link = RegistryContext.forCreate(new UnitCustomer());
                link.setUnitId(u.getId());
                link.setCustomerId(d.customerId());
                link.setRole(d.relation());
            } else {
                RegistryContext.touch(link);
            }
            link.setStartDate(d.startDate());
            link.setShowOnBill(Boolean.TRUE.equals(d.showOnBill()));
            toSave.add(link);
        });
        unitCustomers.saveAllAndFlush(toSave);
    }

    // ------------------------------------------------------------------ montagem das respostas

    ConsumerUnit unit(UUID id) {
        return RegistryContext.require(units.findById(id), () -> "Imóvel não encontrado.");
    }

    /** Entidade do tenant pelo id, ou nulo (inexistente ou de outro tenant, que a RLS já esconde). */
    private <E extends TenantAwareEntity> E find(Class<E> type, UUID id) {
        E e = em.find(type, id);
        return e != null && RegistryContext.tenantId().equals(e.getTenantId()) ? e : null;
    }

    private record Place(Locality locality, CommercialSector sector, Block block, InscriptionView inscription,
                         String inscriptionText) {
    }

    private Place place(ConsumerUnit u) {
        if (u.getBlockId() == null) {
            return new Place(null, null, null, null, null);
        }
        Block b = em.find(Block.class, u.getBlockId());
        CommercialSector s = em.find(CommercialSector.class, b.getSectorId());
        Locality l = em.find(Locality.class, s.getLocalityId());
        Inscription i = new Inscription(l.getCode(), s.getCode(), b.getCode(), u.getLot(), u.getSublot());
        return new Place(l, s, b, new InscriptionView(i.locality(), i.sector(), i.block(), i.lot(), i.sublot()), i.format());
    }

    private record Composition(List<UnitSubcategoryView> lines, Ref mainCategory, Ref mainSubcategory, int economies) {
    }

    /**
     * Categoria principal: a da subcategoria com mais economias (empate: menor código). Total de
     * economias: soma das linhas. Os dois são derivados; não há coluna para eles (V3_1).
     */
    private Composition composition(ConsumerUnit u) {
        List<UnitSubcategoryView> lines = new ArrayList<>();
        Subcategory main = null;
        int mainEconomies = -1;
        int total = 0;
        List<UnitSubcategory> rows = new ArrayList<>(unitSubcategories.findByTenantIdAndUnitId(RegistryContext.tenantId(), u.getId()));
        for (UnitSubcategory line : rows) {
            Subcategory sub = em.find(Subcategory.class, line.getSubcategoryId());
            Category cat = sub == null ? null : em.find(Category.class, sub.getCategoryId());
            lines.add(new UnitSubcategoryView(line.getSubcategoryId(), CatalogService.ref(sub), CatalogService.ref(cat),
                    line.getEconomies()));
            total += line.getEconomies();
            if (sub != null && (line.getEconomies() > mainEconomies
                    || (line.getEconomies() == mainEconomies && sub.getCode().compareTo(main.getCode()) < 0))) {
                main = sub;
                mainEconomies = line.getEconomies();
            }
        }
        lines.sort(Comparator.comparing(l -> l.subcategory() == null ? "" : String.valueOf(l.subcategory().code())));
        Category mainCat = main == null ? null : em.find(Category.class, main.getCategoryId());
        return new Composition(lines, CatalogService.ref(mainCat), CatalogService.ref(main), total);
    }

    private static AddressView addressView(Address a) {
        return a == null ? null : new AddressView(a.getCep(), a.getStreet(), a.getNumber(), a.getComplement(),
                a.getNeighborhood(), a.getCity(), a.getState());
    }

    private static String addressText(Address a) {
        if (a == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(a.getStreet() == null ? "" : a.getStreet());
        if (a.getNumber() != null) {
            sb.append(", ").append(a.getNumber());
        }
        if (a.getComplement() != null) {
            sb.append(" - ").append(a.getComplement());
        }
        if (a.getNeighborhood() != null) {
            sb.append(" - ").append(a.getNeighborhood());
        }
        if (a.getCity() != null) {
            sb.append(" - ").append(a.getCity());
            if (a.getState() != null) {
                sb.append("/").append(a.getState());
            }
        }
        return sb.toString();
    }

    private UnitView view(ConsumerUnit u) {
        Place p = place(u);
        Composition c = composition(u);
        Address a = u.getAddressId() == null ? null : em.find(Address.class, u.getAddressId());
        ReadingRoute route = u.getReadingRouteId() == null ? null : em.find(ReadingRoute.class, u.getReadingRouteId());
        WaterStatus water = u.getWaterStatusId() == null ? null : em.find(WaterStatus.class, u.getWaterStatusId());
        SewerStatus sewer = u.getSewerStatusId() == null ? null : em.find(SewerStatus.class, u.getSewerStatusId());
        return new UnitView(u.getId(), u.getVersion(), u.getMatricula(), u.getCheckDigit(), p.inscription(),
                p.inscriptionText(), u.getStatus(), u.getDeletedAt(), u.getDeletedReason(),
                TerritoryService.ref(p.locality()), TerritoryService.ref(p.sector()), TerritoryService.ref(p.block()),
                u.getLot(), u.getSublot(), TerritoryService.ref(route), u.getReadingSequence(),
                addressView(a), addressText(a), c.lines(), c.mainCategory(), c.mainSubcategory(), c.economies(),
                u.getBuiltAreaM2(), CatalogService.ref(water), CatalogService.ref(sewer),
                u.getDueDay() == null ? null : (int) u.getDueDay(), u.getNotes());
    }

    private UnitSummary summary(ConsumerUnit u) {
        Place p = place(u);
        Composition c = composition(u);
        Address a = u.getAddressId() == null ? null : em.find(Address.class, u.getAddressId());
        WaterStatus water = u.getWaterStatusId() == null ? null : em.find(WaterStatus.class, u.getWaterStatusId());
        SewerStatus sewer = u.getSewerStatusId() == null ? null : em.find(SewerStatus.class, u.getSewerStatusId());
        Customer user = currentUser(u);
        return new UnitSummary(u.getId(), u.getMatricula(), u.getCheckDigit(), p.inscription(), p.inscriptionText(),
                user == null ? null : user.getName(), user == null ? null : user.getDocumentMasked(), addressText(a),
                c.mainCategory() == null ? null : c.mainCategory().name(), water == null ? null : water.getName(),
                sewer == null ? null : sewer.getName(), c.economies(), u.getStatus(), u.getDeletedAt() != null);
    }

    /** Usuário vigente que vai na lista: o que é nome na conta, se houver; senão o primeiro usuário. */
    private Customer currentUser(ConsumerUnit u) {
        List<UnitCustomer> links = unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(RegistryContext.tenantId(), u.getId());
        return links.stream()
                .filter(l -> l.getRole() == UnitCustomer.Role.USER)
                .sorted(Comparator.comparing((UnitCustomer l) -> !l.isShowOnBill()))
                .findFirst()
                .map(l -> em.find(Customer.class, l.getCustomerId()))
                .orElse(null);
    }

    private String statusName(boolean water, UUID id) {
        if (id == null) {
            return null;
        }
        if (water) {
            WaterStatus w = em.find(WaterStatus.class, id);
            return w == null ? null : w.getName();
        }
        SewerStatus s = em.find(SewerStatus.class, id);
        return s == null ? null : s.getName();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
