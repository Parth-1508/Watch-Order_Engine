package com.example.watchorderengine.data.model

import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.model.SharedTimelineCodec

/**
 * Static data source for major, predefined watch orders ("Created by WOE" posts).
 * Injected into the Community Feed, tagged so they surface under the matching
 * chip in [com.example.watchorderengine.ui.screens.TrendingTagsSection].
 */
object PredefinedTimelines {

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
            tags = listOf("Marvel", "Sci-Fi"),
            color = "E23636",
            nodes = listOf(
                buildNode(1771,   "Captain America: The First Avenger", "MOVIE", 2011, 124, 1f, 5f, "Phase One"),
                buildNode(299537, "Captain Marvel",                     "MOVIE", 2019, 123, 2f, 21f, "Phase Three"),
                buildNode(1726,   "Iron Man",                           "MOVIE", 2008, 126, 3f, 1f, "Phase One"),
                buildNode(10138,  "Iron Man 2",                         "MOVIE", 2010, 124, 4f, 3f, "Phase One"),
                buildNode(1724,   "The Incredible Hulk",                "MOVIE", 2008, 112, 5f, 2f, "Phase One"),
                buildNode(10195,  "Thor",                                "MOVIE", 2011, 115, 6f, 4f, "Phase One"),
                buildNode(24428,  "The Avengers",                       "MOVIE", 2012, 143, 7f, 6f, "Phase One"),
                buildNode(76338,  "Thor: The Dark World",               "MOVIE", 2013, 112, 8f, 8f, "Phase Two"),
                buildNode(68721,  "Iron Man 3",                         "MOVIE", 2013, 130, 9f, 7f, "Phase Two"),
                buildNode(100402, "Captain America: The Winter Soldier", "MOVIE", 2014, 136, 10f, 9f, "Phase Two"),
                buildNode(118340, "Guardians of the Galaxy",            "MOVIE", 2014, 121, 11f, 10f, "Phase Two"),
                buildNode(283995, "Guardians of the Galaxy Vol. 2",     "MOVIE", 2017, 136, 12f, 15f, "Phase Two"),
                buildNode(99861,  "Avengers: Age of Ultron",            "MOVIE", 2015, 141, 13f, 11f, "Phase Two"),
                buildNode(102899, "Ant-Man",                            "MOVIE", 2015, 117, 14f, 12f, "Phase Two"),
                buildNode(271110, "Captain America: Civil War",         "MOVIE", 2016, 147, 15f, 13f, "Phase Three"),
                buildNode(497698, "Black Widow",                        "MOVIE", 2021, 134, 16f, 24f, "Phase Four"),
                buildNode(315635, "Spider-Man: Homecoming",             "MOVIE", 2017, 133, 17f, 16f, "Phase Three"),
                buildNode(284052, "Doctor Strange",                     "MOVIE", 2016, 115, 18f, 14f, "Phase Three"),
                buildNode(284054, "Black Panther",                      "MOVIE", 2018, 134, 19f, 18f, "Phase Three"),
                buildNode(284053, "Thor: Ragnarok",                     "MOVIE", 2017, 130, 20f, 17f, "Phase Three"),
                buildNode(299536, "Avengers: Infinity War",             "MOVIE", 2018, 149, 21f, 19f, "Phase Three"),
                buildNode(363088, "Ant-Man and the Wasp",               "MOVIE", 2018, 118, 22f, 20f, "Phase Three"),
                buildNode(299534, "Avengers: Endgame",                  "MOVIE", 2019, 181, 23f, 22f, "Phase Three"),
                buildNode(429617, "Spider-Man: Far From Home",          "MOVIE", 2019, 129, 24f, 23f, "Phase Three"),
            ),
            edges = chain(
                listOf(
                    "tmdb_m_1771", "tmdb_m_299537", "tmdb_m_1726", "tmdb_m_10138",
                    "tmdb_m_1724", "tmdb_m_10195", "tmdb_m_24428", "tmdb_m_76338",
                    "tmdb_m_68721", "tmdb_m_100402", "tmdb_m_118340", "tmdb_m_283995",
                    "tmdb_m_99861", "tmdb_m_102899", "tmdb_m_271110", "tmdb_m_497698",
                    "tmdb_m_315635", "tmdb_m_284052", "tmdb_m_284054", "tmdb_m_284053",
                    "tmdb_m_299536", "tmdb_m_363088", "tmdb_m_299534", "tmdb_m_429617"
                )
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
                buildNode(1893, "Star Wars: Episode I - The Phantom Menace",   "MOVIE", 1999, 136, 1f, 4f, "Prequel Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(1894, "Star Wars: Episode II - Attack of the Clones", "MOVIE", 2002, 142, 2f, 5f, "Prequel Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(4194, "Star Wars: The Clone Wars",                   "TV_SHOW", 2008, 0, 2.5f, 0f, "Clone Wars Era", tags = listOf(GraphEngine.ROUTE_ALL)),
                buildNode(1895, "Star Wars: Episode III - Revenge of the Sith", "MOVIE", 2005, 140, 3f, 6f, "Prequel Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(348350, "Solo: A Star Wars Story",                   "MOVIE", 2018, 135, 4f, 10f, "Anthology", tags = listOf(GraphEngine.ROUTE_ALL, "ANTHOLOGY")),
                buildNode(330459, "Rogue One: A Star Wars Story",              "MOVIE", 2016, 133, 5f, 8f, "Anthology", tags = listOf(GraphEngine.ROUTE_ALL, "ANTHOLOGY")),
                buildNode(11,   "Star Wars: Episode IV - A New Hope",          "MOVIE", 1977, 121, 6f, 1f, "Original Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(1891, "Star Wars: Episode V - The Empire Strikes Back", "MOVIE", 1980, 124, 7f, 2f, "Original Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(1892, "Star Wars: Episode VI - Return of the Jedi",  "MOVIE", 1983, 131, 8f, 3f, "Original Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(140607, "Star Wars: Episode VII - The Force Awakens", "MOVIE", 2015, 138, 9f, 7f, "Sequel Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(181808, "Star Wars: Episode VIII - The Last Jedi",    "MOVIE", 2017, 152, 10f, 9f, "Sequel Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
                buildNode(181812, "Star Wars: Episode IX - The Rise of Skywalker", "MOVIE", 2019, 141, 11f, 11f, "Sequel Trilogy", tags = listOf(GraphEngine.ROUTE_ALL, "ESSENTIAL")),
            ),
            edges = chain(
                listOf(
                    "tmdb_m_1893", "tmdb_m_1894", "tmdb_m_1895",
                    "tmdb_m_11", "tmdb_m_1891", "tmdb_m_1892",
                    "tmdb_m_140607", "tmdb_m_181808", "tmdb_m_181812"
                )
            ) + listOf(
                Edge("tmdb_m_1894", "tmdb_t_4194"),
                Edge("tmdb_t_4194", "tmdb_m_1895"),
                Edge("tmdb_m_1895", "tmdb_m_348350", "OPTIONAL"),
                Edge("tmdb_m_348350", "tmdb_m_330459", "OPTIONAL"),
                Edge("tmdb_m_330459", "tmdb_m_11", "OPTIONAL")
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
                buildNode(297762, "Wonder Woman",                          "MOVIE", 2017, 141, 1f, 4f, "DCEU"),
                buildNode(464052, "Wonder Woman 1984",                     "MOVIE", 2020, 151, 2f, 9f, "DCEU"),
                buildNode(49521,  "Man of Steel",                          "MOVIE", 2013, 143, 3f, 1f, "DCEU"),
                buildNode(209112, "Batman v Superman: Dawn of Justice",    "MOVIE", 2016, 151, 4f, 2f, "DCEU"),
                buildNode(297761, "Suicide Squad",                        "MOVIE", 2016, 123, 5f, 3f, "DCEU"),
                buildNode(791373, "Zack Snyder's Justice League",         "MOVIE", 2021, 242, 6f, 10f, "DCEU"),
            ),
            edges = chain(
                listOf("tmdb_m_297762", "tmdb_m_464052", "tmdb_m_49521", "tmdb_m_209112", "tmdb_m_297761", "tmdb_m_791373")
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
                buildNode(45845,  "Fate/Zero",                                              "ANIME", 2011, 0, 1f, 1f, "Prequel"),
                buildNode(61415,  "Fate/stay night [Unlimited Blade Works]",                 "ANIME", 2014, 0, 2f, 2f, "Route: UBW", tags = listOf(GraphEngine.ROUTE_ALL, "UBW")),
                buildNode(283984, "Fate/stay night [Heaven's Feel] I. presage flower",      "MOVIE", 2017, 120, 2f, 3f, "Route: HF", tags = listOf(GraphEngine.ROUTE_ALL, "HEAVENS_FEEL")),
                buildNode(390634, "Fate/stay night [Heaven's Feel] II. lost butterfly",     "MOVIE", 2019, 117, 3f, 4f, "Route: HF", tags = listOf(GraphEngine.ROUTE_ALL, "HEAVENS_FEEL")),
                buildNode(390635, "Fate/stay night [Heaven's Feel] III. spring song",       "MOVIE", 2020, 122, 4f, 5f, "Route: HF", tags = listOf(GraphEngine.ROUTE_ALL, "HEAVENS_FEEL")),
                buildNode(85368,  "Lord El-Melloi II's Case Files {Rail Zeppelin} Grace note", "ANIME", 2019, 0, 3f, 6f, "Case Files"),
                buildNode(72304,  "Fate/Apocrypha",                                          "ANIME", 2017, 0, 2f, 7f, "Alternate"),
                buildNode(76123,  "Fate/Extra Last Encore",                                  "ANIME", 2018, 0, 2f, 8f, "Alternate"),
                buildNode(428142, "Fate/Grand Order: First Order",                          "MOVIE", 2016, 74, 2f, 9f, "Grand Order"),
                buildNode(637202, "Fate/Grand Order - Camelot - Wandering; Agateram",       "MOVIE", 2020, 89, 3f, 10f, "Grand Order"),
                buildNode(637462, "Fate/Grand Order - Camelot - Paladin; Agateram",         "MOVIE", 2021, 96, 4f, 11f, "Grand Order"),
                buildNode(90677,  "Fate/Grand Order - Absolute Demonic Front: Babylonia",   "ANIME", 2019, 0, 5f, 12f, "Grand Order"),
                buildNode(829920, "Fate/Grand Order - Final Singularity - Solomon",         "MOVIE", 2021, 94, 6f, 13f, "Grand Order"),
            ),
            edges = listOf(
                Edge("tmdb_t_45845",  "tmdb_t_61415", "OPTIONAL"),   // Zero -> UBW route
                Edge("tmdb_t_45845",  "tmdb_m_283984", "OPTIONAL"),  // Zero -> HF route (Branching)
                Edge("tmdb_t_45845",  "tmdb_t_72304", "OPTIONAL"),   // Zero -> Apocrypha (Branching)
                Edge("tmdb_t_45845",  "tmdb_t_76123", "OPTIONAL"),   // Zero -> Extra (Branching)
                Edge("tmdb_t_45845",  "tmdb_m_428142", "OPTIONAL"),  // Zero -> Grand Order (Conceptual link)

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
            description = "The Warrens' case files and the Annabelle doll's origin as two branching lines that cross through The Conjuring 2 — filter by \"CONJURING\" or \"ANNABELLE\" to follow just one thread.",
            tags = listOf("Horror"),
            color = "8B0000",
            nodes = listOf(
                buildNode(439079, "The Nun",                                    "MOVIE", 2018, 96, 1f, 7f, "The Nun", tags = listOf(GraphEngine.ROUTE_ALL, "CONJURING")),
                buildNode(968051, "The Nun II",                                 "MOVIE", 2023, 111, 2f, 9f, "The Nun", tags = listOf(GraphEngine.ROUTE_ALL, "CONJURING")),
                buildNode(396422, "Annabelle: Creation",                        "MOVIE", 2017, 109, 3f, 4f, "Annabelle", tags = listOf(GraphEngine.ROUTE_ALL, "ANNABELLE")),
                buildNode(250546, "Annabelle",                                  "MOVIE", 2014, 99, 4f, 2f, "Annabelle", tags = listOf(GraphEngine.ROUTE_ALL, "ANNABELLE")),
                buildNode(138843, "The Conjuring",                              "MOVIE", 2013, 112, 5f, 1f, "The Conjuring", tags = listOf(GraphEngine.ROUTE_ALL, "CONJURING")),
                buildNode(259693, "The Conjuring 2",                            "MOVIE", 2016, 134, 6f, 3f, "The Conjuring", tags = listOf(GraphEngine.ROUTE_ALL, "CONJURING")),
                buildNode(521029, "Annabelle Comes Home",                       "MOVIE", 2019, 106, 7f, 6f, "Annabelle", tags = listOf(GraphEngine.ROUTE_ALL, "ANNABELLE")),
                buildNode(480414, "The Curse of La Llorona",                    "MOVIE", 2019, 93, 8f, 5f, "Spin-off", tags = listOf(GraphEngine.ROUTE_ALL, "SPINOFF")),
                buildNode(423108, "The Conjuring: The Devil Made Me Do It",     "MOVIE", 2021, 112, 9f, 8f, "The Conjuring", tags = listOf(GraphEngine.ROUTE_ALL, "CONJURING")),
                buildNode(1038392, "The Conjuring: Last Rites",                 "MOVIE", 2025, 135, 10f, 10f, "The Conjuring", tags = listOf(GraphEngine.ROUTE_ALL, "CONJURING")),
            ),
            edges = listOf(
                Edge("tmdb_m_439079", "tmdb_m_968051"),
                Edge("tmdb_m_968051", "tmdb_m_138843"),
                Edge("tmdb_m_138843", "tmdb_m_259693"),
                Edge("tmdb_m_259693", "tmdb_m_423108"),
                Edge("tmdb_m_423108", "tmdb_m_1038392"),
                Edge("tmdb_m_396422", "tmdb_m_250546"),
                Edge("tmdb_m_250546", "tmdb_m_521029"),
                Edge("tmdb_m_259693", "tmdb_m_521029", "OPTIONAL"),
                Edge("tmdb_m_259693", "tmdb_m_480414", "OPTIONAL"),
            )
        ),
    )

        // ─── Builders ───────────────────────────────────────────────────────────

    private fun chain(ids: List<String>, type: String = "REQUIRED"): List<Edge> =
        ids.zipWithNext { from, to -> Edge(from_node_id = from, to_node_id = to, type = type) }

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
            authorAvatarUrl = "woe_internal_avatar",
            universeTitle = title,
            universeDescription = description,
            bannerPosterUrl = nodes.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl,
            accentColor = color,
            timestamp = masterTimestamp - (currentOffset * 1000),
            nodesJson = SharedTimelineCodec.encode(nodes, edges),
            tags = tags,
            isOfficial = true,
        )
    }

    private fun buildNode(
        tmdbId: Int,
        title: String,
        category: String,
        year: Int,
        duration: Int = 0,
        chronoOrder: Float = 0f,
        releaseOrder: Float = 0f,
        phase: String = "",
        episodeCount: Int = 0,
        tags: List<String> = listOf(GraphEngine.ROUTE_ALL),
    ): MediaNode {
        val type = MediaCategory.entries.find { it.name.equals(category, ignoreCase = true) } 
            ?: MediaCategory.MOVIE
        val isMovie = category.uppercase() == "MOVIE"
        val prefix = if (isMovie) "tmdb_m_" else "tmdb_t_"
        return MediaNode(
            id = "$prefix$tmdbId",
            title = title,
            content_type = if (isMovie) "MOVIE" else "SERIES",
            type = type,
            tmdb_id = tmdbId,
            tmdb_media_type = if (isMovie) "movie" else "tv",
            chrono_order = chronoOrder,
            release_order = releaseOrder,
            phase = phase,
            tags = tags,
            releaseYear = year,
            episodeCount = episodeCount,
            durationMin = duration,
            posterUrl = null, // System will resolve posters via TmdbCache fallback
        )
    }
}
