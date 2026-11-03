package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.commercial.registry.domain.GeneratedFile;
import br.com.ziipsan.commercial.registry.repository.GeneratedFileRepository;
import br.com.ziipsan.commercial.registry.service.PersonalDataAccessService;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.infra.queue.MessageQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1.12: a exportação usa SÓ o documento mascarado do resumo da lista (o mesmo
 * da tela, ADR-0008) — nenhum campo de documento em claro existe nos resumos,
 * e o CSV prova isso: contém a máscara e nunca um valor em claro. O teste usa
 * valores obviamente falsos (prefixo TESTE).
 */
@ExtendWith(MockitoExtension.class)
class RegistryExportServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private CustomerService customers;
    @Mock
    private ConsumerUnitService units;
    @Mock
    private PersonalDataAccessService accessLog;
    @Mock
    private GeneratedFileRepository files;
    @Mock
    private MessageQueue queue;

    private RegistryExportService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        service = new RegistryExportService(customers, units, accessLog, files, queue, 1000);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private static CustomerDtos.CustomerSummary customer(String name, String masked, long unitCount) {
        return new CustomerDtos.CustomerSummary(UUID.randomUUID(), name, Customer.PersonType.PF, masked, unitCount, false);
    }

    @Test
    @DisplayName("CSV de clientes traz a máscara da tela, com citação RFC 4180, e registra rows=")
    void customersCsvUsesMaskedDocument() {
        when(customers.search(any())).thenReturn(new PageResult<>(List.of(
                customer("TESTE Silva, João", "***.***.789-**", 2),
                customer("TESTE \"Aspas\"", "***.***.123-**", 0)), 0, 100, 2, 1));

        RegistryExportService.Result result = service.exportCustomers(Map.of());

        assertThat(result).isInstanceOf(RegistryExportService.Sync.class);
        RegistryExportService.Sync sync = (RegistryExportService.Sync) result;
        String csv = new String(sync.csv(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFNome,Tipo de pessoa,Documento,Imóveis,Excluído\r\n");
        assertThat(csv).contains("\"TESTE Silva, João\",PF,***.***.789-**,2,Não");
        assertThat(csv).contains("\"TESTE \"\"Aspas\"\"\",PF,***.***.123-**,0,Não");
        assertThat(csv).doesNotContain("52998224725");
        assertThat(sync.rows()).isEqualTo(2);
        verify(accessLog).recordExport("customers", "rows=2");
        verify(queue, never()).publish(any());
    }

    @Test
    @DisplayName("acima do limite, enfileira na outbox com o id do arquivo como dedup e registra queued")
    void overLimitEnqueues() {
        RegistryExportService limited = new RegistryExportService(customers, units, accessLog, files, queue, 1);
        when(customers.search(any())).thenReturn(new PageResult<>(List.of(
                customer("TESTE Um", "***.***.789-**", 1),
                customer("TESTE Dois", "***.***.123-**", 1)), 0, 100, 2, 1));
        doAnswer(inv -> inv.getArgument(0)).when(files).save(any());

        RegistryExportService.Result result = limited.exportCustomers(Map.of());

        assertThat(result).isInstanceOf(RegistryExportService.Queued.class);
        ArgumentCaptor<GeneratedFile> fileCaptor = ArgumentCaptor.forClass(GeneratedFile.class);
        verify(files).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStatus()).isEqualTo(GeneratedFile.Status.QUEUED);
        assertThat(fileCaptor.getValue().getTenantId()).isEqualTo(TENANT);
        ArgumentCaptor<MessageQueue.QueuedMessage> msgCaptor = ArgumentCaptor.forClass(MessageQueue.QueuedMessage.class);
        verify(queue).publish(msgCaptor.capture());
        assertThat(msgCaptor.getValue().type()).isEqualTo(RegistryExportService.EXPORT_JOB_TYPE);
        assertThat(msgCaptor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(msgCaptor.getValue().messageId()).isEqualTo(fileCaptor.getValue().getId());
        assertThat(msgCaptor.getValue().payloadJson()).contains("\"resource\":\"customers\"");
        verify(accessLog).recordExport("customers", "queued");
    }

    @Test
    @DisplayName("sem registry:export, 403 antes de qualquer consulta")
    void deniedBeforeQuery() {
        doThrow(new ApiException.Forbidden("sem permissão")).when(accessLog).authorizeExport("customers");

        assertThatThrownBy(() -> service.exportCustomers(Map.of())).isInstanceOf(ApiException.Forbidden.class);
        verify(customers, never()).search(any());
    }

    @Test
    @DisplayName("CSV de imóveis traz o documento mascarado do titular, não o em claro")
    void unitsCsvUsesMaskedHolderDocument() {
        when(units.search(any())).thenReturn(new PageResult<>(List.of(
                new ConsumerUnitDtos.UnitSummary(UUID.randomUUID(), "1234567", "9", null, "1.2.3.4.5",
                        "TESTE Titular", "***.***.789-**", "TESTE Rua Um, 10", "TESTE Categoria",
                        "TESTE Água", "TESTE Esgoto", 1, "ACTIVE", false)), 0, 100, 1, 1));

        RegistryExportService.Sync sync = (RegistryExportService.Sync) service.exportUnits(Map.of());
        String csv = new String(sync.csv(), StandardCharsets.UTF_8);
        assertThat(csv).contains("1234567-9,1.2.3.4.5,TESTE Titular,***.***.789-**");
        assertThat(csv).contains("\"TESTE Rua Um, 10\"");
        verify(accessLog).recordExport("consumer-units", "rows=1");
    }

    @Test
    @DisplayName("toCsv cita vírgula, aspa e quebra de linha")
    void toCsvQuotes() {
        byte[] csv = RegistryExportService.toCsv(List.of("A", "B"),
                List.of(List.of("x,y", "q\"q\nq")));
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("\"x,y\",\"q\"\"q\nq\"\r\n");
    }

    @Test
    @DisplayName("download: dono baixa; terceiro sem registry:export recebe 403; sem READY, 404")
    void downloadRules() {
        GeneratedFile ready = new GeneratedFile();
        ready.setId(UUID.randomUUID());
        ready.setTenantId(TENANT);
        ready.setUserId(USER);
        ready.setStatus(GeneratedFile.Status.READY);
        ready.setContent(new byte[]{1});
        when(files.findByIdAndTenantId(ready.getId(), TENANT)).thenReturn(Optional.of(ready));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("u", null, List.of(new SimpleGrantedAuthority("registry:read"))));
        assertThat(service.downloadGeneratedFile(ready.getId())).isSameAs(ready);

        GeneratedFile others = new GeneratedFile();
        others.setId(UUID.randomUUID());
        others.setTenantId(TENANT);
        others.setUserId(UUID.randomUUID());
        others.setStatus(GeneratedFile.Status.READY);
        when(files.findByIdAndTenantId(others.getId(), TENANT)).thenReturn(Optional.of(others));
        assertThatThrownBy(() -> service.downloadGeneratedFile(others.getId()))
                .isInstanceOf(ApiException.Forbidden.class);

        GeneratedFile queued = new GeneratedFile();
        queued.setId(UUID.randomUUID());
        queued.setTenantId(TENANT);
        queued.setUserId(USER);
        queued.setStatus(GeneratedFile.Status.QUEUED);
        when(files.findByIdAndTenantId(queued.getId(), TENANT)).thenReturn(Optional.of(queued));
        assertThatThrownBy(() -> service.downloadGeneratedFile(queued.getId()))
                .isInstanceOf(ApiException.NotFound.class);

        when(files.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.downloadGeneratedFile(UUID.randomUUID()))
                .isInstanceOf(ApiException.NotFound.class);
        // O mock de findByIdAndTenantId(any, any) vale a partir daqui; os
        // retornos específicos acima já foram consumidos na ordem dos stubs.
        verify(files, org.mockito.Mockito.atLeastOnce()).findByIdAndTenantId(any(), any());
    }
}
