package br.com.sigsan.intelligence.quality.scada;

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
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScadaIntegrationService {

    private final ScadaTagRepository tagRepository;
    private final ScadaDataPointRepository dataPointRepository;
    private final QualityParameterRepository parameterRepository;
    private final QualitySampleRepository sampleRepository;
    private final QualityAnalysisResultRepository analysisResultRepository;
    private final SamplingPointRepository samplingPointRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${scada.mqtt.broker:tcp://localhost:1883}")
    private String mqttBroker;

    @Value("${scada.mqtt.username:}")
    private String mqttUsername;

    @Value("${scada.mqtt.password:}")
    private String mqttPassword;

    @Value("${scada.mqtt.client-id:sigsan-quality}")
    private String mqttClientId;

    @Value("${scada.mqtt.topic-pattern:sigsan/+/quality/+}")
    private String topicPattern;

    private MqttClient mqttClient;
    private final Map<String, ScadaTag> tagCache = new ConcurrentHashMap<>();

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
                    processMqttMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used for subscriber
                }
            });

            mqttClient.connect();
            mqttClient.subscribe(topicPattern, 1);
            log.info("Connected to MQTT broker: {}", mqttBroker);

            // Load tags into cache
            reloadTagCache();

        } catch (MqttException e) {
            log.error("Failed to initialize MQTT client", e);
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

    private void reloadTagCache() {
        List<ScadaTag> tags = tagRepository.findByTenantIdAndStatus(TenantContextHolder.getTenantId(), "ACTIVE");
        tagCache.clear();
        for (ScadaTag tag : tags) {
            tagCache.put(tag.getTagName(), tag);
        }
        log.info("Loaded {} SCADA tags into cache", tags.size());
    }

    private void processMqttMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("Received MQTT message on topic {}: {}", topic, payload);

            // Parse topic: sigsan/{tenant}/{type}/{tag}
            String[] parts = topic.split("/");
            if (parts.length < 4) {
                log.warn("Invalid topic format: {}", topic);
                return;
            }

            String tenantSchema = parts[1];
            String messageType = parts[2];
            String tagName = parts[3];

            if (!tenantSchema.equals(TenantContextHolder.getTenant())) {
                log.debug("Message for different tenant: {} vs {}", tenantSchema, TenantContextHolder.getTenant());
                return;
            }

            ScadaTag tag = tagCache.get(tagName);
            if (tag == null) {
                log.warn("Unknown tag: {}", tagName);
                return;
            }

            if ("data".equals(messageType)) {
                processDataPoint(tag, payload);
            } else if ("alert".equals(messageType)) {
                processAlert(tag, payload);
            }

        } catch (Exception e) {
            log.error("Error processing MQTT message on topic {}", topic, e);
        }
    }

    private void processDataPoint(ScadaTag tag, String payload) {
        try {
            // Parse payload (JSON expected)
            // Expected format: {"value": 1.23, "quality": 192, "timestamp": "2024-01-15T10:30:00Z"}
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var jsonNode = mapper.readTree(payload);

            BigDecimal rawValue = jsonNode.has("value") ? jsonNode.get("value").decimalValue() : null;
            Integer quality = jsonNode.has("quality") ? jsonNode.get("quality").asInt(192) : 192;
            OffsetDateTime timestamp = jsonNode.has("timestamp") ?
                    OffsetDateTime.parse(jsonNode.get("timestamp").asText()) : OffsetDateTime.now();

            BigDecimal engineeringValue = convertToEngineering(tag, rawValue);

            // Store data point
            ScadaDataPoint dataPoint = new ScadaDataPoint();
            dataPoint.setTenantId(TenantContextHolder.getTenantId());
            dataPoint.setTag(tag);
            dataPoint.setTimestamp(OffsetDateTime.now());
            dataPoint.setValue(engineeringValue);
            dataPoint.setRawValue(rawValue);
            dataPoint.setQuality(quality);
            dataPoint.setTimestamp(timestamp);
            dataPointRepository.save(dataPoint);

            // Update tag cache
            tag.setLastValue(engineeringValue);
            tag.setLastQuality(quality);
            tag.setLastTimestamp(OffsetDateTime.now());

            // Check alarms
            checkAlarms(tag, engineeringValue);

            // If linked to quality parameter, create analysis result
            if (tag.getQualityParameterId() != null) {
                createOrUpdateAnalysisResult(tag, engineeringValue, timestamp);
            }

        } catch (Exception e) {
            log.error("Error processing data point for tag {}", tag.getTagName(), e);
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

    public void publishCommand(String tagName, BigDecimal value) {
        try {
            ScadaTag tag = tagCache.get(tagName);
            if (tag != null) {
                String topic = "sigsan/" + TenantContextHolder.getTenant() + "/command/" + tagName;
                var payload = String.format("{\"value\":%s,\"timestamp\":\"%s\"}", value, OffsetDateTime.now());
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                message.setRetained(false);
                mqttClient.publish(topic, message);
            }
        } catch (MqttException e) {
            log.error("Failed to publish command to tag {}", tagName, e);
        }
    }
}