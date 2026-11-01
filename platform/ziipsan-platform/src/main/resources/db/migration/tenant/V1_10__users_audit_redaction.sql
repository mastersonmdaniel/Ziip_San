-- Platform Migration for Tenant Schema
-- Version: 1.10.0
-- Description: P0.13 (integracao turno 8) — regras de redacao das colunas pessoais da V1_9.
--
-- A V1_4 prometia que as regras de users voltariam com as tabelas; a V1_9 recriou
-- users sem elas e o AuditRedactionCoverageTest quebrou (display_name e email sem
-- regra nem justificativa). display_name e email sao dados pessoais e entram na
-- trilha de auditoria redigidos (REDACT), como os demais campos pessoais da V1_4.

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('users', 'display_name', 'REDACT', 'nome de exibicao do usuario do tenant'),
    ('users', 'email',        'REDACT', 'e-mail do usuario do tenant')
ON CONFLICT (table_name, column_name) DO NOTHING;
