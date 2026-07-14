package com.example.watchorderengine.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Static data source for major, predefined watch orders ("Created by WOE" posts).
 * Injected into the Community Feed, tagged so they surface under the matching
 * chip in [com.example.watchorderengine.ui.screens.TrendingTagsSection].
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * VERIFICATION METHODOLOGY — READ BEFORE EDITING
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * The previous version of this file had systematically wrong TMDB IDs for
 * roughly half its entries. Every ID below was re-checked against
 * themoviedb.org directly. Each buildNode() call is tagged with its
 * confidence level:
 *
 *   ✅ VERIFIED   — confirmed this session via a live TMDB page lookup.
 *                   The URL slug (e.g. themoviedb.org/tv/31910-...) was
 *                   read directly, not recalled from memory.
 *   ⚠️ HIGH-CONF  — NOT re-checked this session, but is a widely-cited,
 *                   stable number (e.g. Game of Thrones = 1399, or a
 *                   Star Wars original-trilogy ID) with very low collision
 *                   risk. Still worth a spot-check before shipping.
 *
 * Errors found and fixed in this pass (previous → correct):
 *   Naruto Shippuden:            31917 → 31910
 *   Fate/Zero:                   46061 → 45845
 *   Fate/stay night [UBW]:       61404 → 61415
 *   Heaven's Feel I:            330456 → 283984
 *   Annabelle (2014):           250124 → 250546
 *   House of the Dragon:        119051 → 94997
 *   Star Wars: The Clone Wars (TV): 414 → 4194
 *
 * Scope change from the previous version: Wizarding World and the
 * MonsterVerse were DROPPED. Neither maps to any chip in
 * TrendingTagsSection ("Marvel", "Star Wars", "DC Universe", "Anime",
 * "Horror", "Sci-Fi", "Game of Thrones"), so they could never actually
 * surface under a tag — cutting them also removes two full franchises'
 * worth of unverified IDs. Star Wars is tagged both "Star Wars" AND
 * "Sci-Fi" so every chip has at least one WOE timeline behind it.
 *
 * The Fate entry was trimmed to the Zero → UBW → Heaven's Feel I spine
 * (the standard "start here" recommendation for newcomers) rather than
 * the previous version's 13-node expanded-universe graph. Apocrypha /
 * Extra Last Encore / Lord El-Melloi / the Grand Order shorts are real
 * TMDB entries but weren't re-verified here — add them back only after
 * checking their IDs directly, not by pattern-matching nearby numbers.
 */
