package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.ChangeStatusInput;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.ConnectionCardView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.ConnectionView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.CreateInput;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.CurrentMeterView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.HistoryView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.PropertyAddressView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.RestrictedConnectionView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.ServedUnitView;
import br.com.ziipsan.commercial.registry.domain.Address;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatusHistory;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.domain.ServiceConnection;
import br.com.ziipsan.commercial.registry.domain.ServiceConnectionUnit;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.ConnectionStatusHistoryRepository;
import br.com.ziipsan.commercial.registry.repository.ConnectionStatusRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.commercial.registry.repository.ServiceConnectionRepository;
import br.com.ziipsan.commercial.registry.repository.ServiceConnectionUnitRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ligações de água e esgoto (GIS-05, P2.23): criação com a regra 1:1
 * por imóvel/tipo enquanto ativa, mudança de situação só por OS com
 * histórico na ligação, e a ficha da ligação com controle de
 * visibilidade por {@code gis.view-personal}.
 *
 * <p>A permissão segue o nome do §6.6 do GIS-CADASTRO-TECNICO
 * ({@code gis.view-personal}) no formato recurso:ação do projeto:
 * a autoridade {@code gis:view-personal}, conferida no serviço como o
 * {@code PersonalDataAccessService} confere as suas — sem ela, a ficha
 * devolve só identificador e situação.
 *
 * <p>Hidrômetro vigente e ramais são lidos das tabelas donas
 * ({@code meter_installations}/{@code meters} da metering e
 * {@code gis_connection} do GIS) por consulta nativa no mesmo schema do
 * tenant: a ficha é leitura composta, não uma segunda fonte.
 */
@Service
@Transactional(readOnly = true)
public class ServiceConnectionService {

    /** Autoridade recurso:ação correspondente a gis.view-personal (§6.6). */
    static final String VIEW_PERSONAL_AUTHORITY = "gis:view-personal";

    private static final Set<String> CREATE_SOURCES = Set.of("SERVICE_ORDER", "MANUAL");

    private final ServiceConnectionRepository connections;
    private final ServiceConnectionUnitRepository links;
    private final ConnectionStatusRepository statuses;
    private final ConnectionStatusHistoryRepository history;
    private final ConsumerUnitRepository units;
    private final AddressRepository addresses;

    @PersistenceContext
    private EntityManager em;

    public ServiceConnectionService(ServiceConnectionRepository connections,
                                    ServiceConnectionUnitRepository links,
                                    ConnectionStatusRepository statuses,
                                    ConnectionStatusHistoryRepository history,
                                    ConsumerUnitRepository units,
                                    AddressRepository addresses) {
        this.connections = connections;
        this.links = links;
        this.statuses = statuses;
        this.history = history;
        this.units = units;
        this.addresses = addresses;
    }

    // ------------------------------------------------------------- escrita

    @Transactional
    public ConnectionView create(CreateInput in) {
        UUID tenantId = RegistryContext.tenantId();
        ConnectionStatus.Kind kind = parseKind(in.kind());
        ConsumerUnit unit = requireUnit(in.unitId());
        UUID anchorId = ServiceConnectionRules.propertyAnchorId(unit);
        ConsumerUnit anchor = anchorId.equals(unit.getId()) ? unit : requireUnit(anchorId);
        if (anchor.getDeletedAt() != null) {
            throw new ApiException.Conflict("imovel-excluido",
                    "Imóvel excluído não recebe ligação nova.");
        }
        ConnectionStatus status = requireStatus(in.statusId(), kind);
        String source = in.connectedOnSource() == null || in.connectedOnSource().isBlank()
                ? "MANUAL" : in.connectedOnSource().trim().toUpperCase();
        if (!CREATE_SOURCES.contains(source)) {
            throw new ApiException.BadRequest("fonte-de-connected-on-invalida",
                    "connectedOnSource de ligação nova é SERVICE_ORDER ou MANUAL: " + in.connectedOnSource());
        }
        connections.findByTenantIdAndPropertyUnitIdAndKindAndEndedOnIsNull(tenantId, anchorId, kind)
                .ifPresent(existing -> {
                    throw new ApiException.Conflict("ligacao-ativa-existente",
                            "Já existe ligação não encerrada deste tipo para o imóvel: " + existing.getId());
                });

        ServiceConnection sc = RegistryContext.forCreate(new ServiceConnection());
        sc.setPropertyUnitId(anchorId);
        sc.setKind(kind);
        sc.setCode(in.code() == null || in.code().isBlank() ? null : in.code().trim());
        sc.setStatusId(status.getId());
        sc.setConnectedOn(in.connectedOn());
        sc.setConnectedOnSource(source);
        try {
            connections.saveAndFlush(sc);
        } catch (DataIntegrityViolationException e) {
            // Corrida entre a checagem acima e o índice parcial da V3_14:
            // o banco é a autoridade da regra 1:1.
            throw new ApiException.Conflict("ligacao-ativa-existente",
                    "Já existe ligação não encerrada deste tipo para o imóvel.");
        }

        Set<UUID> served = new LinkedHashSet<>();
        served.add(anchorId);
        served.addAll(unitsOfAnchor(tenantId, anchorId));
        if (in.additionalUnitIds() != null) {
            for (UUID extra : in.additionalUnitIds()) {
                ConsumerUnit extraUnit = requireUnit(extra);
                if (!anchorId.equals(ServiceConnectionRules.propertyAnchorId(extraUnit))) {
                    throw new ApiException.BadRequest("unidade-de-outro-imovel",
                            "Unidade não pertence ao imóvel da ligação: " + extra);
                }
                served.add(extra);
            }
        }
        for (UUID unitId : served) {
            ServiceConnectionUnit link = RegistryContext.forCreate(new ServiceConnectionUnit());
            link.setConnectionId(sc.getId());
            link.setUnitId(unitId);
            links.saveAndFlush(link);
        }
        return view(sc);
    }

