// File: frontend/vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Port 3000 on purpose: it is the origin the backend's CORS config allows.
//
// `base` controls the public path assets are served from. GitHub Pages for a
// project repo serves at https://<user>.github.io/<repo>/, not the domain
// root, so the production build needs base=/<repo>/. The CI workflow
// (.github/workflows/pages.yml) sets BASE_PATH to that automatically; local
// dev never sets it, so it falls back to "/" and nothing changes here.
export default defineConfig({
  base: process.env.BASE_PATH || '/',
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
  },
});
