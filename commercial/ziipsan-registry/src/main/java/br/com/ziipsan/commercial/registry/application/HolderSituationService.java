package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.CandidateView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ChangeRequestView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathAlertInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathAlertView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathReviewInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.HolderStatusView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.MergeInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.MergeView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.OccupancyInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.RepresentationInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.RepresentationView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SocialNameInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SplitInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SplitView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.TransferValidationView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationRecordedView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationView;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.commercial.registry.domain.CustomerDocumentValidation;
import br.com.ziipsan.commercial.registry.domain.CustomerMerge;
import br.com.ziipsan.commercial.registry.domain.CustomerRepresentation;
import br.com.ziipsan.commercial.registry.domain.CustomerSplit;
import br.com.ziipsan.commercial.registry.domain.HolderChangeRequest;
import br.com.ziipsan.commercial.registry.domain.HolderDeathAlert;
import br.com.ziipsan.commercial.registry.domain.UnitCustomer;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Situações do titular e validação externa (P1.41, V3_8).
 *
 * <p>Disciplina (a mesma do R2, estendida): TODA escrita passa por OS
 * interna da P1.31, com aprovação por outra pessoa e efeito no
 * encerramento. O fluxo é uniforme em duas etapas:
 * <ol>
 *   <li>pedido: abre a OS (exigindo aprovação), submete (vai a
 *       PENDING_APPROVAL quando o tipo exige) e grava a pendência;</li>
 *   <li>aprovação: outro usuário (≠ criador da OS, conferido aqui e no
 *       motor) aprova, executa e encerra a OS; o efeito é aplicado ANTES
 *       do encerramento, na mesma requisição — se a aplicação falha, a OS
 *       fica aberta e a pendência segue pendente (reaprovável), em vez de
 *       parecer aplicada sem efeito.</li>
 * </ol>
 * A rejeição encerra a OS sem execução (motivo declarado
 * HOLDER_SITUATION_REJECTED) e não muda nada no cadastro. Rejeitar é
 * também a forma de desistir do próprio pedido (sem efeito, sem risco).
 *
 * <p>Tipos de OS (manifesto do pacote, P1.41): HOLDER_DECEASED,
 * REPRESENTATIVE_SET, CUSTOMER_MERGE, CUSTOMER_SPLIT, OCCUPANCY_CHANGE e
 * CUSTOMER_DATA_UPDATE (nome social, pelo mapa campo → OS do R2).
 * Tipos sem aprovação prévia no tenant (declarados por outro caminho) não
 * são redeclarados pelo motor — a regra "outra pessoa" continua valendo
 * aqui, conferida contra o criador da OS.
 *
 * <p>O que este serviço NÃO faz (o plano atribui a outras frentes): bloqueios
 * de corte e de negativação do titular falecido (P3.4, ED-30), cobrança da
 * unidade vaga (P2.5, via bill.inactive_economy_rule), acesso delegado no
 * portal (P3.3) e a consulta externa em si (provedor, cofre, Sirc).
 */
@Service
@Transactional(readOnly = true)
public class HolderSituationService {

    public static final String HOLDER_DECEASED = "HOLDER_DECEASED";
    public static final String REPRESENTATIVE_SET = "REPRESENTATIVE_SET";
    public static final String CUSTOMER_MERGE_TYPE = "CUSTOMER_MERGE";
    public static final String CUSTOMER_SPLIT_TYPE = "CUSTOMER_SPLIT";
    public static final String OCCUPANCY_CHANGE = "OCCUPANCY_CHANGE";
    public static final String CUSTOMER_DATA_UPDATE = "CUSTOMER_DATA_UPDATE";
    public static final String REJECT_REASON = "HOLDER_SITUATION_REJECTED";

    private static final Set<String> REPRESENTATION_TYPES = Set.of(
            CustomerRepresentation.Types.ATTORNEY, CustomerRepresentation.Types.GUARDIAN,
            CustomerRepresentation.Types.CURATOR, CustomerRepresentation.Types.CONDO_MANAGER,
            CustomerRepresentation.Types.ESTATE_ADMINISTRATOR, CustomerRepresentation.Types.LEGAL_REPRESENTATIVE,
            CustomerRepresentation.Types.AUTHORIZED_THIRD_PARTY);
    private static final Set<String> POWERS = Set.of(
            CustomerRepresentation.Powers.VIEW, CustomerRepresentation.Powers.REQUEST,
            CustomerRepresentation.Powers.PAY, CustomerRepresentation.Powers.CHANGE_BILLING,
            CustomerRepresentation.Powers.OWNERSHIP);
    private static final Set<String> PROVISIONAL_KINDS = Set.of("HEIR", "ESTATE_ADMINISTRATOR", "OCCUPANT");
    private static final Set<String> OCCUPANCIES = Set.of("OCCUPIED", "VACANT", "UNKNOWN");

    private final br.com.ziipsan.commercial.registry.repository.CustomerRepository customers;
    private final br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository units;
    private final br.com.ziipsan.commercial.registry.repository.UnitCustomerRepository unitCustomers;
    private final br.com.ziipsan.commercial.registry.repository.CustomerContactRepository contacts;
    private final br.com.ziipsan.commercial.registry.repository.CustomerAddressRepository customerAddresses;
    private final br.com.ziipsan.commercial.registry.repository.CustomerRepresentationRepository representations;
    private final br.com.ziipsan.commercial.registry.repository.HolderDeathAlertRepository deathAlerts;
    private final br.com.ziipsan.commercial.registry.repository.CustomerDocumentValidationRepository validations;
    private final br.com.ziipsan.commercial.registry.repository.CustomerMergeRepository merges;
    private final br.com.ziipsan.commercial.registry.repository.CustomerSplitRepository splits;
    private final br.com.ziipsan.commercial.registry.repository.HolderChangeRequestRepository changeRequests;
    private final InternalOrderService orders;
    private final HolderSettingsService settings;
    private final BillHolderResolver billHolder;
    private final CatalogLookup catalogs;

    @PersistenceContext
    private EntityManager em;

    public HolderSituationService(
            br.com.ziipsan.commercial.registry.repository.CustomerRepository customers,
            br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository units,
            br.com.ziipsan.commercial.registry.repository.UnitCustomerRepository unitCustomers,
            br.com.ziipsan.commercial.registry.repository.CustomerContactRepository contacts,
            br.com.ziipsan.commercial.registry.repository.CustomerAddressRepository customerAddresses,
            br.com.ziipsan.commercial.registry.repository.CustomerRepresentationRepository representations,
            br.com.ziipsan.commercial.registry.repository.HolderDeathAlertRepository deathAlerts,
            br.com.ziipsan.commercial.registry.repository.CustomerDocumentValidationRepository validations,
            br.com.ziipsan.commercial.registry.repository.CustomerMergeRepository merges,
            br.com.ziipsan.commercial.registry.repository.CustomerSplitRepository splits,
            br.com.ziipsan.commercial.registry.repository.HolderChangeRequestRepository changeRequests,
            InternalOrderService orders, HolderSettingsService settings, BillHolderResolver billHolder,
            CatalogLookup catalogs) {
        this.customers = customers;
        this.units = units;
        this.unitCustomers = unitCustomers;
        this.contacts = contacts;
        this.customerAddresses = customerAddresses;
        this.representations = representations;
        this.deathAlerts = deathAlerts;
        this.validations = validations;
        this.merges = merges;
        this.splits = splits;
        this.changeRequests = changeRequests;
        this.orders = orders;
        this.settings = settings;
        this.billHolder = billHolder;
        this.catalogs = catalogs;
    }

    // ------------------------------------------------------------------ nome social