    @Transactional
    public ConnectionView changeStatus(UUID id, ChangeStatusInput in) {
        UUID tenantId = RegistryContext.tenantId();
        ServiceConnection sc = requireConnection(id);
        ConnectionStatus to = requireStatus(in.statusId(), sc.getKind());
        if (to.getId().equals(sc.getStatusId())) {
            return view(sc);
        }
        UUID fromStatusId = sc.getStatusId();
        sc.setStatusId(to.getId());
        if (to.getSystemRole() == ConnectionStatus.SystemRole.DISCONNECTED
                || to.getSystemRole() == ConnectionStatus.SystemRole.NO_CONNECTION) {
            if (sc.getDisconnectedOn() == null) {
                sc.setDisconnectedOn(LocalDate.now());
            }
        }
        RegistryContext.touch(sc);
        connections.saveAndFlush(sc);
        recordHistory(sc, fromStatusId, to.getId(), in.reason(), in.serviceOrderId(),
                in.parameterVersionIds());
        syncLegacyProjection(sc, to);
        return view(sc);
    }

    /** Encerramento: libera o imóvel para nova ligação do mesmo tipo. */
    @Transactional
    public ConnectionView end(UUID id, UUID serviceOrderId, String reason) {
        ServiceConnection sc = requireConnection(id);
        if (sc.getEndedOn() != null) {
            return view(sc);
        }
        sc.setEndedOn(LocalDate.now());
        RegistryContext.touch(sc);
        connections.saveAndFlush(sc);
        recordHistory(sc, sc.getStatusId(), sc.getStatusId(), reason, serviceOrderId, null);
        return view(sc);
    }

    // -------------------------------------------------------------- leitura

    public ConnectionView get(UUID id) {
        return view(requireConnection(id));
    }

    public List<ConnectionView> listByProperty(UUID propertyUnitId) {
        UUID tenantId = RegistryContext.tenantId();
        requireUnit(propertyUnitId);
        return connections.findByTenantIdAndPropertyUnitIdOrderByKind(tenantId, propertyUnitId)
                .stream().map(this::view).toList();
    }

    public List<HistoryView> history(UUID id) {
        UUID tenantId = RegistryContext.tenantId();
        ServiceConnection sc = requireConnection(id);
        return history.findByTenantIdAndConnectionIdOrderByChangedAtDesc(tenantId, sc.getId())
                .stream().map(h -> new HistoryView(
                        h.getId(), h.getFromStatusId(), h.getToStatusId(),
                        statusCode(h.getFromStatusId()), statusCode(h.getToStatusId()),
                        h.getChangedAt(), h.getReason(), h.getServiceOrderId(),
                        h.getParameterVersionIds() == null
                                ? null : readIds(h.getParameterVersionIds())))
                .toList();
    }

