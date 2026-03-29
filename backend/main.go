package main

import (
	"encoding/json"
	"fmt"
	"log"
	"mime"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/anacrolix/torrent"
	"github.com/anacrolix/torrent/metainfo"
)

const (
	ProwlarrURL = "http://127.0.0.1:9696"
	APIKey      = "72e468708d064e54ac439a8e86bca2bb" // Keep this secret!
)

type ProwlarrResult struct {
	Title       string `json:"title"`
	MagnetUrl   string `json:"magnetUrl"`
	DownloadUrl string `json:"downloadUrl"`
	Seeders     int    `json:"seeders"`
}

var (
	activeStreams = make(map[string]*torrent.Torrent)
	streamMutex   sync.Mutex
)

func main() {
	// 1. MAXIMUM OVERDRIVE ENGINE SETTINGS
	clientConfig := torrent.NewDefaultClientConfig()
	clientConfig.DataDir = "./ellipse_cache"
	clientConfig.EstablishedConnsPerTorrent = 250
	clientConfig.HalfOpenConnsPerTorrent = 100

	client, err := torrent.NewClient(clientConfig)
	if err != nil {
		log.Fatalf("Error creating torrent client: %v", err)
	}
	defer client.Close()

	http.HandleFunc("/play", func(w http.ResponseWriter, r *http.Request) {
		query := r.URL.Query().Get("q")
		if query == "" {
			http.Error(w, "Missing query parameter", http.StatusBadRequest)
			return
		}

		// 2. Stream Cache for Instant Seeking
		streamMutex.Lock()
		t, isCached := activeStreams[query]
		streamMutex.Unlock()

		if isCached {
			fmt.Println("\n-> Cache hit! VLC is seeking. Instantly returning active stream...")
		} else {
			fmt.Printf("\n-> Searching Prowlarr for: %s\n", query)

			searchURL := fmt.Sprintf("%s/api/v1/search?query=%s&apikey=%s", ProwlarrURL, url.QueryEscape(query), APIKey)
			resp, err := http.Get(searchURL)
			if err != nil || resp.StatusCode != 200 {
				fmt.Printf("-> ERROR: Prowlarr API rejected the connection. Is Prowlarr running?\n")
				http.Error(w, "Failed to reach Prowlarr", http.StatusInternalServerError)
				return
			}
			defer resp.Body.Close()

			var results []ProwlarrResult
			errDecode := json.NewDecoder(resp.Body).Decode(&results)
			if errDecode != nil {
				fmt.Printf("-> ERROR: Failed to parse JSON from Prowlarr: %v\n", errDecode)
			}

			// VERBOSE LOGGING: Tell us exactly how many raw results came back
			fmt.Printf("-> Prowlarr returned %d raw results.\n", len(results))

			// 3. THE FIXED SCORING ALGORITHM: Multipliers instead of Addition
			var bestResult *ProwlarrResult
			var maxScore float64 = -1.0

			for i, res := range results {
				if res.Seeders == 0 || (res.MagnetUrl == "" && res.DownloadUrl == "") {
					continue
				}

				score := float64(res.Seeders)
				titleUpper := strings.ToUpper(res.Title)

				if strings.Contains(titleUpper, "CAM") || strings.Contains(titleUpper, "TS") ||
					strings.Contains(titleUpper, "3D") || strings.Contains(titleUpper, "DVDSCR") ||
					strings.Contains(titleUpper, "SCR") || strings.Contains(titleUpper, "XVID") {
					score = score * 0.01
				} else {
					if strings.Contains(titleUpper, "2160P") || strings.Contains(titleUpper, "4K") {
						score = score * 1.5
					} else if strings.Contains(titleUpper, "1080P") {
						score = score * 1.2
					} else if strings.Contains(titleUpper, "720P") {
						score = score * 1.0
					} else {
						score = score * 0.8
					}
				}

				if score > maxScore {
					maxScore = score
					bestResult = &results[i]
				}
			}

			// VERBOSE LOGGING: Warn the terminal if everything was filtered out
			if bestResult == nil {
				fmt.Println("-> ERROR: No viable torrents found! Trackers returned 0 matches, or all files were dead/garbage quality.")
				http.Error(w, "No seeded torrents found", http.StatusNotFound)
				return
			}

			finalLink := bestResult.MagnetUrl
			if finalLink == "" {
				finalLink = bestResult.DownloadUrl
			}

			fmt.Printf("-> WINNER: %s (Seeders: %d)\n", bestResult.Title, bestResult.Seeders)

			var engineErr error
			if strings.HasPrefix(finalLink, "magnet:") {
				t, engineErr = client.AddMagnet(finalLink)
			} else if strings.HasPrefix(finalLink, "http") {
				if strings.Contains(finalLink, ProwlarrURL) && !strings.Contains(finalLink, "apikey=") {
					if strings.Contains(finalLink, "?") {
						finalLink += "&apikey=" + APIKey
					} else {
						finalLink += "?apikey=" + APIKey
					}
				}

				req, _ := http.NewRequest("GET", finalLink, nil)
				req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0.0.0 Safari/537.36")

				var redirectedMagnet string
				customClient := &http.Client{
					CheckRedirect: func(redirReq *http.Request, via []*http.Request) error {
						if redirReq.URL.Scheme == "magnet" {
							redirectedMagnet = redirReq.URL.String()
							return http.ErrUseLastResponse
						}
						return nil
					},
				}

				dlResp, errDL := customClient.Do(req)

				if redirectedMagnet != "" {
					t, engineErr = client.AddMagnet(redirectedMagnet)
				} else if errDL != nil {
					engineErr = fmt.Errorf("network blocked the download: %v", errDL)
				} else if dlResp != nil && dlResp.StatusCode != 200 {
					engineErr = fmt.Errorf("tracker rejected us with HTTP Status: %d", dlResp.StatusCode)
				} else if dlResp != nil {
					meta, errMeta := metainfo.Load(dlResp.Body)
					dlResp.Body.Close()
					if errMeta == nil {
						t, engineErr = client.AddTorrent(meta)
					} else {
						engineErr = fmt.Errorf("downloaded file was not a valid .torrent: %v", errMeta)
					}
				} else {
					engineErr = fmt.Errorf("unknown network error occurred")
				}
			}

			if engineErr != nil || t == nil {
				fmt.Printf("-> Engine Error: %v\n", engineErr)
				http.Error(w, fmt.Sprintf("Failed to load into engine: %v", engineErr), http.StatusInternalServerError)
				return
			}

			// 4. THE 20-SECOND KILL SWITCH
			fmt.Println("-> Waiting for swarm metadata (20-second timeout)...")
			select {
			case <-t.GotInfo():
				fmt.Println("-> Metadata acquired safely!")
			case <-time.After(20 * time.Second):
				fmt.Println("-> ERROR: Swarm timeout! The tracker lied about active seeders.")
				t.Drop()
				http.Error(w, "Swarm timeout. Torrent is dead.", http.StatusGatewayTimeout)
				return
			}

			streamMutex.Lock()
			activeStreams[query] = t
			streamMutex.Unlock()
		}

		// 5. Find the movie file
		var targetFile *torrent.File
		var maxLen int64 = 0
		for _, f := range t.Files() {
			if f.Length() > maxLen {
				maxLen = f.Length()
				targetFile = f
			}
		}

		// 6. THE SUBTITLE FIX: Dynamic MIME Type Detection
		ext := filepath.Ext(targetFile.Path())
		mimeType := mime.TypeByExtension(ext)
		if mimeType == "" {
			mimeType = "video/mp4"
		}
		if ext == ".mkv" {
			mimeType = "video/x-matroska"
		}
		w.Header().Set("Content-Type", mimeType)

		// 7. 150MB Readahead Buffer
		reader := targetFile.NewReader()
		reader.SetResponsive()
		reader.SetReadahead(150 * 1024 * 1024)
		defer reader.Close()

		http.ServeContent(w, r, targetFile.DisplayPath(), time.Time{}, reader)
	})

	fmt.Println("EllipseTV Aggressive Engine running on port 8080")
	log.Fatal(http.ListenAndServe("0.0.0.0:8080", nil))
}
