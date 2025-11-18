# Client Terminal SSR (Server-Based)

Lightweight browser-based POS terminal with SSR in Server.

## Architecture

```
Browser (Terminal)
├── Vanilla JS (~200 LOC)
├── Fetches from Hub
└── No frameworks, no build

    ↓ HTTP GET /client/pos

Hub (Port 3002) - Proxy/Router + Cache
├── Routes /client/* requests → Server
├── Caches API responses
└── Fast local cache

    ↓ Forward to Server (first time)

Server (Port 3000) - SSR + Business Logic
├── Renders HTML pages (Hiccup)
├── Client routes in server/client/
├── Centralized = easy updates
└── PostgreSQL/Datomic
```

**Why Server SSR?**
- Centralized updates (deploy once, all terminals get it)
- Rapid development (no hub redeployments)
- Server has all business logic already
- Hub is lightweight proxy/cache layer

## Files

### Server-Side (Clojure)
- `src/corebase/server/client/pages.clj` - SSR pages (250 LOC)
- `src/corebase/server/client/routes.clj` - Route handlers (100 LOC)

### Browser-Side (Vanilla JS)
- `resources/public/client/js/pos.js` - Browser JS (200 LOC)
- `resources/public/client/css/pos.css` - Styles (150 LOC)

**Total: 700 LOC** (vs 8,000+ LOC ClojureScript)

## Pages

- `/client/pos` - POS terminal (main)
- `/client/dashboard` - Dashboard
- `/client/orders` - Order history
- `/client/customers` - Customer list

## Performance

- **Initial load:** <50ms (SSR from Server via Hub)
- **API calls:** <5ms (Hub cache hit)
- **Bundle size:** 5KB (vs 500KB+ before)
- **Time to interactive:** <100ms (vs 5000ms+ before)

## Development Workflow

### Start Services

```powershell
# 1. Start Server (SSR + API)
./start-server.ps1

# 2. Start Hub (proxy/cache)
./start-hub.ps1

# 3. Open browser
# http://localhost:3002/client/pos (via Hub)
# http://localhost:3000/client/pos (direct to Server)
```

### Making Changes

**Server changes (SSR, routes):**
- Edit `.clj` files in `src/corebase/server/client/`
- Restart Server
- Refresh browser

**Browser JS/CSS:**
- Edit `resources/public/client/js/pos.js` or `pos.css`
- Just refresh browser (no restart needed)

**Hub proxy logic:**
- Edit Hub routes
- Restart Hub only

**No build step needed!**

## Flow

### Page Request
1. Browser requests `/client/pos` from Hub
2. Hub proxies request to Server
3. Server renders HTML with Hiccup (SSR)
4. Hub caches response (60s TTL)
5. Browser receives HTML (<50ms)

### API Request
1. Browser calls `/api/products/search` to Hub
2. Hub checks cache
3. If cached: return immediately (<5ms)
4. If not: fetch from Server, cache, return
5. Browser updates UI

## Hub Configuration

Hub behavior is controlled by Server via `/api/hub/config`:

```clojure
{:strategy :full-cache        ; or :pass-through, :hybrid
 :cache-ttl {:products 300    ; seconds
             :customers 60
             :orders 0}        ; no cache
 :features {:offline-mode true
            :barcode-scanner true}}
```

See: Server admin UI at `/admin/hubs` for managing hub configurations.

## Comparison

### Old Client (ClojureScript)

- **LOC:** 8,000+
- **Bundle size:** 500KB+
- **Initial load:** 3000ms+
- **Frameworks:** React, Re-frame, Reagent, DataScript
- **Build time:** 60s+
- **Deployment:** Compile, upload bundle, cache bust

### New Client (SSR)

- **LOC:** 700
- **Bundle size:** 5KB
- **Initial load:** <50ms
- **Frameworks:** None (vanilla JS)
- **Build time:** 0s (no build!)
- **Deployment:** Deploy server, all hubs get it instantly

## Old Code

See `src/corebase/client-archived/` for archived ClojureScript implementation (archived 2025-10-17).

## Related

- Hub management: `/admin/hubs`
- Hub configuration: `src/corebase/server/hub/config_manager.clj`
- Admin routes: `src/corebase/server/admin/routes.clj`

