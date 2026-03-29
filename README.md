# EllipseTV: Fullstack Streaming Platform

## Overview
EllipseTV is a fullstack open-source streaming platform that lets you discover, search, and play movies and TV shows using torrents. It consists of:

- **Android TV App** (Jetpack Compose, ExoPlayer): Modern, TV-friendly UI for browsing and playback.
- **Go Backend**: Handles search, torrent resolution, and streaming proxy.
- **Prowller Service**: External service for finding magnet links/torrents, required by the backend.

---

## Technology Stack
- **Frontend:** Android (Kotlin, Jetpack Compose, ExoPlayer)
- **Backend:** Go (Golang)
- **Torrent Search:** Prowller (Node.js or Go, external service)
- **Data Source:** TMDb API (for trending, details, etc.)

---

## 1. Android App

### Features
- Home: Trending Movies & TV (from TMDb)
- Details: Ratings, cast, seasons, episodes
- Player: Streams video from backend
- TV-optimized navigation (D-pad/focus)

### Setup
1. Open in Android Studio
2. Set your TMDb API key in `TmdbClient.API_KEY`
3. Set `backendBaseUrl` in `AppNavigation()` to your backend’s IP (e.g. `http://192.168.1.33:8080/play?q=`)
4. Build and run on Android TV or emulator

---

## 2. Backend (Go)

### Features
- Receives search queries from app
- Uses Prowller to find torrents/magnets
- Streams video to app via HTTP
- Caches torrent metadata in `backend/ellipse_cache/`

### Setup & Run
1. Install Go 1.20+
2. In terminal:
   \`\`\`sh
   cd backend
   go mod tidy
   go run main.go
   \`\`\`
3. Backend listens on port 8080 by default

---

## 3. Prowller Service (Torrent Search Engine)

### What is Prowller?
Prowller is a separate open-source service that scrapes public torrent sites and provides a search API for magnet links. The backend depends on it to resolve user queries into playable torrents.

### Why is it needed?
- The backend does NOT scrape torrents itself; it delegates to Prowller for safety and modularity.
- Without Prowller running, the backend cannot find or stream any content.

### How to Install & Run
1. Clone the Prowller repo ([https://github.com/Prowlarr/Prowlarr] or your fork)
2. Install dependencies (see Prowller’s README)
3. Start the service:
   \`\`\`sh
   prowller --port 9090
   \`\`\`
4. By default, backend expects Prowller at `http://localhost:9090` (edit in `main.go` if needed)

### Integration
- Start Prowller **before** the backend
- Backend will call Prowller’s API to get magnet links for search queries
- If Prowller is down, the app will not find or play any content

---

## End-to-End Usage
1. Start Prowller service
2. Start backend (`go run main.go`)
3. Launch Android app (on TV or emulator)
4. Browse, search, and play content — the app talks to backend, backend talks to Prowller, and streams are proxied to the app

---

## Example Workflow
- User selects “Blade Runner 2049” in the app
- App sends search query to backend: `/play?q=Blade%20Runner%202049%201080p`
- Backend asks Prowller for matching torrents
- Backend picks a source, starts streaming, and proxies video to the app’s player

---

## Notes
- Do NOT expose backend or Prowller to the public internet without security
- Cache folder (`backend/ellipse_cache/`) stores torrent DBs and metadata (large files are .gitignored)
- Replace hardcoded URLs and API keys for production

---

## Contributing & Issues
- PRs and issues welcome!
- See each component’s folder for more details
'; Set-Content -Path README.md -Value $readme -Encoding UTF8; git add README.md; git commit -m "Rewrite README: fullstack, prowller, usage, stack, workflow"; git push origin main
