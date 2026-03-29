package com.example.ellipsetv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EllipseTVTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun EllipseTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF141414))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

// --- NAVIGATION ENGINE ---
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Use your laptop's Wi-Fi IP if deploying to a real TV, otherwise 10.0.2.2 for emulator
    val backendBaseUrl = "http://192.168.1.33:8080/play?q="

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController)
        }
        composable("details/{mediaType}/{id}") { backStackEntry ->
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val id = backStackEntry.arguments?.getString("id")?.toInt() ?: 0
            DetailsScreen(navController, mediaType, id)
        }
        composable("player/{query}") { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            val decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.toString())
            val finalUrl = backendBaseUrl + URLEncoder.encode(decodedQuery, StandardCharsets.UTF_8.toString())
            VideoPlayerScreen(videoUrl = finalUrl, onBack = { navController.popBackStack() })
        }
    }
}

// --- 1. HOME SCREEN ---
@Composable
fun HomeScreen(navController: NavController) {
    var trendingMovies by remember { mutableStateOf<List<MediaResult>>(emptyList()) }
    var trendingTv by remember { mutableStateOf<List<MediaResult>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val response = TmdbClient.instance.getTrending(TmdbClient.API_KEY)
                trendingMovies = response.results.filter { it.media_type == "movie" }
                trendingTv = response.results.filter { it.media_type == "tv" }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("EllipseTV", color = Color.Red, fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
        if (trendingMovies.isNotEmpty()) {
            item { MediaRow("Trending Movies", trendingMovies, navController) }
        }
        if (trendingTv.isNotEmpty()) {
            item { MediaRow("Trending TV Shows", trendingTv, navController) }
        }
        item { Spacer(modifier = Modifier.height(50.dp)) }
    }
}

@Composable
fun MediaRow(title: String, mediaList: List<MediaResult>, navController: NavController) {
    Column {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(mediaList) { media ->
                val imageUrl = "https://image.tmdb.org/t/p/w500${media.poster_path}"
                val displayName = media.title ?: media.name ?: "Unknown"

                PosterCard(imageUrl, displayName) {
                    navController.navigate("details/${media.media_type}/${media.id}")
                }
            }
        }
    }
}

@Composable
fun PosterCard(imageUrl: String, title: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) Color.White else Color.Transparent

    Box(
        modifier = Modifier
            .width(150.dp).height(225.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, borderColor, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .clickable { onClick() }
    ) {
        AsyncImage(model = imageUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

// --- 2. DETAILS SCREEN (MOVIES & TV) ---
@Composable
fun DetailsScreen(navController: NavController, mediaType: String, id: Int) {
    var movieDetails by remember { mutableStateOf<MovieDetails?>(null) }
    var tvDetails by remember { mutableStateOf<TvDetails?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(id) {
        coroutineScope.launch {
            try {
                if (mediaType == "movie") {
                    movieDetails = TmdbClient.instance.getMovieDetails(id, TmdbClient.API_KEY)
                } else {
                    tvDetails = TmdbClient.instance.getTvDetails(id, TmdbClient.API_KEY)
                    val firstSeason = tvDetails?.seasons?.firstOrNull { it.season_number > 0 }?.season_number
                    if (firstSeason != null) {
                        selectedSeason = firstSeason
                        episodes = TmdbClient.instance.getTvSeasonDetails(id, firstSeason, TmdbClient.API_KEY).episodes
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // A helper function to fetch episodes when a season is clicked
    fun loadSeason(seasonNumber: Int) {
        selectedSeason = seasonNumber
        coroutineScope.launch {
            try {
                episodes = TmdbClient.instance.getTvSeasonDetails(id, seasonNumber, TmdbClient.API_KEY).episodes
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        item {
            val title = movieDetails?.title ?: tvDetails?.name ?: "Loading..."
            val overview = movieDetails?.overview ?: tvDetails?.overview ?: ""
            val rating = movieDetails?.vote_average ?: tvDetails?.vote_average ?: 0.0
            val cast = (movieDetails?.credits?.cast ?: tvDetails?.credits?.cast)?.take(5)?.joinToString { it.name } ?: ""
            val releaseYear = (movieDetails?.release_date?.take(4) ?: tvDetails?.first_air_date?.take(4)) ?: ""

            Row {
                val posterPath = movieDetails?.poster_path ?: tvDetails?.poster_path
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w500$posterPath",
                    contentDescription = title,
                    modifier = Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.padding(start = 24.dp)) {
                    Text(title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("⭐ $rating/10  |  $releaseYear", color = Color.Yellow, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(overview, color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp))
                    Text("Cast: $cast", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    if (mediaType == "movie") {
                        Button(
                            onClick = {
                                val searchQuery = "$title $releaseYear 1080p"
                                navController.navigate("player/${URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString())}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("▶ Play Movie", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // TV Show Season & Episode Logic
        if (mediaType == "tv" && tvDetails != null) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Seasons", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                LazyRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tvDetails!!.seasons.filter { it.season_number > 0 }) { season ->
                        val isSelected = season.season_number == selectedSeason
                        Button(
                            onClick = { loadSeason(season.season_number) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color.Red else Color.DarkGray)
                        ) {
                            Text("Season ${season.season_number}", color = Color.White)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Episodes", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            items(episodes) { episode ->
                EpisodeRow(tvDetails!!.name, selectedSeason ?: 1, episode, navController)
            }
        }

        item { Spacer(modifier = Modifier.height(50.dp)) }
    }
}

@Composable
fun EpisodeRow(showName: String, season: Int, episode: Episode, navController: NavController) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bgColor = if (isFocused) Color(0xFF333333) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth().padding(top = 8.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .clickable {
                // Formats as "Show Name S01E01 1080p" for the Go Backend
                val sFormatted = String.format("%02d", season)
                val eFormatted = String.format("%02d", episode.episode_number)
                val searchQuery = "$showName S${sFormatted}E${eFormatted} 1080p"
                navController.navigate("player/${URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString())}")
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/w500${episode.still_path}",
            contentDescription = episode.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text("${episode.episode_number}. ${episode.name}", color = Color.White, fontWeight = FontWeight.Bold)
            Text(episode.overview.take(100) + "...", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// --- 3. VIDEO PLAYER SCREEN ---
@Composable
fun VideoPlayerScreen(videoUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowSubtitleButton(true)
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x88000000))
        ) {
            Text("Back", color = Color.White)
        }
    }
}