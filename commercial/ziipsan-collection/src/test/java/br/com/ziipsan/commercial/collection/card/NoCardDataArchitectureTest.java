package br.com.ziipsan.commercial.collection.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A PROVA DO ACEITE DA ED-28 — "nenhum dado de cartao trafega pelo
 * produto nem e gravado nele, provado por teste sobre requisicoes,
 * logs e banco":
 *
 * <ul>
 *   <li><b>tipos</b>: por reflexao, nenhum record/classe do pacote
 *       {@code card} tem componente ou campo de dado de cartao;</li>
 *   <li><b>fontes</b>: varredura das fontes de producao do pacote
 *       (exceto a propria guarda, que DETECTA esses nomes) — nenhum
 *       identificador proibido e nenhum PAN;</li>
 *   <li><b>requisicao</b>: a requisicao de checkout capturada pela
 *       credenciadora falsa passa limpa pela guarda;</li>
 *   <li><b>logs e banco (espelhos em memoria)</b>: num fluxo completo
 *       (abertura, retorno aprovado, conciliacao), todos os logs
 *       capturados e todos os registros gravados nas portas falsas
 *       passam limpos pela guarda.</li>
 * </ul>
 * A perna "banco" sobre o SQL e do {@code CardMigrationConsistencyTest}.
 *
 * <p>Correcao D1/D2 da revisao de 08/11/2026: o vocabulario proibido
 * NAO vive mais aqui — ele vive, unico, em
 * {@link CardDataGuard#FORBIDDEN_NAME_TOKENS} /
 * {@link CardDataGuard#isForbiddenFieldName(String)} (PT + EN), e este
 * teste, o teste de banco e a propria guarda consomem a MESMA fonte.
 */
class NoCardDataArchitectureTest {

    /** Identificadores Java (nomes de campo, metodo, tipo e palavras de literais/comentarios). */
    private static final java.util.regex.Pattern IDENTIFIER =
            java.util.regex.Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private static final List<Class<?>> PACKAGE_TYPES = List.of(
            CardEnums.class, CardEnums.CheckoutMode.class, CardEnums.SessionStatus.class,
            CardEnums.AcquirerTxStatus.class, CardParameters.class, CardDataGuard.class,
            CardPorts.class, CardPorts.AcquirerPort.class,
            CardPorts.AcquirerPort.CheckoutRequest.class, CardPorts.AcquirerPort.AcquirerSession.class,
            CardPorts.AcquirerPort.AcquirerTransaction.class, CardPorts.AcquirerPort.StatementLine.class,
            CardPorts.SettlementPort.class, CardPorts.SettlementPort.SettleCommand.class,
            CardPorts.CardStore.class, CardPorts.CardStore.CardSettlement.class,
            CardPorts.AuditLogPort.class, CardCheckoutService.class,
            CardCheckoutService.CardCheckoutSession.class, CardCheckoutService.CreateCommand.class,
            CardReturnProcessor.class, CardReturnProcessor.ReturnCommand.class,
            CardReturnProcessor.ReturnOutcome.class, CardStatementReconciler.class,
            CardStatementReconciler.ExpectedSettlement.class, CardStatementReconciler.Item.class,
            CardStatementReconciler.Result.class);

    @Test
    @DisplayName("reflexao: nenhum componente/campo de nenhum tipo do pacote e dado de cartao")
    void noCardDataFieldsByReflection() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : PACKAGE_TYPES) {
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    checkName(type.getSimpleName() + "." + component.getName(), offenders);
                }
            }
            for (var field : type.getDeclaredFields()) {
                checkName(type.getSimpleName() + "." + field.getName(), offenders);
            }
        }
        assertThat(offenders).as("campos de dado de cartao encontrados").isEmpty();
    }

    private static void checkName(String qualified, List<String> offenders) {
        String name = qualified.substring(qualified.indexOf('.') + 1);
        if (CardDataGuard.isForbiddenFieldName(name)) {
            offenders.add(qualified);
        }
    }

    @Test
    @DisplayName("vocabulario unico da guarda cobre PT e EN — e nao morde os nomes legitimos do pacote")
    void vocabularyCoversPtAndEn() {
        // PT + EN, em snake_case, camelCase e caixa alta: TODOS proibidos.
        for (String forbidden : new String[]{
                "numero_cartao", "numeroCartao", "NUMERO_CARTAO", "numero_do_cartao", "numeroDoCartao",
                "num_cartao", "card_number", "cardNumber", "credit_card", "creditCard", "cartao_credito",
                "validade", "validade_cartao", "expiry", "expiryDate", "card_expiry", "expiration_date",
                "security_code", "securityCode", "codigo_seguranca", "codigoSeguranca",
                "cvv", "cvv2", "cvc", "cardholder", "cardHolderName", "holder", "holderName",
                "nome_impresso", "nomeImpresso", "titular", "titular_cartao",
                "track1", "track2", "card_pin", "cardPin", "pan", "pin"}) {
            assertThat(CardDataGuard.isForbiddenFieldName(forbidden))
                    .as("nome proibido: %s", forbidden).isTrue();
        }
        // Nomes legitimos do pacote e do fluxo: NENHUM pode ser mordido.
        for (String legit : new String[]{
                "tenantId", "acquirerCode", "acquirerTransactionId", "sessionReference",
                "grossAmount", "feeAmount", "netAmount", "expiresAt", "authorizationCode",
                "payloadHash", "payloadJson", "receivedAt", "statementTolerance",
                "checkoutUrl", "descriptionCode", "installmentNumber", "collectionMonth",
                "settlementDate", "returnUrl", "reference", "amount", "status"}) {
            assertThat(CardDataGuard.isForbiddenFieldName(legit))
                    .as("nome legitimo: %s", legit).isFalse();
        }
    }

    @Test
    @DisplayName("fontes de producao do pacote (fora a guarda) sem nome proibido e sem PAN")
    void productionSourcesAreClean() throws IOException {
        Path dir = locateMainCardSources();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(files).isNotEmpty();
        boolean sawGuard = false;
        for (Path file : files) {
            String content = Files.readString(file);
            if (file.getFileName().toString().equals("CardDataGuard.java")) {
                sawGuard = true;
                // A guarda e o unico lugar onde o vocabulario de deteccao vive.
                assertThat(content).contains("FORBIDDEN_NAME_TOKENS")
                        .contains("isForbiddenFieldName").contains("passesLuhn");
                continue;
            }
            // Cada identificador da fonte passa pelo MESMO teste de nome da guarda:
            // um campo String numeroCartao declarado na fonte morde aqui, nao so na reflexao.
            var identifiers = IDENTIFIER.matcher(content);
            while (identifiers.find()) {
                String identifier = identifiers.group();
                assertThat(CardDataGuard.isForbiddenFieldName(identifier))
                        .as("fonte %s nao pode conter o identificador proibido '%s'",
                                file.getFileName(), identifier)
                        .isFalse();
            }
            assertThat(CardDataGuard.containsPan(content))
                    .as("fonte %s nao pode carregar um PAN", file.getFileName())
                    .isFalse();
        }
        assertThat(sawGuard).as("a guarda CardDataGuard.java faz parte do pacote").isTrue();
    }

    @Test
    @DisplayName("fluxo completo: requisicao, logs e registros gravados passam limpos pela guarda")
    void endToEndRequestLogsAndStoreAreClean() {
        var acquirer = new CardTestSupport.FakeAcquirer();
        var settlements = new CardTestSupport.FakeSettlements();
        var store = new CardTestSupport.FakeStore();
        var log = new CardTestSupport.CapturingLog();

        var checkout = new CardCheckoutService(CardTestSupport.params(true), acquirer, store, log);
        var session = checkout.create(new CardCheckoutService.CreateCommand(
                CardTestSupport.TENANT, CardTestSupport.AGREEMENT, 3, true, true,
                new BigDecimal("106.61"), new BigDecimal("106.61"),
                "https://portal.example.invalid/retorno",
                Instant.parse("2026-11-05T23:59:00Z"), Instant.parse("2026-11-05T10:00:00Z")));

        // REQUISICAO que trafega para a credenciadora:
        assertThat(CardDataGuard.containsCardData(acquirer.lastRequest.toString())).isFalse();
        assertThat(CardDataGuard.containsCardData(session.toString())).isFalse();

        acquirer.programApproved("tx-1", session.reference(), "106.61", "3.20");
        var processor = new CardReturnProcessor(acquirer, settlements, store, log);
        String payload = "{\"eventId\":\"evt-1\",\"acquirerTransactionId\":\"tx-1\","
                + "\"sessionReference\":\"" + session.reference() + "\",\"status\":\"APPROVED\","
                + "\"grossAmount\":106.61,\"feeAmount\":3.20,\"paidAt\":\"2026-11-05T14:30:00Z\"}";
        var outcome = processor.process(new CardReturnProcessor.ReturnCommand(
                CardTestSupport.TENANT, CardTestSupport.ACQUIRER, true, payload,
                Instant.parse("2026-11-05T14:31:00Z")));
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.BAIXA);

        // LOGS do fluxo inteiro:
        assertThat(log.lines).isNotEmpty();
        for (String line : log.lines) {
            assertThat(CardDataGuard.containsCardData(line)).as("log limpo: %s", line).isFalse();
            assertThat(line).doesNotContain("4111");
        }
        // BANCO (espelhos gravados nas portas): sessao, baixa e ordem de baixa:
        assertThat(CardDataGuard.containsCardData(store.sessions.values().toString())).isFalse();
        assertThat(CardDataGuard.containsCardData(store.settlementsByTx.values().toString())).isFalse();
        assertThat(CardDataGuard.containsCardData(settlements.settled.toString())).isFalse();
    }

    /** Localiza as fontes de producao do pacote card (propriedade, ou subindo da classe). */
    static Path locateMainCardSources() {
        String prop = System.getProperty("ziipsan.card.mainSrc");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String relative = "src/main/java/br/com/ziipsan/commercial/collection/card";
        Path cursor = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            Path direct = cursor.resolve(relative);
            if (Files.isDirectory(direct)) {
                return direct;
            }
            Path inModule = cursor.resolve("commercial/ziipsan-collection").resolve(relative);
            if (Files.isDirectory(inModule)) {
                return inModule;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("fontes de producao do pacote card nao localizadas "
                + "(defina -Dziipsan.card.mainSrc)");
    }
}
