# fiscal-contract-fixtures — vetores de referência (FISCAL-PORTE-JAVA §5)

Regra (aceite da P3.7): ao fim de cada ciclo de homologação fiscal local, os XMLs
assinados com o e-CNPJ da Ziip/TGP são **apagados** da área separada, com exclusão
registrada. O que fica guardado é **só** o vetor de referência de cada documento:

- **sem assinatura**;
- **sem o elemento `Signature`**;
- **sem certificado** (nenhum `X509Certificate`, nenhum dado do e-CNPJ real).

Os arquivos deste diretório são **sintéticos de teste** (emitente/destinatário são
os CNPJs de teste dos vetores do projeto; o namespace declara que não é o leiaute
do MOC). Nenhum deles é um XML autorizado real, e nenhum XML autorizado real da
homologação pode ser copiado para cá — o teste `FiscalContractFixturesTest`
recusa qualquer arquivo deste diretório que contenha `Signature`, certificado
ou o thumbprint configurado do certificado da Ziip/TGP.

Novos vetores nascem de `VetorReferencia.sanearParaFixture(...)` sobre o XML do
ciclo **antes** do expurgo, e são conferidos pela mesma regra.