    /**
     * Nome de exibição do cliente numa interação (atendimento, portal,
     * mensagem): nome social quando PF com social_name e o modo não é
     * CIVIL_NAME_ONLY; senão o nome civil. O documento fiscal e os termos
     * sempre usam o civil (o chamador não passa por aqui nesses casos).
     */
    public static String displayName(Customer customer, String socialNameDisplayMode) {
        if (customer.getPersonType() == Customer.PersonType.PF
                && customer.getSocialName() != null && !customer.getSocialName().isBlank()
                && !HolderSettingsService.SocialNameDisplay.CIVIL_NAME_ONLY.equals(socialNameDisplayMode)) {
            return customer.getSocialName();
        }
        return customer.getName();
    }

    // ------------------------------------------------------------------ representações

    /** Representação vigente numa data: ACTIVE e dentro da vigência. Vencida deixa de valer. */
    public static boolean isRepresentationValid(CustomerRepresentation rep, LocalDate today) {
        return "ACTIVE".equals(rep.getStatus())
                && (rep.getValidFrom() == null || !rep.getValidFrom().isAfter(today))
                && (rep.getValidTo() == null || !rep.getValidTo().isBefore(today));
    }

    @Transactional
    public RepresentationView requestRepresentation(RepresentationInput in) {
        Customer represented = activeCustomer(in.representedId());
        Customer representative = activeCustomer(in.representativeId());
        if (in.representedId().equals(in.representativeId())) {
            throw validation("representativeId", "SAME_AS_REPRESENTED",
                    "O representante precisa ser diferente do representado.");
        }
        if (!REPRESENTATION_TYPES.contains(in.type())) {
            throw validation("type", "INVALID", "Tipo de representação inválido.");
        }
        if (in.powers() == null || in.powers().isEmpty() || !POWERS.containsAll(in.powers())) {
            throw validation("powers", "INVALID", "Informe ao menos um poder válido.");
        }
        if (in.unitId() != null) {
            unit(in.unitId());
        }
        if (in.proofDocumentId() == null) {
            throw validation("proofDocumentId", "REQUIRED", "O documento comprobatório é obrigatório.");
        }
        LocalDate from = in.validFrom() == null ? LocalDate.now() : in.validFrom();
        if (in.validTo() != null && in.validTo().isBefore(from)) {
            throw validation("validTo", "INVALID", "A validade final não pode ser anterior à inicial.");
        }
        UUID orderId = orders.openRequiringApproval(REPRESENTATIVE_SET);
        orders.submit(orderId);
        CustomerRepresentation rep = RegistryContext.forCreate(new CustomerRepresentation());
        rep.setRepresentedId(represented.getId());
        rep.setRepresentativeId(representative.getId());
        rep.setType(in.type());
        rep.setPowers(in.powers().toArray(new String[0]));
        rep.setUnitId(in.unitId());
        rep.setValidFrom(from);
        rep.setValidTo(in.validTo());
        rep.setProofDocumentId(in.proofDocumentId());
        rep.setStatus("PENDING_APPROVAL");
        rep.setServiceOrderId(orderId);
        rep = representations.saveAndFlush(rep);
        return representationView(rep);
    }

    @Transactional
    public RepresentationView approveRepresentation(UUID id) {
        CustomerRepresentation rep = representation(id);
        requirePending(rep.getStatus(), "representação");
        UUID approver = actor();
        requireAnotherPerson(orders.getOrder(rep.getServiceOrderId()), approver);
        rep.setStatus("ACTIVE");
        RegistryContext.touch(rep);
        representations.saveAndFlush(rep);
        orders.approveExecuteAndClose(rep.getServiceOrderId(), approver);
        return representationView(rep);
    }

    @Transactional
    public RepresentationView rejectRepresentation(UUID id) {
        CustomerRepresentation rep = representation(id);
        requirePending(rep.getStatus(), "representação");
        orders.closeNotExecuted(rep.getServiceOrderId(), REJECT_REASON, actor());
        rep.setStatus("REJECTED");
        RegistryContext.touch(rep);
        representations.saveAndFlush(rep);
        return representationView(rep);
    }

    public List<RepresentationView> listRepresentations(UUID representedId, UUID representativeId,
                                                        boolean activeOnly) {
        List<CustomerRepresentation> rows;
        if (representedId != null) {
            rows = representations.findByTenantIdAndRepresentedIdOrderByValidFromDesc(
                    RegistryContext.tenantId(), representedId);
        } else if (representativeId != null) {
            rows = representations.findByTenantIdAndRepresentativeIdOrderByValidFromDesc(
                    RegistryContext.tenantId(), representativeId);
        } else {
            throw validation("representedId", "REQUIRED",
                    "Informe o representado ou o representante para listar.");
        }
        LocalDate today = LocalDate.now();
        return rows.stream()
                .filter(r -> !activeOnly || isRepresentationValid(r, today))
                .map(this::representationView)
                .toList();
    }

    private RepresentationView representationView(CustomerRepresentation rep) {
        return new RepresentationView(rep.getId(), rep.getRepresentedId(), rep.getRepresentativeId(),
                rep.getType(), rep.getPowers() == null ? List.of() : List.of(rep.getPowers()),
                rep.getUnitId(), rep.getValidFrom(), rep.getValidTo(), rep.getProofDocumentId(),
                rep.getStatus(), rep.getServiceOrderId(), isRepresentationValid(rep, LocalDate.now()));
    }

    // ------------------------------------------------------------------ óbito

    @Transactional
    public DeathAlertView reportDeathAlert(DeathAlertInput in) {
        Customer customer = activeCustomer(in.customerId());
        if (in.unitId() != null) {
            unit(in.unitId());
        }
        return deathAlertView(openDeathAlert(customer.getId(), in.unitId(),
                HolderDeathAlert.Sources.MANUAL_REPORT));
    }