    /**
     * Ficha da ligação. Sem {@code gis.view-personal}, devolve a forma
     * restrita (só identificador e situação) — o chamador escolhe pelo
     * tipo devolvido; aqui o serviço decide a partir das autoridades do
     * token, nunca de parâmetro da requisição.
     */
    public Object card(UUID id) {
        ServiceConnection sc = requireConnection(id);
        if (!canViewPersonal()) {
            ConnectionStatus status = statusOrNull(sc.getStatusId());
            return new RestrictedConnectionView(sc.getId(), sc.getKind().name(),
                    status == null ? null : status.getCode(),
                    status == null ? null : status.getName(),
                    status == null ? null : status.getSystemRole().name());
        }
        UUID tenantId = RegistryContext.tenantId();
        ConsumerUnit anchor = requireUnit(sc.getPropertyUnitId());
        PropertyAddressView addressView = null;
        if (anchor.getAddressId() != null) {
            Address a = addresses.findById(anchor.getAddressId()).orElse(null);
            if (a != null) {
                Point p = a.getCoordinates() != null ? a.getCoordinates() : anchor.getCoordinates();
                addressView = new PropertyAddressView(a.getId(), a.getStreet(), a.getNumber(),
                        a.getComplement(), a.getNeighborhood(), a.getCity(), a.getState(),
                        a.getCep(), a.getMunicipalityIbge(), a.getGeocodePrecision(),
                        p == null ? null : p.getX(), p == null ? null : p.getY());
            }
        }
        List<ServedUnitView> served = new ArrayList<>();
        List<CurrentMeterView> meters = new ArrayList<>();
        for (ServiceConnectionUnit link : links.findByTenantIdAndConnectionIdOrderByUnitId(tenantId, sc.getId())) {
            ConsumerUnit u = units.findById(link.getUnitId()).orElse(null);
            if (u == null) {
                continue;
            }
            served.add(new ServedUnitView(u.getId(), u.getRegistrationNumber(), u.getCheckDigit(),
                    u.getId().equals(anchor.getId())));
            meters.addAll(currentMeters(tenantId, u.getId(), sc.getKind()));
        }
        return new ConnectionCardView(view(sc), anchor.getId(), anchor.getRegistrationNumber(),
                addressView, served, meters, serviceLineIds(tenantId, sc.getId()));
    }

