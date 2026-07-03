package com.example.watchorderengine.data.graph

/**
 * Catalogue of well-known franchise "anchor" collection IDs on TMDB.
 *
 * ── The Problem ──────────────────────────────────────────────────────────────
 * TMDB structures movie universes as a two-level hierarchy:
 *
 *   PARENT COLLECTION  (e.g. "Marvel Cinematic Universe Collection" id=131292)
 *      └─ SUB-COLLECTION  (e.g. "The Avengers Collection" id=86311)
 *            └─ Individual movie  (e.g. "Avengers: Endgame" id=299534)
 *
 * When a user taps "Generate Watch Order" on Avengers: Endgame, the code's
 * getMovie() call returns belongsToCollection.id = 86311 (The Avengers
 * Collection), which only has 4 entries. getMovieCollection(86311) therefore
 * gives Gemini 4 movies — not the full 30+ of the MCU.
 *
 * ── The Fix ──────────────────────────────────────────────────────────────────
 * For any sub-collection whose parent appears in KNOWN_FRANCHISE_COLLECTIONS,
 * we bypass the sub-collection and call getMovieCollection() with the PARENT
 * collection ID instead, giving Gemini the full franchise dataset.
 *
 * ── How the mapping works ────────────────────────────────────────────────────
 * [SUB_COLLECTION_TO_PARENT]: maps ANY sub-collection ID → root franchise ID.
 * [MOVIE_TO_PARENT]: maps specific movie TMDB IDs that DON'T belong to a
 *   sub-collection but ARE part of a large franchise (e.g., standalone MCU
 *   Phase 1 films that TMDB didn't bundle into a sub-collection).
 * [FRANCHISE_LABELS]: human-readable labels for logging.
 *
 * ── Maintenance ──────────────────────────────────────────────────────────────
 * TMDB collection IDs are stable (never recycled). Add new rows as franchises
 * expand. IDs can be verified at: https://www.themoviedb.org/collection/{id}
 */
object FranchiseAnchors {

    /**
     * Maps known sub-collection TMDB IDs → the root franchise collection ID.
     *
     * When generateWatchOrder calls getMovie() and the movie's
     * belongsToCollection.id appears as a KEY here, we use the VALUE as the
     * collection ID passed to getMovieCollection() instead.
     */
    val SUB_COLLECTION_TO_PARENT: Map<Int, Int> = mapOf(

        // ── Marvel Cinematic Universe ────────────────────────────────────────
        // Parent: "Marvel Cinematic Universe Collection" (131292)
        86311  to 131292,   // The Avengers Collection
        131295 to 131292,   // Iron Man Collection
        131296 to 131292,   // Captain America Collection
        131297 to 131292,   // Thor Collection
        422834 to 131292,   // Guardians of the Galaxy Collection
        263854 to 131292,   // Ant-Man Collection
        284433 to 131292,   // Spider-Man (MCU) Collection
        529892 to 131292,   // Black Panther Collection
        863788 to 131292,   // Doctor Strange Collection
        736095 to 131292,   // Eternals (standalone, but MCU anchor applies)

        // ── Star Wars ────────────────────────────────────────────────────────
        // Parent: "Star Wars Collection" (10)
        115575 to 10,       // Star Wars Prequel Trilogy
        115577 to 10,       // Star Wars Original Trilogy
        115576 to 10,       // Star Wars Sequel Trilogy
        667139 to 10,       // Anthology Collection

        // ── DC Extended Universe ──────────────────────────────────────────────
        // Parent: "DC Extended Universe Collection" (263365)
        702342  to 263365,  // Batman Collection (DCEU)
        468552  to 263365,  // Aquaman Collection
        1565734 to 263365,  // Wonder Woman Collection

        // ── Wizarding World ───────────────────────────────────────────────────
        // Parent: "Wizarding World Collection" (1241) — Harry Potter + Fantastic Beasts
        1241  to 1241,      // Harry Potter Collection (IS the parent; map to self)
        435259 to 1241,     // Fantastic Beasts Collection

        // ── The Lord of the Rings ─────────────────────────────────────────────
        // Parent: "Middle Earth Collection" (121938)
        119  to 121938,     // The Lord of the Rings Collection
        121938 to 121938,   // The Hobbit Collection (IS sub; mapped to parent)

        // ── The Dark Knight / Nolan Batman ───────────────────────────────────
        // These are their own 3-film collection; no parent exists on TMDB
        // so we map sub→self to avoid re-fetching single movies.
        535  to 535,        // (no sub-collections; single franchise collection)

        // ── James Bond ────────────────────────────────────────────────────────
        // Parent: "James Bond Collection" (645)
        645 to 645,         // Maps to self — TMDB has this as a flat collection

        // ── Mission: Impossible ───────────────────────────────────────────────
        87359 to 87359,     // Mission: Impossible Collection (flat — maps to self)

        // ── John Wick ────────────────────────────────────────────────────────
        404609 to 404609,   // John Wick Collection

        // ── Fast & Furious ───────────────────────────────────────────────────
        9485 to 9485,       // The Fast and the Furious Collection

        // ── X-Men ────────────────────────────────────────────────────────────
        748 to 748,         // X-Men Collection
    )

