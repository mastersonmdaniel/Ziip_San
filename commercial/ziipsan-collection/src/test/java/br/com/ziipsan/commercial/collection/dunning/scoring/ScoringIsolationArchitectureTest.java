package br.com.ziipsan.commercial.collection.dunning.scoring;

import br.com.ziipsan.commercial.collection.dunning.CollectionGates;
import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TESTE DE ARQUITETURA da GES-24 (aceite da P4.1), ENDURECIDO na correcao
 * D2 da revisao cruzada: corte, negativacao e protesto NUNCA leem o
 * escore (Lei 14.015/2020; LGPD, art. 20).
 *
 * <p>Por que a versao anterior nao bastava: ela so vedava nomes com
 * "score"/"escore" e os tipos {@code double}/{@code Double}/{@code Score}
 * nos fatos — o revisor demonstrou um mutante com
 * {@code int propensityBps} em {@code PreCutFacts} lido por
 * {@code preCutBlocks} passando 5/5. Esta versao mata esse canal:
 *
 * <ol>
 *   <li>os componentes dos fatos sao conferidos contra a LISTA EXATA de
 *       nomes esperados e uma LISTA FECHADA de tipos permitidos
 *       (booleano, conjunto de efeitos da repactuacao e o desfecho da
 *       prescricao) — qualquer componente novo, numerico ou nao
 *       ({@code int}, {@code long}, {@code String}, {@code Map},
 *       {@code Object}...), reprova;</li>
 *   <li>os metodos dos portoes so podem receber os fatos/purpose e
 *       devolver o conjunto de motivos — nenhum parametro novo entra;</li>
 *   <li>varredura GENERICA de todo o modulo: nenhuma classe de producao
 *       fora de {@code CollectionScoring} referencia o tipo do escore em
 *       campo, componente, parametro, retorno ou tipo generico;</li>
 *   <li>varredura das FONTES, quando localizaveis no ambiente de teste:
 *       nenhum arquivo de producao fora do subpacote {@code scoring}
 *       menciona o tipo do escore.</li>
 * </ol>
 *
 * <p>FRONTEIRA DA TARIFA SOCIAL (declarada, nao um ✔ sem prova): nao ha
 * NENHUMA classe de tarifa social neste modulo — a concessao/manutencao
 * dela vive na P1.5 (Lei 14.898/2024), que nao faz parte desta entrega.
 * Este teste prova a ausencia aqui e o isolamento dos portoes DESTE
 * modulo; a prova de que a tarifa social nao le escore pertence ao
 * modulo dela. O teste {@link #socialTariffLivesOutsideThisModule()}
 * registra essa fronteira de forma verificavel (a varredura de classes
 * falharia se uma classe de tarifa social nascesse aqui sem entrar
 * nesta analise).
 */
class ScoringIsolationArchitectureTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    /** Tipos permitidos nos fatos dos portoes — lista FECHADA (o mutante int propensityBps morre aqui). */
    private static final Set<Class<?>> FACT_ALLOWED_TYPES = Set.of(
            boolean.class, Boolean.class, Set.class, Prescription.Outcome.class);

    @Test
    @DisplayName("arquitetura: fatos dos portoes tem EXATAMENTE os componentes esperados, so de tipos permitidos (mutante int propensityBps reprova)")
    void gateFactsAreClosedAgainstAnyScoreChannel() {
        assertFactShape(CollectionGates.PreCutFacts.class,
                Set.of("billUnderReviewOrContested", "repactuationActive", "repactuationEffects",
                        "prescriptionOutcome", "activeInstallmentAgreementInGoodStanding"));
        assertFactShape(CollectionGates.ListingFacts.class,
                Set.of("billUnderReviewOrContested", "repactuationActive", "repactuationEffects",
                        "prescriptionOutcome"));
    }

    private static void assertFactShape(Class<?> factsType, Set<String> expectedComponents) {
        assertThat(factsType.isRecord()).isTrue();
        List<String> names = new ArrayList<>();
        for (RecordComponent component : factsType.getRecordComponents()) {
            names.add(component.getName());
            String lower = component.getName().toLowerCase();
            assertThat(lower).as("nome do componente %s", component.getName())
                    .doesNotContain("score").doesNotContain("escore").doesNotContain("propens");
            assertThat(FACT_ALLOWED_TYPES)
                    .as("tipo de %s.%s fora da lista fechada — canal numerico/textual de escore e vedado",
                            factsType.getSimpleName(), component.getName())
                    .contains(component.getType());
            if (component.getType() == Set.class) {
                assertThat(component.getGenericType().getTypeName())
                        .as("conjunto de %s so pode ser de efeitos da repactuacao", component.getName())
                        .contains(Repactuation.Effect.class.getName());
            }
            assertThat(component.getGenericType().getTypeName())
                    .doesNotContain("Score").doesNotContain("double").doesNotContain("Double");
        }
        assertThat(names).as("componentes de %s", factsType.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(expectedComponents);
    }

    @Test
    @DisplayName("arquitetura: metodos dos portoes so recebem os fatos e devolvem motivos — nenhum parametro novo (numerico ou nao)")
    void gateMethodsTakeOnlyFacts() {
        List<Method> methods = Arrays.stream(CollectionGates.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic()).toList();
        assertThat(methods).extracting(Method::getName)
                .containsExactlyInAnyOrder("preCutBlocks", "listingBlocks");
        for (Method method : methods) {
            for (Class<?> param : method.getParameterTypes()) {
                assertThat(param).as("parametro de %s", method.getName())
                        .isIn(CollectionGates.PreCutFacts.class, CollectionGates.ListingFacts.class,
                                CollectionGates.ListingPurpose.class);
            }
            assertThat(method.getReturnType()).isEqualTo(Set.class);
            assertThat(method.getGenericReturnType().getTypeName())
                    .contains(CollectionGates.BlockReason.class.getName());
        }
        assertThat(CollectionGates.class.getDeclaredFields()).isEmpty();
    }

    @Test
    @DisplayName("arquitetura: NENHUMA classe de producao do modulo (fora o proprio escore) referencia o tipo Score — nem em genericos")
    void noProductionClassOutsideScoringReferencesScore() throws Exception {
        for (Class<?> type : moduleProductionClasses()) {
            if (type.getName().startsWith(CollectionScoring.class.getName())) {
                continue;
            }
            List<String> surfaces = new ArrayList<>();
            Arrays.stream(type.getDeclaredFields())
                    .forEach(f -> surfaces.add(f.getGenericType().getTypeName()));
            Arrays.stream(type.getDeclaredMethods()).forEach(m -> {
                surfaces.add(m.getGenericReturnType().getTypeName());
                Arrays.stream(m.getGenericParameterTypes()).forEach(t -> surfaces.add(t.getTypeName()));
            });
            if (type.isRecord()) {
                Arrays.stream(type.getRecordComponents())
                        .forEach(c -> surfaces.add(c.getGenericType().getTypeName()));
            }
            assertThat(surfaces).as("superficies de %s", type.getName())
                    .noneMatch(s -> s.contains("CollectionScoring"));
        }
    }

    @Test
    @DisplayName("arquitetura: fontes de producao fora do subpacote scoring nao mencionam o tipo do escore (quando as fontes sao localizaveis)")
    void productionSourcesOutsideScoringDoNotMentionScore() throws Exception {
        Path sourcesRoot = locateModuleSources();
        if (sourcesRoot == null) {
            // Ambiente sem arvore de fontes (ex.: testes contra jar): a prova
            // por reflexao acima continua valendo; esta varredura roda no
            // harness local e no Maven (src/main/java ao lado de target/).
            return;
        }
        Path dunning = sourcesRoot.resolve("br/com/ziipsan/commercial/collection/dunning");
        try (Stream<Path> walk = Files.walk(dunning)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.toString().contains("/scoring/")) {
                    continue;
                }
                String content = Files.readString(file);
                assertThat(content).as("fonte %s", file.getFileName())
                        .doesNotContain("CollectionScoring")
                        .doesNotContainPattern("\\bScore\\b");
            }
        }
    }

    @Test
    @DisplayName("fronteira declarada: NAO ha classe de tarifa social neste modulo — a prova dela pertence a P1.5, onde ela vive")
    void socialTariffLivesOutsideThisModule() throws Exception {
        List<String> names = moduleProductionClasses().stream().map(Class::getName).toList();
        assertThat(names).as("classes do modulo: %s", names)
                .noneMatch(n -> n.toLowerCase().contains("socialtariff")
                        || n.toLowerCase().contains("tarifasocial"));
        // O que ESTE modulo decide sobre tarifa social: nada. Os portoes
        // acima nao recebem escore (provado) e nao decidem beneficio.
    }

    private static List<Class<?>> moduleProductionClasses() throws Exception {
        Path codeSource = Path.of(CollectionGates.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path pkg = codeSource.resolve("br/com/ziipsan/commercial/collection/dunning");
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(pkg)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = codeSource.relativize(file).toString()
                        .replace('/', '.').replace('\\', '.')
                        .replaceAll("\\.class$", "");
                if (name.contains("Test") || name.endsWith("DunningTestSupport")) {
                    continue;
                }
                classes.add(Class.forName(name, false,
                        ScoringIsolationArchitectureTest.class.getClassLoader()));
            }
        }
        assertThat(classes).as("classes de producao encontradas no classpath").isNotEmpty();
        return classes;
    }

    private static Path locateModuleSources() throws Exception {
        Path dir = Path.of(CollectionGates.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        for (int i = 0; i < 6 && dir != null; i++) {
            try (Stream<Path> walk = Files.walk(dir, 12)) {
                var found = walk.filter(p -> p.getFileName().toString().equals("CollectionGates.java")
                        && p.toString().contains("collection")).findFirst();
                if (found.isPresent()) {
                    Path gates = found.get();
                    Path cursor = gates;
                    while (cursor != null && !Files.isDirectory(cursor.resolve("br/com/ziipsan"))) {
                        cursor = cursor.getParent();
                    }
                    if (cursor != null) {
                        return cursor;
                    }
                }
            } catch (java.io.IOException ignored) {
                // tenta o proximo ancestral
            }
            dir = dir.getParent();
        }
        return null;
    }

    @Test
    @DisplayName("escore DESLIGADO (padrao): fila e contatos voltam identicos, mesmo com escore alto calculado fora")
    void scoringOffByDefault() {
        var contacts = List.of(
                new CollectionScoring.Contact(A, "EMAIL", "MORNING", false),
                new CollectionScoring.Contact(B, "EMAIL", "MORNING", false));
        var scores = List.of(new CollectionScoring.Score(B, 0.99, List.of("atrasos recentes")));
        var ordered = CollectionScoring.orderContacts(contacts, scores, Set.of(),
                DunningParameters.fromValues(Map.of()));
        assertThat(ordered).isEqualTo(contacts);
    }

    @Test
    @DisplayName("escore LIGADO: so ordena o contato e adapta canal/horario/oferta")
    void scoringOnOrdersAndAdaptsOnly() {
        var contacts = List.of(
                new CollectionScoring.Contact(A, "EMAIL", "MORNING", false),
                new CollectionScoring.Contact(B, "EMAIL", "MORNING", false),
                new CollectionScoring.Contact(C, "EMAIL", "MORNING", false));
        var scores = List.of(
                new CollectionScoring.Score(A, 0.30, List.of("um atraso")),
                new CollectionScoring.Score(B, 0.90, List.of("atrasos recorrentes")),
                new CollectionScoring.Score(C, 0.55, List.of("atraso recente")));
        var params = DunningParameters.fromValues(Map.of("ai.collection_scoring_enabled", "true"));
        var ordered = CollectionScoring.orderContacts(contacts, scores, Set.of(), params);
        assertThat(ordered).extracting(CollectionScoring.Contact::unitId)
                .containsExactly(B, C, A);
        assertThat(ordered.get(0).channel()).isEqualTo("WHATSAPP");
        assertThat(ordered.get(0).preferredHourBand()).isEqualTo("EVENING");
        assertThat(ordered.get(0).installmentOffer()).isTrue();
        assertThat(ordered.get(1).preferredHourBand()).isEqualTo("MORNING");
        assertThat(ordered.get(1).installmentOffer()).isTrue();
        assertThat(ordered.get(2)).isEqualTo(contacts.get(0));
    }

    @Test
    @DisplayName("oposicao do titular: sai da ordenacao por escore e nao e adaptado (LGPD, art. 20)")
    void oppositionOptsOut() {
        var contacts = List.of(
                new CollectionScoring.Contact(A, "EMAIL", "MORNING", false),
                new CollectionScoring.Contact(B, "EMAIL", "MORNING", false));
        var scores = List.of(new CollectionScoring.Score(B, 0.95, List.of("atrasos recorrentes")));
        var params = DunningParameters.fromValues(Map.of("ai.collection_scoring_enabled", "true"));
        var ordered = CollectionScoring.orderContacts(contacts, scores, Set.of(B), params);
        assertThat(ordered).containsExactlyElementsOf(contacts);
        assertThat(ordered).extracting(CollectionScoring.Contact::channel)
                .containsOnly("EMAIL");
    }

    @Test
    @DisplayName("D3 — sonda do revisor: [B oposto (0,95) PRIMEIRO, A (0,30)] sai [B, A] — o oposto NAO e rebaixado pelo escore")
    void oppositionIsNotDemotedByScore() {
        var contacts = List.of(
                new CollectionScoring.Contact(B, "EMAIL", "MORNING", false),
                new CollectionScoring.Contact(A, "EMAIL", "MORNING", false));
        var scores = List.of(
                new CollectionScoring.Score(B, 0.95, List.of("atrasos recorrentes")),
                new CollectionScoring.Score(A, 0.30, List.of("um atraso")));
        var params = DunningParameters.fromValues(Map.of("ai.collection_scoring_enabled", "true"));
        var ordered = CollectionScoring.orderContacts(contacts, scores, Set.of(B), params);
        assertThat(ordered).extracting(CollectionScoring.Contact::unitId).containsExactly(B, A);
        assertThat(ordered.get(0)).isEqualTo(contacts.get(0));
        // e na ordem inversa de entrada, o oposto tambem conserva a posicao:
        var reversed = CollectionScoring.orderContacts(
                List.of(contacts.get(1), contacts.get(0)), scores, Set.of(B), params);
        assertThat(reversed).extracting(CollectionScoring.Contact::unitId).containsExactly(A, B);
    }

    @Test
    @DisplayName("explicacao ao titular devolve os fatores registrados do escore")
    void explanationReturnsFactors() {
        var params = DunningParameters.fromValues(Map.of("ai.collection_scoring_enabled", "true"));
        var explanation = CollectionScoring.explain(B,
                List.of(new CollectionScoring.Score(B, 0.90,
                        List.of("atrasos recorrentes", "valor medio em aberto"))), params);
        assertThat(explanation.scoringEnabled()).isTrue();
        assertThat(explanation.value()).isEqualTo(0.90);
        assertThat(explanation.factors()).containsExactly("atrasos recorrentes", "valor medio em aberto");
        assertThat(Arrays.stream(CollectionScoring.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("cutoff")
                        || m.getName().toLowerCase().contains("negativ"))).isTrue();
    }
}