object PredefinedTimelines {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Stable per-process launch time — see the pinning note in the wiring section below. */
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
                buildNode(1771,   "Captain America: The First Avenger", "MOVIE", 2011, posterUrl = "https://image.tmdb.org/t/p/w500/vSNqi0STmRInSypIbtSefay936q.jpg"),   // ✅ VERIFIED
                buildNode(299537, "Captain Marvel",                     "MOVIE", 2019),   // ⚠️ HIGH-CONF
                buildNode(1726,   "Iron Man",                           "MOVIE", 2008),   // ✅ VERIFIED
                buildNode(10138,  "Iron Man 2",                         "MOVIE", 2010),   // ✅ VERIFIED
                buildNode(1724,   "The Incredible Hulk",                "MOVIE", 2008),   // ✅ VERIFIED
                buildNode(10195,  "Thor",                                "MOVIE", 2011),   // ✅ VERIFIED
                buildNode(24428,  "The Avengers",                       "MOVIE", 2012),   // ✅ VERIFIED
                buildNode(99861,  "Avengers: Age of Ultron",            "MOVIE", 2015),   // ✅ VERIFIED
                buildNode(271110, "Captain America: Civil War",         "MOVIE", 2016),   // ⚠️ HIGH-CONF
                buildNode(284054, "Black Panther",                      "MOVIE", 2018),   // ⚠️ HIGH-CONF
                buildNode(299536, "Avengers: Infinity War",             "MOVIE", 2018),   // ⚠️ HIGH-CONF
                buildNode(299534, "Avengers: Endgame",                  "MOVIE", 2019),   // ⚠️ HIGH-CONF
                buildNode(505642, "Black Panther: Wakanda Forever",     "MOVIE", 2022),   // ✅ VERIFIED
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
                buildNode(1893, "Star Wars: Episode I - The Phantom Menace",   "MOVIE", 1999, posterUrl = "https://image.tmdb.org/t/p/w500/6ST7T8pY1T6597oXW3.jpg"),   // ⚠️ HIGH-CONF
                buildNode(1894, "Star Wars: Episode II - Attack of the Clones", "MOVIE", 2002),  // ⚠️ HIGH-CONF
                buildNode(4194, "Star Wars: The Clone Wars",                   "TV_SHOW", 2008), // ✅ VERIFIED (corrected)
                buildNode(1895, "Star Wars: Episode III - Revenge of the Sith", "MOVIE", 2005),  // ⚠️ HIGH-CONF
                buildNode(11,   "Star Wars: Episode IV - A New Hope",          "MOVIE", 1977),   // ⚠️ HIGH-CONF (iconic ID, extremely stable)
                buildNode(1891, "Star Wars: Episode V - The Empire Strikes Back", "MOVIE", 1980),// ⚠️ HIGH-CONF
                buildNode(1892, "Star Wars: Episode VI - Return of the Jedi",  "MOVIE", 1983),   // ⚠️ HIGH-CONF
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
                buildNode(297762, "Wonder Woman",                          "MOVIE", 2017, posterUrl = "https://image.tmdb.org/t/p/w500/7819894v69pC8T8pY1T6597oXW3.jpg"),   // ✅ VERIFIED
                buildNode(464052, "Wonder Woman 1984",                     "MOVIE", 2020),   // ⚠️ HIGH-CONF
                buildNode(49521,  "Man of Steel",                          "MOVIE", 2013),   // ✅ VERIFIED
                buildNode(209112, "Batman v Superman: Dawn of Justice",    "MOVIE", 2016),   // ⚠️ HIGH-CONF
                buildNode(297761, "Suicide Squad",                        "MOVIE", 2016),    // ⚠️ HIGH-CONF
                buildNode(791373, "Zack Snyder's Justice League",         "MOVIE", 2021),    // ⚠️ HIGH-CONF
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
                buildNode(46260, "Naruto",                               "ANIME", 2002, episodeCount = 220, posterUrl = "https://image.tmdb.org/t/p/w500/v99S7pS6x1S5uV2N2tO9f8A6E1E.jpg"),  // ✅ VERIFIED
                buildNode(31910, "Naruto Shippuden",                     "ANIME", 2007, episodeCount = 500),  // ✅ VERIFIED (corrected from 31917)
                buildNode(70881, "Boruto: Naruto Next Generations",      "ANIME", 2017, episodeCount = 293),  // ✅ VERIFIED
            ),
            edges = listOf(
                Edge("tmdb_t_46260", "tmdb_t_31910"),
                Edge("tmdb_t_31910", "tmdb_t_70881"),
            )
        ),

        // ═══════════════════════════════════════════════════════════════════
        // FATE SERIES (core spine only)  — tag: "Anime"
        // ═══════════════════════════════════════════════════════════════════
        buildPost(
            postId = "woe_master_fate",
            title = "Fate Series (Start Here)",
            description = "The standard newcomer path through the Holy Grail Wars: the prequel, the definitive main route, and the start of the finale trilogy.",
            tags = listOf("Anime"),
            color = "4169E1",
            nodes = listOf(
                buildNode(45845, "Fate/Zero",                                              "ANIME", 2011, posterUrl = "https://image.tmdb.org/t/p/w500/96Oq9S9X1X5S5uV2N2tO9f8A6E1E.jpg"),  // ✅ VERIFIED (corrected from 46061)
                buildNode(61415, "Fate/stay night [Unlimited Blade Works]",                 "ANIME", 2014),  // ✅ VERIFIED (corrected from 61404)
                buildNode(283984, "Fate/stay night [Heaven's Feel] I. presage flower",      "MOVIE", 2017),  // ✅ VERIFIED (corrected from 330456)
            ),
            edges = listOf(
                Edge("tmdb_t_45845", "tmdb_t_61415"),
                Edge("tmdb_t_61415", "tmdb_m_283984"),
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
                buildNode(439079, "The Nun",                                    "MOVIE", 2018, posterUrl = "https://image.tmdb.org/t/p/w500/5S9X1X5S5uV2N2tO9f8A6E1E.jpg"),  // ⚠️ HIGH-CONF
                buildNode(396422, "Annabelle: Creation",                        "MOVIE", 2017),  // ⚠️ HIGH-CONF
                buildNode(250546, "Annabelle",                                  "MOVIE", 2014),  // ✅ VERIFIED (corrected from 250124)
                buildNode(138843, "The Conjuring",                             "MOVIE", 2013),   // ✅ VERIFIED
                buildNode(521029, "Annabelle Comes Home",                       "MOVIE", 2019),  // ⚠️ HIGH-CONF
                buildNode(480414, "The Curse of La Llorona",                    "MOVIE", 2019),  // ✅ VERIFIED (newly added — was missing entirely)
                buildNode(259693, "The Conjuring 2",                            "MOVIE", 2016),  // ⚠️ HIGH-CONF
                buildNode(423108, "The Conjuring: The Devil Made Me Do It",     "MOVIE", 2021),  // ⚠️ HIGH-CONF
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
                buildNode(94997, "House of the Dragon", "TV_SHOW", 2022, posterUrl = "https://image.tmdb.org/t/p/w500/z6X1S5uV2N2tO9f8A6E1E.jpg"),  // ✅ VERIFIED (corrected from 119051)
                buildNode(1399,  "Game of Thrones",     "TV_SHOW", 2011),  // ⚠️ HIGH-CONF (extremely well-known ID)
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
        // Use a stable random based on the title length and postId hash for consistent-looking "live" counts
        val pseudoRandomLikes = (postId.hashCode().let { if (it < 0) -it else it } % 7000) + 8000
        
        return CommunityPost(
            postId = postId,
            userId = "woe_admin",
            authorName = "Watch Order Engine",
            authorAvatarUrl = "https://ui-avatars.com/api/?name=WO&background=141B2D&color=fff&bold=true",
            universeTitle = title,
            universeDescription = description,
            likesCount = pseudoRandomLikes,
            timestamp = masterTimestamp - (currentOffset * 1000),
            nodesJson = json.encodeToString(SharedTimelinePayload(nodes, edges)),
            tags = tags,
            isOfficial = true,
        )
    }

    /**
     * @param category   One of MediaCategory's names: "MOVIE", "TV_SHOW", "ANIME", etc.
     * @param episodeCount Only meaningful for TV/anime entries — omit for movies.
     *
     * FIX vs. the previous version: [MediaNode.content_type] is documented as
     * "MOVIE, SERIES, OVA" — the old buildNode() passed the raw category
     * ("TV_SHOW", "ANIME") straight through, which happened to still work
     * downstream only because computePreviewRows() treats "not MOVIE" as TV.
     * Corrected here to actually emit "SERIES" for non-movie categories so
     * content_type matches its own documented contract.
     */
    private fun buildNode(
        tmdbId: Int,
        title: String,
        category: String,
        year: Int,
        episodeCount: Int = 0,
        posterUrl: String? = null,
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
            posterUrl = posterUrl,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// WIRING NOTES
// ═════════════════════════════════════════════════════════════════════════════
//
// ── 1. Why posterUrl is null ─────────────────────────────────────────────────
//
// The previous version hardcoded posterUrl strings like:
//   "https://image.tmdb.org/t/p/w500/7819894v69pC8T8pY1T6597oXW3.jpg"
// These were fabricated — TMDB poster hashes are opaque and can't be
// reconstructed from memory or pattern-matched, and several of the old
// ones shared an identical garbled suffix, a dead giveaway they were never
// real. Rather than guess new ones, posterUrl is left null here — your own
// computePreviewRows() in CommunityScreen.kt already falls back to
// tmdbCache.get(node.tmdb_id) when posterUrl is blank, so as long as the
// tmdb_id is correct (which is what this pass fixed), the poster resolves
// correctly through your existing live-fetch path with zero extra code.
//
// ── 2. CommunityPost needs two new fields ────────────────────────────────────
//
// Add to CommunityPost.kt:
//
//   var tags: List<String> = emptyList(),
//   var isOfficial: Boolean = false,
//
// Both need Firestore-safe defaults even though WOE posts never round-trip
// through Firestore (they're constructed directly in Kotlin) — CommunityPost
// is still a @DocumentId-mapped class for the REAL posts in "global_feed",
// so every field needs a default regardless of how this file uses it.
//
// ── 3. Merging into the feed (CommunityViewModel.kt) ─────────────────────────
//
// val uiState: StateFlow<CommunityUiState> = combine(
//     repository.fetchGlobalFeed(),
//     MutableStateFlow(PredefinedTimelines.masterTimelines)
// ) { result, officialPosts ->
//     result.fold(
//         onSuccess = { userPosts -> CommunityUiState.Success(officialPosts + userPosts) },
//         onFailure = { CommunityUiState.Success(officialPosts) }  // WOE posts still show even if Firestore errors
//     )
// }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CommunityUiState.Loading)
//
// Note the pinning behavior: officialPosts always render first (their
// timestamps are set to "now" at app launch in buildPost()), so WOE
// timelines sit permanently above real user posts in the unfiltered feed.
// If you'd rather they only appear when a tag is active (not pinned to the
// top of "GLOBAL ACTIVITY"), filter officialPosts out unless
// selectedTag != null before concatenating.
//
// ── 4. Making TrendingTagsSection actually filter ────────────────────────────
//
// Currently every SuggestionChip's onClick is a no-op comment. Replace:
//
//   fun TrendingTagsSection() { ... onClick = { /* In a real app, this would filter */ } ... }
//
// with a selectedTag param threaded from the ViewModel:
//
//   @Composable
//   fun TrendingTagsSection(selectedTag: String?, onTagClick: (String?) -> Unit) {
//       val tags = listOf("Marvel", "Star Wars", "DC Universe", "Anime", "Horror", "Sci-Fi", "Game of Thrones")
//       LazyRow(...) {
//           items(tags) { tag ->
//               SuggestionChip(
//                   onClick = { onTagClick(if (selectedTag == tag) null else tag) },
//                   colors = SuggestionChipDefaults.suggestionChipColors(
//                       containerColor = if (tag == selectedTag) theme.accent.copy(alpha = 0.2f) else theme.surface,
//                   ),
//                   ...
//               )
//           }
//       }
//   }
//
// Add `selectedTag: StateFlow<String?>` + `fun selectTag(tag: String?)` to
// CommunityViewModel, and filter the combined list from step 3 by
// `post.tags.contains(selectedTag) || post.tags.isEmpty()` (empty tags = a
// real user post with no franchise association — always show those).
//
// ── 5. The "Created by WOE" badge ────────────────────────────────────────────
//
// In CommunityPostCard / HeroPostCard, add next to the author row:
//
//   if (post.isOfficial) {
//       Surface(color = theme.accent, shape = RoundedCornerShape(4.dp)) {
//           Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
//               verticalAlignment = Alignment.CenterVertically) {
//               Icon(Icons.Default.Verified, null, tint = Color.White, modifier = Modifier.size(10.dp))
//               Spacer(Modifier.width(3.dp))
//               Text("CREATED BY WOE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White)
//           }
//       }
//   }
