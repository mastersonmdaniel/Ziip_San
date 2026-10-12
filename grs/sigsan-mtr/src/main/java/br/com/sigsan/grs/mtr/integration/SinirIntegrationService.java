package br.com.sigsan.grs.mtr.integration;

import br.com.sigsan.grs.mtr.domain.WasteMtr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Integração com o SINIR (Sistema Nacional de Informações sobre Resíduos Sólidos).
 *
 * IMPORTANTE — IMPLEMENTAÇÃO PENDENTE:
 * Este serviço monta e valida o payload de envio conforme o layout do MTR, porém
 * NÃO executa a chamada HTTP ao SINIR. Não há endpoint oficial publicado nem
 * credenciais (certificado ICP-Brasil / token) disponíveis neste ambiente, e
 * inventar uma URL ou devolver sucesso simulado produziria um MTR marcado como
 * sincronizado no banco sem nunca ter chegado ao órgão regulador.
 *
 * O método {@link #syncMtr(WasteMtr, PartyDocuments)} sempre devolve
 * {@link SinirSyncStatus#PENDING} enquanto a integração não for implementada.
 * A persistência do status é feita pelo chamador transacional.
 *
 * Documentos: desde a V2_8 o MTR guarda o CPF do motorista e o documento do gerador só cifrados.
 * O SINIR exige os documentos completos, então o chamador decifra e entrega os valores em
 * {@link PartyDocuments}; este serviço não tem acesso à chave e não lê a cifra.
 */
@Slf4j
@Service
public class SinirIntegrationService {

    private final String baseUrl;
    private final boolean enabled;

    public SinirIntegrationService(
            @Value("${sinir.base-url:}") String baseUrl,
            @Value("${sinir.enabled:false}") boolean enabled) {
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    /**
     * Chamado por MtrService em collectMtr e destinateMtr.
     * O valor de retorno é ignorado pelo chamador; serve para registrar o
     * resultado da tentativa de sincronização.
     */
    public SinirSyncResult syncMtr(WasteMtr mtr, PartyDocuments documents) {
        SinirMtrRequest request = buildRequest(mtr, documents);

        if (!enabled || baseUrl == null || baseUrl.isBlank()) {
            log.warn("SINIR não configurado (enabled={}, baseUrl='{}'). MTR {} permanece PENDENTE de sincronização.",
                    enabled, baseUrl, request.mtrNumber());
            return SinirSyncResult.pending(request.mtrNumber(), "SINIR não configurado: endpoint e credenciais ausentes");
        }

        // Não prossegue: sem implementação de cliente HTTP autenticado.
        // Ver relatório de implantação antes de habilitar sinir.enabled=true.
        log.warn("SINIR habilitado mas sem cliente HTTP implementado. MTR {} permanece PENDENTE.", request.mtrNumber());
        return SinirSyncResult.pending(request.mtrNumber(), "Cliente HTTP do SINIR não implementado");
    }

    /**
     * Monta o payload de envio aplicando as validações obrigatórias do MTR.
     * Lança IllegalArgumentException quando o manifesto é inválido — falha cedo,
     * antes de qualquer tentativa de rede.
     */
    public SinirMtrRequest buildRequest(WasteMtr mtr, PartyDocuments documents) {
        if (mtr == null) {
            throw new IllegalArgumentException("MTR não informado para sincronização SINIR");
        }
        if (documents == null) {
            throw new IllegalArgumentException("Documentos das partes não informados para sincronização SINIR");
        }
        requireText(mtr.getMtrNumber(), "mtrNumber");
        requireText(mtr.getStatus() != null ? mtr.getStatus().name() : null, "status");
        requireText(mtr.getIssuedAt() != null ? mtr.getIssuedAt().toString() : null, "issuedAt");

        if (mtr.getGenerator() == null) {
            throw new IllegalArgumentException("MTR " + mtr.getMtrNumber() + " sem gerador");
        }
        requireText(documents.generatorDocument(), "generator.document");

        if (mtr.getTransporter() == null) {
            throw new IllegalArgumentException("MTR " + mtr.getMtrNumber() + " sem transportador");
        }
        requireText(mtr.getTransporter().getCnpj(), "transporter.cnpj");

        if (mtr.getDestination() == null) {
            throw new IllegalArgumentException("MTR " + mtr.getMtrNumber() + " sem destinador");
        }
        requireText(mtr.getDestination().getCnpj(), "destination.cnpj");

        if (mtr.getWasteClass() == null) {
            throw new IllegalArgumentException("MTR " + mtr.getMtrNumber() + " sem classe de resíduo");
        }
        requireText(mtr.getWasteClass().getCode(), "wasteClass.code");

        BigDecimal quantityKg = mtr.getQuantityKg();
        if (quantityKg == null || quantityKg.signum() <= 0) {
            throw new IllegalArgumentException("MTR " + mtr.getMtrNumber() + " com quantidade inválida: " + quantityKg);
        }

        return new SinirMtrRequest(
                mtr.getMtrNumber(),
                mtr.getStatus().name(),
                documents.generatorDocument(),
                mtr.getTransporter().getCnpj(),
                mtr.getDestination().getCnpj(),
                mtr.getWasteClass().getCode(),
                mtr.getWasteClass().getSinirCode(),
                quantityKg,
                mtr.getPackagingType(),
                mtr.getPackagingCount(),
                mtr.getVehiclePlate(),
                documents.driverCpf(),
                mtr.getIssuedAt(),
                mtr.getCollectedAt(),
                mtr.getReceivedAt(),
                mtr.getHashChain()
        );
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no MTR para envio ao SINIR: " + field);
        }
    }

    /** Status do envio ao SINIR. NUNCA retorna SYNCED sem protocolo real. */
    public enum SinirSyncStatus {
        PENDING,
        SYNCING,
        SYNCED,
        FAILED,
        RETRYING
    }

    /**
     * Documentos em claro das partes do MTR, decifrados pelo chamador só para o envio.
     * {@code driverCpf} é nulo quando o MTR ainda não tem motorista.
     */
    public record PartyDocuments(String generatorDocument, String driverCpf) {
        @Override
        public String toString() {
            // O record entraria inteiro num log com o toString padrão.
            return "PartyDocuments[documentos omitidos]";
        }
    }

    /** Payload de envio do MTR ao SINIR. Espelha WasteMtr, sem dependência de JPA. */
    public record SinirMtrRequest(
            String mtrNumber,
            String status,
            String generatorCnpjCpf,
            String transporterCnpj,
            String destinationCnpj,
            String wasteClassCode,
            String wasteClassSinirCode,
            BigDecimal quantityKg,
            String packagingType,
            Integer packagingCount,
            String vehiclePlate,
            String driverCpf,
            OffsetDateTime issuedAt,
            OffsetDateTime collectedAt,
            OffsetDateTime receivedAt,
            String hashChain
    ) {
        @Override
        public String toString() {
            // Leva o CPF do motorista e o documento do gerador em claro (o SINIR exige): o toString
            // padrão os poria em qualquer log que imprimisse o payload.
            return "SinirMtrRequest[mtrNumber=" + mtrNumber + ", status=" + status + ", documentos omitidos]";
        }
    }

    /** Resposta da tentativa de sincronização. */
    public record SinirSyncResult(
            String mtrNumber,
            SinirSyncStatus status,
            String protocol,
            String reason,
            OffsetDateTime attemptedAt
    ) {
        public static SinirSyncResult pending(String mtrNumber, String reason) {
            return new SinirSyncResult(mtrNumber, SinirSyncStatus.PENDING, null, reason, OffsetDateTime.now());
        }
    }
}