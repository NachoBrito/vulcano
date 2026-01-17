import { defineConfig } from "vite";

export default defineConfig({
  build: {
    minify: "terser", // Can also be 'esbuild'
    outDir: "dist",
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
    },
  },
});