    @Transactional
    public DeathAlertView reviewDeathAlert(UUID alertId, DeathReviewInput in) {
        HolderDeathAlert alert = RegistryContext.require(deathAlerts.findById(alertId),
                () -> "Alerta de óbito não encontrado.");
        if (!"PENDING_REVIEW".equals(alert.getStatus())) {
            throw new ApiException.Conflict("alert-not-pending",
                    "Só alerta pendente de revisão pode ser revisto.");
        }
        UUID reviewer = actor();
        ServiceOrderService.OrderView order = orders.getOrder(alert.getServiceOrderId());
        if ("REJECT".equals(in.decision())) {
            orders.closeNotExecuted(alert.getServiceOrderId(), REJECT_REASON, reviewer);
            alert.setStatus("REJECTED");
            alert.setReviewedBy(reviewer);
            alert.setReviewedAt(OffsetDateTime.now());
            RegistryContext.touch(alert);
            deathAlerts.saveAndFlush(alert);
            return deathAlertView(alert);
        }
        requireAnotherPerson(order, reviewer);
        if (alert.getUnitId() != null) {
            ConsumerUnit unit = unit(alert.getUnitId());
            if (in.endReasonId() == null) {
                throw validation("endReasonId", "REQUIRED",
                        "Informe o motivo de fim de vínculo por falecimento (catálogo unit_link_end_reasons).");
            }
            requireEndReason(in.endReasonId());
            LocalDate today = LocalDate.now();
            List<UnitCustomer> current = unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(
                    RegistryContext.tenantId(), unit.getId());
            for (UnitCustomer link : current) {
                if (link.getRole() == UnitCustomer.Role.USER) {
                    link.setEndDate(today.isBefore(link.getStartDate()) ? link.getStartDate() : today);
                    link.setEndReason("Titular falecido (sucessão).");
                    link.setEndReasonId(in.endReasonId());
                    link.setClosedByServiceOrderId(alert.getServiceOrderId());
                    RegistryContext.touch(link);
                }
            }
            unitCustomers.saveAllAndFlush(current);
            if (in.provisionalCustomerId() != null) {
                Customer provisional = activeCustomer(in.provisionalCustomerId());
                if (in.provisionalKind() == null || !PROVISIONAL_KINDS.contains(in.provisionalKind())) {
                    throw validation("provisionalKind", "INVALID",
                            "Informe o tipo do responsável provisório (HEIR, ESTATE_ADMINISTRATOR ou OCCUPANT).");
                }
                boolean alreadyLinked = current.stream().anyMatch(l ->
                        l.getCustomerId().equals(provisional.getId())
                                && l.getRole() == UnitCustomer.Role.RESPONSIBLE
                                && l.getEndDate() == null);
                if (alreadyLinked) {
                    throw validation("provisionalCustomerId", "ALREADY_LINKED",
                            "O responsável provisório já tem vínculo vigente com a unidade.");
                }
                UUID deliveryModeId = in.deliveryModeId() != null ? in.deliveryModeId() : unit.getDeliveryModeId();
                if (deliveryModeId == null) {
                    throw validation("deliveryModeId", "REQUIRED",
                            "Responsável exige forma de entrega da conta (informe deliveryModeId ou cadastre-a na unidade).");
                }
                UnitCustomer link = RegistryContext.forCreate(new UnitCustomer());
                link.setUnitId(unit.getId());
                link.setCustomerId(provisional.getId());
                link.setRole(UnitCustomer.Role.RESPONSIBLE);
                link.setRoleId(resolveRoleIdOrFail("RESPONSIBLE"));
                link.setStartDate(today);
                link.setShowOnBill(false);
                link.setProvisional(true);
                link.setProvisionalKind(in.provisionalKind());
                link.setDeliveryModeId(deliveryModeId);
                link.setOpenedByServiceOrderId(alert.getServiceOrderId());
                unitCustomers.saveAndFlush(link);
            }
            // Ocupação após o fechamento dos vínculos (o trigger de VACANT
            // exige que já não haja usuário vigente neste ponto).
            if (in.provisionalCustomerId() == null) {
                unit.setOccupancyStatus("VACANT");
            } else if ("OCCUPANT".equals(in.provisionalKind())) {
                unit.setOccupancyStatus("OCCUPIED");
            } else {
                unit.setOccupancyStatus("UNKNOWN");
            }
            RegistryContext.touch(unit);
            units.saveAndFlush(unit);
        }
        alert.setStatus("CONFIRMED");
        alert.setReviewedBy(reviewer);
        alert.setReviewedAt(OffsetDateTime.now());
        RegistryContext.touch(alert);
        deathAlerts.saveAndFlush(alert);
        orders.approveExecuteAndClose(alert.getServiceOrderId(), reviewer);
        return deathAlertView(alert);
    }

    public List<DeathAlertView> listDeathAlerts(String status, UUID unitId) {
        List<HolderDeathAlert> rows;
        if (unitId != null) {
            rows = deathAlerts.findByTenantIdAndUnitIdOrderByDetectedAtDesc(RegistryContext.tenantId(), unitId);
        } else if (status != null) {
            rows = deathAlerts.findByTenantIdAndStatusOrderByDetectedAtDesc(RegistryContext.tenantId(), status);
        } else {
            rows = deathAlerts.findByTenantIdAndStatusOrderByDetectedAtDesc(
                    RegistryContext.tenantId(), "PENDING_REVIEW");
        }
        return rows.stream().map(this::deathAlertView).toList();
    }

    private HolderDeathAlert openDeathAlert(UUID customerId, UUID unitId, String source) {
        UUID orderId = orders.openRequiringApproval(HOLDER_DECEASED);
        orders.submit(orderId);
        HolderDeathAlert alert = RegistryContext.forCreate(new HolderDeathAlert());
        alert.setCustomerId(customerId);
        alert.setUnitId(unitId);
        alert.setSource(source);
        alert.setStatus("PENDING_REVIEW");
        alert.setServiceOrderId(orderId);
        alert.setDetectedAt(OffsetDateTime.now());
        return deathAlerts.saveAndFlush(alert);
    }

    private DeathAlertView deathAlertView(HolderDeathAlert alert) {
        return new DeathAlertView(alert.getId(), alert.getCustomerId(), alert.getUnitId(), alert.getSource(),
                alert.getStatus(), alert.getServiceOrderId(), alert.getDetectedAt(),
                alert.getReviewedBy(), alert.getReviewedAt());
    }

    // ------------------------------------------------------------------ validação externa

    @Transactional
    public ValidationRecordedView recordDocumentValidation(ValidationInput in) {
        Customer customer = activeCustomer(in.customerId());
        if (!"CPF".equals(in.documentKind()) && !"CNPJ".equals(in.documentKind())) {
            throw validation("documentKind", "INVALID", "Espécie inválida.");
        }
        if (in.result() == null || !Set.of(CustomerDocumentValidation.Results.REGULAR,
                CustomerDocumentValidation.Results.IRREGULAR, CustomerDocumentValidation.Results.NOT_FOUND,
                CustomerDocumentValidation.Results.UNABLE_TO_VERIFY,
                CustomerDocumentValidation.Results.DECEASED).contains(in.result())) {
            throw validation("result", "INVALID", "Resultado inválido.");
        }
        HolderSettingsService.Settings config = settings.current();
        CustomerDocumentValidation row = RegistryContext.forCreate(new CustomerDocumentValidation());
        row.setCustomerId(customer.getId());
        row.setDocumentKind(in.documentKind());
        row.setResult(in.result());
        row.setProvider(in.provider() != null ? in.provider() : config.cpfValidationProvider());
        row.setLegalBasis("LGPD art. 7º");
        row.setBiometricVerified(Boolean.TRUE.equals(in.biometricVerified()));
        row.setValidatedAt(OffsetDateTime.now());
        row = validations.saveAndFlush(row);
        List<UUID> alertIds = new ArrayList<>();
        if (CustomerDocumentValidation.Results.DECEASED.equals(in.result())) {
            List<UUID> unitIds = unitCustomers
                    .findByTenantIdAndCustomerIdAndEndDateIsNull(RegistryContext.tenantId(), customer.getId())
                    .stream()
                    .filter(l -> l.getRole() == UnitCustomer.Role.USER)
                    .map(UnitCustomer::getUnitId)
                    .distinct()
                    .toList();
            if (unitIds.isEmpty()) {
                alertIds.add(openDeathAlert(customer.getId(), null,
                        HolderDeathAlert.Sources.EXTERNAL_VALIDATION).getId());
            } else {
                for (UUID unitId : unitIds) {
                    alertIds.add(openDeathAlert(customer.getId(), unitId,
                            HolderDeathAlert.Sources.EXTERNAL_VALIDATION).getId());
                }
            }
        }
        ValidationView view = new ValidationView(row.getId(), row.getCustomerId(), row.getDocumentKind(),
                row.getResult(), row.getProvider(), row.getLegalBasis(), row.isBiometricVerified(),
                row.getValidatedAt());
        return new ValidationRecordedView(view, alertIds,
                CustomerDocumentValidation.Results.IRREGULAR.equals(in.result()));
    }

