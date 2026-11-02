package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.TerritoryDtos.BlockInput;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.BlockView;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.LocalityInput;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.LocalityView;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.RouteInput;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.RouteView;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.SectorInput;
import br.com.ziipsan.commercial.registry.application.TerritoryDtos.SectorView;
import br.com.ziipsan.commercial.registry.domain.Block;
import br.com.ziipsan.commercial.registry.domain.CommercialSector;
import br.com.ziipsan.commercial.registry.domain.Locality;
import br.com.ziipsan.commercial.registry.domain.NetworkCoverage;
import br.com.ziipsan.commercial.registry.domain.ReadingRoute;
import br.com.ziipsan.commercial.registry.repository.BlockRepository;
import br.com.ziipsan.commercial.registry.repository.CommercialSectorRepository;
import br.com.ziipsan.commercial.registry.repository.LocalityRepository;
import br.com.ziipsan.commercial.registry.repository.ReadingRouteRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.domain.TenantAwareEntity;
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
 * Hierarquia territorial: localidade, setor comercial, quadra e rota de leitura.
 *
 * <p>Nenhuma destas tabelas tem coluna de exclusão lógica (V3_1): localidade, setor e quadra têm
 * {@code blocked} e a rota tem {@code active}. Por isso não há exclusão aqui; o controller responde 405
 * dizendo o que usar no lugar. Inventar uma coluna sem migration quebraria o FreshInstallTest, e excluir
 * de verdade quebraria a inscrição dos imóveis.
 *
 * <p>O pai de um registro (localidade do setor, setor da quadra) não muda depois de criado: mudar
 * levaria junto as quadras e os imóveis, e a inscrição de todos eles mudaria em silêncio.
 *
 * <p>P0.16 (opção 1): os campos que a tela já enviava e o backend descartava em silêncio passam a
 * ser modelados (V3_6), sem mudar as validações existentes e sem tornar obrigatório nada que era
 * opcional: localidade {@code headquarters}/{@code computerized}, setor {@code alternative}, quadra
 * {@code waterNetwork}/{@code sewerNetwork}/{@code censusSector}/{@code autoIncrementLot} e rota
 * {@code localityId}/{@code sectorId}/{@code maxUnits}. A rota é o único caso em que o pai pode ser
 * atribuído depois de criado: rotas anteriores à V3_6 não têm pai, então a primeira atribuição é
 * aceita; depois de atribuído, o pai da rota é imutável como os demais.
 */
@Service
@Transactional(readOnly = true)
public class TerritoryService {

    private static final UUID NONE = new UUID(0, 0);

    private static final Map<String, List<String>> LOCALITY_SORT = Map.of(
            "code", List.of("l.code"), "name", List.of("l.name"));
    private static final Map<String, List<String>> SECTOR_SORT = Map.of(
            "code", List.of("s.code"), "name", List.of("s.name"));
    private static final Map<String, List<String>> BLOCK_SORT = Map.of("code", List.of("b.code"));
    private static final Map<String, List<String>> ROUTE_SORT = Map.of(
            "code", List.of("r.code"), "name", List.of("r.name"));

    @PersistenceContext
    private EntityManager em;

    private final LocalityRepository localities;
    private final CommercialSectorRepository sectors;
    private final BlockRepository blocks;
    private final ReadingRouteRepository routes;

    public TerritoryService(LocalityRepository localities, CommercialSectorRepository sectors,
                            BlockRepository blocks, ReadingRouteRepository routes) {
        this.localities = localities;
        this.sectors = sectors;
        this.blocks = blocks;
        this.routes = routes;
    }

    // ------------------------------------------------------------------ localidades