    static boolean canViewPersonal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> VIEW_PERSONAL_AUTHORITY.equals(a.getAuthority()));
    }

    // ------------------------------------------------------------ internos

    private void recordHistory(ServiceConnection sc, UUID fromStatusId, UUID toStatusId,
                               String reason, UUID serviceOrderId, List<UUID> parameterVersionIds) {
        if (serviceOrderId == null) {
            throw new ApiException.BadRequest("os-obrigatoria",
                    "Mudança de situação da ligação exige serviceOrderId (ADR-0039).");
        }
        ConnectionStatusHistory h = RegistryContext.forCreate(new ConnectionStatusHistory());
        h.setConnectionId(sc.getId());
        h.setConsumerUnitId(sc.getPropertyUnitId());
        h.setKind(sc.getKind());
        h.setFromStatusId(fromStatusId);
        h.setToStatusId(toStatusId);
        h.setChangedAt(OffsetDateTime.now());
        h.setReason(reason);
        h.setServiceOrderId(serviceOrderId);
        h.setParameterVersionIds(parameterVersionIds == null || parameterVersionIds.isEmpty()
                ? null : Json.write(parameterVersionIds.stream().map(UUID::toString).toList()));
        history.saveAndFlush(h);
    }

    /**
     * Projeção legada da situação nas unidades atendidas
     * (consumer_units.water_status_id / sewer_status_id): as ids do
     * catálogo único são AS MESMAS dos catálogos antigos (a V3_14 copiou
     * preservando o id), então a projeção é direta. Ela existe até a
     * ED-13 trocar a leitura do ciclo para a ligação (§3.11) — a fonte
     * da escrita é a ligação; a projeção nunca é lida por este serviço.
     */
    private void syncLegacyProjection(ServiceConnection sc, ConnectionStatus to) {
        UUID tenantId = RegistryContext.tenantId();
        for (ServiceConnectionUnit link : links.findByTenantIdAndConnectionIdOrderByUnitId(tenantId, sc.getId())) {
            units.findById(link.getUnitId()).ifPresent(u -> {
                if (sc.getKind() == ConnectionStatus.Kind.WATER) {
                    u.setWaterStatusId(to.getId());
                } else {
                    u.setSewerStatusId(to.getId());
                }
                RegistryContext.touch(u);
                units.saveAndFlush(u);
            });
        }
    }

    private List<UUID> unitsOfAnchor(UUID tenantId, UUID anchorId) {
        // Filhas de condomínio: parent_unit_id = âncora com a relação MACRO.
        return em.createQuery(
                        "select u.id from ConsumerUnit u where u.tenantId = :t "
                                + "and u.parentUnitId = :a and u.parentRelationType = 'CONDOMINIUM_MACRO'",
                        UUID.class)
                .setParameter("t", tenantId)
                .setParameter("a", anchorId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<CurrentMeterView> currentMeters(UUID tenantId, UUID unitId, ConnectionStatus.Kind kind) {
        if (kind != ConnectionStatus.Kind.WATER) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                SELECT mi.unit_id, mi.meter_id, m.meter_number, m.brand, m.model, mi.installed_on
                FROM meter_installations mi
                JOIN meters m ON m.id = mi.meter_id AND m.tenant_id = mi.tenant_id
                WHERE mi.tenant_id = :t AND mi.unit_id = :u AND mi.kind = 'WATER'
                  AND mi.removed_on IS NULL
                ORDER BY mi.installed_on DESC
                """)
                .setParameter("t", tenantId)
                .setParameter("u", unitId)
                .getResultList();
        return rows.stream().map(r -> new CurrentMeterView(
                (UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], (String) r[4],
                toLocalDate(r[5]))).toList();
    }

    /** DATE por query nativa chega como java.sql.Date ou LocalDate, conforme o provedor. */
    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new IllegalStateException("installed_on inesperado: " + value.getClass().getName());
    }

    private List<UUID> serviceLineIds(UUID tenantId, UUID connectionId) {
        return em.createNativeQuery("""
                        SELECT id FROM gis_connection
                        WHERE tenant_id = :t AND service_connection_id = :c
                          AND lifecycle_status <> 'ABANDONED'
                        ORDER BY created_at
                        """, UUID.class)
                .setParameter("t", tenantId)
                .setParameter("c", connectionId)
                .getResultList();
    }

    private ConnectionView view(ServiceConnection sc) {
        UUID tenantId = RegistryContext.tenantId();
        ConnectionStatus status = statusOrNull(sc.getStatusId());
        List<UUID> unitIds = links.findByTenantIdAndConnectionIdOrderByUnitId(tenantId, sc.getId())
                .stream().map(ServiceConnectionUnit::getUnitId).toList();
        return new ConnectionView(sc.getId(), sc.getPropertyUnitId(), sc.getKind().name(),
                sc.getCode(), sc.getStatusId(),
                status == null ? null : status.getCode(),
                status == null ? null : status.getName(),
                status == null ? null : status.getSystemRole().name(),
                sc.getConnectedOn(), sc.getConnectedOnSource(), sc.getDisconnectedOn(),
                sc.getEndedOn(), unitIds, sc.getVersion());
    }

    private ServiceConnection requireConnection(UUID id) {
        return RegistryContext.require(connections.findById(id),
                () -> "Ligação não encontrada.");
    }

    private ConsumerUnit requireUnit(UUID id) {
        return RegistryContext.require(units.findById(id),
                () -> "Unidade consumidora não encontrada.");
    }

    private ConnectionStatus requireStatus(UUID statusId, ConnectionStatus.Kind kind) {
        ConnectionStatus status = RegistryContext.require(statuses.findById(statusId),
                () -> "Situação da ligação não encontrada.");
        if (status.getKind() != kind) {
            throw new ApiException.BadRequest("situacao-de-outro-tipo",
                    "Situação " + status.getCode() + " é de " + status.getKind()
                            + ", não de " + kind + ".");
        }
        if (!status.isActive()) {
            throw new ApiException.BadRequest("situacao-desativada",
                    "A situação da ligação está desativada.");
        }
        return status;
    }

    private ConnectionStatus statusOrNull(UUID statusId) {
        return statusId == null ? null : statuses.findById(statusId).orElse(null);
    }

    private String statusCode(UUID statusId) {
        ConnectionStatus s = statusOrNull(statusId);
        return s == null ? null : s.getCode();
    }

    private static ConnectionStatus.Kind parseKind(String kind) {
        try {
            return ConnectionStatus.Kind.valueOf(kind == null ? "" : kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("tipo-de-ligacao-invalido",
                    "Tipo de ligação desconhecido: " + kind);
        }
    }

    private static List<UUID> readIds(String json) {
        List<UUID> out = new ArrayList<>();
        Json.readTree(json).forEach(node -> out.add(UUID.fromString(node.asString())));
        return out;
    }
}
