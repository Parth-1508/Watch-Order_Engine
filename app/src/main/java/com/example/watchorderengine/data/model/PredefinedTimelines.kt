package com.example.watchorderengine.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Static data source for major, predefined watch orders.
 * These are injected into the Community Feed with a "Created by WOE" badge.
 */
object PredefinedTimelines {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val masterTimestamp = System.currentTimeMillis()

    val masterTimelines = listOf(
        buildPost(
            postId = "woe_master_mcu",
            title = "Marvel Cinematic Universe (Chronological)",
            description = "The definitive chronological timeline for the Infinity Saga and beyond, tracing the stones from 1943 to 2023.",
            color = "E23636",
            nodes = listOf(
                buildNode(1771, "Captain America: The First Avenger", "MOVIE", 2011, "https://image.tmdb.org/t/p/w500/vSNqi0STmRInSypIbtSefay936q.jpg"),
                buildNode(299537, "Captain Marvel", "MOVIE", 2019, "https://image.tmdb.org/t/p/w500/Ats9qEkPmjqYmG4S0S0mTsbpDyz.jpg"),
                buildNode(1726, "Iron Man", "MOVIE", 2008, "https://image.tmdb.org/t/p/w500/7819894v69pC8T8pY1T6597oXW3.jpg"),
                buildNode(10138, "Iron Man 2", "MOVIE", 2010, "https://image.tmdb.org/t/p/w500/6SYR9oY6pC8T8pY1T6597oXW3.jpg"),
                buildNode(1724, "The Incredible Hulk", "MOVIE", 2008, "https://image.tmdb.org/t/p/w500/AsS9oY6pC8T8pY1T6597oXW3.jpg"),
                buildNode(10195, "Thor", "MOVIE", 2011, "https://image.tmdb.org/t/p/w500/prS9oY6pC8T8pY1T6597oXW3.jpg"),
                buildNode(24428, "The Avengers", "MOVIE", 2012, "https://image.tmdb.org/t/p/w500/RY9oY6pC8T8pY1T6597oXW3.jpg"),
                buildNode(299536, "Avengers: Infinity War", "MOVIE", 2018, "https://image.tmdb.org/t/p/w500/7WsyCh0Z3uFa0676oX07pSBy60u.jpg"),
                buildNode(299534, "Avengers: Endgame", "MOVIE", 2019, "https://image.tmdb.org/t/p/w500/or06vS3nBFaZDL77oZ6ow9xYmNQ.jpg")
            ),
            edges = listOf(
                Edge("tmdb_m_1771", "tmdb_m_299537"),
                Edge("tmdb_m_299537", "tmdb_m_1726"),
                Edge("tmdb_m_1726", "tmdb_m_10138"),
                Edge("tmdb_m_10138", "tmdb_m_1724"),
                Edge("tmdb_m_1724", "tmdb_m_10195"),
                Edge("tmdb_m_10195", "tmdb_m_24428"),
                Edge("tmdb_m_24428", "tmdb_m_299536"),
                Edge("tmdb_m_299536", "tmdb_m_299534")
            )
        ),
        buildPost(
            postId = "woe_master_starwars",
            title = "Star Wars Saga (Chronological)",
            description = "The complete history of the galaxy far, far away, from the fall of the Republic to the rise of the First Order.",
            color = "FFD700",
            nodes = listOf(
                buildNode(1893, "Star Wars: Episode I - The Phantom Menace", "MOVIE", 1999, "https://image.tmdb.org/t/p/w500/6ST7T8pY1T6597oXW3.jpg"),
                buildNode(1894, "Star Wars: Episode II - Attack of the Clones", "MOVIE", 2002),
                buildNode(414, "Star Wars: The Clone Wars", "TV_SHOW", 2008),
                buildNode(1895, "Star Wars: Episode III - Revenge of the Sith", "MOVIE", 2005),
                buildNode(11, "Star Wars: Episode IV - A New Hope", "MOVIE", 1977, "https://image.tmdb.org/t/p/w500/6FfCt6CHt6Sly67Z2oTCSidI.jpg"),
                buildNode(1891, "Star Wars: Episode V - The Empire Strikes Back", "MOVIE", 1980),
                buildNode(1892, "Star Wars: Episode VI - Return of the Jedi", "MOVIE", 1983)
            ),
            edges = listOf(
                Edge("tmdb_m_1893", "tmdb_m_1894"),
                Edge("tmdb_m_1894", "tmdb_t_414"),
                Edge("tmdb_t_414", "tmdb_m_1895"),
                Edge("tmdb_m_1895", "tmdb_m_11"),
                Edge("tmdb_m_11", "tmdb_m_1891"),
                Edge("tmdb_m_1891", "tmdb_m_1892")
            )
        ),
        buildPost(
            postId = "woe_master_dc",
            title = "DC Extended Universe (Chronological)",
            description = "The interconnected timeline of the Snyder-verse and the Justice League heroes.",
            color = "0047AB",
            nodes = listOf(
                buildNode(297762, "Wonder Woman", "MOVIE", 2017),
                buildNode(464052, "Wonder Woman 1984", "MOVIE", 2020),
                buildNode(49521, "Man of Steel", "MOVIE", 2013),
                buildNode(209112, "Batman v Superman: Dawn of Justice", "MOVIE", 2016),
                buildNode(297761, "Suicide Squad", "MOVIE", 2016),
                buildNode(791373, "Zack Snyder's Justice League", "MOVIE", 2021)
            ),
            edges = listOf(
                Edge("tmdb_m_297762", "tmdb_m_464052"),
                Edge("tmdb_m_464052", "tmdb_m_49521"),
                Edge("tmdb_m_49521", "tmdb_m_209112"),
                Edge("tmdb_m_209112", "tmdb_m_297761"),
                Edge("tmdb_m_297761", "tmdb_m_791373")
            )
        ),
        buildPost(
            postId = "woe_master_hp",
            title = "The Wizarding World (Chronological)",
            description = "From Newt Scamander's first discoveries to Harry Potter's final battle at Hogwarts.",
            color = "B8860B",
            nodes = listOf(
                buildNode(259316, "Fantastic Beasts and Where to Find Them", "MOVIE", 2016),
                buildNode(338952, "Fantastic Beasts: The Crimes of Grindelwald", "MOVIE", 2018),
                buildNode(338953, "Fantastic Beasts: The Secrets of Dumbledore", "MOVIE", 2022),
                buildNode(671, "Harry Potter and the Philosopher's Stone", "MOVIE", 2001),
                buildNode(12445, "Harry Potter and the Deathly Hallows: Part 2", "MOVIE", 2011)
            ),
            edges = listOf(
                Edge("tmdb_m_259316", "tmdb_m_338952"),
                Edge("tmdb_m_338952", "tmdb_m_338953"),
                Edge("tmdb_m_338953", "tmdb_m_671"),
                Edge("tmdb_m_671", "tmdb_m_12445")
            )
        ),
        buildPost(
            postId = "woe_master_naruto",
            title = "Naruto Franchise (Essential Path)",
            description = "The journey of the Hidden Leaf's most persistent ninja, from the Academy to the Seventh Hokage.",
            color = "FF8C00",
            nodes = listOf(
                buildNode(46260, "Naruto", "ANIME", 2002),
                buildNode(31917, "Naruto Shippuden", "ANIME", 2007),
                buildNode(70881, "Boruto: Naruto Next Generations", "ANIME", 2017)
            ),
            edges = listOf(
                Edge("tmdb_t_46260", "tmdb_t_31917"),
                Edge("tmdb_t_31917", "tmdb_t_70881")
            )
        ),
        buildPost(
            postId = "woe_master_monsterverse",
            title = "The MonsterVerse (Sci-Fi Chronological)",
            description = " Monarch's complete timeline of Titan activity, from the hollow earth to the surface. Sci-Fi essential.",
            color = "2E8B57",
            nodes = listOf(
                buildNode(263115, "Kong: Skull Island", "MOVIE", 2017),
                buildNode(204082, "Monarch: Legacy of Monsters", "TV_SHOW", 2023),
                buildNode(124905, "Godzilla", "MOVIE", 2014),
                buildNode(373571, "Godzilla: King of the Monsters", "MOVIE", 2019),
                buildNode(399566, "Godzilla vs. Kong", "MOVIE", 2021)
            ),
            edges = listOf(
                Edge("tmdb_m_263115", "tmdb_t_204082"),
                Edge("tmdb_t_204082", "tmdb_m_124905"),
                Edge("tmdb_m_124905", "tmdb_m_373571"),
                Edge("tmdb_m_373571", "tmdb_m_399566")
            )
        ),
        buildPost(
            postId = "woe_master_got",
            title = "A Song of Ice and Fire (Chronological)",
            description = "The history of the Seven Kingdoms, starting from the Targaryen dynasty's peak to the Long Night. Game of Thrones universe.",
            color = "8B0000",
            nodes = listOf(
                buildNode(119051, "House of the Dragon", "TV_SHOW", 2022),
                buildNode(1399, "Game of Thrones", "TV_SHOW", 2011)
            ),
            edges = listOf(
                Edge("tmdb_t_119051", "tmdb_t_1399")
            )
        ),
        buildPost(
            postId = "woe_master_conjuring",
            title = "The Conjuring Universe (Chronological)",
            description = "The complete chronological timeline of the Warrens' case files and demonic origins. Horror genre essential.",
            color = "000000",
            nodes = listOf(
                buildNode(439079, "The Nun", "MOVIE", 2018),
                buildNode(396422, "Annabelle: Creation", "MOVIE", 2017),
                buildNode(250124, "Annabelle", "MOVIE", 2014),
                buildNode(138843, "The Conjuring", "MOVIE", 2013),
                buildNode(521029, "Annabelle Comes Home", "MOVIE", 2019),
                buildNode(259693, "The Conjuring 2", "MOVIE", 2016),
                buildNode(423108, "The Conjuring: The Devil Made Me Do It", "MOVIE", 2021)
            ),
            edges = listOf(
                Edge("tmdb_m_439079", "tmdb_m_396422"),
                Edge("tmdb_m_396422", "tmdb_m_250124"),
                Edge("tmdb_m_250124", "tmdb_m_138843"),
                Edge("tmdb_m_138843", "tmdb_m_521029"),
                Edge("tmdb_m_521029", "tmdb_m_259693"),
                Edge("tmdb_m_259693", "tmdb_m_423108")
            )
        ),
        buildPost(
            postId = "woe_master_fate",
            title = "Fate Series (Ultimate Anime Order)",
            description = "The definitive watch order for the core Fate anime timeline, optimizing for character reveals and lore progression. Major Anime Franchise.",
            color = "4169E1",
            nodes = listOf(
                buildNode(46061, "Fate/Zero", "ANIME", 2011),
                buildNode(61404, "Fate/stay night [Unlimited Blade Works]", "ANIME", 2014),
                buildNode(330456, "Fate/stay night [Heaven's Feel] I. presage flower", "MOVIE", 2017)
            ),
            edges = listOf(
                Edge("tmdb_t_46061", "tmdb_t_61404"),
                Edge("tmdb_t_61404", "tmdb_m_330456")
            )
        )
    )

