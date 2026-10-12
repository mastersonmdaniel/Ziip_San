# ADR-0026 — Apps híbridos com Capacitor

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0001 (P4: app de leitura offline); ADR-0003 (emissão fiscal); ADR-0006 (BFF com cookie); ADR-0016
  (marca do tenant; white-label por revenda); ADR-0022 (portal e realm `ziipsan-portal`); ADR-0023 (editais);
  `docs/arquitetura/ARQUITETURA-ALVO.md` §1 e §2 (sincronização de campo); RNF-07, RNF-31, RNF-60; plano P4.4
- **Base:** `docs/levantamento/editais/18d-aderencia-cadastro-medicao-gis.md` §3 (E6) e §4 (decisões 1 e 2);
  `18f-aderencia-atendimento-os-portal.md` §3 (ENT-05, ENT-09) e §4 (D1)

## Contexto

Os dois editais exigem leitura com emissão simultânea da conta em campo, com impressão térmica em bobina em branco e
leiaute desenhado, operação offline longa, fotos e GPS (VISAN 8.1 e alínea e do Anexo II; SAMAE 2.11, crítico), e app
de OS instalado por APK com banco local (TR-621, TR-639). Pedem também app do consumidor em Android e iOS, publicado nas
lojas com o nome e a marca do prestador (TR-653, SG-705, SG-779, SG-780). Nenhum pede iOS no campo.

O plano previa na P4.4 um PWA de leitura offline, sem emissão, impressão nem rastreamento. O navegador não acessa
Bluetooth clássico (o Web Bluetooth só fala BLE) e não mantém GPS com a tela apagada; a impressão Zebra e o
rastreamento ficariam frágeis (`18d` §4, decisão 1). PWA também não é publicado nas lojas como app do prestador.

O ADR-0016 (item 6) diz que white-label por revenda não entra. Os editais pedem outra coisa: a marca do **tenant**, que
o item 2 do mesmo ADR já aplica ao portal do cliente final, levada ao app publicado nas lojas.

## Decisão

1. **Apps híbridos com Capacitor** sobre o frontend React existente: mesmos componentes, validações e cliente da API,
   com plugins nativos para impressão, GPS em segundo plano, câmera e armazenamento offline.
2. **App de campo** (leitura com emissão simultânea, OS e impressão):
   - **só Android**;
   - distribuído por **APK assinado**, instalado diretamente ou por gestão de dispositivos do cliente; não é publicado
     nas lojas;
   - banco local cifrado, sincronização idempotente conforme a `ARQUITETURA-ALVO.md` §2, item 5;
   - entrega em etapas: leitura offline, depois emissão simultânea, depois OS de campo.
3. **App do cliente** (consumidor final):
   - **Android e iOS**, publicado nas lojas **com a marca de cada tenant** (nome, ícone, cores, subdomínio fixo), gerado
     por pipeline a partir do pacote de marca e de `tenant_settings` (ADR-0016);
   - funções do portal do consumidor (ADR-0022), com a mesma conta do realm `ziipsan-portal`;
   - publicado na conta de desenvolvedor do prestador quando a regra da loja exigir (a Apple recusa app gerado de
     modelo comercial publicado por quem não é o dono do conteúdo; conferir as regras vigentes a cada publicação).
4. **Conciliação com o ADR-0016:** o app do cliente é mais um canal da marca do tenant (item 2), como o portal e os
   e-mails, com o produto discreto ("operado com {produto}"). Não é white-label por revenda: nenhum terceiro revende o
   produto com marca própria. O item 6 do ADR-0016 continua valendo.
5. **Impressora:** linha **Zebra portátil (série ZQ, Bluetooth)** homologada. O produto tem **editor de leiaute** que
   gera ZPL ou CPCL por tenant, com código de barras de arrecadação e QR Code Pix impressos pela impressora. Outras
   marcas entram depois, por adaptador, só com demanda.
6. **Substitui a premissa de PWA** da tarefa de app de leitura (plano P4.4 e ADR-0001, "app de leitura offline").

## Consequências

### Positivas

