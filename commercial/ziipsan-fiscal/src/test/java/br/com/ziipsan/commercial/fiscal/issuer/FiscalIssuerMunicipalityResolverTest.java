package br.com.ziipsan.commercial.fiscal.issuer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.ziipsan.commercial.fiscal.issuer.FiscalIssuerMunicipalityResolver.AmbiguousFiscalIssuerException;
import br.com.ziipsan.commercial.fiscal.issuer.FiscalIssuerMunicipalityResolver.IssuerRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Matriz de resolução de emissor por município do MM-05 — REDESENHO R8-C.
 *
 * <p>O eixo é {@code fiscal_issuers.ibge} (V4_5: CHAR(7) NOT NULL, CHECK de
 * 7 dígitos; R8 §A-02). Todo o emissor tem ibge; não há emissor da empresa
 * sem município. "Emissor da empresa" = tenant mono-emissor (D-MM05-R8C-02).
 *
 * <p>IBGEs fictícios de teste (7 dígitos, formato válido); tenants e
 * emissores são UUIDs fixos desta suíte. Nenhum dado real.
 */
class FiscalIssuerMunicipalityResolverTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID OTHER_TENANT = UUID.fromString("99999999-8888-4777-8666-555555555555");
    private static final String MUN_A = "4205407";
    private static final String MUN_B = "4216602";
    private static final String MUN_C = "4200000";

    private static IssuerRef issuer(UUID tenant, String ibge) {
        return new IssuerRef(tenant, UUID.randomUUID(), ibge);
    }

    @Test
    void municipalExatoVence() {
        var municipalA = issuer(TENANT, MUN_A);
        var municipalB = issuer(TENANT, MUN_B);
        var r = FiscalIssuerMunicipalityResolver.resolve(
                List.of(municipalA, municipalB), TENANT, MUN_A);
        assertEquals(municipalA, r.orElseThrow());
    }

    @Test
    void municipioDistintoNaoVaza() {
        var municipalA = issuer(TENANT, MUN_A);
        var municipalB = issuer(TENANT, MUN_B);
        // Contexto MUN_C, sem emissor de MUN_C e sem mono-emissor (2 no
        // tenant): vazio — o emissor municipal de OUTRO municipio nunca
        // e escolhido e nao ha fallback para "empresa".
        var r = FiscalIssuerMunicipalityResolver.resolve(
                List.of(municipalA, municipalB), TENANT, MUN_C);
        assertTrue(r.isEmpty());
    }

    @Test
    void ambiguidadeMunicipalFalhaFechada() {
        var m1 = issuer(TENANT, MUN_A);
        var m2 = issuer(TENANT, MUN_A);
        var outro = issuer(TENANT, MUN_B);
        // 2+ emissores com o mesmo ibge: a UNIQUE vigente e
        // (tenant_id, document_hash), sem UNIQUE em (tenant_id, ibge) —
        // o cadastro admite o conflito e a resolucao falha fechada.
        assertThrows(AmbiguousFiscalIssuerException.class,
                () -> FiscalIssuerMunicipalityResolver.resolve(
                        List.of(m1, m2, outro), TENANT, MUN_A));
    }

    @Test
    void semMunicipioMonoEmissorResolve() {
        var unico = issuer(TENANT, MUN_A);
        var r = FiscalIssuerMunicipalityResolver.resolve(List.of(unico), TENANT, null);
        assertEquals(unico, r.orElseThrow());
    }

    @Test
    void semMunicipioMultiplosEmissoresFalhaFechada() {
        var m1 = issuer(TENANT, MUN_A);
        var m2 = issuer(TENANT, MUN_B);
        // Sem municipio no contexto e 2+ emissores: nada discrimina —
        // falha fechada, nunca escolha silenciosa.
        assertThrows(AmbiguousFiscalIssuerException.class,
                () -> FiscalIssuerMunicipalityResolver.resolve(List.of(m1, m2), TENANT, null));
    }

    @Test
    void semMunicipioSemEmissorVazio() {
        assertTrue(FiscalIssuerMunicipalityResolver.resolve(
                List.of(), TENANT, null).isEmpty());
        assertTrue(FiscalIssuerMunicipalityResolver.resolve(
                List.of(), TENANT, MUN_A).isEmpty());
    }

    @Test
    void emissorDeOutroTenantNuncaResolve() {
        var alheioA = issuer(OTHER_TENANT, MUN_A);
        var alheioB = issuer(OTHER_TENANT, MUN_B);
        assertTrue(FiscalIssuerMunicipalityResolver.resolve(
                List.of(alheioA, alheioB), TENANT, MUN_A).isEmpty());
        assertTrue(FiscalIssuerMunicipalityResolver.resolve(
                List.of(alheioA, alheioB), TENANT, null).isEmpty());
        // E nao cria ambiguidade no tenant certo:
        var meu = issuer(TENANT, MUN_A);
        var r = FiscalIssuerMunicipalityResolver.resolve(
                List.of(alheioA, meu), TENANT, MUN_A);
        assertEquals(meu, r.orElseThrow());
    }

    @Test
    void tenantMonoEmissorVigenteIntacto() {
        // Hipotese mono-emissor: o unico emissor do tenant e o emissor
        // da empresa (D-MM05-R8C-02) e tudo resolve como hoje, com ou
        // sem municipio no contexto — mesmo que o ibge dele nao seja o
        // do contexto: nao ha outro emissor para escolher.
        var unico = issuer(TENANT, MUN_A);
        assertEquals(unico, FiscalIssuerMunicipalityResolver.resolve(
                List.of(unico), TENANT, MUN_A).orElseThrow());
        assertEquals(unico, FiscalIssuerMunicipalityResolver.resolve(
                List.of(unico), TENANT, MUN_B).orElseThrow());
        assertEquals(unico, FiscalIssuerMunicipalityResolver.resolve(
                List.of(unico), TENANT, null).orElseThrow());
    }

    @Test
    void multiEmissorSemCorrespondenciaExataVazio() {
        // 2+ emissores, nenhum com o ibge do contexto, nenhum
        // mono-emissor: vazio — nao ha "emissor da empresa" para onde cair.
        var m1 = issuer(TENANT, MUN_A);
        var m2 = issuer(TENANT, MUN_B);
        assertTrue(FiscalIssuerMunicipalityResolver.resolve(
                List.of(m1, m2), TENANT, MUN_C).isEmpty());
    }

    @Test
    void herancaPelaCadeiaMunicipioEfectivoVemDoEmissor() {
        var municipal = issuer(TENANT, MUN_A);
        // Serie e documento herdam: o municipio efectivo le-se do
        // emissor da cadeia — e esta sempre presente (ibge NOT NULL).
        assertEquals(MUN_A, FiscalIssuerMunicipalityResolver
                .effectiveMunicipality(municipal).orElseThrow());
    }

    @Test
    void ibgeInvalidoRecusado() {
        assertThrows(IllegalArgumentException.class, () -> issuer(TENANT, "123"));
        assertThrows(IllegalArgumentException.class, () -> issuer(TENANT, "42054070"));
        assertThrows(IllegalArgumentException.class, () -> issuer(TENANT, "ABCDEFG"));
        assertThrows(NullPointerException.class, () -> issuer(TENANT, null));
    }

    @Test
    void contextoMunicipioInvalidoRecusado() {
        assertThrows(IllegalArgumentException.class, () ->
                FiscalIssuerMunicipalityResolver.resolve(List.of(), TENANT, "ABC"));
        assertThrows(IllegalArgumentException.class, () ->
                FiscalIssuerMunicipalityResolver.resolve(List.of(), TENANT, "123"));
    }
}