    private fun buildPost(
        postId: String,
        title: String,
        description: String,
        nodes: List<MediaNode>,
        edges: List<Edge>,
        color: String
    ): CommunityPost {
        return CommunityPost(
            postId = postId,
            userId = "woe_admin",
            authorName = "Watch Order Engine",
            authorAvatarUrl = "https://ui-avatars.com/api/?name=WOE&background=$color&color=fff",
            universeTitle = title,
            universeDescription = description,
            likesCount = (8000..15000).random(),
            timestamp = masterTimestamp - (masterTimelines.size * 1000),
            nodesJson = json.encodeToString(SharedTimelinePayload(nodes, edges))
        )
    }

    private fun buildNode(
        tmdbId: Int,
        title: String,
        category: String,
        year: Int,
        posterUrl: String? = null
    ): MediaNode {
        val type = MediaCategory.entries.find { it.name == category } ?: MediaCategory.MOVIE
        val prefix = if (category == "MOVIE") "tmdb_m_" else "tmdb_t_"
        return MediaNode(
            id = "$prefix$tmdbId",
            title = title,
            content_type = category,
            type = type,
            tmdb_id = tmdbId,
            tmdb_media_type = if (category == "MOVIE") "movie" else "tv",
            releaseYear = year,
            posterUrl = posterUrl
        )
    }
}
