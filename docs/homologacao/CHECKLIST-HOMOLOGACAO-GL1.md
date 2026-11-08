# GL.1 — Checklist da homologação local (carga anonimizada)

Marcar só com evidência (registro, relatório ou log). Item sem evidência
fica desmarcado — este checklist não substitui o registro da carga.

## Acordos e origem (piloto)
- [ ] Termo de cooperação vigente com anonimização na origem e rede real autorizada (P0.30/P1.1, ADR-0033)
- [ ] Extração anonimizada NO piloto pelo roteiro da P0.45 (sem nome, documento, endereço, contato)
- [ ] Código substituto no lugar da matrícula; correspondência guardada só no piloto
- [ ] Totais oficiais do sistema do piloto recebidos e lançados no manifesto
- [ ] Lote de documentos anteriores é sintético, assinado com certificado de teste (nenhum XML real)

## Ambiente
- [ ] Perfil `homolog` da P0.27 no ar (ou recriado com `--reset`)
- [ ] `ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false` conferido na fonte que controla a variável: literal `"false"` no `docker-compose.yml` (api/migrate); `.env` sem a variável ligada (defesa extra)
- [ ] Portas só em 127.0.0.1; nenhum recurso AWS
- [ ] Manifesto preenchido e `validar-manifesto.py` verde (`tenantId` UUID, tipos GIS conhecidos em qualquer grafia, rede real com grupo **e** responsáveis nomeados)
- [ ] Comandos dos importadores fiados (`ZIIPSAN_GL1_CMD_*`) ou pendência registrada no diário

## Cadastro técnico (P3.26 / ADR-0033)
- [ ] Rede real (redes, peças, estruturas) com acesso restrito declarado no manifesto
- [ ] Cada camada pessoal (ligação/ramal/cavalete/imóvel) com método registrado: deslocada na quadra, agregada por quadra ou sintética
- [ ] Nenhuma camada pessoal sem método (o validador e a P3.26 recusam)

## Execução e portões
- [ ] `--dry-run` e `--preflight-only` executados; registro parcial conferido
- [ ] Varredura de entrada verde (nenhum CPF/CNPJ formatado ou e-mail na extração — todos os arquivos, incl. `*.md` e binários)
- [ ] Cargas na ordem P1.2 → P2.10 → P2.19 → P3.26, pelos relatórios dos importadores
- [ ] `totais-carregados.json` consolidado dos relatórios (não digitado de memória)
- [ ] Conciliação verde: carregado == totais do piloto (unidades, saldo, histórico, XML)
- [ ] Conciliação fina amarrada: referências aos relatórios dos importadores (P1.2/P2.10/P2.19) preenchidas no registro ou relatórios anexados ao diretório da carga
- [ ] Varredura pós-carga da P0.45 verde: nenhum dado pessoal real na base local

## Fechamento
- [ ] Registro da carga CONCLUIDO em `docs/homologacao/cargas-homologacao/<carimbo>-gl1/`
- [ ] Desvios anotados por quem executou; divergências (se houve) com causa e recarga registrada
- [ ] Acesso restrito à rede real conferido no tenant antes de liberar a GL.2
- [ ] Registro + manifesto commitados como evidência (sem arquivos da extração)
