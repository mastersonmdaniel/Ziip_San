package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.domain.WasteTransporter;
import br.com.ziipsan.security.crypto.FieldProtector;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Documentos pessoais do MTR: o único caminho para gravar e ler o CPF do motorista, o CPF/CNPJ do
 * gerador e a lista de motoristas do transportador.
 *
 * <p>Até a V2_8 os três ficavam em claro nas tabelas (e, antes da E4, também na trilha de auditoria).
 * Agora o banco guarda só a cifra (AES-256-GCM, chave do tenant), o índice cego (HMAC, para unicidade
 * e busca) e a máscara. O valor em claro só existe em memória, para quem precisa dele de fato: a
 * cadeia de hash (o significado do hash não pode mudar com a cifra) e o envio ao SINIR (o órgão exige
 * o documento). O PDF recebe o documento e decide o que mascarar (decisão S2).
 *
 * <p>O contexto de cada campo entra na AAD da cifra e no HMAC: um valor protegido como CPF do
 * motorista não decifra nem casa como documento do gerador. O tenant vem sempre do
 * {@link TenantContextHolder}; sem ele, erro.
 *
 * <p>O bean de {@link FieldProtector} é montado pela aplicação (CryptoConfig, E5).
 */
@Component
public class MtrDocumentProtection {

    public static final String DRIVER_CPF = "waste_mtr.driver_cpf";
    public static final String GENERATOR_DOCUMENT = "waste_generator.document";
    public static final String TRANSPORTER_DRIVERS = "waste_transporter.drivers";

    private final FieldProtector protector;

    public MtrDocumentProtection(FieldProtector protector) {
        this.protector = protector;
    }

    /** Motorista na lista do transportador. Datas em ISO-8601 (yyyy-MM-dd). */
    public record Driver(String name, String cpf, String cnh, LocalDate cnhExpiry, LocalDate moppExpiry) {
    }

    // ------------------------------------------------------------------ CPF do motorista (MTR)

    /**
     * Grava o CPF do motorista no MTR (cifra + índice + máscara). Nulo ou em branco limpa os três: o
     * CPF é opcional na emissão e informado na coleta.
     *
     * @throws IllegalArgumentException CPF inválido (inclusive um CNPJ: motorista é pessoa física)
     */
    public void assignDriverCpf(WasteMtr mtr, String rawCpf) {
        UUID tenantId = requireTenant();
        if (rawCpf == null || rawCpf.isBlank()) {
            mtr.setDriverCpfCiphertext(null);
            mtr.setDriverCpfHash(null);
            mtr.setDriverCpfMasked(null);
            return;
        }
        if (!DocumentNumber.isValidCpf(rawCpf)) {
            throw new IllegalArgumentException("CPF do motorista inválido");
        }
        String normalized = DocumentNumber.normalize(rawCpf);
        var value = protector.protect(tenantId, DRIVER_CPF, normalized, DocumentNumber.mask(normalized));
        mtr.setDriverCpfCiphertext(value.ciphertext());
        mtr.setDriverCpfHash(value.hash());
        mtr.setDriverCpfMasked(value.masked());
    }

    /**
     * P0.12: recalcula a máscara gravada do CPF do motorista com o teto vigente. Sem CPF
     * cifrado, nada a fazer. Devolve verdadeiro quando regravou (quem chama persiste).
     */
    public boolean recalculateDriverCpfMask(WasteMtr mtr) {
        if (mtr.getDriverCpfCiphertext() == null) {
            return false;
        }
        String fresh = DocumentNumber.mask(revealDriverCpf(mtr));
        if (fresh.equals(mtr.getDriverCpfMasked())) {
            return false;
        }
        mtr.setDriverCpfMasked(fresh);
        return true;
    }

    /** CPF do motorista em claro (só dígitos), ou {@code null} se o MTR não tem motorista. */
    public String revealDriverCpf(WasteMtr mtr) {
        UUID tenantId = requireTenant();
        String ciphertext = mtr.getDriverCpfCiphertext();
        return ciphertext == null ? null : protector.reveal(tenantId, DRIVER_CPF, ciphertext);
    }

    // ------------------------------------------------------------------ documento do gerador

    /**
     * Grava o CPF/CNPJ do gerador (cifra + índice + máscara). Não verifica duplicidade: quem cadastra
     * consulta {@link #generatorDocumentHash(String)} antes (e o índice único do banco é a última
     * barreira).
     *
     * @throws IllegalArgumentException documento ausente ou inválido
     */
    public void assignGeneratorDocument(WasteGenerator generator, String rawDocument) {
        UUID tenantId = requireTenant();
        String normalized = requireValidDocument(rawDocument);
        var value = protector.protect(tenantId, GENERATOR_DOCUMENT, normalized, DocumentNumber.mask(normalized));
        generator.setDocumentCiphertext(value.ciphertext());
        generator.setDocumentHash(value.hash());
        generator.setDocumentMasked(value.masked());
    }

