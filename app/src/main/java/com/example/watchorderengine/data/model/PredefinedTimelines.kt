package com.example.watchorderengine.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Static data source for major, predefined watch orders ("Created by WOE" posts).
 * Injected into the Community Feed, tagged so they surface under the matching
 * chip in [com.example.watchorderengine.ui.screens.TrendingTagsSection].
 */
object PredefinedTimelines {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val masterTimestamp = System.currentTimeMillis()
    private var postCount = 0

    val masterTimelines: List<CommunityPost> = listOf(

        // ═══════════════════════════════════════════════════════════════════
        // MARVEL CINEMATIC UNIVERSE  — tag: "Marvel"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_mcu",
            title = "Marvel Cinematic Universe (Chronological)",
            description = "The Infinity Saga in in-universe chronological order — Captain America's origin through the fallout of Endgame.",
            tags = listOf("Marvel"),
            color = "E23636",
            nodes = listOf(
                buildNode(1771,   "Captain America: The First Avenger", "MOVIE", 2011),
                buildNode(299537, "Captain Marvel",                     "MOVIE", 2019),
                buildNode(1726,   "Iron Man",                           "MOVIE", 2008),
                buildNode(10138,  "Iron Man 2",                         "MOVIE", 2010),
                buildNode(1724,   "The Incredible Hulk",                "MOVIE", 2008),
                buildNode(10195,  "Thor",                                "MOVIE", 2011),
                buildNode(24428,  "The Avengers",                       "MOVIE", 2012),
                buildNode(99861,  "Avengers: Age of Ultron",            "MOVIE", 2015),
                buildNode(271110, "Captain America: Civil War",         "MOVIE", 2016),
                buildNode(284054, "Black Panther",                      "MOVIE", 2018),
                buildNode(299536, "Avengers: Infinity War",             "MOVIE", 2018),
                buildNode(299534, "Avengers: Endgame",                  "MOVIE", 2019),
                buildNode(505642, "Black Panther: Wakanda Forever",     "MOVIE", 2022),
            ),
            edges = listOf(
                Edge("tmdb_m_1771",   "tmdb_m_299537"),
                Edge("tmdb_m_299537", "tmdb_m_1726"),
                Edge("tmdb_m_1726",   "tmdb_m_10138"),
                Edge("tmdb_m_10138",  "tmdb_m_1724"),
                Edge("tmdb_m_1724",   "tmdb_m_10195"),
                Edge("tmdb_m_10195",  "tmdb_m_24428"),
                Edge("tmdb_m_24428",  "tmdb_m_99861"),
                Edge("tmdb_m_99861",  "tmdb_m_271110"),
                Edge("tmdb_m_271110", "tmdb_m_284054"),
                Edge("tmdb_m_284054", "tmdb_m_299536"),
                Edge("tmdb_m_299536", "tmdb_m_299534"),
                Edge("tmdb_m_299534", "tmdb_m_505642"),
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // STAR WARS SAGA  — tags: "Star Wars", "Sci-Fi"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_starwars",
            title = "Star Wars Saga (Chronological)",
            description = "The complete Skywalker saga in in-universe chronological order, from the fall of the Republic to the rise of the First Order.",
            tags = listOf("Star Wars", "Sci-Fi"),
            color = "FFD700",
            nodes = listOf(
                buildNode(1893, "Star Wars: Episode I - The Phantom Menace",   "MOVIE", 1999),
                buildNode(1894, "Star Wars: Episode II - Attack of the Clones", "MOVIE", 2002),
                buildNode(4194, "Star Wars: The Clone Wars",                   "TV_SHOW", 2008),
                buildNode(1895, "Star Wars: Episode III - Revenge of the Sith", "MOVIE", 2005),
                buildNode(11,   "Star Wars: Episode IV - A New Hope",          "MOVIE", 1977),
                buildNode(1891, "Star Wars: Episode V - The Empire Strikes Back", "MOVIE", 1980),
                buildNode(1892, "Star Wars: Episode VI - Return of the Jedi",  "MOVIE", 1983),
            ),
            edges = listOf(
                Edge("tmdb_m_1893", "tmdb_m_1894"),
                Edge("tmdb_m_1894", "tmdb_t_4194"),
                Edge("tmdb_t_4194", "tmdb_m_1895"),
                Edge("tmdb_m_1895", "tmdb_m_11"),
                Edge("tmdb_m_11",   "tmdb_m_1891"),
                Edge("tmdb_m_1891", "tmdb_m_1892"),
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // DC EXTENDED UNIVERSE  — tag: "DC Universe"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_dc",
            title = "DC Extended Universe (Chronological)",
            description = "Warner Bros.' own recommended in-story order for the DCEU, starting with Diana's origin in WWI.",
            tags = listOf("DC Universe"),
            color = "0047AB",
            nodes = listOf(
                buildNode(297762, "Wonder Woman",                          "MOVIE", 2017),
                buildNode(464052, "Wonder Woman 1984",                     "MOVIE", 2020),
                buildNode(49521,  "Man of Steel",                          "MOVIE", 2013),
                buildNode(209112, "Batman v Superman: Dawn of Justice",    "MOVIE", 2016),
                buildNode(297761, "Suicide Squad",                        "MOVIE", 2016),
                buildNode(791373, "Zack Snyder's Justice League",         "MOVIE", 2021),
            ),
            edges = listOf(
                Edge("tmdb_m_297762", "tmdb_m_464052"),
                Edge("tmdb_m_464052", "tmdb_m_49521"),
                Edge("tmdb_m_49521",  "tmdb_m_209112"),
                Edge("tmdb_m_209112", "tmdb_m_297761"),
                Edge("tmdb_m_297761", "tmdb_m_791373"),
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // NARUTO FRANCHISE  — tag: "Anime"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_naruto",
            title = "Naruto Franchise (Essential Path)",
            description = "The journey of the Hidden Leaf's most persistent ninja, from the Academy to the Seventh Hokage's son.",
            tags = listOf("Anime"),
            color = "FF8C00",
            nodes = listOf(
                buildNode(46260, "Naruto",                               "ANIME", 2002, episodeCount = 220),
                buildNode(31910, "Naruto Shippuden",                     "ANIME", 2007, episodeCount = 500),
                buildNode(70881, "Boruto: Naruto Next Generations",      "ANIME", 2017, episodeCount = 293),
            ),
            edges = listOf(
                Edge("tmdb_t_46260", "tmdb_t_31910"),
                Edge("tmdb_t_31910", "tmdb_t_70881"),
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // FATE SERIES (The Multiverse) — tag: "Anime"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_fate_expanded",
            title = "Fate Series (The Multiverse Path)",
            description = "The definitive guide to the Nasuverse. Start with Zero, then branch into the three Stay Night routes, the Grand Order singularities, and the alternate Apocrypha/Extra timelines.",
            tags = listOf("Anime"),
            color = "4169E1",
            nodes = listOf(
                buildNode(45845,  "Fate/Zero",                                              "ANIME", 2011),
                buildNode(61415,  "Fate/stay night [Unlimited Blade Works]",                 "ANIME", 2014),
                buildNode(283984, "Fate/stay night [Heaven's Feel] I. presage flower",      "MOVIE", 2017),
                buildNode(390634, "Fate/stay night [Heaven's Feel] II. lost butterfly",     "MOVIE", 2019),
                buildNode(390635, "Fate/stay night [Heaven's Feel] III. spring song",       "MOVIE", 2020),
                buildNode(85368,  "Lord El-Melloi II's Case Files {Rail Zeppelin} Grace note", "ANIME", 2019),
                buildNode(72304,  "Fate/Apocrypha",                                          "ANIME", 2017),
                buildNode(76123,  "Fate/Extra Last Encore",                                  "ANIME", 2018),
                buildNode(428142, "Fate/Grand Order: First Order",                          "MOVIE", 2016),
                buildNode(637202, "Fate/Grand Order - Camelot - Wandering; Agateram",       "MOVIE", 2020),
                buildNode(637462, "Fate/Grand Order - Camelot - Paladin; Agateram",         "MOVIE", 2021),
                buildNode(90677,  "Fate/Grand Order - Absolute Demonic Front: Babylonia",   "ANIME", 2019),
                buildNode(829920, "Fate/Grand Order - Final Singularity - Solomon",         "MOVIE", 2021),
            ),
            edges = listOf(
                // All major paths branch from Fate/Zero (conceptual or direct)
                Edge("tmdb_t_45845",  "tmdb_t_61415"),   // Zero -> UBW route
                Edge("tmdb_t_45845",  "tmdb_m_283984"),  // Zero -> HF route (Branching)
                Edge("tmdb_t_45845",  "tmdb_t_72304"),   // Zero -> Apocrypha (Branching)
                Edge("tmdb_t_45845",  "tmdb_t_76123"),   // Zero -> Extra (Branching)
                Edge("tmdb_t_45845",  "tmdb_m_428142"),  // Zero -> Grand Order (Conceptual link)

                // The Stay Night Routes
                Edge("tmdb_m_283984", "tmdb_m_390634"),
                Edge("tmdb_m_390634", "tmdb_m_390635"),
                Edge("tmdb_t_61415",  "tmdb_t_85368"),   // UBW -> Lord El-Melloi II

                // The Grand Order Saga
                Edge("tmdb_m_428142", "tmdb_m_637202"),
                Edge("tmdb_m_637202", "tmdb_m_637462"),
                Edge("tmdb_m_637462", "tmdb_t_90677"),
                Edge("tmdb_t_90677",  "tmdb_m_829920")
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // THE CONJURING UNIVERSE  — tag: "Horror"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_conjuring",
            title = "The Conjuring Universe (Chronological)",
            description = "The Warrens' case files in in-story chronological order, not release order — from 1950s Romania to 1980s Connecticut.",
            tags = listOf("Horror"),
            color = "8B0000",
            nodes = listOf(
                buildNode(439079, "The Nun",                                    "MOVIE", 2018),
                buildNode(396422, "Annabelle: Creation",                        "MOVIE", 2017),
                buildNode(250546, "Annabelle",                                  "MOVIE", 2014),
                buildNode(138843, "The Conjuring",                             "MOVIE", 2013),
                buildNode(521029, "Annabelle Comes Home",                       "MOVIE", 2019),
                buildNode(480414, "The Curse of La Llorona",                    "MOVIE", 2019),
                buildNode(259693, "The Conjuring 2",                            "MOVIE", 2016),
                buildNode(423108, "The Conjuring: The Devil Made Me Do It",     "MOVIE", 2021),
            ),
            edges = listOf(
                Edge("tmdb_m_439079", "tmdb_m_396422"),
                Edge("tmdb_m_396422", "tmdb_m_250546"),
                Edge("tmdb_m_250546", "tmdb_m_138843"),
                Edge("tmdb_m_138843", "tmdb_m_521029"),
                Edge("tmdb_m_521029", "tmdb_m_480414"),
                Edge("tmdb_m_480414", "tmdb_m_259693"),
                Edge("tmdb_m_259693", "tmdb_m_423108"),
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // A SONG OF ICE AND FIRE  — tag: "Game of Thrones"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_got",
            title = "A Song of Ice and Fire (Chronological)",
            description = "The Targaryen dynasty at its height through the War of the Five Kings and the Long Night — Westeros in story order.",
            tags = listOf("Game of Thrones"),
            color = "8B0000",
            nodes = listOf(
                buildNode(94997, "House of the Dragon", "TV_SHOW", 2022),
                buildNode(1399,  "Game of Thrones",     "TV_SHOW", 2011),
            ),
            edges = listOf(
                Edge("tmdb_t_94997", "tmdb_t_1399"),
            )
        ),
    )

    // ─── Builders ───────────────────────────────────────────────────────────

    private fun buildPost(
        postId: String,
        title: String,
        description: String,
        nodes: List<MediaNode>,
        edges: List<Edge>,
        color: String,
        tags: List<String>,
    ): CommunityPost {
        val currentOffset = postCount++
        return CommunityPost(
            postId = postId,
            userId = "woe_admin",
            authorName = "Watch Order Engine",
            authorAvatarUrl = "https://ui-avatars.com/api/?name=WO&background=141B2D&color=fff&bold=true",
            universeTitle = title,
            universeDescription = description,
            timestamp = masterTimestamp - (currentOffset * 1000),
            nodesJson = json.encodeToString(SharedTimelinePayload(nodes, edges)),
            tags = tags,
            isOfficial = true,
        )
    }

    private fun buildNode(
        tmdbId: Int,
        title: String,
        category: String,
        year: Int,
        episodeCount: Int = 0,
    ): MediaNode {
        val type = MediaCategory.entries.find { it.name == category } ?: MediaCategory.MOVIE
        val isMovie = category == "MOVIE"
        val prefix = if (isMovie) "tmdb_m_" else "tmdb_t_"
        return MediaNode(
            id = "$prefix$tmdbId",
            title = title,
            content_type = if (isMovie) "MOVIE" else "SERIES",
            type = type,
            tmdb_id = tmdbId,
            tmdb_media_type = if (isMovie) "movie" else "tv",
            releaseYear = year,
            episodeCount = episodeCount,
            posterUrl = null, // System will resolve posters via TmdbCache fallback
        )
    }
}
