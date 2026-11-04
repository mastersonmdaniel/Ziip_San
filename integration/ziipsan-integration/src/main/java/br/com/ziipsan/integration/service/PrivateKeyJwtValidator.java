package br.com.ziipsan.integration.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Validação de {@code client_assertion} (OAuth 2.0 private_key_jwt,
 * RFC 7523) para clientes {@code PRIVATE_KEY_JWT} (P2.26).
 *
 * <p>Exige: JWT assinado em RS256 com a chave pública registrada do
 * cliente; {@code iss == sub == client_id}; audiência contendo o
 * identificador do token endpoint ({@value #TOKEN_AUDIENCE}); expiração
 * de no máximo 5 minutos a partir de {@code iat}; {@code jti} presente
 * (a repetição é barrada pela tabela {@code api_assertion_jtis} no
 * serviço de tokens). Assinatura com outra chave, audiência errada,
 * asserção vencida ou reutilizada é recusada — sem distinguir o motivo
 * na resposta (não há oráculo para o atacante enumerar).
 */
public final class PrivateKeyJwtValidator {

    public static final String TOKEN_AUDIENCE = "ziipsan-public-api";
    public static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    public static final long MAX_ASSERTION_SECONDS = 300;

    private PrivateKeyJwtValidator() {
    }

    /** Converte PEM (PKCS#8 com cabeçalho ou DER em base64) em chave RSA. */
    public static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(cleaned);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** Valida a asserção e devolve os claims; lança {@link IllegalArgumentException} se inválida. */
    public static JWTClaimsSet validate(String assertion, String expectedClientId, RSAPublicKey publicKey,
                                        Instant now) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(assertion);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        try {
            if (!"RS256".equals(jwt.getHeader().getAlgorithm().getName())) {
                throw new IllegalArgumentException("Asserção inválida.");
            }
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new IllegalArgumentException("Asserção inválida.");
            }
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        if (!expectedClientId.equals(claims.getIssuer()) || !expectedClientId.equals(claims.getSubject())) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(TOKEN_AUDIENCE)) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        Date exp = claims.getExpirationTime();
        Date iat = claims.getIssueTime();
        if (exp == null || exp.toInstant().isBefore(now) || exp.toInstant().isAfter(now.plusSeconds(600))) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        if (iat != null && exp.toInstant().isAfter(iat.toInstant().plusSeconds(MAX_ASSERTION_SECONDS))) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        if (claims.getJWTID() == null || claims.getJWTID().isBlank()) {
            throw new IllegalArgumentException("Asserção inválida.");
        }
        return claims;
    }
}
