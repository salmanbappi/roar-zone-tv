package eu.kanade.tachiyomi.animeextension.all.roarzonetv

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import extensions.utils.Source
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class RoarZoneTV : Source(), ConfigurableAnimeSource {

    override val name = "Roar zone tv"
    override val baseUrl = "https://tv.roarzone.net"
    override val lang = "all"
    override val supportsLatest = false
    override val id: Long = 84769302158234569L

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "$baseUrl/")
                .build()
            chain.proceed(request)
        }
        .build()

    private suspend fun fetchChannels(): List<SAnime> {
        val response = client.newCall(GET(baseUrl)).execute()
        val document = Jsoup.parse(response.body?.string() ?: "")
        
        return document.select(".channel-card").map { element ->
            SAnime.create().apply {
                title = element.attr("data-title")
                url = element.attr("data-stream")
                thumbnail_url = element.select("img").attr("abs:src")
                genre = element.attr("data-tags")
                initialized = true
            }
        }
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return AnimesPage(fetchChannels(), false)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = AnimesPage(emptyList(), false)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val filtered = fetchChannels().filter { it.title.contains(query, ignoreCase = true) }
        return AnimesPage(filtered, false)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime.apply {
        status = SAnime.UNKNOWN
        description = "Live Stream: $title"
        initialized = true
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            name = anime.title
            url = anime.url
            episode_number = 1F
        })
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val playerUrl = "$baseUrl/player.php?stream=${episode.url}"
        val response = client.newCall(GET(playerUrl)).execute()
        val html = response.body?.string() ?: ""
        
        // Extract m3u8 from the Plyr/Hls script
        val streamUrl = Regex("""hls\.loadSource\(['"](.*?)['"]\)""").find(html)?.groupValues?.get(1)
            ?: Regex("""source src=['"](.*?)['"]""").find(html)?.groupValues?.get(1)
            ?: throw Exception("Could not find stream URL")

        return listOf(Video(streamUrl, "Live Stream", streamUrl))
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
