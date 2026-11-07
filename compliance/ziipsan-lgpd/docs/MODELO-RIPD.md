# Modelo de RIPD por módulo (P3.10)

Um RIPD por **módulo** do Ziip SAN que trate dado pessoal (o registro correspondente no
módulo lgpd é o `lgpd_dpia` já modelado — telas são da P3.40). Cada RIPD nasce ligado à
**ocorrência da P2.25** quando decorrer de incidente, e às operações de tratamento quando
a P3.40 as entregar.

## Identificação
- Módulo: [ziipsan-registry / ziipsan-billing / ziipsan-metering / …] e versão do modelo
- Controlador (tenant) e operador (Ziip SAN); encarregado do tenant: [`lgpd.dpo_contact`]

## Tratamentos do módulo
Para cada tratamento: finalidade; base legal; categorias de titulares; **categorias de
dado pelas colunas classificadas da P0.11 (DAD-29)** — listar tabela.coluna e classe
(ID/CT/LOC/FIN/SOC/SENS); compartilhamento e suboperadores; **transferência internacional**
(provedor fora do país exige as cláusulas-padrão da Res. CD/ANPD 19/2024 — conferência da
P3.9); retenção por tabela de retenção (`lgpd_retention_schedule`).

## Necessidade e proporcionalidade
Por que cada categoria é necessária à finalidade; o que aconteceria sem ela; minimização
aplicada (máscara da P0.11, acesso por papel).

## Riscos aos titulares
Cenários de risco (probabilidade × impacto) por categoria, com SENS destacado; riscos
residuais após as medidas.

## Medidas de segurança e mitigação
Controles do módulo (cifra, máscara com teto, registro em `personal_data_access_log`,
permissões do catálogo), plano de ação com responsável e prazo — ações viram ação da
ocorrência da P2.25 quando houver incidente.

## Vínculos
- Ocorrências da P2.25 ligadas: [identificadores]
- Registro de operações de tratamento (P3.40): [`processingRecordRef` — vínculo preparado
  na P3.10, preenchido na P3.40]
- Incidentes que citaram este RIPD: [identificadores]

## Revisão
Revisar a cada incidente relevante, mudança de tratamento ou troca de suboperador;
responsável e data da próxima revisão: [preencher].
