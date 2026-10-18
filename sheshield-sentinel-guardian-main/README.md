# Naari website — narios.vercel.app

This directory is the sole retained website project for https://narios.vercel.app. The existing folder name is retained to preserve deployment root configuration.

## Local development

Use Node.js compatible with the pinned Vite release (Node 22.12+ recommended) and npm:

```sh
npm ci
npm run dev
npm run build
```

`package-lock.json` is the single dependency lockfile. The application uses React, TanStack Start/Router, Tailwind and Framer Motion with the existing Lovable Vite integration. Keep the established router/server/error handling and build plugins intact.

## Application structure

- `src/routes/`: page entry and root shell.
- `src/components/sections/`: landing page sections.
- `src/components/demo/`, `src/hooks/use-sentinel-demo.ts`: isolated interactive safety-state simulation.
- `src/components/watch/`: Watch OS Studio and browser watch simulator.
- `public/icons/`, `public/wallpapers/`: assets used by the watch simulator.

Browser simulations do not prove phone SMS/call delivery or real watch sensor behavior. Actual watch code, audio drivers and BLE transport are in `../firmware/`.

## Deployment

The local `.vercel/` connection targets the existing `narios` project; it is not committed. Preserve its project root and follow the existing deployment process. Local build cleanup does not publish or change the hosted website automatically.

Generated output, dependencies, provider metadata and caches are ignored. See `../docs/REPOSITORY_CLEANUP.md` for the cleanup scope. Follow `AGENTS.md` when changing this project; do not rewrite published history.