    /**
     * Índice cego do documento do gerador, para procurar duplicidade sem decifrar nada.
     *
     * @throws IllegalArgumentException documento ausente ou inválido
     */
    public String generatorDocumentHash(String rawDocument) {
        UUID tenantId = requireTenant();
        return protector.indexOf(tenantId, GENERATOR_DOCUMENT, requireValidDocument(rawDocument));
    }

    /**
     * P0.12: recalcula a máscara gravada do documento do gerador com o teto vigente.
     * Devolve verdadeiro quando regravou (quem chama persiste). A lista de motoristas do
     * transportador não tem coluna de máscara (só cifra), então não entra no recálculo.
     */
    public boolean recalculateGeneratorDocumentMask(WasteGenerator generator) {
        if (generator.getDocumentCiphertext() == null) {
            return false;
        }
        String fresh = DocumentNumber.mask(revealGeneratorDocument(generator));
        if (fresh.equals(generator.getDocumentMasked())) {
            return false;
        }
        generator.setDocumentMasked(fresh);
        return true;
    }

    /** CPF/CNPJ do gerador em claro (normalizado). */
    public String revealGeneratorDocument(WasteGenerator generator) {
        UUID tenantId = requireTenant();
        if (generator.getDocumentCiphertext() == null) {
            throw new IllegalStateException("gerador sem documento cifrado");
        }
        return protector.reveal(tenantId, GENERATOR_DOCUMENT, generator.getDocumentCiphertext());
    }

    // ------------------------------------------------------------------ motoristas do transportador

    /**
     * Grava a lista de motoristas do transportador, cifrada inteira. Lista nula ou vazia limpa a
     * coluna. Cada CPF é validado e normalizado antes da cifra.
     *
     * <p>Só a cifra é guardada: o índice cego de uma lista inteira não serve para busca, e a máscara
     * não tem tela que a use. {@link FieldProtector#protect} calcula os dois; aqui são descartados.
     *
     * @throws IllegalArgumentException motorista sem nome ou com CPF ausente ou inválido
     */
    public void assignDrivers(WasteTransporter transporter, List<Driver> drivers) {
        UUID tenantId = requireTenant();
        if (drivers == null || drivers.isEmpty()) {
            transporter.setDriversCiphertext(null);
            return;
        }
        List<Map<String, String>> rows = new ArrayList<>(drivers.size());
        for (int i = 0; i < drivers.size(); i++) {
            Driver d = drivers.get(i);
            if (d == null || d.name() == null || d.name().isBlank()) {
                throw new IllegalArgumentException("motorista " + (i + 1) + " sem nome");
            }
            if (!DocumentNumber.isValidCpf(d.cpf())) {
                throw new IllegalArgumentException("motorista " + (i + 1) + " com CPF inválido");
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", d.name().trim());
            row.put("cpf", DocumentNumber.normalize(d.cpf()));
            row.put("cnh", d.cnh());
            row.put("cnhExpiry", d.cnhExpiry() == null ? null : d.cnhExpiry().toString());
            row.put("moppExpiry", d.moppExpiry() == null ? null : d.moppExpiry().toString());
            rows.add(row);
        }
        var value = protector.protect(tenantId, TRANSPORTER_DRIVERS, Json.write(rows), null);
        transporter.setDriversCiphertext(value.ciphertext());
    }

    /** Lista de motoristas em claro; vazia se o transportador não tem motoristas cadastrados. */
    public List<Driver> revealDrivers(WasteTransporter transporter) {
        UUID tenantId = requireTenant();
        if (transporter.getDriversCiphertext() == null) {
            return List.of();
        }
        JsonNode list = Json.readTree(protector.reveal(tenantId, TRANSPORTER_DRIVERS, transporter.getDriversCiphertext()));
        if (!list.isArray()) {
            throw new IllegalStateException("lista de motoristas decifrada não é um array JSON");
        }
        List<Driver> drivers = new ArrayList<>(list.size());
        for (JsonNode item : list) {
            drivers.add(new Driver(text(item, "name"), text(item, "cpf"), text(item, "cnh"),
                    date(item, "cnhExpiry"), date(item, "moppExpiry")));
        }
        return List.copyOf(drivers);
    }

    // ------------------------------------------------------------------

    private static String requireValidDocument(String rawDocument) {
        if (DocumentNumber.kind(rawDocument) == DocumentNumber.Kind.INVALID) {
            throw new IllegalArgumentException("CPF/CNPJ do gerador inválido");
        }
        return DocumentNumber.normalize(rawDocument);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("data inválida na lista de motoristas: " + field, e);
        }
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto: documento do MTR não pode ser processado");
        }
        return tenantId;
    }
}
