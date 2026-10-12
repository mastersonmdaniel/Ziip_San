package br.com.sigsan.intelligence.quality.scada;

import br.com.sigsan.core.json.Json;
import br.com.sigsan.intelligence.quality.domain.QualityParameter;
import br.com.sigsan.intelligence.quality.domain.QualityAnalysisResult;
import br.com.sigsan.intelligence.quality.domain.QualitySample;
import br.com.sigsan.intelligence.quality.domain.SamplingPoint;
import br.com.sigsan.intelligence.quality.repository.QualityAnalysisResultRepository;
import br.com.sigsan.intelligence.quality.repository.QualityParameterRepository;
import br.com.sigsan.intelligence.quality.repository.QualitySampleRepository;
import br.com.sigsan.intelligence.quality.repository.SamplingPointRepository;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.core.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ingestão de telemetria SCADA por MQTT.
 *
 * <p><b>Desligado por padrão.</b> O bean só existe com {@code sigsan.scada.mqtt.enabled=true}; sem a
 * propriedade nada conecta ao broker. Ligado, {@code sigsan.scada.mqtt.broker} é obrigatório (sem
 * default inventado) e a falha de conexão na subida derruba a aplicação, em vez de deixá-la de pé
 * fingindo que recebe dados.
 *
 * <p><b>Tenant.</b> O callback do Paho roda fora de qualquer requisição, então o tenant vem do tópico
 * ({@link ScadaTopic}: {@code sigsan/{tenantUuid}/scada/{data|alert}/{tag}}) e é posto no
 * {@link TenantContextHolder} só durante o processamento da mensagem, com limpeza em {@code finally}:
 * a thread do Paho é reaproveitada, e um tenant esquecido nela contaminaria a mensagem seguinte.
 *
 * <p><b>Antes de ligar em produção:</b> o tenant do tópico é afirmado por quem publica. O broker
 * precisa de ACL que só permita a cada cliente publicar em {@code sigsan/<seu-tenant>/#}; sem isso,
 * qualquer cliente conectado grava telemetria em nome de outro tenant. A validação do UUID contra o
 * catálogo de tenants e a resolução do schema a partir dele ficam para a etapa E5: hoje o contexto
 * recebe só o {@code tenantId}, e o schema da sessão JPA cai no padrão do resolvedor.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sigsan.scada.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ScadaIntegrationService {

    private final ScadaTagRepository tagRepository;
    private final ScadaDataPointRepository dataPointRepository;
    private final QualityParameterRepository parameterRepository;
    private final QualitySampleRepository sampleRepository;
    private final QualityAnalysisResultRepository analysisResultRepository;
    private final SamplingPointRepository samplingPointRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${sigsan.scada.mqtt.broker}")
    private String mqttBroker;

    @Value("${sigsan.scada.mqtt.username:}")
    private String mqttUsername;

    @Value("${sigsan.scada.mqtt.password:}")
    private String mqttPassword;

    @Value("${sigsan.scada.mqtt.client-id:sigsan-quality}")
    private String mqttClientId;

    private MqttClient mqttClient;

    /**
     * Tags ativas por (tenant, nome), carregadas na primeira mensagem de cada uma. A versão anterior
     * carregava tudo no {@code @PostConstruct}, sem tenant no contexto, e o cache saía vazio. Sem
     * expiração: alterar ou desativar uma tag exige reiniciar o serviço. Tag desconhecida não entra no
     * cache, para que uma tag cadastrada depois passe a ser aceita.
     */
    private final Map<TagKey, ScadaTag> tagCache = new ConcurrentHashMap<>();

    private record TagKey(UUID tenantId, String tagName) {
    }

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(mqttBroker, mqttClientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);
            options.setAutomaticReconnect(true);

            if (!mqttUsername.isEmpty()) {
                options.setUserName(mqttUsername);
                options.setPassword(mqttPassword.toCharArray());
            }

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    processMqttMessage(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used for subscriber
                }
            });

            mqttClient.connect(options);
            mqttClient.subscribe(ScadaTopic.SUBSCRIPTION, 1);
            log.info("Connected to MQTT broker: {}", mqttBroker);
        } catch (MqttException e) {
            throw new IllegalStateException("SCADA ligado, mas a conexão com o broker MQTT falhou: " + mqttBroker, e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException e) {
            log.error("Error closing MQTT client", e);
        }
    }

    /**
     * Processa uma mensagem recebida. Visível no pacote para teste: o callback do Paho só repassa o
     * tópico e os bytes.
     *
     * <p>Mensagem descartada ou rejeitada não leva o payload ao log: é conteúdo de quem publica, sem
     * validação, e pode ser grande. Só o alerta, cujo texto é o próprio aviso, é registrado inteiro.
     */
    void processMqttMessage(String topic, byte[] payload) {
        Optional<ScadaTopic> parsed = ScadaTopic.parse(topic);
        if (parsed.isEmpty()) {
            // O tópico também não vai para o log: é texto livre de quem publica.
            log.warn("Mensagem MQTT descartada: tópico fora do formato {}", ScadaTopic.SUBSCRIPTION);
            return;
        }
        ScadaTopic t = parsed.get();

        TenantContextHolder.setTenantId(t.tenantId());
        try {
            Optional<ScadaTag> tag = findTag(t.tenantId(), t.tag());
            if (tag.isEmpty()) {
                log.warn("Mensagem SCADA descartada: tag {} desconhecida ou inativa no tenant {}",
                        t.tag(), t.tenantId());
                return;
            }
            String body = new String(payload, StandardCharsets.UTF_8);
            switch (t.type()) {
                case DATA -> processDataPoint(tag.get(), body);
                case ALERT -> processAlert(tag.get(), body);
            }
        } catch (RuntimeException e) {
            log.error("Erro ao processar mensagem SCADA da tag {} no tenant {}", t.tag(), t.tenantId(), e);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Optional<ScadaTag> findTag(UUID tenantId, String tagName) {
        TagKey key = new TagKey(tenantId, tagName);
        ScadaTag cached = tagCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ScadaTag> loaded = tagRepository.findByTenantIdAndTagNameAndStatus(tenantId, tagName, "ACTIVE");
        loaded.ifPresent(tag -> tagCache.put(key, tag));
        return loaded;
    }

    /**
     * Formato esperado: {@code {"value": 1.23, "quality": 192, "timestamp": "2024-01-15T10:30:00Z"}}.
     *
     * <p>{@code timestamp} e {@code quality} são obrigatórios. A versão anterior completava o que
     * faltasse com {@code now()} e 192 ("boa" no padrão OPC): uma leitura sem hora virava leitura de
     * agora, e uma de qualidade desconhecida virava confiável. Mensagem incompleta é rejeitada.
     */
    private void processDataPoint(ScadaTag tag, String payload) {
        JsonNode json;
        try {
            json = Json.readTree(payload);
        } catch (IllegalStateException e) {
            // Sem a exceção no log: a causa do Jackson cita o trecho lido do payload.
            log.warn("Mensagem SCADA da tag {} rejeitada: payload não é JSON", tag.getTagName());
            return;
        }
        JsonNode tsNode = json.get("timestamp");
        JsonNode qualityNode = json.get("quality");
        JsonNode valueNode = json.get("value");

        if (tsNode == null || !tsNode.isString() || qualityNode == null || !qualityNode.isIntegralNumber()
                || !qualityNode.canConvertToInt()) {
            log.warn("Mensagem SCADA da tag {} rejeitada: sem timestamp ou sem quality", tag.getTagName());
            return;
        }
        if (valueNode != null && !valueNode.isNull() && !valueNode.isNumber()) {
            log.warn("Mensagem SCADA da tag {} rejeitada: value não é numérico", tag.getTagName());
            return;
        }
        OffsetDateTime timestamp;
        try {
            timestamp = OffsetDateTime.parse(tsNode.asString());
        } catch (DateTimeParseException e) {
            log.warn("Mensagem SCADA da tag {} rejeitada: timestamp fora do ISO-8601 com fuso", tag.getTagName());
            return;
        }
        int quality = qualityNode.asInt();
        BigDecimal rawValue = valueNode == null || valueNode.isNull() ? null : valueNode.decimalValue();

        BigDecimal engineeringValue = convertToEngineering(tag, rawValue);

        ScadaDataPoint dataPoint = new ScadaDataPoint();
        dataPoint.setTenantId(TenantContextHolder.getTenantId());
        dataPoint.setTag(tag);
        dataPoint.setTimestamp(timestamp);
        dataPoint.setValue(engineeringValue);
        dataPoint.setRawValue(rawValue);
        dataPoint.setQuality(quality);
        dataPointRepository.save(dataPoint);

        // Update tag cache
        tag.setLastValue(engineeringValue);
        tag.setLastQuality(quality);
        tag.setLastTimestamp(timestamp);

        // Check alarms
        checkAlarms(tag, engineeringValue);

        // If linked to quality parameter, create analysis result
        if (tag.getQualityParameterId() != null) {
            createOrUpdateAnalysisResult(tag, engineeringValue, timestamp);
        }
    }

    private void processAlert(ScadaTag tag, String payload) {
        log.warn("SCADA Alert for tag {}: {}", tag.getTagName(), payload);
        // Implement alert processing (notifications, escalation, etc.)
    }

    private BigDecimal convertToEngineering(ScadaTag tag, BigDecimal rawValue) {
        if (rawValue == null) return null;
        return rawValue.multiply(tag.getScaleFactor()).add(tag.getOffsetValue());
    }

    private void checkAlarms(ScadaTag tag, BigDecimal value) {
        if (value == null) return;

        if (tag.getQualityParameterId() != null) {
            QualityParameter param = parameterRepository.findById(tag.getQualityParameterId()).orElse(null);
            if (param != null) {
                if (param.getCriticalMin() != null && value.compareTo(param.getCriticalMin()) < 0) {
                    triggerAlarm(tag, "CRITICAL_LOW", param.getCriticalMin());
                } else if (param.getCriticalMax() != null && value.compareTo(param.getCriticalMax()) > 0) {
                    triggerAlarm(tag, "CRITICAL_HIGH", param.getCriticalMax());
                } else if (param.getAlertMin() != null && value.compareTo(param.getAlertMin()) < 0) {
                    triggerAlarm(tag, "ALERT_LOW", param.getAlertMin());
                } else if (param.getAlertMax() != null && value.compareTo(param.getAlertMax()) > 0) {
                    triggerAlarm(tag, "ALERT_HIGH", param.getAlertMax());
                }
            }
        }
    }

    private void triggerAlarm(ScadaTag tag, String alarmType, BigDecimal threshold) {
        log.warn("SCADA ALARM: {} - Tag: {} Value: {} Threshold: {}", alarmType, tag.getTagName(), tag.getLastValue(), threshold);
        // Send notification, create alert event, etc.
    }

    private void createOrUpdateAnalysisResult(ScadaTag tag, BigDecimal value, OffsetDateTime timestamp) {
        // Find or create sample for this parameter at this sampling point
        if (tag.getSamplingPointId() != null) {
            SamplingPoint point = samplingPointRepository.findById(tag.getSamplingPointId()).orElse(null);
            if (point != null) {
                // Find or create a sample for today
                QualitySample sample = sampleRepository.findBySamplingPointIdAndStatus(
                        tag.getSamplingPointId(), QualitySample.SampleStatus.IN_ANALYSIS).orElseGet(() -> {
                    QualitySample newSample = new QualitySample();
                    newSample.setTenantId(TenantContextHolder.getTenantId());
                    newSample.setSampleCode("SCADA-" + tag.getSamplingPointId() + "-" + OffsetDateTime.now().toLocalDate());
                    newSample.setSamplingPoint(point);
                    newSample.setSampleType(QualitySample.SampleType.OPERATIONAL);
                    newSample.setStatus(QualitySample.SampleStatus.IN_ANALYSIS);
                    newSample.setCollectedAt(OffsetDateTime.now());
                    return sampleRepository.save(newSample);
                });

                // Create or update analysis result
                QualityAnalysisResult result = analysisResultRepository
                        .findBySampleIdAndParameterId(sample.getId(), tag.getQualityParameterId())
                        .orElseGet(() -> {
                            QualityAnalysisResult newResult = new QualityAnalysisResult();
                            newResult.setTenantId(TenantContextHolder.getTenantId());
                            newResult.setSample(sample);
                            newResult.setParameter(parameterRepository.findById(tag.getQualityParameterId()).orElse(null));
                            newResult.setStatus(QualityAnalysisResult.AnalysisStatus.IN_PROGRESS);
                            return newResult;
                        });

                result.setResultValue(tag.getLastValue());
                result.setUnit(tag.getUnit());
                result.setAnalyzedAt(OffsetDateTime.now());
                result.setStatus(QualityAnalysisResult.AnalysisStatus.COMPLETED);
                analysisResultRepository.save(result);
            }
        }
    }

    /**
     * Publica um comando para uma tag do tenant corrente em {@code sigsan/{tenantUuid}/command/{tag}}.
     *
     * <p>Tenant ausente, tag desconhecida ou falha de publicação lançam exceção: um comando de campo
     * que não sai precisa ser visto por quem o deu, não só aparecer no log.
     */
    public void publishCommand(String tagName, BigDecimal value) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Comando SCADA sem tenant no contexto");
        }
        if (value == null) {
            throw new IllegalArgumentException("Comando SCADA sem valor para a tag " + tagName);
        }
        if (findTag(tenantId, tagName).isEmpty()) {
            throw new IllegalArgumentException("Tag SCADA desconhecida ou inativa: " + tagName);
        }
        // A versão anterior montava o JSON com String.format; Json.write garante número e data ISO-8601.
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("value", value);
        command.put("timestamp", OffsetDateTime.now());
        try {
            MqttMessage message = new MqttMessage(Json.write(command).getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(false);
            mqttClient.publish(ScadaTopic.command(tenantId, tagName), message);
        } catch (MqttException e) {
            throw new IllegalStateException("Falha ao publicar comando SCADA para a tag " + tagName, e);
        }
    }
}