- Um código para portal, app do cliente e app de campo; uma pessoa consegue manter.
- Acesso ao SDK da Zebra, ao serviço de localização em primeiro plano e à câmera, o que o PWA não oferece.
- Atende ao APK de Videira e à publicação nas lojas com a marca do prestador do SAMAE.

### Negativas

- Pipeline de build Android e iOS, assinatura e publicação **por tenant**; contas de desenvolvedor do prestador na
  Apple e no Google, com prazos de revisão das lojas fora do controle do Ziip.
- Plugins nativos a manter a cada versão do Android, do iOS e do Capacitor.
- **Autenticação nos apps:** o BFF com cookie `__Host-` do ADR-0006 não se transpõe diretamente ao WebView do app, cuja
  origem é local; cookie de terceiro é bloqueado no iOS. Será preciso decidir, em ADR próprio, entre OAuth nativo com
  PKCE e token no armazenamento seguro do sistema (exceção ao RNF-31) ou outra forma que preserve o ADR-0006.
- **Cálculo da conta no dispositivo:** a emissão simultânea exige calcular a conta offline com resultado idêntico ao
  do servidor (motor de consumo e `TariffEngine`, hoje em Java). A forma (porte, execução embarcada ou outra) fica para
  a tarefa da emissão, com teste de equivalência sobre os mesmos vetores.
- **Documento fiscal em campo:** a conta impressa no local precisa conviver com a NFAg-e (ADR-0003); a emissão offline
  depende do regime de contingência do leiaute, a confirmar antes da emissão simultânea.
- Hardware Zebra para desenvolvimento, homologação e demonstração (kit de campo da POC, ADR-0023).
- Notificação push passa por serviço do Google e da Apple, que entram como suboperadores no DPA; o conteúdo não leva dado
  pessoal.

### O que muda no código

- Projeto Capacitor no frontend, com alvo Android (campo e cliente) e iOS (cliente).
- Plugins: impressão Zebra (Bluetooth), localização em segundo plano, câmera, banco local cifrado.
- Editor de leiaute de impressão por tenant com saída ZPL e CPCL; teste com impressora real.
- Pipeline de geração do app do cliente por tenant a partir do pacote de marca.

### O que muda no roadmap

- P4.4 passa a ser "app de campo Android (Capacitor)": leitura offline como primeira entrega e emissão simultânea como
  segunda; OS de campo em seguida.
- App do cliente nas lojas entra na trilha de editais do P4 (ADR-0023).

## Alternativas consideradas

- **PWA para portal e campo (premissa anterior da P4.4).** Rejeitada: sem Bluetooth clássico para a Zebra, sem GPS
  confiável em segundo plano, sem APK e sem publicação nas lojas como app do prestador.
- **Apps nativos (Kotlin e Swift).** Rejeitada: duas bases de código e duas linguagens a mais para uma equipe pequena,
  com custo por cliente alto para o porte dos SAAEs.
- **Camada de impressão ZPL e ESC/POS desde o início.** Rejeitada por ora: dobra o teste de leiaute sem demanda
  comprovada; outras marcas entram por adaptador.
- **App do cliente único, com a marca do produto, para todos os tenants.** Rejeitada: o SAMAE exige o app nas lojas com
  o nome do prestador (SG-779).

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| ADR de autenticação nos apps (OAuth nativo com PKCE ou alternativa compatível com o ADR-0006) | P4, antes do primeiro app |
| Projeto Capacitor, pipeline de build e assinatura Android | P4 |
| App de campo: leitura offline com sincronização idempotente (substitui o PWA da P4.4) | P4 |
| Homologação da Zebra série ZQ por Bluetooth; editor de leiaute ZPL/CPCL por tenant | P4 |
| Emissão simultânea: cálculo da conta no dispositivo com teste de equivalência; contingência da NFAg-e | P4 (trilha de editais) |
| App de campo de OS (fotos, GPS, offline) | P4 (trilha de editais) |
| App do cliente Android e iOS por tenant, publicação nas lojas e push sem dado pessoal | P4 (trilha de editais) |
| Kit de campo para a POC (aparelho Android e impressora Zebra) | P4 |
