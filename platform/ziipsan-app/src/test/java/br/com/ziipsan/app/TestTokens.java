package br.com.ziipsan.app;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tokens JWT assinados de verdade (RS256) para os testes da API, com a mesma forma dos tokens do
 * Keycloak (realm "ziipsan"): iss, aud contendo ziipsan-api, tenant_id, realm_access.roles.
 *
 * <p>A chave é gerada a cada JVM de teste; a aplicação recebe só a pública
 * (ziipsan.oidc.public-key). Nada aqui é segredo de nenhum ambiente.
 */
final class TestTokens {

    static final String ISSUER = "http://localhost:8080/realms/ziipsan";
    static final String AUDIENCE = "ziipsan-api";

    static final KeyPair KEYS = generate();
    /** Outra chave: token assinado com ela precisa ser recusado. */
    static final KeyPair OTHER_KEYS = generate();

    private TestTokens() {
    }

    static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    }

    /** Token válido para o tenant e os papéis dados; {@code customize} altera os claims antes de assinar. */
    static String token(UUID tenantId, List<String> roles, Consumer<JWTClaimsSet.Builder> customize) {
        return sign(KEYS, tenantId, roles, customize);
    }

    static String token(UUID tenantId, String... roles) {
        return token(tenantId, List.of(roles), b -> {
        });
    }

    static String signedWithOtherKey(UUID tenantId) {
        return sign(OTHER_KEYS, tenantId, List.of("VIEWER"), b -> {
        });
    }

    private static String sign(KeyPair keys, UUID tenantId, List<String> roles, Consumer<JWTClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE, "account"))
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("preferred_username", "usuario.teste")
                .claim("realm_access", Map.of("roles", roles));
        if (tenantId != null) {
            claims.claim("tenant_id", tenantId.toString());
        }
        customize.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