    public PageResult<LocalityView> listLocalities(String q, Boolean blocked, PageSpec spec) {
        JpqlQuery query = new JpqlQuery("l", "Locality l")
                .where("l.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("l.blocked = :blocked", "blocked", blocked);
        nameOrCode(query, "l", q);
        return query.page(em, Locality.class, spec, spec.orderBy(LOCALITY_SORT, "code", "l.id")).map(TerritoryService::view);
    }

    public LocalityView getLocality(UUID id) {
        return view(locality(id));
    }

    @Transactional
    public LocalityView createLocality(LocalityInput in) {
        Checks checks = new Checks();
        checkLocalityCode(checks, in.code(), NONE);
        checks.throwIfAny();
        Locality l = RegistryContext.forCreate(new Locality());
        apply(l, in);
        return view(localities.saveAndFlush(l));
    }

    @Transactional
    public LocalityView updateLocality(UUID id, LocalityInput in) {
        Locality l = locality(id);
        RegistryContext.checkVersion(l, in.version());
        Checks checks = new Checks();
        checkLocalityCode(checks, in.code(), id);
        checks.throwIfAny();
        apply(l, in);
        RegistryContext.touch(l);
        return view(localities.saveAndFlush(l));
    }

    private void checkLocalityCode(Checks checks, int code, UUID self) {
        if (exists(em, "select count(l) from Locality l where l.tenantId = :t and l.code = :code and l.id <> :self",
                "t", RegistryContext.tenantId(), "code", code, "self", self)) {
            checks.error("code", "CODE_IN_USE", "Já existe localidade com o código " + code + ".");
        }
    }

    private static void apply(Locality l, LocalityInput in) {
        l.setCode(in.code());
        l.setName(in.name().trim());
        l.setMunicipalityIbge(blankToNull(in.municipalityIbge()));
        l.setHeadquarters(Boolean.TRUE.equals(in.headquarters()));
        l.setComputerized(Boolean.TRUE.equals(in.computerized()));
        l.setBlocked(Boolean.TRUE.equals(in.blocked()));
    }

    Locality locality(UUID id) {
        return RegistryContext.require(localities.findById(id), () -> "Localidade não encontrada.");
    }

    static LocalityView view(Locality l) {
        return new LocalityView(l.getId(), l.getVersion(), l.getCode(), l.getName(), l.getMunicipalityIbge(),
                l.isHeadquarters(), l.isComputerized(), l.isBlocked());
    }

    static Ref ref(Locality l) {
        return l == null ? null : new Ref(l.getId(), l.getCode(), l.getName());
    }

    // ------------------------------------------------------------------ setores comerciais

    public PageResult<SectorView> listSectors(String q, UUID localityId, Boolean blocked, PageSpec spec) {
        JpqlQuery query = new JpqlQuery("s", "CommercialSector s")
                .where("s.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("s.localityId = :locality", "locality", localityId)
                .whereIfPresent("s.blocked = :blocked", "blocked", blocked);
        nameOrCode(query, "s", q);
        return query.page(em, CommercialSector.class, spec, spec.orderBy(SECTOR_SORT, "code", "s.id")).map(this::view);
    }

    public SectorView getSector(UUID id) {
        return view(sector(id));
    }

    @Transactional
    public SectorView createSector(SectorInput in) {
        Checks checks = new Checks();
        if (localities.findById(in.localityId()).filter(this::ofTenant).isEmpty()) {
            checks.error("localityId", "NOT_FOUND", "Localidade não encontrada.");
        } else {
            checkSectorCode(checks, in.localityId(), in.code(), NONE);
        }
        checks.throwIfAny();
        CommercialSector s = RegistryContext.forCreate(new CommercialSector());
        s.setLocalityId(in.localityId());
        apply(s, in);
        return view(sectors.saveAndFlush(s));
    }

    @Transactional
    public SectorView updateSector(UUID id, SectorInput in) {
        CommercialSector s = sector(id);
        RegistryContext.checkVersion(s, in.version());
        Checks checks = new Checks();
        if (!s.getLocalityId().equals(in.localityId())) {
            checks.error("localityId", "IMMUTABLE", "A localidade de um setor não muda depois de criado.");
        } else {
            checkSectorCode(checks, s.getLocalityId(), in.code(), id);
        }
        checks.throwIfAny();
        apply(s, in);
        RegistryContext.touch(s);
        return view(sectors.saveAndFlush(s));
    }

    private void checkSectorCode(Checks checks, UUID localityId, int code, UUID self) {
        if (exists(em, "select count(s) from CommercialSector s where s.tenantId = :t and s.localityId = :locality "
                        + "and s.code = :code and s.id <> :self",
                "t", RegistryContext.tenantId(), "locality", localityId, "code", code, "self", self)) {
            checks.error("code", "CODE_IN_USE", "Já existe setor com o código " + code + " nesta localidade.");
        }
    }

    private static void apply(CommercialSector s, SectorInput in) {
        s.setCode(in.code());
        s.setName(in.name().trim());
        s.setAlternative(Boolean.TRUE.equals(in.alternative()));
        s.setBlocked(Boolean.TRUE.equals(in.blocked()));
    }

    CommercialSector sector(UUID id) {
        return RegistryContext.require(sectors.findById(id), () -> "Setor comercial não encontrado.");
    }

    private SectorView view(CommercialSector s) {
        return new SectorView(s.getId(), s.getVersion(), s.getLocalityId(), ref(em.find(Locality.class, s.getLocalityId())),
                s.getCode(), s.getName(), s.isAlternative(), s.isBlocked());
    }

    static Ref ref(CommercialSector s) {
        return s == null ? null : new Ref(s.getId(), s.getCode(), s.getName());
    }

    // ------------------------------------------------------------------ quadras

    public PageResult<BlockView> listBlocks(String q, UUID sectorId, UUID localityId, UUID readingRouteId, Boolean blocked,
                                            PageSpec spec) {
        JpqlQuery query = new JpqlQuery("b", "Block b join CommercialSector s on s.id = b.sectorId")
                .where("b.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("b.sectorId = :sector", "sector", sectorId)
                .whereIfPresent("s.localityId = :locality", "locality", localityId)
                .whereIfPresent("b.readingRouteId = :route", "route", readingRouteId)
                .whereIfPresent("b.blocked = :blocked", "blocked", blocked);
        String text = blankToNull(q);
        if (text != null) {
            if (!text.chars().allMatch(Character::isDigit)) {
                throw new ApiException.BadRequest("invalid-filter",
                        "Quadra não tem nome: a busca é pelo código (só dígitos).");
            }
            query.where("b.code = :code", "code", Integer.parseInt(text));
        }
        return query.page(em, Block.class, spec, spec.orderBy(BLOCK_SORT, "code", "b.id")).map(this::view);
    }

    public BlockView getBlock(UUID id) {
        return view(block(id));
    }

    @Transactional
    public BlockView createBlock(BlockInput in) {
        Checks checks = new Checks();
        if (sectors.findById(in.sectorId()).filter(this::ofTenant).isEmpty()) {
            checks.error("sectorId", "NOT_FOUND", "Setor comercial não encontrado.");
        } else {
            checkBlockCode(checks, in.sectorId(), in.code(), NONE);
        }
        checkRoute(checks, in.readingRouteId());
        checks.throwIfAny();
        Block b = RegistryContext.forCreate(new Block());
        b.setSectorId(in.sectorId());
        apply(b, in);
        return view(blocks.saveAndFlush(b));
    }

    @Transactional
    public BlockView updateBlock(UUID id, BlockInput in) {
        Block b = block(id);
        RegistryContext.checkVersion(b, in.version());
        Checks checks = new Checks();
        if (!b.getSectorId().equals(in.sectorId())) {
            checks.error("sectorId", "IMMUTABLE", "O setor de uma quadra não muda depois de criada.");
        } else {
            checkBlockCode(checks, b.getSectorId(), in.code(), id);
        }
        checkRoute(checks, in.readingRouteId());
        checks.throwIfAny();
        apply(b, in);
        RegistryContext.touch(b);
        return view(blocks.saveAndFlush(b));
    }

    private void checkBlockCode(Checks checks, UUID sectorId, int code, UUID self) {
        if (exists(em, "select count(b) from Block b where b.tenantId = :t and b.sectorId = :sector "
                        + "and b.code = :code and b.id <> :self",
                "t", RegistryContext.tenantId(), "sector", sectorId, "code", code, "self", self)) {
            checks.error("code", "CODE_IN_USE", "Já existe quadra com o código " + code + " neste setor.");
        }
    }

    private void checkRoute(Checks checks, UUID routeId) {
        if (routeId != null && routes.findById(routeId).filter(this::ofTenant).isEmpty()) {
            checks.error("readingRouteId", "NOT_FOUND", "Rota de leitura não encontrada.");
        }
    }

    private static void apply(Block b, BlockInput in) {
        b.setCode(in.code());
        b.setReadingRouteId(in.readingRouteId());
        NetworkCoverage water = network(in.waterNetwork(), in.hasWaterNetwork());
        NetworkCoverage sewer = network(in.sewerNetwork(), in.hasSewerNetwork());
        b.setWaterNetwork(water);
        b.setHasWaterNetwork(water != NetworkCoverage.NONE);
        b.setSewerNetwork(sewer);
        b.setHasSewerNetwork(sewer != NetworkCoverage.NONE);
        b.setCensusSector(blankToNull(in.censusSector()));
        b.setAutoIncrementLot(Boolean.TRUE.equals(in.autoIncrementLot()));
        b.setBlocked(Boolean.TRUE.equals(in.blocked()));
    }

    /**
     * O enum é a fonte; o booleano legado só o alimenta quando o enum não vem. Sem nenhum dos dois
     * (payload legado sem rede), a quadra fica sem rede, como antes: omitir o booleano já zerava o
     * flag. {@code true} sem enum vira {@code FULL} — aproximação documentada em
     * {@link NetworkCoverage}: booleano não distingue parcial de total.
     */
    private static NetworkCoverage network(NetworkCoverage value, Boolean legacy) {
        if (value != null) {
            return value;
        }
        if (legacy != null) {
            return legacy ? NetworkCoverage.FULL : NetworkCoverage.NONE;
        }
        return NetworkCoverage.NONE;
    }

    Block block(UUID id) {
        return RegistryContext.require(blocks.findById(id), () -> "Quadra não encontrada.");
    }

    private BlockView view(Block b) {
        CommercialSector s = em.find(CommercialSector.class, b.getSectorId());
        Locality l = s == null ? null : em.find(Locality.class, s.getLocalityId());
        ReadingRoute r = b.getReadingRouteId() == null ? null : em.find(ReadingRoute.class, b.getReadingRouteId());
        // Linhas anteriores à V3_6 têm o enum preenchido pelo backfill; o fallback só cobre o caso
        // de a coluna estar nula por qualquer outro caminho, para a resposta nunca omitir a rede.
        NetworkCoverage water = b.getWaterNetwork() == null
                ? (b.isHasWaterNetwork() ? NetworkCoverage.FULL : NetworkCoverage.NONE) : b.getWaterNetwork();
        NetworkCoverage sewer = b.getSewerNetwork() == null
                ? (b.isHasSewerNetwork() ? NetworkCoverage.FULL : NetworkCoverage.NONE) : b.getSewerNetwork();
        return new BlockView(b.getId(), b.getVersion(), b.getSectorId(), ref(s), l == null ? null : l.getId(), ref(l),
                b.getCode(), b.getReadingRouteId(), ref(r), b.isHasWaterNetwork(), b.isHasSewerNetwork(),
                water, sewer, b.getCensusSector(), b.isAutoIncrementLot(), b.isBlocked());
    }

    static Ref ref(Block b) {
        return b == null ? null : new Ref(b.getId(), b.getCode(), null);
    }

    // ------------------------------------------------------------------ rotas de leitura

    public PageResult<RouteView> listRoutes(String q, Boolean active, PageSpec spec) {
        JpqlQuery query = new JpqlQuery("r", "ReadingRoute r")
                .where("r.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("r.active = :active", "active", active);
        nameOrCode(query, "r", q);
        return query.page(em, ReadingRoute.class, spec, spec.orderBy(ROUTE_SORT, "code", "r.id")).map(this::view);
    }

    public RouteView getRoute(UUID id) {
        return view(route(id));
    }

    @Transactional
    public RouteView createRoute(RouteInput in) {
        Checks checks = new Checks();
        checkRouteCode(checks, in.code(), NONE);
        checkRouteLocality(checks, in.localityId());
        checkRouteSector(checks, in.sectorId());
        checks.throwIfAny();
        ReadingRoute r = RegistryContext.forCreate(new ReadingRoute());
        apply(r, in);
        return view(routes.saveAndFlush(r));
    }

    @Transactional
    public RouteView updateRoute(UUID id, RouteInput in) {
        ReadingRoute r = route(id);
        RegistryContext.checkVersion(r, in.version());
        Checks checks = new Checks();
        checkRouteCode(checks, in.code(), id);
        // Pai da rota: imutável depois de atribuído; a primeira atribuição (rota pré-V3_6, sem pai)
        // é aceita. Não há validação cruzada localidade × setor: a tela já filtra o setor pela
        // localidade, e nenhum outro pai do serviço faz esse cruzamento.
        if (r.getLocalityId() != null && !r.getLocalityId().equals(in.localityId())) {
            checks.error("localityId", "IMMUTABLE", "A localidade de uma rota não muda depois de criada.");
        } else {
            checkRouteLocality(checks, in.localityId());
        }
        if (r.getSectorId() != null && !r.getSectorId().equals(in.sectorId())) {
            checks.error("sectorId", "IMMUTABLE", "O setor de uma rota não muda depois de criado.");
        } else {
            checkRouteSector(checks, in.sectorId());
        }
        checks.throwIfAny();
        apply(r, in);
        RegistryContext.touch(r);
        return view(routes.saveAndFlush(r));
    }

    private void checkRouteCode(Checks checks, int code, UUID self) {
        if (exists(em, "select count(r) from ReadingRoute r where r.tenantId = :t and r.code = :code and r.id <> :self",
                "t", RegistryContext.tenantId(), "code", code, "self", self)) {
            checks.error("code", "CODE_IN_USE", "Já existe rota com o código " + code + ".");
        }
    }

    private void checkRouteLocality(Checks checks, UUID localityId) {
        if (localityId != null && localities.findById(localityId).filter(this::ofTenant).isEmpty()) {
            checks.error("localityId", "NOT_FOUND", "Localidade não encontrada.");
        }
    }

    private void checkRouteSector(Checks checks, UUID sectorId) {
        if (sectorId != null && sectors.findById(sectorId).filter(this::ofTenant).isEmpty()) {
            checks.error("sectorId", "NOT_FOUND", "Setor comercial não encontrado.");
        }
    }

    private static void apply(ReadingRoute r, RouteInput in) {
        r.setCode(in.code());
        r.setName(in.name().trim());
        r.setLocalityId(in.localityId());
        r.setSectorId(in.sectorId());
        r.setMaxUnits(in.maxUnits());
        r.setActive(in.active() == null || in.active());
    }

    ReadingRoute route(UUID id) {
        return RegistryContext.require(routes.findById(id), () -> "Rota de leitura não encontrada.");
    }

    private RouteView view(ReadingRoute r) {
        Locality l = r.getLocalityId() == null ? null : em.find(Locality.class, r.getLocalityId());
        CommercialSector s = r.getSectorId() == null ? null : em.find(CommercialSector.class, r.getSectorId());
        return new RouteView(r.getId(), r.getVersion(), r.getLocalityId(), ref(l), r.getSectorId(), ref(s),
                r.getCode(), r.getName(), r.getMaxUnits(), r.isActive());
    }

    static Ref ref(ReadingRoute r) {
        return r == null ? null : new Ref(r.getId(), r.getCode(), r.getName());
    }

    // ------------------------------------------------------------------ comum

    private boolean ofTenant(TenantAwareEntity e) {
        return RegistryContext.tenantId().equals(e.getTenantId());
    }

    /** {@code q}: só dígitos busca pelo código exato OU pelo nome; com letras, só pelo nome (contém). */
    private static void nameOrCode(JpqlQuery query, String alias, String q) {
        String text = blankToNull(q);
        if (text == null) {
            return;
        }
        query.param("name", contains(text));
        if (text.length() <= 9 && text.chars().allMatch(Character::isDigit)) {
            query.where("(" + alias + ".code = :code or lower(" + alias + ".name) like :name" + LIKE_ESCAPE + ")")
                    .param("code", Integer.parseInt(text));
        } else {
            query.where("lower(" + alias + ".name) like :name" + LIKE_ESCAPE);
        }
    }
}
