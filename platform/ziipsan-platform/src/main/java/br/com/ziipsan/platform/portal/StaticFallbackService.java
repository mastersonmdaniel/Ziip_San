package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.security.crypto.FieldCipher;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Modo de contingencia estatico (PLT-40, P3.3): no FECHAMENTO de cada
 * conta, grava-se um objeto CIFRADO com a 2a via e o Pix copia e cola.
 * Quando a api ou o banco estao indisponiveis, a borda serve esse objeto
 * por link assinado de curta duracao, enviado ao contato VERIFICADO do
 * titular, com expiracao e revogacao ({@code portal.static_fallback_enabled},
 * classe d).
 *
 * <p>Esta classe nao depende de nenhuma API de faturamento em tempo de
 * leitura: construir, assinar, verificar, expirar e revogar operam so
 * sobre o objeto gravado e a porta de assinatura — e assim que o teste
 * "api derrubada" e exercitado. A servicao pela borda e o envio ao
 * contato sao da integracao/LC (pendencia declarada).
 */
public final class StaticFallbackService {

    public static final String CONTEXT = "portal.static_fallback";

    /** Objeto de contingencia gravado no fechamento (so cifra; nunca PDF/Pix em claro). */
    public record FallbackObject(UUID tenantId, UUID billId, UUID holderCustomerId,
                                 String ciphertext, Instant createdAt) { }

    /** Porta de assinatura do link (adaptador de borda/KMS = integracao). */
    public interface LinkSigner {
        String sign(String payload);
        boolean verify(String payload, String signature);
    }

    /** Porta de armazenamento dos objetos (borda/objeto = integracao). */
    public interface FallbackStore {
        void put(FallbackObject object);
        java.util.Optional<FallbackObject> get(UUID tenantId, UUID billId);
    }

    private final FieldCipher cipher;
    private final LinkSigner signer;
    private final FallbackStore store;

    public StaticFallbackService(FieldCipher cipher, LinkSigner signer, FallbackStore store) {
        if (cipher == null || signer == null || store == null) {
            throw new IllegalArgumentException("cipher, signer e store sao obrigatorios");
        }
        this.cipher = cipher;
        this.signer = signer;
        this.store = store;
    }

    /** Fecha a conta -> grava o objeto cifrado (2a via em PDF base64 + Pix copia e cola). */
    public FallbackObject buildAtClosing(UUID tenantId, UUID billId, UUID holderCustomerId,
                                         String secondCopyPdfBase64, String pixCopyPaste, Instant now) {
        if (tenantId == null || billId == null || holderCustomerId == null || now == null
                || secondCopyPdfBase64 == null || secondCopyPdfBase64.isBlank()
                || pixCopyPaste == null || pixCopyPaste.isBlank()) {
            throw new IllegalArgumentException("tenant, conta, titular, PDF e Pix sao obrigatorios no fechamento");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("billId", billId.toString());
        payload.put("secondCopyPdfBase64", secondCopyPdfBase64);
        payload.put("pixCopyPaste", pixCopyPaste);
        String ciphertext = cipher.encrypt(tenantId, CONTEXT, Json.write(payload));
        FallbackObject object = new FallbackObject(tenantId, billId, holderCustomerId, ciphertext, now);
        store.put(object);
        return object;
    }

    /**
     * Link assinado de curta duracao para o contato verificado.
     * @param contactVerified contato do titular verificado (sem verificacao, sem link)
     */
    public String issueSignedLink(FallbackObject object, boolean contactVerified,
                                  Instant now, java.time.Duration ttl) {
        if (!contactVerified) {
            throw new ApiException.Forbidden("Link de contingencia so vai ao contato verificado do titular.");
        }
        if (object == null || object.tenantId() == null || object.billId() == null
                || now == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("objeto, instante e TTL positivo sao obrigatorios");
        }
        Instant expires = now.plus(ttl);
        String payload = object.tenantId() + ":" + object.billId() + ":" + expires.getEpochSecond();
        return payload + "." + signer.sign(payload);
    }

    /** Verifica assinatura, expiracao e revogacao; devolve o conteudo decifrado. */
    public Map<String, Object> openSignedLink(String link, Set<UUID> revokedBillIds, Instant now) {
        if (link == null || now == null) {
            throw new IllegalArgumentException("link e instante sao obrigatorios");
        }
        int dot = link.lastIndexOf('.');
        if (dot < 0) {
            throw new ApiException.Forbidden("Link de contingencia invalido.");
        }
        String payload = link.substring(0, dot);
        String signature = link.substring(dot + 1);
        String[] parts = payload.split(":");
        if (parts.length != 3 || !signer.verify(payload, signature)) {
            throw new ApiException.Forbidden("Link de contingencia invalido.");
        }
        UUID tenantId = UUID.fromString(parts[0]);
        UUID billId = UUID.fromString(parts[1]);
        Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[2]));
        if (!now.isBefore(expires)) {
            throw new ApiException.Forbidden("Link de contingencia expirado.");
        }
        if (revokedBillIds != null && revokedBillIds.contains(billId)) {
            throw new ApiException.Forbidden("Link de contingencia revogado.");
        }
        FallbackObject object = store.get(tenantId, billId)
                .orElseThrow(() -> new ApiException.NotFound("Objeto de contingencia nao encontrado."));
        String json = cipher.decrypt(tenantId, CONTEXT, object.ciphertext());
        return Json.readMap(json);
    }
}
