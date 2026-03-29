package com.example.ellipsetv // <--- CHANGE THIS TO YOUR ACTUAL PACKAGE NAME!

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// --- 1. RETROFIT INTERFACE ---
interface TmdbApiService {
    @GET("trending/all/week")
    suspend fun getTrending(@Query("api_key") apiKey: String): TmdbTrendingResponse

    @GET("movie/{movie_id}?append_to_response=credits")
    suspend fun getMovieDetails(@Path("movie_id") movieId: Int, @Query("api_key") apiKey: String): MovieDetails

    @GET("tv/{tv_id}?append_to_response=credits")
    suspend fun getTvDetails(@Path("tv_id") tvId: Int, @Query("api_key") apiKey: String): TvDetails

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): SeasonDetails
}

// --- 2. SINGLETON CLIENT ---
object TmdbClient {
    private const val BASE_URL = "https://api.themoviedb.org/3/"
    // TODO: Paste your TMDB API Key here!
    const val API_KEY = "557fbaff22f720f70abc6c885b60b3c1"

    val instance: TmdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApiService::class.java)
    }
}

// --- 3. DATA MODELS ---
data class TmdbTrendingResponse(val results: List<MediaResult>)

data class MediaResult(
    val id: Int,
    val title: String?, // Movies use 'title'
    val name: String?,  // TV uses 'name'
    val poster_path: String?,
    val media_type: String // "movie" or "tv"
)

data class MovieDetails(
    val id: Int,
    val title: String,
    val overview: String,
    val release_date: String,
    val vote_average: Double,
    val poster_path: String?,
    val credits: Credits
)

data class TvDetails(
    val id: Int,
    val name: String,
    val overview: String,
    val first_air_date: String,
    val vote_average: Double,
    val poster_path: String?,
    val seasons: List<Season>,
    val credits: Credits
)

data class Credits(val cast: List<CastMember>)
data class CastMember(val name: String, val known_for_department: String)

data class Season(
    val id: Int,
    val name: String,
    val season_number: Int,
    val episode_count: Int
)

data class SeasonDetails(
    val _id: String,
    val episodes: List<Episode>
)

data class Episode(
    val id: Int,
    val name: String,
    val episode_number: Int,
    val overview: String,
    val still_path: String?
)