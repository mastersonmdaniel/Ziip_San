# ED-53 (GIS-09) — pendências, fora de escopo e ensaio SAMAE

## Fora desta entrega (ED-84) — declarado, NÃO construído

Viabilidade, loteamento, recebimento de rede e recomposição de
obras pertencem à ED-84. Nenhuma tela, endpoint, tabela ou regra
desses quatro temas foi criada neste pacote (OPE-27 remetido à
ED-84, conforme o `consumed` da tarefa).

## Itens SAMAE 2.8 e 2.27 — ensaio da onda A

O aceite pede os itens 2.8 (cadastro) e 2.27 do SAMAE "sem falha
no ensaio da onda A". O ensaio é ato de homologação com
ambiente integrado e rede de piloto — não executável nesta VM.
O que esta entrega deixa pronto para o ensaio:

- 2.8 (parte GIS): cadastro de unidades ETA/ETE/elevatórias/
  reservatório com geometria (SG-148..152), editor completo e
  formulário gerado do JSON Schema — regras e contratos
  provados por teste unitário; a demonstração no ensaio é
  pendência de homologação.
- 2.27: edição web completa sobre pacotes com revisão por
  outra pessoa (GIS-04) — idem.

Nenhum dos dois é declarado "sem falha": isso só o ensaio diz.

## Pendências nomeadas

1. **Flyway**: SQL em `db/pendente/ED-53__gis_web_edit.sql` sem
   número (nenhum reservado à ED-53; série na main até V13_12,
   V13_13 ocupado pelo pacote local da P3.27). A integração
   promove ao próximo V13_x livre.
2. **Pendência 12 (herdada da ED-55)**: como o endpoint define
   o GUC a partir do mapeamento de papéis — decisão de
   integração/dono, NÃO resolvida aqui; o serviço segue o
   padrão de contexto existente (TenantContextHolder).
3. **Porta do cadastro (SG-866/TR-1053)**: `DrawnConnectionPort`
   sem implementação ligada ao módulo registry (ED-13) neste
   pacote — sem a porta, a sincronização fica PENDING
   registrada, nunca fingida SYNCED. A ligação da porta é da
   integração.
4. **Authority própria de edição web** no catálogo
   (P0.13/P1.37): não inventada; escritas usam `gis:import-edit`
   e a granularidade fina é a tabela `gis_layer_permission`.
5. **Ambiente real GeoServer/GDAL** (herdado da ED-67): a
   lógica de edição foi provada sem ambiente externo; tile/
   renderização seguem a GIS-06 (P3.25).
6. **Split/merge persistidos**: a matemática é de `GisGeom`
   (provada); a segmentação gravada passa pelos itens do
   pacote GIS-04 — função PostGIS própria de split não foi
   criada sem ensaio em PostGIS real (ver cabeçalho do SQL).
7. **IT de banco** (`Gis09WebEditIT`): REESCRITA na correção
   D3 (a versão original era vacuosa — substituída, declarado
   no javadoc dela e no CORRECAO-ED-53.md) com 7 cenários reais
   de serviço + SQL contra o SQL pendente; roda na CI do dono;
   não executada nesta VM (sem PostgreSQL/PostGIS).
8. **Frontend**: ferramentas da página ligadas às operações
   geométricas puras (utils/geometry.ts) e aos endpoints de
   geometria/formulário/permissões do serviço (correção D8);
   desenho no mapa (GIS-06) e gravação em pacote (GIS-04)
   seguem ESQUELETO fora da página, marcado nela. Type-check
   `tsc --strict` limpo com toolchain ad hoc nesta VM; o build
   do PROJETO (Vite) segue não executado — conferência na
   integração.
9. **Vínculo factory_code** dos kinds ETA/ETE/ELEVATORY/
   RESERVOIR com o catálogo de fábrica: conferido na
   integração; nenhum código de fábrica foi inventado.