    /**
     * Maps individual movie TMDB IDs that DON'T have a TMDB sub-collection but
     * belong to a large franchise. This catches standalone entries (e.g., a Phase 1
     * MCU film TMDB hasn't grouped into a sub-collection yet).
     */
    val MOVIE_TO_PARENT: Map<Int, Int> = mapOf(
        // MCU standalone films with no sub-collection on TMDB
        1726   to 131292,   // Iron Man (2008) — has sub-collection, belt-and-suspenders
        10138  to 131292,   // Iron Man 2
        68721  to 131292,   // Iron Man 3
        1771   to 131292,   // Captain America: The First Avenger
        272    to 131292,   // The Incredible Hulk (2008) — no sub-collection on TMDB
        568    to 131292,   // Avengers (2012)
        100402 to 131292,   // Captain America: The Winter Soldier
        118340 to 131292,   // Guardians of the Galaxy
        99861  to 131292,   // Avengers: Age of Ultron
        102899 to 131292,   // Ant-Man
        271110 to 131292,   // Captain America: Civil War
        284052 to 131292,   // Doctor Strange
        283995 to 131292,   // Guardians of the Galaxy Vol. 2
        315635 to 131292,   // Spider-Man: Homecoming
        284053 to 131292,   // Thor: Ragnarok
        284054 to 131292,   // Black Panther
        299536 to 131292,   // Avengers: Infinity War
        363088 to 131292,   // Ant-Man and the Wasp
        299537 to 131292,   // Captain Marvel
        299534 to 131292,   // Avengers: Endgame
        429617 to 131292,   // Spider-Man: Far from Home
        566525 to 131292,   // Shang-Chi
        524434 to 131292,   // Eternals
        634649 to 131292,   // Spider-Man: No Way Home
        616037 to 131292,   // Thor: Love and Thunder
        616038 to 131292,   // Black Panther: Wakanda Forever
        505642 to 131292,   // Black Panther: Wakanda Forever (same)
        832502 to 131292,   // Ant-Man and the Wasp: Quantumania
        848326 to 131292,   // Guardians of the Galaxy Vol. 3
    )

    /**
     * Human-readable names for franchise root collection IDs.
     * Used in log messages and UI labels ("Expanding MCU — 34 films").
     */
    val FRANCHISE_LABELS: Map<Int, String> = mapOf(
        131292  to "Marvel Cinematic Universe",
        10      to "Star Wars",
        263365  to "DC Extended Universe",
        1241    to "Wizarding World",
        121938  to "Middle Earth",
        535     to "The Dark Knight Trilogy",
        645     to "James Bond",
        87359   to "Mission: Impossible",
        404609  to "John Wick",
        9485    to "Fast & Furious",
        748     to "X-Men",
    )

    /**
     * Given a movie's TMDB ID and its belongs_to_collection.id (may be null),
     * returns the franchise root collection ID to fetch, or null if this movie
     * is not part of a known franchise and should use the sub-collection as-is.
     *
     * Resolution order:
     *   1. Check MOVIE_TO_PARENT for an explicit per-movie override.
     *   2. Check SUB_COLLECTION_TO_PARENT for the sub-collection.
     *   3. Return null → caller uses subCollectionId directly (or null).
     */
    fun resolveRootCollectionId(movieTmdbId: Int, subCollectionId: Int?): Int? {
        // Explicit per-movie override wins
        MOVIE_TO_PARENT[movieTmdbId]?.let { return it }
        // Sub-collection redirect
        if (subCollectionId != null && subCollectionId > 0) {
            SUB_COLLECTION_TO_PARENT[subCollectionId]?.let { return it }
        }
        return null   // no known franchise anchor → use subCollectionId as-is
    }

    /**
     * Returns a displayable franchise label for a root collection ID, or null
     * if this is an unknown / smaller franchise.
     */
    fun labelFor(rootCollectionId: Int): String? = FRANCHISE_LABELS[rootCollectionId]
}
