# Repository cleanup — 17 October 2026

## Retained projects

- **Main website:** `sheshield-sentinel-guardian-main/`, serving https://narios.vercel.app. Confirmed by the local Vercel project name `narios` and matching public page title/content. Keep this folder name so the existing deployment root stays valid.
- **Watch OS:** `firmware/main/`, configuration, partitions, dependencies.lock, asset generators, font/license, tests and actual C++ browser preview tools. Existing v8.1 changes were preserved.
- **Android companion:** `Naari-os-app/` appeared during cleanup and contains an Android Gradle project with its own Git repository. Preserved without edits; not an extra website. Its functionality/build was not assessed in this website cleanup.
- **Product information:** companion PRD, poster brief, hardware/UI documentation and historical audits/proposals.

The cleanup changes the working repository, not deployed cloud projects. No deployment, remote project deletion, commit, push or published-history rewrite was performed.

## Removed

| Item | Reason |
| --- | --- |
| `dashboard/` | Separate obsolete guardian portal and duplicate browser watch implementation; not used by the main website |
| `backend/` | Express/SMS-webhook demo used by the removed portal; no references from the main site or active watch transport |
| `supabase/` | Database schema for the removed demo system; no dependency from the retained website |
| 46 unused `src/components/ui/*.tsx` files | Generated component library not reachable from any retained page/runtime/build entry |
| `src/hooks/use-mobile.tsx` | Only consumed by the removed unused sidebar component |
| 39 direct website dependencies | Only needed by unused components; removed from package manifest and npm lock |
| `bun.lock`, `bunfig.toml` | Alternate unused package-manager state; npm/package-lock.json is the single installation path |
| `components.json` | Generator configuration for the removed unused component templates |
| `public/favicon.ico` | Unreferenced duplicate favicon; the active SVG favicon remains |
| `firmware/build/` | Stale generated firmware build; current production build remains available locally |
| `.DS_Store` | OS metadata |

The site's landing sections, linked watch/companion demo, Watch OS Studio, native error handling, route infrastructure, referenced icons/wallpapers and local Vercel connection are retained. Dynamic CSS and framework dependencies remain where needed; no speculative selector deletion or framework rewrite was attempted.

## Generated files and Git

Added root `.gitignore` and removed previously tracked generated content from the Git index: firmware build directories, native preview output, managed dependencies and caches. The latest `firmware/build-premium/` image/manifest and `firmware/build-ui-preview/` output remain on disk and are ignored. Managed components are restored by the ESP-IDF dependency manager using the checked-in manifest/lock.

Deleted old web projects included their tracked node_modules and dist output. No node_modules or firmware/build paths remain in the Git index. Large deletion counts largely represent these generated files, not watch source removal. Existing Git history still contains old files; reclaiming historical Git-object storage would require a separate history rewrite, which was not done.

Made local VS Code firmware configuration workspace-relative rather than pointing to an old Desktop location. It is ignored as machine-local configuration.

## Verification

- Baseline website production build passed before removal.
- TypeScript import-graph traversal from all route/router/start/server/Vite entries found 47 unreachable source files; after removal, no unresolved relative imports or orphan source files remain.
- Retained public assets were checked against source references, including dynamic watch app/wallpaper configuration.
- `npm ci --ignore-scripts --no-audit --no-fund` passed from the updated lockfile.
- `npm run build` passed after the clean dependency installation.
- `npm run typecheck` passed.
- `git diff --check` passed.
- Local HTTP smoke check covers the homepage, demo/studio content and retained public assets.
- Current production firmware image, manifest, watch BLE source and local Vercel linkage were checked for preservation. Firmware source was not changed by this cleanup.

Use root README for current project layout and commands. Historical audits reference removed directories deliberately as historical findings; they are not a current component inventory.