    public List<ValidationView> listValidations(UUID customerId, boolean irregularOnly) {
        List<CustomerDocumentValidation> rows = irregularOnly
                ? validations.findByTenantIdAndCustomerIdAndResultOrderByValidatedAtDesc(
                        RegistryContext.tenantId(), customerId, CustomerDocumentValidation.Results.IRREGULAR)
                : validations.findByTenantIdAndCustomerIdOrderByValidatedAtDesc(
                        RegistryContext.tenantId(), customerId);
        return rows.stream().map(r -> new ValidationView(r.getId(), r.getCustomerId(), r.getDocumentKind(),
                r.getResult(), r.getProvider(), r.getLegalBasis(), r.isBiometricVerified(),
                r.getValidatedAt())).toList();
    }

    /**
     * Condição da troca de titularidade (P1.41): com validação online
     * diferente de NONE, exige validação REGULAR registrada (e, no modo
     * STATUS_AND_BIOMETRICS, com biometria verificada). Resultado irregular
     * NÃO bloqueia (gera alerta, consultável em listValidations): se há
     * REGULAR registrada, a troca prossegue mesmo com irregular posterior.
     */
    public void validateForOwnershipTransfer(UUID customerId) {
        Customer customer = activeCustomer(customerId);
        String mode = settings.current().validateDocumentOnline();
        if (HolderSettingsService.ValidateOnline.NONE.equals(mode)) {
            return;
        }
        boolean biometric = HolderSettingsService.ValidateOnline.STATUS_AND_BIOMETRICS.equals(mode);
        boolean satisfied = validations
                .findByTenantIdAndCustomerIdAndResultOrderByValidatedAtDesc(
                        RegistryContext.tenantId(), customer.getId(), CustomerDocumentValidation.Results.REGULAR)
                .stream()
                .anyMatch(r -> !biometric || r.isBiometricVerified());
        if (!satisfied) {
            throw validation("customerId", "VALIDATION_REQUIRED",
                    "A troca de titularidade exige validação externa REGULAR registrada"
                            + (biometric ? " com biometria verificada" : "")
                            + " (registry.validate_document_online = " + mode + ").");
        }
    }

    public TransferValidationView transferValidationStatus(UUID customerId) {
        Customer customer = activeCustomer(customerId);
        String mode = settings.current().validateDocumentOnline();
        List<CustomerDocumentValidation> rows = validations.findByTenantIdAndCustomerIdOrderByValidatedAtDesc(
                RegistryContext.tenantId(), customer.getId());
        String latest = rows.isEmpty() ? null : rows.get(0).getResult();
        boolean biometric = HolderSettingsService.ValidateOnline.STATUS_AND_BIOMETRICS.equals(mode);
        boolean satisfied = HolderSettingsService.ValidateOnline.NONE.equals(mode)
                || rows.stream().anyMatch(r ->
                        CustomerDocumentValidation.Results.REGULAR.equals(r.getResult())
                                && (!biometric || r.isBiometricVerified()));
        return new TransferValidationView(customer.getId(), mode, satisfied, latest);
    }

    // ------------------------------------------------------------------ fusão

    @Transactional
    public MergeView requestMerge(MergeInput in) {
        Customer source = activeCustomer(in.sourceCustomerId());
        Customer target = activeCustomer(in.targetCustomerId());
        if (source.getId().equals(target.getId())) {
            throw validation("targetCustomerId", "SAME_AS_SOURCE",
                    "A origem e o destino da fusão precisam ser diferentes.");
        }
        requireEndReason(in.mergeEndReasonId());
        Map<String, Object> preview = buildMergePreview(source.getId());
        UUID orderId = orders.openRequiringApproval(CUSTOMER_MERGE_TYPE);
        orders.submit(orderId);
        CustomerMerge merge = RegistryContext.forCreate(new CustomerMerge());
        merge.setSourceCustomerId(source.getId());
        merge.setTargetCustomerId(target.getId());
        merge.setStatus("PENDING_APPROVAL");
        merge.setServiceOrderId(orderId);
        merge.setMergeEndReasonId(in.mergeEndReasonId());
        merge.setPreview(Json.write(preview));
        merge.setSnapshot("{}");
        merge = merges.saveAndFlush(merge);
        return mergeView(merge);
    }

    @Transactional
    public MergeView approveMerge(UUID id) {
        CustomerMerge merge = merge(id);
        requirePending(merge.getStatus(), "fusão");
        UUID approver = actor();
        requireAnotherPerson(orders.getOrder(merge.getServiceOrderId()), approver);
        applyMerge(merge);
        merge.setStatus("APPLIED");
        RegistryContext.touch(merge);
        merges.saveAndFlush(merge);
        orders.approveExecuteAndClose(merge.getServiceOrderId(), approver);
        return mergeView(merge);
    }

    @Transactional
    public MergeView rejectMerge(UUID id) {
        CustomerMerge merge = merge(id);
        requirePending(merge.getStatus(), "fusão");
        orders.closeNotExecuted(merge.getServiceOrderId(), REJECT_REASON, actor());
        merge.setStatus("REJECTED");
        RegistryContext.touch(merge);
        merges.saveAndFlush(merge);
        return mergeView(merge);
    }

    @Transactional
    public MergeView requestMergeReverse(UUID id) {
        CustomerMerge merge = merge(id);
        if (!"APPLIED".equals(merge.getStatus())) {
            throw new ApiException.Conflict("merge-not-applied",
                    "Só fusão aplicada pode ser desfeita por OS inversa.");
        }
        if (merge.getReversedByServiceOrderId() != null) {
            throw new ApiException.Conflict("merge-reverse-pending",
                    "Já existe uma OS inversa pendente para esta fusão.");
        }
        UUID reverseOrderId = orders.openReversal(CUSTOMER_MERGE_TYPE, merge.getServiceOrderId());
        orders.submit(reverseOrderId);
        merge.setReversedByServiceOrderId(reverseOrderId);
        RegistryContext.touch(merge);
        merges.saveAndFlush(merge);
        return mergeView(merge);
    }

    @Transactional
    public MergeView approveMergeReverse(UUID id) {
        CustomerMerge merge = merge(id);
        if (!"APPLIED".equals(merge.getStatus()) || merge.getReversedByServiceOrderId() == null) {
            throw new ApiException.Conflict("merge-reverse-missing",
                    "Não há OS inversa pendente para esta fusão.");
        }
        UUID approver = actor();
        requireAnotherPerson(orders.getOrder(merge.getReversedByServiceOrderId()), approver);
        reverseMerge(merge);
        merge.setStatus("REVERSED");
        RegistryContext.touch(merge);
        merges.saveAndFlush(merge);
        orders.approveExecuteAndClose(merge.getReversedByServiceOrderId(), approver);
        return mergeView(merge);
    }

    @Transactional
    public MergeView rejectMergeReverse(UUID id) {
        CustomerMerge merge = merge(id);
        if (!"APPLIED".equals(merge.getStatus()) || merge.getReversedByServiceOrderId() == null) {
            throw new ApiException.Conflict("merge-reverse-missing",
                    "Não há OS inversa pendente para esta fusão.");
        }
        orders.closeNotExecuted(merge.getReversedByServiceOrderId(), REJECT_REASON, actor());
        merge.setReversedByServiceOrderId(null);
        RegistryContext.touch(merge);
        merges.saveAndFlush(merge);
        return mergeView(merge);
    }

    public MergeView getMerge(UUID id) {
        return mergeView(merge(id));
    }

