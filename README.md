# EllipseTV

## Project summary

EllipseTV is an end-to-end media discovery and playback platform. It includes:

1. Android app frontend (`app/`) built with Jetpack Compose and ExoPlayer.
2. Go backend (`backend/`) that serves torrent/media search and playback proxy endpoints.

This repo maintains a clean split between these two components and includes a local cache for media metadata.

## Android App (UI)

### Location
- `app/src/main/java/com/example/ellipsetv/MainActivity.kt`

### Features
- Home screen: fetches “Trending Movies” and “Trending TV” from TMDb.
- Details screen: movie/tv details, ratings, cast, seasons, and episodes.
- Player screen: forwards selected content query to backend for playback.
- TV-friendly focusable UI with keyboard/d-pad support.

### Configuration
- TMDb API key is stored in `TmdbClient.API_KEY` (replace with your own key).
- Backend URL is configured in `AppNavigation()` as `backendBaseUrl`:
  - Example: `http://192.168.1.33:8080/play?q=` for LAN usage.
  - For emulator use: `http://10.0.2.2:8080/play?q=`.

### Execution
1. Open Android Studio.
2. Import project and sync Gradle.
3. Run on emulator or Android TV device.

## Backend service

### Location
- `backend/main.go`
- `backend/go.mod`, `backend/go.sum`
- `backend/ellipse_cache/` (cache for torrent DB and metadata)

### Endpoint behavior
- `GET /play?q=<encoded search query>`
  - The app sends queries like `"Blade Runner 2049 1080p"` using URL-encoded strings.
  - Backend resolves torrent / video source and returns proxied playable stream URL.

### Run backend
1. Ensure Go 1.20+ is installed.
2. Run in terminal:
   ```pwsh
   cd c:\ellipsetv-backend\backend
   go mod tidy
   go test ./...
   go run main.go
   ```
3. You should see server listening on configured port (default `8080`).

## How to use end-to-end

1. Start backend (`go run main.go`). Ensure it is accessible from the Android device.
2. Configure `backendBaseUrl` in `MainActivity` with your backend host (local IP or emulator alias).
3. Install and run Android app.
4. From home screen, select media and press Play. The app navigates to player route, and the backend fetches/streams the chosen media.

## Backend caching and persistence

- `backend/ellipse_cache/` stores local torrent DB files (`.torrent.db`, `.torrent.db-wal` etc.).
- `.gitignore` includes patterns to avoid committing large or dynamically generated cache DB files:
  - `backend/ellipse_cache/*.db*`

## Git workflow

- Keep `main` updated:
  - `git checkout main`
  - `git pull origin main`
- After changes:
  - `git add backend app README.md .gitignore`
  - `git commit -m "Update EllipseTV docs and features"`
  - `git push origin main`

## Notes and improvements

- Replace hardcoded `backendBaseUrl` with runtime config or build flavor.
- Secure TMDb key using build config, not inline source.
- Add integration tests for the backend playback endpoint.
