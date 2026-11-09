# Pacote LC.2 — Produtor, sessao 764b25ad-2ccf-40e5-afbf-986d42be0213
output "guardduty_detector_id" {
  value = aws_guardduty_detector.this.id
}

output "config_recorder_name" {
  value = aws_config_configuration_recorder.this.name
}
