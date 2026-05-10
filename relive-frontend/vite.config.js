import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {},
  },
  // Leaflet imports PNG marker images — tell Vite to handle them as assets
  assetsInclude: ['**/*.png', '**/*.jpg', '**/*.svg'],
})