    private Map<String, Object> buildMergePreview(UUID sourceId) {
        UUID tenant = RegistryContext.tenantId();
        List<UnitCustomer> links = unitCustomers.findByTenantIdAndCustomerId(tenant, sourceId);
        List<UUID> currentLinks = new ArrayList<>();
        List<UUID> historicalLinks = new ArrayList<>();
        for (UnitCustomer link : links) {
            (link.getEndDate() == null ? currentLinks : historicalLinks).add(link.getId());
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        Map<String, Object> linkMap = new LinkedHashMap<>();
        linkMap.put("current", currentLinks);
        linkMap.put("historical", historicalLinks);
        preview.put("links", linkMap);
        preview.put("contacts", contacts.findByTenantIdAndCustomerId(tenant, sourceId)
                .stream().map(c -> c.getId()).toList());
        preview.put("addresses", customerAddresses.findByTenantIdAndCustomerIdOrderByValidFromAsc(tenant, sourceId)
                .stream().map(a -> a.getId()).toList());
        preview.put("representationsAsRepresented",
                representations.findByTenantIdAndRepresentedIdOrderByValidFromDesc(tenant, sourceId)
                        .stream().map(r -> r.getId()).toList());
        preview.put("representationsAsRepresentative",
                representations.findByTenantIdAndRepresentativeIdOrderByValidFromDesc(tenant, sourceId)
                        .stream().map(r -> r.getId()).toList());
        preview.put("validations", validations.findByTenantIdAndCustomerIdOrderByValidatedAtDesc(tenant, sourceId)
                .stream().map(v -> v.getId()).toList());
        preview.put("consents", consentIdsFor(sourceId).size());
        // Módulos futuros (P1.36, P2.5, P3.3): sem tabelas nesta camada, a
        // prévia nasce vazia; o serviço as preenche quando existirem.
        preview.put("protocols", List.of());
        preview.put("debts", List.of());
        preview.put("portalAccounts", List.of());
        preview.put("note", "Protocolos, débitos e contas do portal: módulos futuros (P1.36, P2.5, P3.3); "
                + "a prévia lista essas seções como vazias até existirem. "
                + "Prévia e snapshot levam só identificadores, contagens, códigos e datas.");
        return preview;
    }

    private void applyMerge(CustomerMerge merge) {
        UUID tenant = RegistryContext.tenantId();
        UUID source = merge.getSourceCustomerId();
        UUID target = merge.getTargetCustomerId();
        LocalDate today = LocalDate.now();
        List<UnitCustomer> sourceLinks = unitCustomers.findByTenantIdAndCustomerId(tenant, source);
        List<UnitCustomer> targetLinksBefore = unitCustomers.findByTenantIdAndCustomerId(tenant, target);
        List<UUID> movedLinkIds = new ArrayList<>();
        List<UUID> closedLinkIds = new ArrayList<>();
        List<UUID> skippedHistoricalIds = new ArrayList<>();
        List<UnitCustomer> toSave = new ArrayList<>();
        for (UnitCustomer link : sourceLinks) {
            boolean current = link.getEndDate() == null;
            boolean conflict = targetLinksBefore.stream().anyMatch(t ->
                    t.getUnitId().equals(link.getUnitId())
                            && sameRole(t, link)
                            && rangesOverlap(t.getStartDate(), t.getEndDate(),
                                    link.getStartDate(), link.getEndDate()));
            if (conflict) {
                if (current) {
                    link.setEndDate(today.isBefore(link.getStartDate()) ? link.getStartDate() : today);
                    link.setEndReason("Vínculo encerrado pela fusão de cadastros.");
                    link.setEndReasonId(merge.getMergeEndReasonId());
                    link.setClosedByServiceOrderId(merge.getServiceOrderId());
                    RegistryContext.touch(link);
                    toSave.add(link);
                    closedLinkIds.add(link.getId());
                } else {
                    skippedHistoricalIds.add(link.getId());
                }
            } else {
                link.setCustomerId(target);
                RegistryContext.touch(link);
                toSave.add(link);
                movedLinkIds.add(link.getId());
            }
        }
        unitCustomers.saveAllAndFlush(toSave);
        List<UUID> movedContactIds = new ArrayList<>();
        List<UUID> demotedContactIds = new ArrayList<>();
        var sourceContacts = contacts.findByTenantIdAndCustomerId(tenant, source);
        var targetContacts = contacts.findByTenantIdAndCustomerId(tenant, target);
        for (var contact : sourceContacts) {
            if (contact.isPrimaryContact() && targetContacts.stream().anyMatch(t ->
                    t.isPrimaryContact() && t.getKind() == contact.getKind())) {
                contact.setPrimaryContact(false);
                demotedContactIds.add(contact.getId());
            }
            contact.setCustomerId(target);
            RegistryContext.touch(contact);
            movedContactIds.add(contact.getId());
        }
        contacts.saveAllAndFlush(sourceContacts);
        List<UUID> movedAddressIds = new ArrayList<>();
        var sourceAddresses = customerAddresses.findByTenantIdAndCustomerIdOrderByValidFromAsc(tenant, source);
        for (var address : sourceAddresses) {
            address.setCustomerId(target);
            RegistryContext.touch(address);
            movedAddressIds.add(address.getId());
        }
        customerAddresses.saveAllAndFlush(sourceAddresses);
        List<UUID> movedAsRepresented = new ArrayList<>();
        var representedReps = representations.findByTenantIdAndRepresentedIdOrderByValidFromDesc(tenant, source);
        for (var rep : representedReps) {
            rep.setRepresentedId(target);
            RegistryContext.touch(rep);
            movedAsRepresented.add(rep.getId());
        }
        representations.saveAllAndFlush(representedReps);
        List<UUID> movedAsRepresentative = new ArrayList<>();
        var representativeReps = representations.findByTenantIdAndRepresentativeIdOrderByValidFromDesc(tenant, source);
        for (var rep : representativeReps) {
            rep.setRepresentativeId(target);
            RegistryContext.touch(rep);
            movedAsRepresentative.add(rep.getId());
        }
        representations.saveAllAndFlush(representativeReps);
        List<UUID> movedValidationIds = new ArrayList<>();
        var sourceValidations = validations.findByTenantIdAndCustomerIdOrderByValidatedAtDesc(tenant, source);
        for (var validation : sourceValidations) {
            validation.setCustomerId(target);
            RegistryContext.touch(validation);
            movedValidationIds.add(validation.getId());
        }
        validations.saveAllAndFlush(sourceValidations);
        List<UUID> movedConsentIds = consentIdsFor(source);
        repointConsents(movedConsentIds, target);
        Customer sourceCustomer = activeCustomer(source);
        sourceCustomer.setDeletedAt(OffsetDateTime.now());
        sourceCustomer.setDeletedReason("Fusão no cliente " + target);
        RegistryContext.touch(sourceCustomer);
        customers.saveAndFlush(sourceCustomer);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("movedLinkIds", movedLinkIds);
        snapshot.put("closedLinkIds", closedLinkIds);
        snapshot.put("skippedHistoricalLinkIds", skippedHistoricalIds);
        snapshot.put("movedContactIds", movedContactIds);
        snapshot.put("demotedContactIds", demotedContactIds);
        snapshot.put("movedAddressIds", movedAddressIds);
        snapshot.put("movedRepresentationAsRepresentedIds", movedAsRepresented);
        snapshot.put("movedRepresentationAsRepresentativeIds", movedAsRepresentative);
        snapshot.put("movedValidationIds", movedValidationIds);
        snapshot.put("movedConsentIds", movedConsentIds);
        snapshot.put("sourceDeleted", true);
        merge.setSnapshot(Json.write(snapshot));
    }

    private void reverseMerge(CustomerMerge merge) {
        UUID tenant = RegistryContext.tenantId();
        UUID source = merge.getSourceCustomerId();
        UUID target = merge.getTargetCustomerId();
        JsonNode snapshot = Json.readTree(merge.getSnapshot());
        for (UUID linkId : uuidList(snapshot, "movedLinkIds")) {
            unitCustomers.findById(linkId).ifPresent(link -> {
                if (tenant.equals(link.getTenantId()) && target.equals(link.getCustomerId())) {
                    link.setCustomerId(source);
                    RegistryContext.touch(link);
                    unitCustomers.saveAndFlush(link);
                }
            });
        }
        for (UUID linkId : uuidList(snapshot, "closedLinkIds")) {
            unitCustomers.findById(linkId).ifPresent(link -> {
                if (tenant.equals(link.getTenantId()) && source.equals(link.getCustomerId())
                        && merge.getServiceOrderId().equals(link.getClosedByServiceOrderId())) {
                    link.setEndDate(null);
                    link.setEndReason(null);
                    link.setEndReasonId(null);
                    link.setClosedByServiceOrderId(null);
                    RegistryContext.touch(link);
                    unitCustomers.saveAndFlush(link);
                }
            });
        }
        for (UUID contactId : uuidList(snapshot, "movedContactIds")) {
            contacts.findById(contactId).ifPresent(contact -> {
                if (tenant.equals(contact.getTenantId()) && target.equals(contact.getCustomerId())) {
                    if (uuidList(snapshot, "demotedContactIds").contains(contactId)) {
                        contact.setPrimaryContact(true);
                    }
                    contact.setCustomerId(source);
                    RegistryContext.touch(contact);
                    contacts.saveAndFlush(contact);
                }
            });
        }
        for (UUID addressId : uuidList(snapshot, "movedAddressIds")) {
            customerAddresses.findById(addressId).ifPresent(address -> {
                if (tenant.equals(address.getTenantId()) && target.equals(address.getCustomerId())) {
                    address.setCustomerId(source);
                    RegistryContext.touch(address);
                    customerAddresses.saveAndFlush(address);
                }
            });
        }
        for (UUID repId : uuidList(snapshot, "movedRepresentationAsRepresentedIds")) {
            representations.findById(repId).ifPresent(rep -> {
                if (tenant.equals(rep.getTenantId()) && target.equals(rep.getRepresentedId())) {
                    rep.setRepresentedId(source);
                    RegistryContext.touch(rep);
                    representations.saveAndFlush(rep);
                }
            });
        }
        for (UUID repId : uuidList(snapshot, "movedRepresentationAsRepresentativeIds")) {
            representations.findById(repId).ifPresent(rep -> {
                if (tenant.equals(rep.getTenantId()) && target.equals(rep.getRepresentativeId())) {
                    rep.setRepresentativeId(source);
                    RegistryContext.touch(rep);
                    representations.saveAndFlush(rep);
                }
            });
        }
        for (UUID validationId : uuidList(snapshot, "movedValidationIds")) {
            validations.findById(validationId).ifPresent(validation -> {
                if (tenant.equals(validation.getTenantId()) && target.equals(validation.getCustomerId())) {
                    validation.setCustomerId(source);
                    RegistryContext.touch(validation);
                    validations.saveAndFlush(validation);
                }
            });
        }
        repointConsents(uuidList(snapshot, "movedConsentIds"), source);
        customers.findById(source).ifPresent(sourceCustomer -> {
            if (tenant.equals(sourceCustomer.getTenantId()) && sourceCustomer.getDeletedAt() != null) {
                sourceCustomer.setDeletedAt(null);
                sourceCustomer.setDeletedReason(null);
                RegistryContext.touch(sourceCustomer);
                customers.saveAndFlush(sourceCustomer);
            }
        });
    }

    private MergeView mergeView(CustomerMerge merge) {
        return new MergeView(merge.getId(), merge.getSourceCustomerId(), merge.getTargetCustomerId(),
                merge.getStatus(), merge.getServiceOrderId(), merge.getReversedByServiceOrderId(),
                merge.getPreview(), merge.getSnapshot());
    }

    // ------------------------------------------------------------------ separação

    @Transactional
    public SplitView requestSplit(SplitInput in) {
        Customer source = activeCustomer(in.sourceCustomerId());
        Customer fresh = activeCustomer(in.newCustomerId());
        if (source.getId().equals(fresh.getId())) {
            throw validation("newCustomerId", "SAME_AS_SOURCE",
                    "A origem e o cliente novo da separação precisam ser diferentes.");
        }
        UUID tenant = RegistryContext.tenantId();
        List<UnitCustomer> sourceCurrent = unitCustomers.findByTenantIdAndCustomerIdAndEndDateIsNull(tenant, source.getId());
        List<UnitCustomer> freshCurrent = unitCustomers.findByTenantIdAndCustomerIdAndEndDateIsNull(tenant, fresh.getId());
        Map<UUID, UnitCustomer> byId = new LinkedHashMap<>();
        for (UnitCustomer link : sourceCurrent) {
            byId.put(link.getId(), link);
        }
        List<UUID> conflicts = new ArrayList<>();
        for (UUID linkId : in.linkIds()) {
            UnitCustomer link = byId.get(linkId);
            if (link == null) {
                throw validation("linkIds", "NOT_CURRENT_SOURCE_LINK",
                        "Vínculo " + linkId + " não é vigente da origem.");
            }
            boolean conflict = freshCurrent.stream().anyMatch(f ->
                    f.getUnitId().equals(link.getUnitId()) && sameRole(f, link));
            if (conflict) {
                conflicts.add(linkId);
            }
        }
        if (!conflicts.isEmpty()) {
            throw validation("linkIds", "SPLIT_CONFLICT",
                    "O cliente novo já tem vínculo vigente com o mesmo papel nas unidades dos vínculos: "
                            + conflicts);
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("links", in.linkIds());
        UUID orderId = orders.openRequiringApproval(CUSTOMER_SPLIT_TYPE);
        orders.submit(orderId);
        CustomerSplit split = RegistryContext.forCreate(new CustomerSplit());
        split.setSourceCustomerId(source.getId());
        split.setNewCustomerId(fresh.getId());
        split.setStatus("PENDING_APPROVAL");
        split.setServiceOrderId(orderId);
        split.setPreview(Json.write(preview));
        split.setSnapshot("{}");
        split = splits.saveAndFlush(split);
        return splitView(split);
    }

    @Transactional
    public SplitView approveSplit(UUID id) {
        CustomerSplit split = split(id);
        requirePending(split.getStatus(), "separação");
        UUID approver = actor();
        requireAnotherPerson(orders.getOrder(split.getServiceOrderId()), approver);
        JsonNode preview = Json.readTree(split.getPreview());
        List<UUID> linkIds = uuidList(preview, "links");
        for (UUID linkId : linkIds) {
            UnitCustomer link = RegistryContext.require(unitCustomers.findById(linkId),
                    () -> "Vínculo da separação não encontrado.");
            if (!split.getSourceCustomerId().equals(link.getCustomerId()) || link.getEndDate() != null) {
                throw new ApiException.Conflict("split-link-changed",
                        "Vínculo " + linkId + " mudou desde o pedido; revise a separação.");
            }
            link.setCustomerId(split.getNewCustomerId());
            RegistryContext.touch(link);
            unitCustomers.saveAndFlush(link);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("movedLinkIds", linkIds);
        split.setSnapshot(Json.write(snapshot));
        split.setStatus("APPLIED");
        RegistryContext.touch(split);
        splits.saveAndFlush(split);
        orders.approveExecuteAndClose(split.getServiceOrderId(), approver);
        return splitView(split);
    }

    @Transactional
    public SplitView rejectSplit(UUID id) {
        CustomerSplit split = split(id);
        requirePending(split.getStatus(), "separação");
        orders.closeNotExecuted(split.getServiceOrderId(), REJECT_REASON, actor());
        split.setStatus("REJECTED");
        RegistryContext.touch(split);
        splits.saveAndFlush(split);
        return splitView(split);
    }

    @Transactional
    public SplitView requestSplitReverse(UUID id) {
        CustomerSplit split = split(id);
        if (!"APPLIED".equals(split.getStatus())) {
            throw new ApiException.Conflict("split-not-applied",
                    "Só separação aplicada pode ser desfeita por OS inversa.");
        }
        if (split.getReversedByServiceOrderId() != null) {
            throw new ApiException.Conflict("split-reverse-pending",
                    "Já existe uma OS inversa pendente para esta separação.");
        }
        UUID reverseOrderId = orders.openReversal(CUSTOMER_SPLIT_TYPE, split.getServiceOrderId());
        orders.submit(reverseOrderId);
        split.setReversedByServiceOrderId(reverseOrderId);
        RegistryContext.touch(split);
        splits.saveAndFlush(split);
        return splitView(split);
    }

    @Transactional
    public SplitView approveSplitReverse(UUID id) {
        CustomerSplit split = split(id);
        if (!"APPLIED".equals(split.getStatus()) || split.getReversedByServiceOrderId() == null) {
            throw new ApiException.Conflict("split-reverse-missing",
                    "Não há OS inversa pendente para esta separação.");
        }
        UUID approver = actor();
        requireAnotherPerson(orders.getOrder(split.getReversedByServiceOrderId()), approver);
        JsonNode snapshot = Json.readTree(split.getSnapshot());
        for (UUID linkId : uuidList(snapshot, "movedLinkIds")) {
            unitCustomers.findById(linkId).ifPresent(link -> {
                if (RegistryContext.tenantId().equals(link.getTenantId())
                        && split.getNewCustomerId().equals(link.getCustomerId())) {
                    link.setCustomerId(split.getSourceCustomerId());
                    RegistryContext.touch(link);
                    unitCustomers.saveAndFlush(link);
                }
            });
        }
        split.setStatus("REVERSED");
        RegistryContext.touch(split);
        splits.saveAndFlush(split);
        orders.approveExecuteAndClose(split.getReversedByServiceOrderId(), approver);
        return splitView(split);
    }

    @Transactional
    public SplitView rejectSplitReverse(UUID id) {
        CustomerSplit split = split(id);
        if (!"APPLIED".equals(split.getStatus()) || split.getReversedByServiceOrderId() == null) {
            throw new ApiException.Conflict("split-reverse-missing",
                    "Não há OS inversa pendente para esta separação.");
        }
        orders.closeNotExecuted(split.getReversedByServiceOrderId(), REJECT_REASON, actor());
        split.setReversedByServiceOrderId(null);
        RegistryContext.touch(split);
        splits.saveAndFlush(split);
        return splitView(split);
    }

    public SplitView getSplit(UUID id) {
        return splitView(split(id));
    }

    private SplitView splitView(CustomerSplit split) {
        return new SplitView(split.getId(), split.getSourceCustomerId(), split.getNewCustomerId(),
                split.getStatus(), split.getServiceOrderId(), split.getReversedByServiceOrderId(),
                split.getPreview(), split.getSnapshot());
    }

    // ------------------------------------------------------------------ ocupação e nome social

    @Transactional
    public ChangeRequestView requestOccupancyChange(UUID unitId, OccupancyInput in) {
        ConsumerUnit unit = unit(unitId);
        if (!OCCUPANCIES.contains(in.occupancyStatus())) {
            throw validation("occupancyStatus", "INVALID", "Ocupação inválida.");
        }
        if ("VACANT".equals(in.occupancyStatus())) {
            requireNoCurrentUser(unitId);
        }
        UUID orderId = orders.openRequiringApproval(OCCUPANCY_CHANGE);
        orders.submit(orderId);
        HolderChangeRequest change = RegistryContext.forCreate(new HolderChangeRequest());
        change.setKind(OCCUPANCY_CHANGE);
        change.setTargetType("CONSUMER_UNIT");
        change.setTargetId(unit.getId());
        change.setOldValue(unit.getOccupancyStatus());
        change.setNewValue(in.occupancyStatus());
        change.setServiceOrderId(orderId);
        change.setStatus("PENDING_APPROVAL");
        change = changeRequests.saveAndFlush(change);
        return changeView(change);
    }

    @Transactional
    public ChangeRequestView requestSocialNameChange(UUID customerId, SocialNameInput in) {
        Customer customer = activeCustomer(customerId);
        if (customer.getPersonType() != Customer.PersonType.PF) {
            throw validation("socialName", "PERSON_TYPE",
                    "Nome social só existe para pessoa física.");
        }
        String next = in.socialName() == null || in.socialName().isBlank() ? null : in.socialName().trim();
        UUID orderId = orders.openRequiringApproval(CUSTOMER_DATA_UPDATE);
        orders.submit(orderId);
        HolderChangeRequest change = RegistryContext.forCreate(new HolderChangeRequest());
        change.setKind("SOCIAL_NAME_CHANGE");
        change.setTargetType("CUSTOMER");
        change.setTargetId(customer.getId());
        change.setOldValue(customer.getSocialName());
        change.setNewValue(next);
        change.setServiceOrderId(orderId);
        change.setStatus("PENDING_APPROVAL");
        change = changeRequests.saveAndFlush(change);
        return changeView(change);
    }

    @Transactional
    public ChangeRequestView approveChange(UUID changeId) {
        HolderChangeRequest change = change(changeId);
        requirePending(change.getStatus(), "alteração");
        UUID approver = actor();
        requireAnotherPerson(orders.getOrder(change.getServiceOrderId()), approver);
        if (OCCUPANCY_CHANGE.equals(change.getKind())) {
            ConsumerUnit unit = unit(change.getTargetId());
            if ("VACANT".equals(change.getNewValue())) {
                requireNoCurrentUser(unit.getId());
            }
            unit.setOccupancyStatus(change.getNewValue());
            RegistryContext.touch(unit);
            units.saveAndFlush(unit);
        } else {
            Customer customer = activeCustomer(change.getTargetId());
            if (customer.getPersonType() != Customer.PersonType.PF) {
                throw validation("socialName", "PERSON_TYPE",
                        "Nome social só existe para pessoa física.");
            }
            customer.setSocialName(change.getNewValue());
            RegistryContext.touch(customer);
            customers.saveAndFlush(customer);
        }
        change.setStatus("APPLIED");
        RegistryContext.touch(change);
        changeRequests.saveAndFlush(change);
        orders.approveExecuteAndClose(change.getServiceOrderId(), approver);
        return changeView(change);
    }

    @Transactional
    public ChangeRequestView rejectChange(UUID changeId) {
        HolderChangeRequest change = change(changeId);
        requirePending(change.getStatus(), "alteração");
        orders.closeNotExecuted(change.getServiceOrderId(), REJECT_REASON, actor());
        change.setStatus("REJECTED");
        RegistryContext.touch(change);
        changeRequests.saveAndFlush(change);
        return changeView(change);
    }

    public List<ChangeRequestView> listChanges(String targetType, UUID targetId) {
        return changeRequests.findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
                        RegistryContext.tenantId(), targetType, targetId)
                .stream().map(this::changeView).toList();
    }

    private ChangeRequestView changeView(HolderChangeRequest change) {
        return new ChangeRequestView(change.getId(), change.getKind(), change.getTargetType(),
                change.getTargetId(), change.getOldValue(), change.getNewValue(),
                change.getServiceOrderId(), change.getStatus());
    }

    // ------------------------------------------------------------------ situação da unidade

    public HolderStatusView holderStatus(UUID unitId) {
        ConsumerUnit unit = unit(unitId);
        UUID tenant = RegistryContext.tenantId();
        List<UnitCustomer> current = unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(tenant, unitId);
        UUID userId = null;
        UUID ownerId = null;
        List<UUID> provisional = new ArrayList<>();
        for (UnitCustomer link : current) {
            if (link.getRole() == UnitCustomer.Role.USER && userId == null) {
                userId = link.getCustomerId();
            } else if (link.getRole() == UnitCustomer.Role.OWNER && ownerId == null) {
                ownerId = link.getCustomerId();
            }
            if (link.isProvisional()) {
                provisional.add(link.getCustomerId());
            }
        }
        HolderSettingsService.Settings config = settings.current();
        String billName = billHolder.holderName(unitId, config.holderNameDisplay(),
                config.vacantUnitHolder(), config.socialNameDisplay());
        long pendingAlerts = deathAlerts.findByTenantIdAndUnitIdOrderByDetectedAtDesc(tenant, unitId)
                .stream().filter(a -> "PENDING_REVIEW".equals(a.getStatus())).count();
        return new HolderStatusView(unit.getId(), unit.getOccupancyStatus(), userId, ownerId, provisional,
                billName, billName == null, pendingAlerts);
    }

    // ------------------------------------------------------------------ candidatos à fusão

    /**
     * Detecção de candidatos à fusão (sugestão, nunca fusão automática):
     * mesmo documento (índice cego) ou mesmo nome + mesma data de
     * nascimento. Documento digitado de formas diferentes só é capturado
     * quando a normalização do índice cego as iguala; o operador confirma
     * pela prévia antes de qualquer fusão.
     */
    public List<CandidateView> findMergeCandidates(String documentHash, String name, LocalDate birthDate) {
        UUID tenant = RegistryContext.tenantId();
        List<Customer> found = new ArrayList<>();
        if (documentHash != null && !documentHash.isBlank()) {
            customers.findByTenantIdAndDocumentHashAndDeletedAtIsNull(tenant, documentHash)
                    .ifPresent(found::add);
        }
        if (name != null && !name.isBlank() && birthDate != null) {
            for (Customer candidate : customers.findByTenantIdAndNameIgnoreCaseAndBirthDateAndDeletedAtIsNull(
                    tenant, name, birthDate)) {
                if (found.stream().noneMatch(c -> c.getId().equals(candidate.getId()))) {
                    found.add(candidate);
                }
            }
        }
        return found.stream().map(c -> new CandidateView(c.getId(), c.getName(), c.getPersonType(),
                c.getDocumentMasked())).toList();
    }

    // ------------------------------------------------------------------ apoio

    private Customer activeCustomer(UUID id) {
        Customer customer = RegistryContext.require(customers.findById(id),
                () -> "Cliente não encontrado.");
        if (customer.getDeletedAt() != null) {
            throw new ApiException.Conflict("customer-deleted", "O cliente está excluído.");
        }
        return customer;
    }

    private ConsumerUnit unit(UUID id) {
        return RegistryContext.require(units.findById(id), () -> "Unidade consumidora não encontrada.");
    }

    private CustomerRepresentation representation(UUID id) {
        return RegistryContext.require(representations.findById(id),
                () -> "Representação não encontrada.");
    }

    private CustomerMerge merge(UUID id) {
        return RegistryContext.require(merges.findById(id), () -> "Fusão não encontrada.");
    }

    private CustomerSplit split(UUID id) {
        return RegistryContext.require(splits.findById(id), () -> "Separação não encontrada.");
    }

    private HolderChangeRequest change(UUID id) {
        return RegistryContext.require(changeRequests.findById(id), () -> "Alteração não encontrada.");
    }

    private static UUID actor() {
        UUID user = TenantContextHolder.getUserId();
        if (user == null) {
            throw new IllegalStateException("sem usuário no contexto da requisição para a OS interna");
        }
        return user;
    }

    private static ApiException.Validation validation(String field, String code, String message) {
        return ApiException.Validation.of(new ApiException.FieldError(field, code, message));
    }

    private static void requirePending(String status, String what) {
        if (!"PENDING_APPROVAL".equals(status)) {
            throw new ApiException.Conflict("not-pending",
                    "Só " + what + " pendente de aprovação pode ser aprovada ou rejeitada.");
        }
    }

    /** Aprovação por outra pessoa: o aprovador não pode ser o criador da OS. */
    private static void requireAnotherPerson(ServiceOrderService.OrderView order, UUID approver) {
        if (order != null && approver.equals(order.createdBy())) {
            throw validation("approvedBy", "SAME_AS_CREATOR",
                    "Quem aprova precisa ser diferente de quem registrou a OS.");
        }
    }

    private void requireNoCurrentUser(UUID unitId) {
        boolean hasUser = unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(
                        RegistryContext.tenantId(), unitId)
                .stream().anyMatch(l -> l.getRole() == UnitCustomer.Role.USER);
        if (hasUser) {
            throw validation("occupancyStatus", "USER_LINK_EXISTS",
                    "VACANT só vale sem usuário vigente (há vínculo USER vigente na unidade).");
        }
    }

    private void requireEndReason(UUID endReasonId) {
        if (endReasonId == null) {
            throw validation("endReasonId", "REQUIRED", "Informe o motivo de fim de vínculo.");
        }
        if (catalogs != null && catalogs.find("unit_link_end_reasons", endReasonId) == null) {
            throw validation("endReasonId", "UNKNOWN",
                    "Motivo de fim de vínculo desconhecido: " + endReasonId + ".");
        }
    }

    private UUID resolveRoleIdOrFail(String systemRole) {
        if (catalogs != null) {
            CatalogLookup.CatalogRow row = catalogs.roleBySystemRole(systemRole);
            if (row != null) {
                return row.id();
            }
        }
        throw validation("roleId", "CATALOG_MISSING",
                "Papel " + systemRole + " sem linha no catálogo relation_roles.");
    }

    private static boolean sameRole(UnitCustomer a, UnitCustomer b) {
        if (a.getRole() != b.getRole()) {
            return false;
        }
        return a.getRoleId() == null || b.getRoleId() == null || a.getRoleId().equals(b.getRoleId());
    }

    /** Sobreposição de vigências, com fim nulo = aberto (mesma semântica da EXCLUDE do banco). */
    private static boolean rangesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        boolean aBeforeBEnd = bEnd == null || !aStart.isAfter(bEnd);
        boolean bBeforeAEnd = aEnd == null || !bStart.isAfter(aEnd);
        return aBeforeBEnd && bBeforeAEnd;
    }

    private List<UUID> consentIdsFor(UUID customerId) {
        if (em == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery(
                        "SELECT id FROM lgpd_consent WHERE tenant_id = :t AND subject_type = 'CUSTOMER' "
                                + "AND subject_id = :c")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("c", customerId)
                .getResultList();
        return rows;
    }

    private void repointConsents(List<UUID> consentIds, UUID targetCustomerId) {
        if (em == null || consentIds.isEmpty()) {
            return;
        }
        em.createNativeQuery(
                        "UPDATE lgpd_consent SET subject_id = :t2 WHERE tenant_id = :t AND id IN (:ids)")
                .setParameter("t2", targetCustomerId)
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("ids", consentIds)
                .executeUpdate();
    }

    private static List<UUID> uuidList(JsonNode node, String field) {
        List<UUID> ids = new ArrayList<>();
        if (node == null) {
            return ids;
        }
        JsonNode array = node.get(field);
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                ids.add(UUID.fromString(item.asText()));
            }
        }
        return ids;
    }

    static boolean rangesOverlapForTest(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return rangesOverlap(aStart, aEnd, bStart, bEnd);
    }
}
