package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathAlertInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathReviewInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.MergeInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.OccupancyInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.RepresentationInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SocialNameInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationInput;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.commercial.registry.domain.CustomerDocumentValidation;
import br.com.ziipsan.commercial.registry.domain.CustomerMerge;
import br.com.ziipsan.commercial.registry.domain.CustomerRepresentation;
import br.com.ziipsan.commercial.registry.domain.HolderDeathAlert;
import br.com.ziipsan.commercial.registry.domain.UnitCustomer;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerAddressRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerContactRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerDocumentValidationRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerMergeRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerRepresentationRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerSplitRepository;
import br.com.ziipsan.commercial.registry.repository.HolderChangeRequestRepository;
import br.com.ziipsan.commercial.registry.repository.HolderDeathAlertRepository;
import br.com.ziipsan.commercial.registry.repository.UnitCustomerRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1.41: regras das situações do titular sem banco (mocks). A prova contra
 * PostgreSQL real (migration, RLS, backfill, aprovação do motor) vive no
 * P141MigrationIT; aqui valem as regras de serviço: outra pessoa aprova,
 * representação vencida não vale, nome social só em PF, VACANT só sem
 * usuário, óbito sem efeito automático, fusão com reversa.
 */
class HolderSituationServiceTest {

