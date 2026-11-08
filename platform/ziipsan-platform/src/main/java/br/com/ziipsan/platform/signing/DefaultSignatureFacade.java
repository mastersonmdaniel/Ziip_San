package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implementacao padrao da porta dos controllers sobre o
 * {@link SignatureService}: serializa com a classe {@code Json} do core
 * (regra do projeto — nada de montagem manual nem ObjectMapper fora dela).
 */
public final class DefaultSignatureFacade implements SignatureService.SignatureFacade {

    private final SignatureService service;

    public DefaultSignatureFacade(SignatureService service) {
        this.service = Objects.requireNonNull(service, "service e obrigatorio");
    }

    @Override
    public String signJson(SignatureService.SignCommand command) {
        DocumentSigner.SignatureRecord record = service.sign(command);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("signatureId", record.id().toString());
        map.put("documentType", record.documentType().name());
        map.put("level", record.level().name());
        map.put("contentHash", record.contentHash());
        map.put("signedAt", record.signedAt().toString());
        map.put("verificationCode", record.verificationCode());
        return Json.write(map);
    }

    @Override
    public String publicValidationJson(String verificationCode, String sourceIp) {
        SignatureVerification.Outcome outcome = service.verify(verificationCode, sourceIp);
        return SignatureVerification.publicJson(service.find(verificationCode).orElse(null), outcome);
    }
}
