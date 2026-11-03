#!/usr/bin/env node
/**
 * P0.35 — gera o tema do Keycloak (login + e-mail) a partir do pacote de
 * marca. Trocar ZIIPSAN_BRAND (ou o brand.json) e rodar este script muda
 * login e e-mail sem alterar codigo: e o que scripts/brand/test-brand-swap.mjs
 * prova. Os arquivos gerados vao para infra/keycloak/themes/<tema>/; a
 * imagem de producao que os embarca e da P0.29 (outra frente) — aqui vao
 * so os arquivos do tema, o teste de troca e este gerador.
 * Uso: node scripts/brand/generate-keycloak-theme.mjs [--brand-dir DIR] [--themes-dir DIR]
 */
import { readFileSync, writeFileSync, copyFileSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.split('=')
    return [k.replace(/^--/, ''), v]
  }),
)
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const brandDir = args['brand-dir'] || join(root, 'branding', process.env.ZIIPSAN_BRAND || 'ziipsan')
const themesDir = args['themes-dir'] || join(root, 'infra', 'keycloak', 'themes')
const brand = JSON.parse(readFileSync(join(brandDir, 'brand.json'), 'utf8'))
const texts = JSON.parse(readFileSync(join(brandDir, 'texts', 'pt-BR.json'), 'utf8'))
const themeName = process.env.ZIIPSAN_THEME || 'ziipsan'
const themeDir = join(themesDir, themeName)

const sub = (s) =>
  s
    .replaceAll('__PRODUCT_NAME__', brand.productName)
    .replaceAll('__SHORT_NAME__', brand.shortName)
    .replaceAll('__DESCRIPTION__', brand.description)
    .replaceAll('__BRAND_PRIMARY__', brand.brandPrimary)
    .replaceAll('__THEME_COLOR_LIGHT__', brand.themeColor.light)
    .replaceAll('__EMAIL_SIGNATURE__', texts.emailSignature)
const write = (rel, content) => {
  const path = join(themeDir, rel)
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, sub(content))
}
const GEN = `# GERADO por scripts/brand/generate-keycloak-theme.mjs a partir do pacote de marca.\n# Nao edite a mao: troque o pacote branding/ e regenere.\n`

write(
  'login/theme.properties',
  `${GEN}parent=keycloak\nstyles=css/login.css\nlocales=pt-BR,en\n`,
)
write(
  'login/messages/messages_pt-BR.properties',
  `${GEN}loginTitle=__PRODUCT_NAME__\nloginTitleHtml=__PRODUCT_NAME__\ndoLogIn=Entrar\ndoForgotPassword=Esqueci minha senha\n`,
)
write(
  'login/resources/css/login.css',
  `/* GERADO do pacote de marca: nao edite a mao. */\n:root { --brand-primary: __BRAND_PRIMARY__; }\nbody { background-color: #f1f5f9; }\na { color: var(--brand-primary); }\nbutton[type="submit"], input[type="submit"] { background-color: var(--brand-primary); border-color: var(--brand-primary); }\n#kc-header-wrapper { color: var(--brand-primary); }\n`,
)
write(
  'email/theme.properties',
  `${GEN}parent=keycloak\nlocales=pt-BR,en\n`,
)
write(
  'email/messages/messages_pt-BR.properties',
  `${GEN}passwordResetSubject=Redefinir sua senha no __PRODUCT_NAME__\nemailVerificationSubject=Confirme seu e-mail no __PRODUCT_NAME__\nexecuteActionsSubject=Acao necessaria na sua conta __PRODUCT_NAME__\n`,
)
const emailHtml = (title, body) => `<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>${title}</title></head>
<body style="font-family: sans-serif; color: #0f172a;">
<div style="background: __BRAND_PRIMARY__; color: #ffffff; padding: 16px; font-size: 20px; font-weight: bold;">__PRODUCT_NAME__</div>
<div style="padding: 16px;">${body}</div>
<div style="padding: 16px; color: #64748b; font-size: 12px;">__EMAIL_SIGNATURE__</div>
</body></html>
`
const emailText = (body) => `__PRODUCT_NAME__\n\n${body}\n\n__EMAIL_SIGNATURE__\n`
write(
  'email/html/password-reset.ftl',
  emailHtml('Redefinir senha', `<p>Para redefinir sua senha, abra o link abaixo. Ele expira em \${linkExpiration} minutos.</p><p><a href="\${link}">Redefinir senha</a></p>`),
)
write('email/text/password-reset.ftl', emailText(`Para redefinir sua senha, abra o link abaixo (expira em \${linkExpiration} minutos):\n\${link}\n`))
write(
  'email/html/email-verification.ftl',
  emailHtml('Confirmar e-mail', `<p>Para confirmar seu e-mail, abra o link abaixo. Ele expira em \${linkExpiration} minutos.</p><p><a href="\${link}">Confirmar e-mail</a></p>`),
)
write('email/text/email-verification.ftl', emailText(`Para confirmar seu e-mail, abra o link abaixo (expira em \${linkExpiration} minutos):\n\${link}\n`))
write(
  'email/html/execute-actions.ftl',
  emailHtml('Acao necessaria', `<p>Ha uma acao pendente na sua conta. Abra o link abaixo (expira em \${linkExpiration} minutos).</p><p><a href="\${link}">Continuar</a></p>`),
)
write('email/text/execute-actions.ftl', emailText(`Ha uma acao pendente na sua conta. Abra o link abaixo (expira em \${linkExpiration} minutos):\n\${link}\n`))

// Logo do tema: copia do pacote (o tema nao carrega SVG de outro lugar).
mkdirSync(join(themeDir, 'login', 'resources', 'img'), { recursive: true })
copyFileSync(join(brandDir, 'assets', 'logo-light.svg'), join(themeDir, 'login', 'resources', 'img', 'logo.svg'))
console.log(`OK: tema Keycloak '${themeName}' gerado a partir de ${brandDir}`)
