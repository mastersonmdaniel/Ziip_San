# 23 — Nota de conferência: licença de uso comercial do ODA File Converter

Conferência da P1.25 (ED-55 depende dela na P3.26: DWG convertido pelo ODA
File Converter na etapa assistida, com hash do original e do DXF). Data da
conferência: 03/11/2026. Fontes públicas consultadas (não o contrato de
licença, que só a ODA fornece ao membro):

- Open Design Alliance — página de trial e acordos de membership
  (https://www.opendesign.com/free-trial);
- Open Design Alliance — Commercial Membership Agreement 2025
  (https://www.opendesign.com/agreements/2025/en/ODA%20Commercial%20Membership%20Agreement%202025%20Oct.pdf);
- Open Design Alliance — FAQ sobre nível de membership para programa CAD
  distribuído (https://www.opendesign.com/faq/question/membership-type-cad-program-distributed-free-which-membership-level-do-we-need);
- Open Design Alliance — verbete público (https://en.wikipedia.org/wiki/Open_Design_Alliance).

## O que foi apurado

1. **Os SDKs da ODA são licenciados por membership, não software livre:**
   fontes proprietárias, disponíveis a membros; membership por empresa, sem
   limite de usuários dentro da empresa membro.
2. **Downloads gratuitos de trial do ODA File Converter foram removidos em
   setembro de 2024** (registro público). Não há, nas fontes consultadas, um
   download gratuito vigente do conversor.
3. **Níveis e uso comercial (público, sujeito à redação vigente do acordo):**
   - Commercial: uso comercial limitado (distribuição de até 100 cópias);
     **uso web/SaaS não permitido**;
   - Sustaining: uso comercial ilimitado, **web e SaaS permitidos**;
   - Founding/Corporate: uso comercial ilimitado (Corporate: várias unidades
     de negócio).
4. **Uso do Ziip SAN (P3.26):** conversão DWG → DXF em etapa **assistida** de
   migração, interna à implantação, com hash do original e do convertido —
   não distribuição do conversor a clientes, nem embutimento no SaaS. As
   fontes públicas **não confirmam** que esse uso específico esteja coberto
   sem membership, nem qual nível o cobre.

## Conclusão (honesta, sem invenção)

- **A licença de uso comercial NÃO está conferida como existente.** O que
  está conferido é o quadro acima: sem membership, não há base pública para
  afirmar uso comercial; com membership, o nível depende do uso exato.
- **PENDENTE (antes da P3.26, dono do produto):** confirmar com a ODA, por
  escrito, (a) se a etapa assistida da P3.26 exige membership e qual nível;
  (b) custo e vigência; (c) se o conversor standalone tem termos próprios
  distintos dos SDKs. Sem essa confirmação, a P3.26 não pode assumir o
  conversor como disponível.
- **Alternativa já registrada no consolidado 18 §8.2:** SDK licenciado da
  ODA, com custo (também PENDENTE de cotação). Outra alternativa (conversor
  de terceiros ou implementação própria de DWG) não foi avaliada nesta
  entrega e não deve ser assumida sem análise.
- **Nenhum custo, prazo ou termo foi inventado aqui:** valores de membership
  que circulam em páginas públicas variam por ano e por pacote e não são
  reproduzidos como cotação; a cotação válida é a da ODA, no momento da
  contratação.

## Fontes

- Consolidado 18 §8.2 ("Licença do ODA File Converter": "Conferência no P1
  (P1.25); alternativa: SDK licenciado da ODA, com custo"); ED-55
  (consolidado 18 §5.1); P3.26 (plano v3.0).
