package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.commercial.registry.application.RegistryExportService;
import br.com.ziipsan.commercial.registry.domain.GeneratedFile;
import br.com.ziipsan.commercial.registry.repository.GeneratedFileRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.infra.queue.OutboxMessage;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1.12: o handler da exportação assíncrona — idempotente e com tenant explícito. */
@ExtendWith(MockitoExtension.class)
class RegistryExportHandlerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();

    @Mock
    private TenantCatalog catalog;
    @Mock
    private RegistryExportService exports;
    @Mock
    private GeneratedFileRepository files;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private RegistryExportHandler handler() {
        return new RegistryExportHandler(catalog, exports, files);
    }

    private OutboxMessage message() {
        String payload = "{\"generatedFileId\":\"" + FILE_ID + "\",\"resource\":\"customers\",\"filters\":{}}";
        return new OutboxMessage(UUID.randomUUID(), TENANT, "default", RegistryExportService.EXPORT_JOB_TYPE, payload, 0);
    }

    private void activeTenant() {
        when(catalog.find(TENANT)).thenReturn(Optional.of(new TenantCatalog.Entry(TENANT, "tenant_schema_teste", "ACTIVE")));
    }

    private GeneratedFile file(GeneratedFile.Status status) {
        GeneratedFile file = new GeneratedFile();
        file.setId(FILE_ID);
        file.setTenantId(TENANT);
        file.setStatus(status);
        return file;
    }

    @Test
    @DisplayName("arquivo QUEUED vira READY com o CSV e a contagem; contexto do tenant é limpo depois")
    void queuedBecomesReady() throws Exception {
        activeTenant();
        GeneratedFile queued = file(GeneratedFile.Status.QUEUED);
        when(files.findByIdAndTenantId(FILE_ID, TENANT)).thenReturn(Optional.of(queued));
        when(exports.buildAsyncCsv("customers", Map.of()))
                .thenReturn(new RegistryExportService.CsvResult(new byte[]{9, 8}, 42));

        handler().handle(message());

        ArgumentCaptor<GeneratedFile> captor = ArgumentCaptor.forClass(GeneratedFile.class);
        verify(files).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GeneratedFile.Status.READY);
        assertThat(captor.getValue().getContent()).containsExactly((byte) 9, (byte) 8);
        assertThat(captor.getValue().getRowCount()).isEqualTo(42);
        assertThat(captor.getValue().getReadyAt()).isNotNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("arquivo já READY não é regenerado (entrega repetida da outbox)")
    void readyIsNotRegenerated() throws Exception {
        activeTenant();
        when(files.findByIdAndTenantId(FILE_ID, TENANT)).thenReturn(Optional.of(file(GeneratedFile.Status.READY)));

        handler().handle(message());

        verify(exports, never()).buildAsyncCsv(any(), any());
        verify(files, never()).save(any());
    }

    @Test
    @DisplayName("tenant inativo ou arquivo inexistente falha (vai para nova tentativa/dead-letter, nunca some)")
    void inactiveTenantOrMissingFileFails() {
        when(catalog.find(TENANT)).thenReturn(Optional.of(new TenantCatalog.Entry(TENANT, "s", "SUSPENDED")));
        assertThatThrownBy(() -> handler().handle(message())).isInstanceOf(IllegalStateException.class);

        activeTenant();
        when(files.findByIdAndTenantId(FILE_ID, TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler().handle(message())).isInstanceOf(IllegalStateException.class);
    }
}
