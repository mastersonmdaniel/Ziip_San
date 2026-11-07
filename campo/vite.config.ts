import { defineConfig } from 'vite';

// Build minimo do app de campo: sem plugins de marca do frontend web (o app tem
// identidade propria do tenant aplicada na casca nativa, nao no bundle web).
export default defineConfig({
  build: { outDir: 'dist', sourcemap: false },
});
