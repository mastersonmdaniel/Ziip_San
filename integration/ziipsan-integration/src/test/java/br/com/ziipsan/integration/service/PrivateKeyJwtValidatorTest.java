package br.com.ziipsan.integration.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.26: validação de client_assertion (private_key_jwt, RFC 7523) —
 * RS256, iss == sub == client_id, audiência do token endpoint, expiração
 * curta e jti presente. Sem distinguir o motivo na resposta.
 */
class PrivateKeyJwtValidatorTest {

    private static final String CLIENT_ID = "cliente-teste";
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    private record Keys(KeyPair pair, String pem) {
    }

    private static Keys keys() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        return new Keys(pair, pem);
    }

    private static String assertion(KeyPair pair, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(pair.getPrivate()));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(CLIENT_ID)
                .subject(CLIENT_ID)
                .audience(PrivateKeyJwtValidator.TOKEN_AUDIENCE)
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(120)))
                .jwtID("jti-1");
    }

    @Test
    @DisplayName("asserção válida com a chave registrada passa e o jti volta nos claims")
    void validAssertionPasses() throws Exception {
        Keys k = keys();
        RSAPublicKey pub = PrivateKeyJwtValidator.parsePublicKey(k.pem());
        var claims = PrivateKeyJwtValidator.validate(
                assertion(k.pair(), baseClaims().build()), CLIENT_ID, pub, NOW);
        assertThat(claims.getJWTID()).isEqualTo("jti-1");
    }

    @Test
    @DisplayName("assinatura com outra chave, audiência errada e iss/sub divergente são recusados")
    void wrongKeyAudienceAndSubjectRejected() throws Exception {
        Keys k = keys();
        Keys other = keys();
        RSAPublicKey pub = PrivateKeyJwtValidator.parsePublicKey(k.pem());
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate(
                assertion(other.pair(), baseClaims().build()), CLIENT_ID, pub, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate(
                assertion(k.pair(), baseClaims().audience("outra-audiencia").build()),
                CLIENT_ID, pub, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate(
                assertion(k.pair(), baseClaims().subject("outro-cliente").build()),
                CLIENT_ID, pub, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("asserção vencida, com vida acima de 5 minutos e sem jti são recusadas")
    void expiredLongLivedAndMissingJtiRejected() throws Exception {
        Keys k = keys();
        RSAPublicKey pub = PrivateKeyJwtValidator.parsePublicKey(k.pem());
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate(
                assertion(k.pair(), baseClaims().expirationTime(Date.from(NOW.minusSeconds(1))).build()),
                CLIENT_ID, pub, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate(
                assertion(k.pair(), baseClaims().expirationTime(Date.from(NOW.plusSeconds(301))).build()),
                CLIENT_ID, pub, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate(
                assertion(k.pair(), baseClaims().jwtID(null).build()),
                CLIENT_ID, pub, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivateKeyJwtValidator.validate("nao-um-jwt", CLIENT_ID, pub, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PEM malformado não vira chave")
    void malformedPemRejected() {
        assertThatThrownBy(() -> PrivateKeyJwtValidator.parsePublicKey("nao-uma-chave"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a audiência exigida é o identificador do token endpoint")
    void tokenAudienceConstant() {
        assertThat(PrivateKeyJwtValidator.TOKEN_AUDIENCE).isEqualTo("ziipsan-public-api");
        assertThat(PrivateKeyJwtValidator.ASSERTION_TYPE)
                .isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        assertThat(List.of(PrivateKeyJwtValidator.MAX_ASSERTION_SECONDS)).containsExactly(300L);
    }
}