    private static final UUID TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CREATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID APPROVER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REVERSE_ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private CustomerRepository customers;
    private ConsumerUnitRepository units;
    private UnitCustomerRepository unitCustomers;
    private CustomerRepresentationRepository representations;
    private HolderDeathAlertRepository deathAlerts;
    private CustomerDocumentValidationRepository validations;
    private CustomerMergeRepository merges;
    private CustomerSplitRepository splits;
    private HolderChangeRequestRepository changeRequests;
    private InternalOrderService orders;
    private HolderSettingsService settings;
    private CatalogLookup catalogs;
    private HolderSituationService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(CREATOR);
        customers = mock(CustomerRepository.class);
        units = mock(ConsumerUnitRepository.class);
        unitCustomers = mock(UnitCustomerRepository.class);
        representations = mock(CustomerRepresentationRepository.class);
        deathAlerts = mock(HolderDeathAlertRepository.class);
        validations = mock(CustomerDocumentValidationRepository.class);
        merges = mock(CustomerMergeRepository.class);
        splits = mock(CustomerSplitRepository.class);
        changeRequests = mock(HolderChangeRequestRepository.class);
        orders = mock(InternalOrderService.class);
        settings = mock(HolderSettingsService.class);
        catalogs = mock(CatalogLookup.class);
        when(orders.openRequiringApproval(any())).thenReturn(ORDER_ID);
        when(orders.openReversal(any(), any())).thenReturn(REVERSE_ORDER_ID);
        when(orders.getOrder(any())).thenReturn(orderView(CREATOR, ServiceOrderStatus.PENDING_APPROVAL));
        when(settings.current()).thenReturn(new HolderSettingsService.Settings(
                HolderSettingsService.Factory.DECEASED_HOLDER_POLICY,
                HolderSettingsService.Factory.VACANT_UNIT_HOLDER,
                HolderSettingsService.Factory.REPRESENTATION_PROOF,
                HolderSettingsService.Factory.SOCIAL_NAME_DISPLAY,
                HolderSettingsService.Factory.VALIDATE_DOCUMENT_ONLINE,
                null,
                HolderSettingsService.Factory.DECEASED_CHECK,
                HolderSettingsService.Factory.DELEGATED_ACCESS,
                HolderSettingsService.Factory.HOLDER_NAME_DISPLAY));
        stubSave(representations);
        stubSave(deathAlerts);
        stubSave(validations);
        stubSave(merges);
        stubSave(splits);
        stubSave(changeRequests);
        service = new HolderSituationService(customers, units, unitCustomers,
                mock(CustomerContactRepository.class), mock(CustomerAddressRepository.class),
                representations, deathAlerts, validations, merges, splits,
                changeRequests, orders, settings, mock(BillHolderResolver.class),
                catalogs);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static <T> void stubSave(org.springframework.data.jpa.repository.JpaRepository<T, UUID> repo) {
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ServiceOrderService.OrderView orderView(UUID createdBy, ServiceOrderStatus status) {
        return new ServiceOrderService.OrderView(ORDER_ID, "TEST", status,
                ServiceOrderEnums.ExecutionMode.INTERNAL, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, createdBy, null, null, null, null, null, null, null, null);
    }

    private static Customer customer(UUID id, Customer.PersonType type, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setPersonType(type);
        c.setName(name);
        return c;
    }

    private static ConsumerUnit unit(UUID id, String occupancy) {
        ConsumerUnit u = new ConsumerUnit();
        u.setId(id);
        u.setTenantId(TENANT);
        u.setOccupancyStatus(occupancy);
        return u;
    }

    private static UnitCustomer link(UUID id, UUID unitId, UUID customerId, UnitCustomer.Role role,
                                     LocalDate start, LocalDate end) {
        UnitCustomer l = new UnitCustomer();
        l.setId(id);
        l.setTenantId(TENANT);
        l.setUnitId(unitId);
        l.setCustomerId(customerId);
        l.setRole(role);
        l.setStartDate(start);
        l.setEndDate(end);
        return l;
    }

    // ---------------------------------------------------------- nome social

    @Test
    @DisplayName("nome de exibição: social em PF, civil com CIVIL_NAME_ONLY ou sem social")
    void displayNameRules() {
        Customer pf = customer(UUID.randomUUID(), Customer.PersonType.PF, "João Civil");
        pf.setSocialName("Joana Social");
        assertThat(HolderSituationService.displayName(pf,
                HolderSettingsService.SocialNameDisplay.SOCIAL_EVERYWHERE_EXCEPT_FISCAL))
                .isEqualTo("Joana Social");
        assertThat(HolderSituationService.displayName(pf,
                HolderSettingsService.SocialNameDisplay.CIVIL_NAME_ONLY)).isEqualTo("João Civil");
        Customer pj = customer(UUID.randomUUID(), Customer.PersonType.PJ, "Empresa Ltda");
        pj.setSocialName("Fantasia");
        assertThat(HolderSituationService.displayName(pj,
                HolderSettingsService.SocialNameDisplay.SOCIAL_EVERYWHERE_EXCEPT_FISCAL))
                .isEqualTo("Empresa Ltda");
    }

    @Test
    @DisplayName("nome social: PJ é recusado; PF abre CUSTOMER_DATA_UPDATE pendente")
    void socialNameOnlyPf() {
        UUID pjId = UUID.randomUUID();
        when(customers.findById(pjId)).thenReturn(Optional.of(customer(pjId, Customer.PersonType.PJ, "Empresa")));
        assertThatThrownBy(() -> service.requestSocialNameChange(pjId, new SocialNameInput("Nome")))
                .isInstanceOf(ApiException.Validation.class);
        UUID pfId = UUID.randomUUID();
        when(customers.findById(pfId)).thenReturn(Optional.of(customer(pfId, Customer.PersonType.PF, "Civil")));
        var change = service.requestSocialNameChange(pfId, new SocialNameInput("Social"));
        assertThat(change.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(change.newValue()).isEqualTo("Social");
        verify(orders).openRequiringApproval(HolderSituationService.CUSTOMER_DATA_UPDATE);
    }

    // ---------------------------------------------------------- representações

    @Test
    @DisplayName("representação: vencida não vale; vigente vale; PENDING não vale")
    void representationValidity() {
        LocalDate today = LocalDate.now();
        CustomerRepresentation active = new CustomerRepresentation();
        active.setStatus("ACTIVE");
        active.setValidFrom(today.minusDays(1));
        active.setValidTo(today.plusDays(1));
        assertThat(HolderSituationService.isRepresentationValid(active, today)).isTrue();
        active.setValidTo(today.minusDays(1));
        assertThat(HolderSituationService.isRepresentationValid(active, today)).isFalse();
        active.setValidTo(null);
        active.setStatus("PENDING_APPROVAL");
        assertThat(HolderSituationService.isRepresentationValid(active, today)).isFalse();
    }

    @Test
    @DisplayName("representação: mesmo cliente, sem poder e sem prova são recusados")
    void representationRequestValidation() {
        UUID id = UUID.randomUUID();
        when(customers.findById(id)).thenReturn(Optional.of(customer(id, Customer.PersonType.PF, "Pessoa")));
        assertThatThrownBy(() -> service.requestRepresentation(new RepresentationInput(
                id, id, "ATTORNEY", List.of("VIEW"), null, null, null, UUID.randomUUID())))
                .isInstanceOf(ApiException.Validation.class);
        UUID other = UUID.randomUUID();
        when(customers.findById(other)).thenReturn(Optional.of(customer(other, Customer.PersonType.PF, "Outra")));
        assertThatThrownBy(() -> service.requestRepresentation(new RepresentationInput(
                id, other, "ATTORNEY", List.of(), null, null, null, UUID.randomUUID())))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.requestRepresentation(new RepresentationInput(
                id, other, "ATTORNEY", List.of("VIEW"), null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("representação: pedido nasce pendente; criador não aprova; outro aprova")
    void representationApproveFlow() {
        UUID represented = UUID.randomUUID();
        UUID representative = UUID.randomUUID();
        when(customers.findById(represented))
                .thenReturn(Optional.of(customer(represented, Customer.PersonType.PF, "Representado")));
        when(customers.findById(representative))
                .thenReturn(Optional.of(customer(representative, Customer.PersonType.PF, "Representante")));
        var view = service.requestRepresentation(new RepresentationInput(represented, representative,
                "ATTORNEY", List.of("VIEW", "PAY"), null, null, null, UUID.randomUUID()));
        assertThat(view.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(view.serviceOrderId()).isEqualTo(ORDER_ID);
        ArgumentCaptor<CustomerRepresentation> captor = ArgumentCaptor.forClass(CustomerRepresentation.class);
        verify(representations).saveAndFlush(captor.capture());
        CustomerRepresentation saved = captor.getValue();
        when(representations.findById(saved.getId())).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.approveRepresentation(saved.getId()))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(((ApiException.Validation) e).errors().get(0).code())
                        .isEqualTo("SAME_AS_CREATOR"));
        TenantContextHolder.setUserId(APPROVER);
        var approved = service.approveRepresentation(saved.getId());
        assertThat(approved.status()).isEqualTo("ACTIVE");
        verify(orders).approveExecuteAndClose(ORDER_ID, APPROVER);
    }

    // ---------------------------------------------------------- óbito

    @Test
    @DisplayName("óbito: rejeição não muda vínculo nenhum e encerra a OS sem execução")
    void deathRejectHasNoEffect() {
        UUID customerId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        HolderDeathAlert alert = new HolderDeathAlert();
        alert.setId(UUID.randomUUID());
        alert.setTenantId(TENANT);
        alert.setCustomerId(customerId);
        alert.setUnitId(unitId);
        alert.setStatus("PENDING_REVIEW");
        alert.setServiceOrderId(ORDER_ID);
        when(deathAlerts.findById(alert.getId())).thenReturn(Optional.of(alert));
        TenantContextHolder.setUserId(APPROVER);
        var view = service.reviewDeathAlert(alert.getId(), new DeathReviewInput("REJECT", null, null, null, null));
        assertThat(view.status()).isEqualTo("REJECTED");
        verify(unitCustomers, never()).saveAllAndFlush(any());
        verify(orders).closeNotExecuted(ORDER_ID, HolderSituationService.REJECT_REASON, APPROVER);
    }

    @Test
    @DisplayName("óbito: confirmação fecha o usuário, abre o provisório ocupante e confirma")
    void deathConfirmAppliesEffects() {
        UUID customerId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID provisionalId = UUID.randomUUID();
        UUID endReasonId = UUID.randomUUID();
        UUID deliveryModeId = UUID.randomUUID();
        ConsumerUnit u = unit(unitId, "OCCUPIED");
        u.setDeliveryModeId(deliveryModeId);
        when(units.findById(unitId)).thenReturn(Optional.of(u));
        when(customers.findById(customerId))
                .thenReturn(Optional.of(customer(customerId, Customer.PersonType.PF, "Falecido")));
        when(customers.findById(provisionalId))
                .thenReturn(Optional.of(customer(provisionalId, Customer.PersonType.PF, "Herdeiro")));
        UnitCustomer userLink = link(UUID.randomUUID(), unitId, customerId, UnitCustomer.Role.USER,
                LocalDate.now().minusYears(1), null);
        when(unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(TENANT, unitId))
                .thenReturn(List.of(userLink));
        when(catalogs.find("unit_link_end_reasons", endReasonId))
                .thenReturn(new CatalogLookup.CatalogRow(endReasonId, "DECEASED", "Falecimento", true, Map.of()));
        when(catalogs.roleBySystemRole("RESPONSIBLE"))
                .thenReturn(new CatalogLookup.CatalogRow(UUID.randomUUID(), "RESP", "Responsável", true, Map.of()));
        HolderDeathAlert alert = new HolderDeathAlert();
        alert.setId(UUID.randomUUID());
        alert.setTenantId(TENANT);
        alert.setCustomerId(customerId);
        alert.setUnitId(unitId);
        alert.setStatus("PENDING_REVIEW");
        alert.setServiceOrderId(ORDER_ID);
        when(deathAlerts.findById(alert.getId())).thenReturn(Optional.of(alert));
        TenantContextHolder.setUserId(APPROVER);
        var view = service.reviewDeathAlert(alert.getId(),
                new DeathReviewInput("CONFIRM", provisionalId, "OCCUPANT", endReasonId, null));
        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(userLink.getEndDate()).isNotNull();
        assertThat(userLink.getEndReasonId()).isEqualTo(endReasonId);
        assertThat(u.getOccupancyStatus()).isEqualTo("OCCUPIED");
        ArgumentCaptor<UnitCustomer> linkCaptor = ArgumentCaptor.forClass(UnitCustomer.class);
        verify(unitCustomers).saveAndFlush(linkCaptor.capture());
        assertThat(linkCaptor.getValue().isProvisional()).isTrue();
        assertThat(linkCaptor.getValue().getProvisionalKind()).isEqualTo("OCCUPANT");
        verify(orders).approveExecuteAndClose(ORDER_ID, APPROVER);
    }

    @Test
    @DisplayName("óbito: criador do alerta não confirma a própria revisão")
    void deathConfirmRequiresAnotherPerson() {
        HolderDeathAlert alert = new HolderDeathAlert();
        alert.setId(UUID.randomUUID());
        alert.setTenantId(TENANT);
        alert.setCustomerId(UUID.randomUUID());
        alert.setUnitId(null);
        alert.setStatus("PENDING_REVIEW");
        alert.setServiceOrderId(ORDER_ID);
        when(deathAlerts.findById(alert.getId())).thenReturn(Optional.of(alert));
        assertThatThrownBy(() -> service.reviewDeathAlert(alert.getId(),
                new DeathReviewInput("CONFIRM", null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
    }

    // ---------------------------------------------------------- validação externa

    @Test
    @DisplayName("validação DECEASED abre um alerta por unidade do titular; IRREGULAR só sinaliza")
    void validationDeathAndIrregular() {
        UUID customerId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(customers.findById(customerId))
                .thenReturn(Optional.of(customer(customerId, Customer.PersonType.PF, "Titular")));
        when(unitCustomers.findByTenantIdAndCustomerIdAndEndDateIsNull(TENANT, customerId))
                .thenReturn(List.of(link(UUID.randomUUID(), unitId, customerId, UnitCustomer.Role.USER,
                        LocalDate.now().minusYears(1), null)));
        var recorded = service.recordDocumentValidation(
                new ValidationInput(customerId, "CPF", "DECEASED", null, false));
        assertThat(recorded.deathAlertIds()).hasSize(1);
        ArgumentCaptor<HolderDeathAlert> captor = ArgumentCaptor.forClass(HolderDeathAlert.class);
        verify(deathAlerts).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUnitId()).isEqualTo(unitId);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_REVIEW");
        var irregular = service.recordDocumentValidation(
                new ValidationInput(customerId, "CPF", "IRREGULAR", null, false));
        assertThat(irregular.irregularAlert()).isTrue();
        assertThat(irregular.deathAlertIds()).isEmpty();
    }

    @Test
    @DisplayName("troca de titularidade: NONE libera; STATUS_ONLY exige REGULAR; biometria exige flag")
    void transferValidationModes() {
        UUID customerId = UUID.randomUUID();
        when(customers.findById(customerId))
                .thenReturn(Optional.of(customer(customerId, Customer.PersonType.PF, "Novo")));
        when(validations.findByTenantIdAndCustomerIdAndResultOrderByValidatedAtDesc(
                TENANT, customerId, "REGULAR")).thenReturn(List.of());
        service.validateForOwnershipTransfer(customerId);
        when(settings.current()).thenReturn(settingsWithMode("STATUS_ONLY"));
        assertThatThrownBy(() -> service.validateForOwnershipTransfer(customerId))
                .isInstanceOf(ApiException.Validation.class);
        CustomerDocumentValidation regular = new CustomerDocumentValidation();
        regular.setResult("REGULAR");
        regular.setBiometricVerified(false);
        when(validations.findByTenantIdAndCustomerIdAndResultOrderByValidatedAtDesc(
                TENANT, customerId, "REGULAR")).thenReturn(List.of(regular));
        service.validateForOwnershipTransfer(customerId);
        when(settings.current()).thenReturn(settingsWithMode("STATUS_AND_BIOMETRICS"));
        assertThatThrownBy(() -> service.validateForOwnershipTransfer(customerId))
                .isInstanceOf(ApiException.Validation.class);
        regular.setBiometricVerified(true);
        service.validateForOwnershipTransfer(customerId);
    }

    private static HolderSettingsService.Settings settingsWithMode(String mode) {
        return new HolderSettingsService.Settings("CONTINUE_SERVICE_SUSPEND_CHARGES", "NO_HOLDER_BILL_HELD",
                "REQUIRED", "SOCIAL_EVERYWHERE_EXCEPT_FISCAL", mode, null, "DISABLED", false,
                "USER_HOLDER_ONLY");
    }

    // ---------------------------------------------------------- ocupação

    @Test
    @DisplayName("ocupação: VACANT com usuário vigente é recusado antes de abrir OS")
    void vacantRequiresNoUser() {
        UUID unitId = UUID.randomUUID();
        when(units.findById(unitId)).thenReturn(Optional.of(unit(unitId, "OCCUPIED")));
        when(unitCustomers.findByTenantIdAndUnitIdAndEndDateIsNull(TENANT, unitId))
                .thenReturn(List.of(link(UUID.randomUUID(), unitId, UUID.randomUUID(),
                        UnitCustomer.Role.USER, LocalDate.now().minusYears(1), null)));
        assertThatThrownBy(() -> service.requestOccupancyChange(unitId, new OccupancyInput("VACANT")))
                .isInstanceOf(ApiException.Validation.class);
        verify(orders, never()).openRequiringApproval(any());
    }

    // ---------------------------------------------------------- fusão + reversa

    @Test
    @DisplayName("fusão: aprova move o vínculo e exclui a origem; reversa devolve tudo")
    void mergeAndReverse() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID endReasonId = UUID.randomUUID();
        Customer source = customer(sourceId, Customer.PersonType.PF, "Origem");
        Customer target = customer(targetId, Customer.PersonType.PF, "Destino");
        when(customers.findById(sourceId)).thenReturn(Optional.of(source));
        when(customers.findById(targetId)).thenReturn(Optional.of(target));
        when(catalogs.find("unit_link_end_reasons", endReasonId))
                .thenReturn(new CatalogLookup.CatalogRow(endReasonId, "MERGE", "Fusão", true, Map.of()));
        UnitCustomer sourceLink = link(UUID.randomUUID(), unitId, sourceId, UnitCustomer.Role.USER,
                LocalDate.now().minusYears(1), null);
        when(unitCustomers.findByTenantIdAndCustomerId(TENANT, sourceId)).thenReturn(List.of(sourceLink));
        when(unitCustomers.findByTenantIdAndCustomerId(TENANT, targetId)).thenReturn(List.of());
        when(unitCustomers.findByTenantIdAndCustomerIdAndEndDateIsNull(TENANT, sourceId)).thenReturn(List.of());
        var requested = service.requestMerge(new MergeInput(sourceId, targetId, endReasonId));
        assertThat(requested.status()).isEqualTo("PENDING_APPROVAL");
        ArgumentCaptor<CustomerMerge> captor = ArgumentCaptor.forClass(CustomerMerge.class);
        verify(merges).saveAndFlush(captor.capture());
        CustomerMerge merge = captor.getValue();
        when(merges.findById(merge.getId())).thenReturn(Optional.of(merge));
        TenantContextHolder.setUserId(APPROVER);
        var applied = service.approveMerge(merge.getId());
        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(sourceLink.getCustomerId()).isEqualTo(targetId);
        assertThat(source.getDeletedAt()).isNotNull();
        var reverseRequested = service.requestMergeReverse(merge.getId());
        assertThat(reverseRequested.reversedByServiceOrderId()).isEqualTo(REVERSE_ORDER_ID);
        when(orders.getOrder(REVERSE_ORDER_ID)).thenReturn(orderView(CREATOR, ServiceOrderStatus.PENDING_APPROVAL));
        when(unitCustomers.findById(sourceLink.getId())).thenReturn(Optional.of(sourceLink));
        var reversed = service.approveMergeReverse(merge.getId());
        assertThat(reversed.status()).isEqualTo("REVERSED");
        assertThat(sourceLink.getCustomerId()).isEqualTo(sourceId);
        assertThat(source.getDeletedAt()).isNull();
        verify(orders).approveExecuteAndClose(REVERSE_ORDER_ID, APPROVER);
    }
}
