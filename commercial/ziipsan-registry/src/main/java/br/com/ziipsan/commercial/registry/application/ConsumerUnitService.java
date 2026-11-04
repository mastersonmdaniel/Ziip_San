package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.CoordinatesView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.ExternalIdsView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.HistoryChange;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.PhysicalView;
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
import br.com.ziipsan.commercial.registry.domain.UnitDueDay;
import br.com.ziipsan.commercial.registry.domain.UnitStatusHistory;
import br.com.ziipsan.commercial.registry.domain.UnitSubcategory;
import br.com.ziipsan.commercial.registry.domain.UnitSubcategoryActivity;
import br.com.ziipsan.commercial.registry.domain.WaterStatus;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.commercial.registry.repository.UnitCustomerRepository;
import br.com.ziipsan.commercial.registry.repository.UnitDueDayRepository;
import br.com.ziipsan.commercial.registry.repository.UnitStatusHistoryRepository;
import br.com.ziipsan.commercial.registry.repository.UnitSubcategoryActivityRepository;
import br.com.ziipsan.commercial.registry.repository.UnitSubcategoryRepository;
import br.com.ziipsan.commercial.registry.rules.Inscription;
import br.com.ziipsan.commercial.registry.rules.Matricula;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.commercial.registry.service.PersonalDataAccessService;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.audit.AuditLog;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.core.json.Json;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            "registration", List.of("length(u.registrationNumber)", "u.registrationNumber"),
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
    private final UnitSubcategoryActivityRepository unitSubcategoryActivities;
    private final UnitStatusHistoryRepository statusHistory;
    private final AuditLogRepository auditLog;
    private final CustomerDocumentService documents;
    private final RegistrySettingsService settings;
    private final PersonalDataAccessService accessLog;
    /** P1.3: nulos no construtor antigo (testes legados) — sem eles, catálogo/OS/dia de vencimento são pulados. */
    private final CatalogLookup catalogs;
    private final InternalOrderService orders;
    private final CadastralChangeService changes;
    private final UnitDueDayRepository dueDays;

    /** SIRGAS 2000 (EPSG:4674), o SRID das colunas de coordenadas (V1_1). x = longitude, y = latitude. */
    private static final GeometryFactory GEOMETRY_4674 = new GeometryFactory(new PrecisionModel(), 4674);

    public ConsumerUnitService(ConsumerUnitRepository units, AddressRepository addresses,
                               UnitCustomerRepository unitCustomers, UnitSubcategoryRepository unitSubcategories,
                               UnitSubcategoryActivityRepository unitSubcategoryActivities,
                               UnitStatusHistoryRepository statusHistory, AuditLogRepository auditLog,
                               CustomerDocumentService documents, RegistrySettingsService settings,
                               PersonalDataAccessService accessLog) {
        this(units, addresses, unitCustomers, unitSubcategories, unitSubcategoryActivities, statusHistory, auditLog,
                documents, settings, accessLog, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ConsumerUnitService(ConsumerUnitRepository units, AddressRepository addresses,
                               UnitCustomerRepository unitCustomers, UnitSubcategoryRepository unitSubcategories,
                               UnitSubcategoryActivityRepository unitSubcategoryActivities,
                               UnitStatusHistoryRepository statusHistory, AuditLogRepository auditLog,
                               CustomerDocumentService documents, RegistrySettingsService settings,
                               PersonalDataAccessService accessLog, CatalogLookup catalogs,
                               InternalOrderService orders, CadastralChangeService changes,
                               UnitDueDayRepository dueDays) {
        this.units = units;
        this.addresses = addresses;
        this.unitCustomers = unitCustomers;
        this.unitSubcategories = unitSubcategories;
        this.unitSubcategoryActivities = unitSubcategoryActivities;
        this.statusHistory = statusHistory;
        this.auditLog = auditLog;
        this.documents = documents;
        this.settings = settings;
        this.accessLog = accessLog;
        this.catalogs = catalogs;
        this.orders = orders;
        this.changes = changes;
        this.dueDays = dueDays;
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
        Optional<String> documentHash = freeText(query, blankToNull(f.q()));
        PageResult<ConsumerUnit> page = query.page(em, ConsumerUnit.class, spec, spec.orderBy(SORT, "inscription", "u.id"));
        documentHash.ifPresent(hash -> accessLog.recordDocumentSearch(hash, !page.content().isEmpty()));
        return page.map(this::summary);
    }

    /**
     * Texto livre: inscrição formatada (com pontos), CPF/CNPJ válido do titular (índice cego), matrícula
     * (dígitos, com ou sem DV) ou parte do nome de um titular vigente. Devolve o índice cego
     * quando o texto era um documento válido (para o registro da busca); vazio nos demais casos.
     */
    private Optional<String> freeText(JpqlQuery query, String text) {
        if (text == null) {
            return Optional.empty();
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
            return Optional.empty();
        }
        Optional<String> hash = documents.searchIndex(text);
        if (hash.isPresent()) {
            // P0.11, ADR-0008: busca por documento do titular exige a permissão própria e
            // conta na cota; sem ela, 403. O resultado é registrado depois da consulta.
            accessLog.authorizeDocumentSearch(hash.get());
            query.where("exists (select 1 from UnitCustomer uc join Customer c on c.id = uc.customerId "
                    + "where uc.unitId = u.id and uc.endDate is null and c.documentHash = :hash)", "hash", hash.get());
            return hash;
        } else if (text.matches("\\d{1,18}(-\\d)?")) {
            String[] parts = text.split("-");
            query.where("u.registrationNumber = :matricula", "matricula", parts[0]);
            if (parts.length > 1) {
                query.where("u.checkDigit = :dv", "dv", parts[1]);
            }
        } else {
            query.where("exists (select 1 from UnitCustomer uc join Customer c on c.id = uc.customerId "
                    + "where uc.unitId = u.id and uc.endDate is null and lower(c.name) like :name" + LIKE_ESCAPE + ")",
                    "name", contains(text));
        }
        return Optional.empty();
    }

    public UnitView get(UUID id) {
        return view(unitForRead(id));
    }

    public List<UnitCustomerLink> customers(UUID id) {
        ConsumerUnit u = unitForRead(id);
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
        ConsumerUnit u = unitForRead(id);
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

        Map<String, UUID> orderIds = openWriteOrders(in, null);
        ConsumerUnit u = RegistryContext.forCreate(new ConsumerUnit());
        u.setId(UUID.randomUUID());
        String matricula = nextMatricula();
        u.setRegistrationNumber(matricula);
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

        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.WATER, null, u.getWaterStatusId(), "Cadastro do imóvel",
                orderIds.get("UNIT_PROFILE_UPDATE"));
        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.SEWER, null, u.getSewerStatusId(), "Cadastro do imóvel",
                orderIds.get("UNIT_PROFILE_UPDATE"));
        syncSubcategories(u, in.subcategories());
        syncCustomers(u, in.customers(), orderIds.get("OWNERSHIP_TRANSFER"), in.linkEndReasonId(),
                in.deliveryModeId());
        syncDueDay(u, in.dueDay(), orderIds.get("ALTERNATIVE_DUE_DAY"));
        recordUnitWrite(u, unitFieldDiffs(in, null, null), orderIds);
        closeWriteOrders(orderIds);
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
        Map<String, UUID> orderIds = openWriteOrders(in, u);

        Address a = u.getAddressId() == null ? null : addresses.findById(u.getAddressId()).orElse(null);
        Address beforeAddress = a == null ? null : copyAddress(a);
        ConsumerUnit beforeUnit = copyForDiff(u);
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

        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.WATER, waterBefore, u.getWaterStatusId(),
                "Alteração do cadastro", orderIds.get("UNIT_PROFILE_UPDATE"));
        recordStatus(u.getId(), UnitStatusHistory.ServiceKind.SEWER, sewerBefore, u.getSewerStatusId(),
                "Alteração do cadastro", orderIds.get("UNIT_PROFILE_UPDATE"));
        syncSubcategories(u, in.subcategories());
        syncCustomers(u, in.customers(), orderIds.get("OWNERSHIP_TRANSFER"), in.linkEndReasonId(),
                in.deliveryModeId());
        syncDueDay(u, in.dueDay(), orderIds.get("ALTERNATIVE_DUE_DAY"));
        recordUnitWrite(u, unitFieldDiffs(in, beforeUnit, beforeAddress), orderIds);
        closeWriteOrders(orderIds);
        return view(u);
    }

    /** Exclusão lógica: libera a inscrição (índice parcial) e encerra os vínculos vigentes. */
    @Transactional
    public void delete(UUID id, String reason) {
        delete(id, reason, null);
    }

    /**
     * Exclusão lógica com o motivo de fim dos vínculos por catálogo (CHECK da V3_12:
     * vínculo com data de fim exige end_reason_id). Sem ele, o fechamento é recusado
     * com 400 nomeando o campo, em vez de um erro opaco do banco.
     */
    @Transactional
    public void delete(UUID id, String reason, UUID linkEndReasonId) {
        ConsumerUnit u = unit(id);
        if (u.getDeletedAt() != null) {
            throw new ApiException.Conflict("record-already-deleted", "O imóvel já está excluído.");
        }
        List<UnitCustomer> vigente =
                unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(RegistryContext.tenantId(), id);
        if (!vigente.isEmpty() && linkEndReasonId == null
                && vigente.stream().allMatch(l -> l.getEndReasonId() == null)) {
            throw new ApiException.BadRequest("linkEndReasonId",
                    "Informe o motivo de fim dos vínculos por catálogo (linkEndReasonId).");
        }
        u.setDeletedAt(OffsetDateTime.now());
        u.setDeletedReason(reason.trim());
        RegistryContext.touch(u);
        units.saveAndFlush(u);
        LocalDate today = LocalDate.now();
        for (UnitCustomer link : vigente) {
            link.setEndDate(today.isBefore(link.getStartDate()) ? link.getStartDate() : today);
            link.setEndReason("Imóvel excluído.");
            if (link.getEndReasonId() == null) {
                link.setEndReasonId(linkEndReasonId);
            }
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
                                    + " já pertence à matrícula " + other.getRegistrationNumber() + "-" + other.getCheckDigit() + "."));
                }
            }
        } else if (in.lot() != null || in.sublot() != null) {
            checks.error("blockId", "REQUIRED", "Lote e sublote só existem dentro de uma quadra.");
        }

        UUID routeId = in.readingRouteId() != null ? in.readingRouteId() : block == null ? null : block.getReadingRouteId();
        if (in.readingRouteId() != null && find(ReadingRoute.class, in.readingRouteId()) == null) {
            checks.error("readingRouteId", "NOT_FOUND", "Rota de leitura não encontrada.");
        }
        // P0.16: estas tres referencias tem tabela e FK (V3_5), entao a existencia e
        // checada como a da rota de leitura. As demais referencias novas (face de
        // quadra, modo de envio, esgotamento, perfil, fonte, poco, pavimentos) nao
        // tem catalogo no backend e sao aceitas como UUID, sem checagem, ate o
        // catalogo existir; inventar um NOT_FOUND sem tabela para consultar seria
        // recusar ids validos do frontend.
        if (in.deliveryRouteId() != null && find(ReadingRoute.class, in.deliveryRouteId()) == null) {
            checks.error("deliveryRouteId", "NOT_FOUND", "Rota de entrega não encontrada.");
        }
        if (in.altRouteId() != null && find(ReadingRoute.class, in.altRouteId()) == null) {
            checks.error("altRouteId", "NOT_FOUND", "Rota alternativa não encontrada.");
        }
        if (in.parentUnitId() != null && find(ConsumerUnit.class, in.parentUnitId()) == null) {
            checks.error("parentUnitId", "NOT_FOUND", "Imóvel principal não encontrado.");
        }
        checkR2Unit(checks, in, current, config);
        // Coordenada incompleta (so lat ou so lng): o par e atomico no frontend
        // (textToCoordinates so emite o par completo ou nulo). Faixa geografica ja
        // vem do bean validation de CoordinatesInput; a caixa do Brasil nao e
        // imposta (ver o DTO: limites "A CONFIRMAR com o IBGE").
        if (in.address() != null && in.address().coordinates() != null
                && ((in.address().coordinates().lat() == null) != (in.address().coordinates().lng() == null))) {
            checks.error("address.coordinates", "INCOMPLETE", "Informe latitude e longitude juntas, ou deixe as duas em branco.");
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

    /**
     * Regras R2 da unidade (P1.3, V3_12), com o campo no erro. Sem CatalogLookup
     * (construtor antigo), só as regras sem catálogo valem (relação, ciclo, mesma quadra).
     */
    private void checkR2Unit(Checks checks, UnitInput in, ConsumerUnit current,
                             RegistrySettingsService.Settings config) {
        UUID self = current == null ? null : current.getId();
        // Unidade-mãe: tipo de relação, mesma quadra, sem ciclo.
        if (in.parentUnitId() != null) {
            if (blankToNull(in.parentRelationType()) == null) {
                checks.error("parentRelationType", "REQUIRED", "Informe o tipo de relação com a unidade-mãe.");
            }
            if (in.parentUnitId().equals(self)) {
                checks.error("parentUnitId", "CYCLE", "O imóvel não pode ser a própria unidade-mãe.");
            } else {
                ConsumerUnit parent = find(ConsumerUnit.class, in.parentUnitId());
                if (parent != null) {
                    if (parent.getBlockId() != null && in.blockId() != null
                            && !parent.getBlockId().equals(in.blockId())) {
                        checks.error("parentUnitId", "BLOCK_MISMATCH", "A unidade-mãe precisa estar na mesma quadra.");
                    }
                    if (parent.getBlockId() == null && in.blockId() != null) {
                        checks.error("parentUnitId", "BLOCK_MISMATCH", "A unidade-mãe não tem quadra e a unidade tem.");
                    }
                    UUID walking = parent.getParentUnitId();
                    for (int depth = 0; depth < 100 && walking != null; depth++) {
                        if (walking.equals(self)) {
                            checks.error("parentUnitId", "CYCLE", "Unidade-mãe forma ciclo.");
                            break;
                        }
                        ConsumerUnit next = find(ConsumerUnit.class, walking);
                        walking = next == null ? null : next.getParentUnitId();
                    }
                }
            }
        } else if (blankToNull(in.parentRelationType()) != null) {
            checks.error("parentRelationType", "WITHOUT_PARENT", "Tipo de relação sem unidade-mãe.");
        }
        // Fechamento de vínculos (update): sem motivo por catálogo, o CHECK da V3_12 recusaria.
        if (current != null && in.customers() != null) {
            Set<String> desiredKeys = new HashSet<>();
            in.customers().forEach(c -> desiredKeys.add(c.customerId() + "|" + c.relation()));
            boolean willClose = unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(RegistryContext.tenantId(),
                            current.getId()).stream()
                    .anyMatch(l -> !desiredKeys.contains(l.getCustomerId() + "|" + l.getRole())
                            && l.getEndReasonId() == null);
            if (willClose && in.linkEndReasonId() == null) {
                checks.error("linkEndReasonId", "REQUIRED",
                        "Informe o motivo de fim dos vínculos por catálogo (linkEndReasonId).");
            }
        }
        if (catalogs == null) {
            return;
        }
        checkCatalog(checks, "block_faces", in.blockFaceId(), "blockFaceId", "Face de quadra");
        if (!config.useBlockFace() && in.blockFaceId() != null) {
            checks.error("blockFaceId", "DISABLED", "Face de quadra desligada (registry.use_block_face).");
        }
        checkCatalog(checks, "sewer_discharges", in.sewerDischargeId(), "sewerDischargeId", "Tipo de esgotamento");
        if (in.profileId() != null) {
            CatalogLookup.CatalogRow row = catalogs.find("property_profiles", in.profileId());
            if (row == null) {
                checks.error("profileId", "NOT_FOUND", "Perfil do imóvel não encontrado.");
            } else if (!row.active()) {
                checks.error("profileId", "INACTIVE", "Perfil do imóvel desativado.");
            } else {
                Boolean provisional = catalogs.flag("property_profiles", in.profileId(), "provisional_only");
                if (Boolean.TRUE.equals(provisional) && !"PROVISIONAL".equals(in.status())) {
                    checks.error("profileId", "PROVISIONAL_ONLY", "Perfil provisório só em unidade PROVISIONAL.");
                }
            }
        }
        if (in.iptuMunicipalityIbge() != null
                && catalogs.byCode("ibge_municipalities", in.iptuMunicipalityIbge()) == null) {
            checks.error("iptuMunicipalityIbge", "NOT_FOUND", "Município do IPTU não encontrado no catálogo do IBGE.");
        }
        if (in.address() != null) {
            if (in.address().municipalityIbge() != null
                    && catalogs.byCode("ibge_municipalities", in.address().municipalityIbge()) == null) {
                checks.error("address.municipalityIbge", "NOT_FOUND", "Município do endereço não encontrado no IBGE.");
            }
            checkCatalog(checks, "street_names", in.address().streetId(), "address.streetId", "Logradouro");
            checkCatalog(checks, "neighborhoods", in.address().neighborhoodId(), "address.neighborhoodId", "Bairro");
        }
        for (int i = 0; i < in.customers().size(); i++) {
            UnitCustomerInput link = in.customers().get(i);
            String base = "customers[" + i + "]";
            if (link.roleId() != null) {
                CatalogLookup.CatalogRow row = catalogs.find("relation_roles", link.roleId());
                if (row == null) {
                    checks.error(base + ".roleId", "NOT_FOUND", "Papel do vínculo não encontrado.");
                } else if (!row.active()) {
                    checks.error(base + ".roleId", "INACTIVE", "Papel do vínculo desativado.");
                } else if (!link.relation().name().equals(catalogs.text("relation_roles", link.roleId(), "system_role"))) {
                    checks.error(base + ".roleId", "INCOMPATIBLE", "Papel do catálogo incompatível com o papel do vínculo.");
                }
            } else if (catalogs.roleBySystemRole(link.relation().name()) == null) {
                checks.error(base + ".relation", "NOT_FOUND", "Papel do vínculo sem linha no catálogo relation_roles.");
            }
            if (link.endReasonId() != null) {
                checkCatalog(checks, "unit_link_end_reasons", link.endReasonId(), base + ".endReasonId",
                        "Motivo de fim do vínculo");
            }
            UUID delivery = link.deliveryModeId() != null ? link.deliveryModeId() : in.deliveryModeId();
            if (link.relation() == UnitCustomer.Role.RESPONSIBLE && delivery == null) {
                checks.error(base + ".deliveryModeId", "REQUIRED", "Responsável: informe a forma de entrega.");
            }
        }
        for (int i = 0; i < in.subcategories().size(); i++) {
            SubcategoryInput line = in.subcategories().get(i);
            if (line.validFrom() != null && line.validTo() != null && line.validTo().isBefore(line.validFrom())) {
                checks.error("subcategories[" + i + "].validTo", "INVALID", "Fim da vigência antes do início.");
            }
        }
    }

    private void checkCatalog(Checks checks, String table, UUID id, String field, String label) {
        if (id == null) {
            return;
        }
        CatalogLookup.CatalogRow row = catalogs.find(table, id);
        if (row == null) {
            checks.error(field, "NOT_FOUND", label + " não encontrado.");
        } else if (!row.active()) {
            checks.error(field, "INACTIVE", label + " desativado.");
        }
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
     * Próxima matrícula do tenant, da sequência {@code registry_matricula_seq} do schema do
     * tenant (V3_10, P1.4). nextval nunca bloqueia: dois lotes paralelos de inclusão não
     * esperam um pelo outro (a trava consultiva anterior serializava a geração) e nunca
     * recebem o mesmo valor; o índice único (tenant_id, matricula) continua sendo a
     * garantia final. Ressalva: valor consumido por transação desfeita não é reutilizado,
     * então um lote desfeito pode deixar um salto na numeração (ver V3_10).
     */
    private String nextMatricula() {
        Number next = (Number) em.createNativeQuery("SELECT nextval('registry_matricula_seq')")
                .getSingleResult();
        return Long.toString(next.longValue());
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
        // P1.3: município IBGE, referência, precisão, catálogos de logradouro/bairro.
        a.setMunicipalityIbge(blankToNull(in.municipalityIbge()));
        a.setReferencePoint(blankToNull(in.referencePoint()));
        a.setGeocodePrecision(blankToNull(in.geocodePrecision()));
        a.setStreetId(in.streetId());
        a.setNeighborhoodId(in.neighborhoodId());
        // P0.16: coordenadas aninhadas (o AddressInput nao as tinha; a coluna ja
        // existia desde V1_1). Nulo ou par nulo = sem coordenada; par parcial ja
        // foi recusado em resolve(). x = longitude, y = latitude.
        if (in.coordinates() == null || (in.coordinates().lat() == null && in.coordinates().lng() == null)) {
            a.setCoordinates(null);
        } else {
            a.setCoordinates(GEOMETRY_4674.createPoint(
                    new Coordinate(in.coordinates().lng(), in.coordinates().lat())));
        }
    }

    private static void apply(ConsumerUnit u, UnitInput in, Resolved r) {
        u.setStatus(in.status() == null ? "ACTIVE" : in.status());
        u.setBlockId(in.blockId());
        u.setBlockFaceId(in.blockFaceId());
        u.setLot(in.blockId() == null ? null : in.lot());
        u.setSublot(in.blockId() == null ? null : in.sublot());
        u.setReadingRouteId(r.routeId());
        u.setReadingSequence(in.readingSequence());
        u.setLotFrontageM(in.lotFrontageM());
        u.setDeliveryModeId(in.deliveryModeId());
        u.setWaterStatusId(in.waterStatusId());
        u.setSewerStatusId(in.sewerStatusId());
        u.setSewerDischargeId(in.sewerDischargeId());
        u.setPropertyProfileId(in.profileId());
        u.setWaterSourceId(in.waterSourceId());
        u.setWellTypeId(in.wellTypeId());
        u.setPavementStreetId(in.pavementStreetId());
        u.setPavementSidewalkId(in.pavementSidewalkId());
        // Fisico: nulo no todo limpa as 4 colunas; objeto parcial guarda so o que veio.
        u.setPhysicalGarden(in.physical() == null ? null : in.physical().garden());
        u.setPhysicalLowerReservoirM3(in.physical() == null ? null : in.physical().lowerReservoirM3());
        u.setPhysicalUpperReservoirM3(in.physical() == null ? null : in.physical().upperReservoirM3());
        u.setPhysicalPoolM3(in.physical() == null ? null : in.physical().poolM3());
        u.setUsagePoints(in.usagePoints());
        u.setResidents(in.residents());
        u.setExternalIptu(in.externalIds() == null ? null : blankToNull(in.externalIds().iptu()));
        u.setDeliveryRouteId(in.deliveryRouteId());
        u.setDeliverySequence(in.deliverySequence());
        u.setAltRouteId(in.altRouteId());
        u.setParentUnitId(in.parentUnitId());
        u.setParentRelationType(blankToNull(in.parentRelationType()));
        u.setIptuMunicipalityIbge(blankToNull(in.iptuMunicipalityIbge()));
        u.setCnaeCode(blankToNull(in.cnaeCode()));
        u.setSeasonal(Boolean.TRUE.equals(in.seasonal()));
        u.setDraftId(in.draftId());
        u.setBuiltAreaM2(in.builtAreaM2());
        u.setDueDay(in.dueDay() == null ? null : in.dueDay().shortValue());
        u.setNotes(blankToNull(in.notes()));
    }

    private void recordStatus(UUID unitId, UnitStatusHistory.ServiceKind kind, UUID from, UUID to, String reason,
                              UUID serviceOrderId) {
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
        // P1.3 (V3_12): service_order_id é NOT NULL; sem wiring, segue nulo (caminho legado).
        h.setServiceOrderId(serviceOrderId);
        statusHistory.saveAndFlush(h);
    }

    /** Cópia rasa dos campos comparados no diff da OS. */
    private static ConsumerUnit copyForDiff(ConsumerUnit u) {
        ConsumerUnit copy = new ConsumerUnit();
        copy.setId(u.getId());
        copy.setBlockId(u.getBlockId());
        copy.setLot(u.getLot());
        copy.setSublot(u.getSublot());
        copy.setBuiltAreaM2(u.getBuiltAreaM2());
        copy.setWaterStatusId(u.getWaterStatusId());
        copy.setSewerStatusId(u.getSewerStatusId());
        copy.setPropertyProfileId(u.getPropertyProfileId());
        copy.setDueDay(u.getDueDay());
        return copy;
    }

    private static Address copyAddress(Address a) {
        Address copy = new Address();
        copy.setCep(a.getCep());
        copy.setStreet(a.getStreet());
        copy.setNumber(a.getNumber());
        copy.setComplement(a.getComplement());
        copy.setNeighborhood(a.getNeighborhood());
        copy.setCity(a.getCity());
        copy.setState(a.getState());
        return copy;
    }

    /**
     * OS internas da escrita (P1.3): um OS por tipo distinto entre os campos alterados.
     * Sem wiring, o mapa volta vazio e a escrita segue o caminho legado. As OS são
     * abertas ANTES da gravação (o histórico de situação e os vínculos precisam do
     * id) e encerradas depois, por {@link #closeWriteOrders}.
     */
    private Map<String, UUID> openWriteOrders(UnitInput in, ConsumerUnit before) {
        if (orders == null) {
            return Map.of();
        }
        Map<String, String> fieldToType = new LinkedHashMap<>();
        if (before == null || !java.util.Objects.equals(before.getPropertyProfileId(), in.profileId())) {
            fieldToType.put("profileId", "UNIT_PROFILE_CHANGE");
        }
        if (before == null || !java.util.Objects.equals(before.getBuiltAreaM2(), in.builtAreaM2())
                || !java.util.Objects.equals(before.getBlockId(), in.blockId())
                || !java.util.Objects.equals(before.getLot(), in.blockId() == null ? null : in.lot())
                || !java.util.Objects.equals(before.getSublot(), in.blockId() == null ? null : in.sublot())
                || !java.util.Objects.equals(before.getWaterStatusId(), in.waterStatusId())
                || !java.util.Objects.equals(before.getSewerStatusId(), in.sewerStatusId())) {
            fieldToType.put("physical", "UNIT_PROFILE_UPDATE");
        }
        if (before == null || !java.util.Objects.equals(before.getDueDay(),
                in.dueDay() == null ? null : in.dueDay().shortValue())) {
            fieldToType.put("dueDay", "ALTERNATIVE_DUE_DAY");
        }
        if (in.subcategories() != null) {
            fieldToType.put("subcategories", "ECONOMIES_CATEGORY_CHANGE");
        }
        if (in.customers() != null) {
            fieldToType.put("links", "OWNERSHIP_TRANSFER");
        }
        if (in.address() != null) {
            fieldToType.put("address", "DELIVERY_ADDRESS_CHANGE");
        }
        Map<String, UUID> orderIds = new LinkedHashMap<>();
        for (String type : new LinkedHashSet<>(fieldToType.values())) {
            orderIds.put(type, orders.open(type));
        }
        return orderIds;
    }

    private void closeWriteOrders(Map<String, UUID> orderIds) {
        if (orders == null) {
            return;
        }
        for (UUID orderId : orderIds.values()) {
            orders.executeAndClose(orderId);
        }
    }

    /** Campos alterados (nome → [antes, depois]) para o registro campo a campo. */
    private Map<String, String[]> unitFieldDiffs(UnitInput in, ConsumerUnit before, Address beforeAddress) {
        Map<String, String[]> diffs = new LinkedHashMap<>();
        putUnitField(diffs, "profileId", before, before == null ? null : str(before.getPropertyProfileId()),
                str(in.profileId()));
        putUnitField(diffs, "builtAreaM2", before, before == null ? null : str(before.getBuiltAreaM2()),
                str(in.builtAreaM2()));
        putUnitField(diffs, "blockId", before, before == null ? null : str(before.getBlockId()), str(in.blockId()));
        putUnitField(diffs, "waterStatusId", before, before == null ? null : str(before.getWaterStatusId()),
                str(in.waterStatusId()));
        putUnitField(diffs, "sewerStatusId", before, before == null ? null : str(before.getSewerStatusId()),
                str(in.sewerStatusId()));
        putUnitField(diffs, "dueDay", before, before == null ? null : str(before.getDueDay()), str(in.dueDay()));
        if (before == null || in.subcategories() != null) {
            diffs.put("subcategories", new String[]{null, null});
        }
        if (before == null || in.customers() != null) {
            diffs.put("links", new String[]{null, null});
        }
        if (beforeAddress == null || in.address() == null
                || !java.util.Objects.equals(beforeAddress.getCep(),
                        in.address().cep() == null ? null : in.address().cep().replaceAll("\\D", ""))
                || !java.util.Objects.equals(beforeAddress.getStreet(), in.address().streetName())
                || !java.util.Objects.equals(beforeAddress.getNumber(), in.address().number())) {
            diffs.put("address", new String[]{null, null});
        }
        return diffs;
    }

    private static void putUnitField(Map<String, String[]> diffs, String field, ConsumerUnit before, String oldValue,
                                     String newValue) {
        if (before == null || !java.util.Objects.equals(oldValue, newValue)) {
            diffs.put(field, new String[]{oldValue, newValue});
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Registra cada campo alterado na OS do seu tipo (mapa da P1.31). */
    private void recordUnitWrite(ConsumerUnit u, Map<String, String[]> diffs, Map<String, UUID> orderIds) {
        if (changes == null || orderIds.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String[]> entry : diffs.entrySet()) {
            String type = CadastralChangeService.osTypeFor("CONSUMER_UNIT", entry.getKey());
            if (type == null || !orderIds.containsKey(type)) {
                continue;
            }
            changes.record("CONSUMER_UNIT", u.getId(), entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                    orderIds.get(type));
        }
    }

    /**
     * Dia de vencimento (P1.3): unit_due_days é a única fonte; consumer_units.due_day
     * é o espelho do valor vigente. Vigência nova começa no primeiro dia da
     * competência corrente; a anterior fecha no dia anterior. Sem wiring, nada aqui.
     */
    private void syncDueDay(ConsumerUnit u, Integer dueDay, UUID orderId) {
        if (dueDays == null) {
            return;
        }
        UUID tenantId = RegistryContext.tenantId();
        List<UnitDueDay> rows = dueDays.findByTenantIdAndUnitIdOrderByValidFromDesc(tenantId, u.getId());
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        for (UnitDueDay row : rows) {
            if (row.getValidTo() == null) {
                row.setValidTo(firstOfMonth.minusDays(1));
                RegistryContext.touch(row);
            }
        }
        dueDays.flush();
        if (dueDay != null) {
            UnitDueDay row = RegistryContext.forCreate(new UnitDueDay());
            row.setUnitId(u.getId());
            row.setDueDay(dueDay.shortValue());
            row.setValidFrom(firstOfMonth);
            row.setServiceOrderId(orderId);
            dueDays.saveAndFlush(row);
        }
    }

    /**
     * Subcategorias da unidade: a linha que sai é removida (é composição do imóvel, não um cadastro; a
     * trilha registra o DELETE), a que fica tem as economias atualizadas, a nova é criada.
     */
    private void syncSubcategories(ConsumerUnit u, List<SubcategoryInput> wanted) {
        Map<UUID, SubcategoryInput> desired = new LinkedHashMap<>();
        wanted.forEach(s -> desired.put(s.subcategoryId(), s));
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
        desired.forEach((subId, d) -> {
            UnitSubcategory line = kept.get(subId);
            if (line == null) {
                line = RegistryContext.forCreate(new UnitSubcategory());
                line.setUnitId(u.getId());
                line.setSubcategoryId(subId);
            } else if (line.getEconomies() == d.economies()
                    && java.util.Objects.equals(line.getPrivateUnits(), d.privateUnits())
                    && java.util.Objects.equals(line.getCollectiveUnits(), d.collectiveUnits())) {
                return;
            } else {
                RegistryContext.touch(line);
            }
            line.setEconomies(d.economies());
            line.setPrivateUnits(d.privateUnits());
            line.setCollectiveUnits(d.collectiveUnits());
            // P1.3: vigência da linha; ausente = primeiro dia da competência corrente.
            line.setValidFrom(d.validFrom() != null ? d.validFrom() : LocalDate.now().withDayOfMonth(1));
            line.setValidTo(d.validTo());
            toSave.add(line);
        });
        unitSubcategories.saveAllAndFlush(toSave);
        // Ramos de atividade (P0.16): a lista faz parte da linha e nao tem identidade
        // propria, entao o conjunto e substituido por inteiro quando muda (o frontend
        // os trata como conjunto; ids repetidos na mesma linha colapsam pelo indice
        // unico). Sem mudanca, nada e reescrito, para nao poluir a trilha de auditoria
        // com DELETE+INSERT sem efeito a cada PUT.
        java.util.Set<String> desiredBranches = new java.util.LinkedHashSet<>();
        for (SubcategoryInput d : wanted) {
            if (d.activityBranchIds() == null) {
                continue;
            }
            for (UUID branchId : d.activityBranchIds()) {
                if (branchId != null) {
                    desiredBranches.add(d.subcategoryId() + "|" + branchId);
                }
            }
        }
        List<UnitSubcategoryActivity> existing =
                unitSubcategoryActivities.findByTenantIdAndUnitId(RegistryContext.tenantId(), u.getId());
        java.util.Set<String> currentBranches = new java.util.HashSet<>();
        for (UnitSubcategoryActivity row : existing) {
            currentBranches.add(row.getSubcategoryId() + "|" + row.getActivityBranchId());
        }
        if (!currentBranches.equals(desiredBranches)) {
            unitSubcategoryActivities.deleteAll(existing);
            unitSubcategoryActivities.flush();
            List<UnitSubcategoryActivity> branchesToSave = new ArrayList<>();
            for (String key : desiredBranches) {
                String[] parts = key.split("\\|");
                UnitSubcategoryActivity row = RegistryContext.forCreate(new UnitSubcategoryActivity());
                row.setUnitId(u.getId());
                row.setSubcategoryId(UUID.fromString(parts[0]));
                row.setActivityBranchId(UUID.fromString(parts[1]));
                branchesToSave.add(row);
            }
            unitSubcategoryActivities.saveAllAndFlush(branchesToSave);
        }
    }

    /**
     * Vínculos vigentes: o corpo traz o conjunto desejado (cliente + papel). O que não veio é encerrado
     * hoje; o que veio e não existia é criado. Em duas fases por causa do índice único do nome na conta.
     */
    private void syncCustomers(ConsumerUnit u, List<UnitCustomerInput> wanted, UUID ownershipOrderId,
                               UUID linkEndReasonId, UUID unitDeliveryModeId) {
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
                if (link.getEndReasonId() == null) {
                    link.setEndReasonId(linkEndReasonId);
                }
                link.setClosedByServiceOrderId(ownershipOrderId);
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
                link.setOpenedByServiceOrderId(ownershipOrderId);
            } else {
                RegistryContext.touch(link);
            }
            link.setStartDate(d.startDate());
            link.setShowOnBill(Boolean.TRUE.equals(d.showOnBill()));
            // P1.3: papel por catálogo (espelho em role), motivo de fim e forma de entrega.
            link.setRoleId(resolveRoleId(d));
            if (d.endReasonId() != null) {
                link.setEndReasonId(d.endReasonId());
            }
            link.setDeliveryModeId(d.deliveryModeId() != null ? d.deliveryModeId() : unitDeliveryModeId);
            toSave.add(link);
        });
        unitCustomers.saveAllAndFlush(toSave);
    }

    /** role_id do vínculo: o informado, ou o do system_role (backfill da V3_12); nulo sem catálogo. */
    private UUID resolveRoleId(UnitCustomerInput d) {
        if (d.roleId() != null) {
            return d.roleId();
        }
        if (catalogs == null) {
            return null;
        }
        CatalogLookup.CatalogRow row = catalogs.roleBySystemRole(d.relation().name());
        return row == null ? null : row.id();
    }

    // ------------------------------------------------------------------ montagem das respostas

    ConsumerUnit unit(UUID id) {
        return RegistryContext.require(units.findById(id), () -> "Imóvel não encontrado.");
    }

    /**
     * P1.15: leitura por id (GET, GET /customers, GET /history — todos com
     * registry:read, ou seja, alcançáveis pelo VIEWER). Imóvel excluído é 404,
     * como se não existisse: as listas já o escondem por padrão, mas o GET por id
     * o devolvia. O helper unit(id) NÃO muda: as escritas precisam dele para os
     * próprios erros de "já excluído".
     */
    private ConsumerUnit unitForRead(UUID id) {
        ConsumerUnit u = unit(id);
        if (u.getDeletedAt() != null) {
            throw new ApiException.NotFound("Imóvel não encontrado.");
        }
        return u;
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
        // Ramos por subcategoria (P0.16), numa unica leitura para a unidade.
        Map<UUID, List<UUID>> branchesBySubcategory = new HashMap<>();
        for (UnitSubcategoryActivity row :
                unitSubcategoryActivities.findByTenantIdAndUnitId(RegistryContext.tenantId(), u.getId())) {
            branchesBySubcategory.computeIfAbsent(row.getSubcategoryId(), k -> new ArrayList<>())
                    .add(row.getActivityBranchId());
        }
        List<UnitSubcategory> rows = new ArrayList<>(unitSubcategories.findByTenantIdAndUnitId(RegistryContext.tenantId(), u.getId()));
        for (UnitSubcategory line : rows) {
            Subcategory sub = em.find(Subcategory.class, line.getSubcategoryId());
            Category cat = sub == null ? null : em.find(Category.class, sub.getCategoryId());
            lines.add(new UnitSubcategoryView(line.getSubcategoryId(), CatalogService.ref(sub), CatalogService.ref(cat),
                    line.getEconomies(), line.getPrivateUnits(), line.getCollectiveUnits(),
                    branchesBySubcategory.getOrDefault(line.getSubcategoryId(), List.of())));
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
        if (a == null) {
            return null;
        }
        // P0.16: o ponto sai como (lat, lng); no JTS, x = longitude, y = latitude.
        CoordinatesView coordinates = a.getCoordinates() == null ? null
                : new CoordinatesView(a.getCoordinates().getY(), a.getCoordinates().getX());
        return new AddressView(a.getCep(), a.getStreet(), a.getNumber(), a.getComplement(),
                a.getNeighborhood(), a.getCity(), a.getState(), coordinates,
                a.getMunicipalityIbge(), a.getReferencePoint(), a.getGeocodePrecision());
    }

    /** Ref sem nome para id de catalogo ainda inexistente (P0.16, ver UnitView): so o id. */
    private static Ref pendingRef(UUID id) {
        return id == null ? null : new Ref(id, null, null);
    }

    /** Ref resolvida no catálogo R2 quando o wiring existe; senão o comportamento P0.16 (id, sem nome). */
    private Ref catalogRef(String table, UUID id) {
        if (id == null) {
            return null;
        }
        if (catalogs == null) {
            return pendingRef(id);
        }
        Ref ref = catalogs.ref(table, id);
        return ref.name() == null ? pendingRef(id) : ref;
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
        ReadingRoute deliveryRoute = u.getDeliveryRouteId() == null ? null : em.find(ReadingRoute.class, u.getDeliveryRouteId());
        ReadingRoute altRoute = u.getAltRouteId() == null ? null : em.find(ReadingRoute.class, u.getAltRouteId());
        WaterStatus water = u.getWaterStatusId() == null ? null : em.find(WaterStatus.class, u.getWaterStatusId());
        SewerStatus sewer = u.getSewerStatusId() == null ? null : em.find(SewerStatus.class, u.getSewerStatusId());
        // Fisico: nulo se as 4 colunas forem nulas; senao o objeto, mesmo parcial.
        PhysicalView physical = u.getPhysicalGarden() == null && u.getPhysicalLowerReservoirM3() == null
                && u.getPhysicalUpperReservoirM3() == null && u.getPhysicalPoolM3() == null ? null
                : new PhysicalView(u.getPhysicalGarden(), u.getPhysicalLowerReservoirM3(),
                        u.getPhysicalUpperReservoirM3(), u.getPhysicalPoolM3());
        ExternalIdsView externalIds = u.getExternalIptu() == null ? null : new ExternalIdsView(u.getExternalIptu());
        return new UnitView(u.getId(), u.getVersion(), u.getRegistrationNumber(), u.getCheckDigit(), p.inscription(),
                p.inscriptionText(), u.getStatus(), u.getDeletedAt(), u.getDeletedReason(),
                TerritoryService.ref(p.locality()), TerritoryService.ref(p.sector()), TerritoryService.ref(p.block()),
                catalogRef("block_faces", u.getBlockFaceId()),
                u.getLot(), u.getSublot(), TerritoryService.ref(route), u.getReadingSequence(), u.getLotFrontageM(),
                addressView(a), addressText(a), c.lines(), c.mainCategory(), c.mainSubcategory(), c.economies(),
                u.getBuiltAreaM2(), CatalogService.ref(water), CatalogService.ref(sewer),
                catalogRef("sewer_discharges", u.getSewerDischargeId()),
                catalogRef("property_profiles", u.getPropertyProfileId()), pendingRef(u.getWaterSourceId()),
                pendingRef(u.getWellTypeId()), pendingRef(u.getPavementStreetId()), pendingRef(u.getPavementSidewalkId()),
                physical,
                u.getUsagePoints(), u.getResidents(), externalIds,
                pendingRef(u.getDeliveryModeId()), TerritoryService.ref(deliveryRoute), u.getDeliverySequence(),
                TerritoryService.ref(altRoute), pendingRef(u.getParentUnitId()),
                u.getDueDay() == null ? null : (int) u.getDueDay(), u.getNotes(), u.getDraftId(),
                u.getParentRelationType(), u.getIptuMunicipalityIbge(), u.getCnaeCode(), u.isSeasonal());
    }

    private UnitSummary summary(ConsumerUnit u) {
        Place p = place(u);
        Composition c = composition(u);
        Address a = u.getAddressId() == null ? null : em.find(Address.class, u.getAddressId());
        WaterStatus water = u.getWaterStatusId() == null ? null : em.find(WaterStatus.class, u.getWaterStatusId());
        SewerStatus sewer = u.getSewerStatusId() == null ? null : em.find(SewerStatus.class, u.getSewerStatusId());
        Customer user = currentUser(u);
        return new UnitSummary(u.getId(), u.getRegistrationNumber(), u.getCheckDigit(), p.inscription(), p.inscriptionText(),
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
