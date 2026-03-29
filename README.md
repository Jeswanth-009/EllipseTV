# EllipseTV Backend

## Overview

This repository contains the backend service for EllipseTV.

The backend is organized under `backend/` with source files, module metadata, and cache data.

## Repository layout

- `backend/main.go` — Go application entrypoint.
- `backend/go.mod`, `backend/go.sum` — Go module dependencies for backend.
- `backend/ellipse_cache/` — local cache storage for downloaded media metadata and files.
- `README.md` — project documentation.
- `.gitignore` — contains files/folders to exclude from version control.

## How to run

1. Open a terminal in `c:\ellipsetv-backend\backend`.
2. Run `go mod tidy` to ensure dependencies are fetched.
3. Run `go test ./...` to execute tests.
4. Run `go run main.go` to start the backend service.

## Development workflow

- `git checkout main`
- `git pull origin main`
- Make changes in `backend/`
- `git add backend/main.go backend/go.mod backend/go.sum backend/ellipse_cache README.md .gitignore`
- `git commit -m "Update backend and README"
- `git push origin main`

## Cache folder behavior

`backend/ellipse_cache/` is used for caching downloads, metadata, and working data. This folder is tracked for utility, but generated database files are ignored to avoid large binary history.

## Notes

- If copying this project into another branch, keep the backend folder as a separate service component and do not mix with UI/mobile artifacts that may exist in this repository.
- Avoid checking-in large media or .torrent files; keep cache folder trimmed.
