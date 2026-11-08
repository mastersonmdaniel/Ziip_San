package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.platform.portal.LinkingCode;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.20 - vinculo: codigo + documento do titular por indice cego, resposta uniforme. */
class ConsumerLinkServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID UNIT2 = UUID.randomUUID();
    private static final String CPF = "52998224725";
    private static final String CODE = LinkingCode.generate();

    private final FieldProtector protector = P320TestSupport.protector();
    private final List<String> savedLinks = new ArrayList<>();
    private final List<String> savedDocIndexes = new ArrayList<>();
    private final List<String> savedCodeIndexes = new ArrayList<>();
    private final List<Boolean> attemptSuccess = new ArrayList<>();

    private ConsumerLinkService.LinkStorePort linksPort() {
        return new ConsumerLinkService.LinkStorePort() {
            @Override public boolean isAlreadyLinked(UUID t, String s, UUID u) { return savedLinks.contains(s + "|" + u); }
            @Override public void saveLink(UUID t, String s, UUID u, String docIdx, String codeIdx, Instant at) {
                savedLinks.add(s + "|" + u); savedDocIndexes.add(docIdx); savedCodeIndexes.add(codeIdx);
            }
        };
    }

    private ConsumerLinkService.AttemptPort attemptsPort(int ipAttempts, int unitAttempts) {
        return new ConsumerLinkService.AttemptPort() {
            @Override public int attemptsByUnit(UUID t, UUID u) { return unitAttempts; }
            @Override public int attemptsByIp(UUID t, String ip) { return ipAttempts; }
            @Override public int attemptsByTenant(UUID t) { return 0; }
            @Override public void recordAttempt(UUID t, UUID u, String ip, boolean ok, boolean alert) { attemptSuccess.add(ok); }
        };
    }

    private ConsumerLinkService.UnitForLink unitForLink(UUID unitId) {
        return new ConsumerLinkService.UnitForLink(unitId,
                protector.indexOf(TENANT, HolderDocumentContext.CONTEXT, DocumentNumber.normalize(CPF)),
                protector.indexOf(TENANT, LinkingCode.CONTEXT, CODE));
    }

    private ConsumerLinkService service(boolean unitExists, int ipAttempts) {
        return service(unitExists, ipAttempts, 0);
    }

    private ConsumerLinkService service(boolean unitExists, int ipAttempts, int unitAttempts) {
        var units = new ConsumerLinkService.UnitLookupPort() {
            @Override public Optional<ConsumerLinkService.UnitForLink> findByRegistration(UUID t, String reg) {
                if (!unitExists) { return Optional.empty(); }
                return Optional.of(unitForLink(UNIT));
            }
        };
        return new ConsumerLinkService(protector, units, linksPort(), attemptsPort(ipAttempts, unitAttempts),
                new LinkAttemptLimiter.Limits(5, 5, 100, 4));
    }

    @Test
    void vinculoComCodigoEDocumentoCorretos() {
        UUID linked = service(true, 0).link(TENANT, "sub-1", "MAT-1", CODE, CPF, "10.0.0.1");
        assertThat(linked).isEqualTo(UNIT);
        assertThat(attemptSuccess).containsExactly(true);
        // D3b: o vinculo gravado carrega os DOIS indices cegos conferidos.
        assertThat(savedDocIndexes).containsExactly(
                protector.indexOf(TENANT, HolderDocumentContext.CONTEXT, DocumentNumber.normalize(CPF)));
        assertThat(savedCodeIndexes).containsExactly(protector.indexOf(TENANT, LinkingCode.CONTEXT, CODE));
    }

    @Test
    void umaContaVinculaVariosImoveis() {
        // D4: o mesmo subject vincula DUAS unidades (modelo por unidade).
        Map<String, UUID> byRegistration = Map.of("MAT-1", UNIT, "MAT-2", UNIT2);
        var units = new ConsumerLinkService.UnitLookupPort() {
            @Override public Optional<ConsumerLinkService.UnitForLink> findByRegistration(UUID t, String reg) {
                UUID u = byRegistration.get(reg);
                return u == null ? Optional.empty() : Optional.of(unitForLink(u));
            }
        };
        var svc = new ConsumerLinkService(protector, units, linksPort(), attemptsPort(0, 0),
                new LinkAttemptLimiter.Limits(5, 5, 100, 4));
        assertThat(svc.link(TENANT, "sub-1", "MAT-1", CODE, CPF, "10.0.0.1")).isEqualTo(UNIT);
        assertThat(svc.link(TENANT, "sub-1", "MAT-2", CODE, CPF, "10.0.0.1")).isEqualTo(UNIT2);
        assertThat(savedLinks).hasSize(2);
        assertThat(attemptSuccess).containsExactly(true, true);
    }

    @Test
    void fatoresSaoComputadosMesmoSemMatricula() {
        // D2(a): sem curto-circuito — a conferencia dos indices cegos roda
        // nos tres caminhos de falha. A contagem e das buscas de CHAVE do
        // indice cego (1 por indice computado: documento + codigo = 2).
        AtomicInteger blindKeyLookups = new AtomicInteger();
        byte[] enc = new byte[32]; byte[] idx = new byte[32];
        Arrays.fill(enc, (byte) 7); Arrays.fill(idx, (byte) 9);
        TenantKeyProvider countingKeys = new TenantKeyProvider() {
            @Override public byte[] encryptionKey(UUID tenantId) { return enc.clone(); }
            @Override public byte[] blindIndexKey(UUID tenantId) {
                blindKeyLookups.incrementAndGet(); return idx.clone();
            }
        };
        FieldProtector counting = new FieldProtector(new FieldCipher(countingKeys), new BlindIndex(countingKeys));
        var units = new ConsumerLinkService.UnitLookupPort() {
            @Override public Optional<ConsumerLinkService.UnitForLink> findByRegistration(UUID t, String reg) {
                if (reg.equals("MAT-X")) { return Optional.empty(); }
                return Optional.of(unitForLink(UNIT));
            }
        };
        var svc = new ConsumerLinkService(counting, units, linksPort(), attemptsPort(0, 0),
                new LinkAttemptLimiter.Limits(5, 5, 100, 4));

        blindKeyLookups.set(0);
        failure(() -> svc.link(TENANT, "sub-1", "MAT-X", CODE, CPF, "10.0.0.1"));
        assertThat(blindKeyLookups.get()).isEqualTo(2);
        blindKeyLookups.set(0);
        failure(() -> svc.link(TENANT, "sub-1", "MAT-1", LinkingCode.generate(), CPF, "10.0.0.1"));
        assertThat(blindKeyLookups.get()).isEqualTo(2);
        blindKeyLookups.set(0);
        failure(() -> svc.link(TENANT, "sub-1", "MAT-1", CODE, "11144477735", "10.0.0.1"));
        assertThat(blindKeyLookups.get()).isEqualTo(2);
    }

    @Test
    void bloqueioSoPelaUnidadeNaoSeDistingueDaFalhaUniforme() {
        // D2(b): unidade real martelada (5 = limite por unidade) com IP novo
        // responde a MESMA falha uniforme da matricula inexistente — nunca
        // 429 — e nao vincula.
        String blocked = failure(() -> service(true, 0, 5)
                .link(TENANT, "sub-1", "MAT-1", CODE, CPF, "10.0.0.7"));
        String inexistent = failure(() -> service(false, 0)
                .link(TENANT, "sub-1", "MAT-X", CODE, CPF, "10.0.0.7"));
        assertThat(blocked).isEqualTo(ConsumerLinkService.UNIFORM_FAILURE);
        assertThat(blocked).isEqualTo(inexistent);
        assertThat(savedLinks).isEmpty();
    }

    @Test
    void bloqueioPorIpDa429ParaExistenteEInexistente() {
        // D2(b): o 429 vem da dimensao do solicitante (IP), igual nos dois
        // caminhos — nao revela existencia.
        assertThatThrownBy(() -> service(true, 5).link(TENANT, "sub-1", "MAT-1", CODE, CPF, "10.0.0.9"))
                .isInstanceOf(ApiException.TooManyRequests.class);
        assertThatThrownBy(() -> service(false, 5).link(TENANT, "sub-1", "MAT-X", CODE, CPF, "10.0.0.9"))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    @Test
    void respostaIgualParaInexistenteCodigoErradoEDocumentoErrado() {
        String m1 = failure(() -> service(false, 0).link(TENANT, "sub-1", "MAT-X", CODE, CPF, "10.0.0.1"));
        String m2 = failure(() -> service(true, 0).link(TENANT, "sub-1", "MAT-1", LinkingCode.generate(), CPF, "10.0.0.1"));
        String m3 = failure(() -> service(true, 0).link(TENANT, "sub-1", "MAT-1", CODE, "11144477735", "10.0.0.1"));
        assertThat(m1).isEqualTo(ConsumerLinkService.UNIFORM_FAILURE);
        assertThat(m2).isEqualTo(m1);
        assertThat(m3).isEqualTo(m1);
        assertThat(attemptSuccess).containsExactly(false, false, false);
    }

    @Test
    void excessoDeTentativasRecebe429() {
        assertThatThrownBy(() -> service(true, 5).link(TENANT, "sub-1", "MAT-1", CODE, CPF, "10.0.0.9"))
                .isInstanceOf(ApiException.TooManyRequests.class);
    }

    private String failure(Runnable r) {
        try { r.run(); } catch (ApiException e) { return e.getMessage(); }
        throw new AssertionError("esperava ApiException");
    }
}
