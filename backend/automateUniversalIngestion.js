/**
 * automateUniversalIngestion.js
 * Universal Media ETL Pipeline for Watch Order Engine
 *
 * Usage:
 *   node automateUniversalIngestion.js "One Piece"
 *   node automateUniversalIngestion.js "Avengers Infinity War" --type movie
 *   node automateUniversalIngestion.js "Fate/Zero" --universe "Fate Series"
 *
 * Dependencies:
 *   npm install @google/genai firebase-admin axios dotenv
 *
 * Required environment variables (in .env):
 *   GEMINI_API_KEY=...
 *   TMDB_API_READ_TOKEN=...
 *   GOOGLE_APPLICATION_CREDENTIALS=./serviceAccountKey.json
 */

'use strict';

require('dotenv').config();
const { GoogleGenAI } = require('@google/genai');
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');
const axios = require('axios').default;
const path = require('path');

// ─── CLI Args ─────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
if (args.length === 0) {
    console.error('[ERR] Usage: node automateUniversalIngestion.js "Title" [--type movie|tv] [--universe "Universe Name"]');
    process.exit(1);
}

const SEARCH_TITLE = args[0];
const FORCE_TYPE   = args.includes('--type') ? args[args.indexOf('--type') + 1] : null;
const UNIVERSE     = args.includes('--universe') ? args[args.indexOf('--universe') + 1] : null;

// ─── SDK Initialization ───────────────────────────────────────────────────────

// Firebase Admin — modular V12 SDK
try {
    const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS
        ? path.resolve(process.env.GOOGLE_APPLICATION_CREDENTIALS)
        : path.resolve(__dirname, 'serviceAccountKey.json');

    initializeApp({
        credential: cert(serviceAccountPath)
    });
} catch (e) {
    console.warn(`[Firebase] Warning: Failed to initialize Firebase Admin. Script will fail during write operations. Ensure serviceAccountKey.json exists. Error: ${e.message}`);
}

const db = getFirestore();

// Gemini Burner Keys for high-volume ingestion (provided by user)
const GEMINI_BURNER_KEYS = [
    'AQ.Ab8RN6Jktf2kcZzKhlAsLRJ8TSAnkvJYy9zcZg-B8CdeyJSojg',
    'AIzaSyABDubBEFM-nCypDV7uriN-_gIAxBuNgS8',
    'AIzaSyAFTy3tjExX4GlWRWRaj4CbB1lAERl5tS0',
    'AIzaSyCNyE4bmP9XRlPcOO3gJCnlv_w38s9mqes',
    'AIzaSyDLVirTpnwxKzzO3tgdLixGz18k7GFWj-Q',
    'AIzaSyCcyo3tyrg-JwPjsd0lZR3NBVsxMNm2Kxk',
    'AIzaSyAG3_ERba9DzrJg-6xsGd9eB9pJLnmcFAw'
];
let currentGeminiKeyIndex = 0;

/**
 * Helper to get a configured Gemini model with automatic key rotation.
 */
function getGeminiModel() {
    const apiKey = GEMINI_BURNER_KEYS[currentGeminiKeyIndex] || process.env.GEMINI_API_KEY;
    const ai = new GoogleGenAI({ apiKey: apiKey });
    return ai.getGenerativeModel({ model: 'gemini-1.5-flash' });
}

// TMDB Axios instance (Bearer token auth)
const tmdb = axios.create({
    baseURL: 'https://api.themoviedb.org/3',
    headers: {
        Authorization: `Bearer ${process.env.TMDB_API_READ_TOKEN}`,
        Accept: 'application/json',
    },
    timeout: 30_000,
});

// AniList GraphQL endpoint
const ANILIST_URL = 'https://graphql.anilist.co';

// ─── TMDB Helpers ─────────────────────────────────────────────────────────────

/**
 * Searches TMDB for a title and returns the best match.
 * If FORCE_TYPE is set, only searches that media type.
 */
async function searchTmdb(title) {
    console.log(`[TMDB] Searching for: "${title}"...`);
    const types = FORCE_TYPE ? [FORCE_TYPE] : ['tv', 'movie'];

    for (const type of types) {
        try {
            const { data } = await tmdb.get(`/search/${type}`, {
                params: { query: title, include_adult: false, language: 'en-US', page: 1 },
            });
            if (data.results && data.results.length > 0) {
                const best = data.results[0];
                console.log(`[TMDB] Found ${type.toUpperCase()}: "${best.name || best.title}" (ID: ${best.id})`);
                return { ...best, mediaType: type };
            }
        } catch (e) {
            console.warn(`[TMDB] Search for ${type} failed: ${e.message}`);
        }
    }
    throw new Error(`[TMDB] No results found for "${title}"`);
}

/**
 * Fetches the complete TMDB detail document with a maximally comprehensive
 * append_to_response payload, reducing network round-trips to a single call.
 */
async function fetchTmdbDetail(id, mediaType) {
    console.log(`[TMDB] Fetching full detail for ${mediaType} ID: ${id}...`);

    const appendModules = mediaType === 'movie'
        ? 'credits,videos,recommendations,external_ids,release_dates,keywords,watch/providers'
        : 'credits,videos,recommendations,external_ids,content_ratings,keywords,watch/providers,aggregate_credits';

    const { data } = await tmdb.get(`/${mediaType}/${id}`, {
        params: { language: 'en-US', append_to_response: appendModules },
    });
    return data;
}

/**
 * Fetches all seasons of a TV show in parallel, including episode-level
 * data (title, overview, runtime, still_path, etc.).
 */
async function fetchAllSeasons(showId, seasonNumbers) {
    console.log(`[TMDB] Fetching ${seasonNumbers.length} season(s) for show ID: ${showId}...`);
    const promises = seasonNumbers.map((n) =>
        tmdb.get(`/tv/${showId}/season/${n}`, { params: { language: 'en-US' } })
            .then((r) => r.data)
            .catch((e) => {
                console.warn(`[TMDB] Warning: season ${n} fetch failed — ${e.message}`);
                return null;
            })
    );
    const results = await Promise.all(promises);
    return results.filter(Boolean); // Drop failed seasons
}

/** Extracts the US content rating from TMDB's content_ratings/release_dates response. */
function extractAgeRating(data, mediaType) {
    if (mediaType === 'tv') {
        const us = data.content_ratings?.results?.find((r) => r.iso_3166_1 === 'US');
        return us?.rating || 'NR';
    }
    const us = data.release_dates?.results?.find((r) => r.iso_3166_1 === 'US');
    const cert = us?.release_dates?.find((d) => d.certification)?.certification;
    return cert || 'NR';
}

// ─── AniList Helpers ──────────────────────────────────────────────────────────

/** Fetches comprehensive AniList data for an anime series via GraphQL. */
async function fetchAnilistData(title, externalMalId) {
    console.log(`[AniList] Querying AniList for: "${title}"...`);

    const query = `
    query ($search: String, $idMal: Int) {
      Media(search: $search, idMal: $idMal, type: ANIME) {
        id
        idMal
        title { romaji english native }
        episodes
        description(asHtml: false)
        coverImage { large extraLarge }
        bannerImage
        genres
        tags { name rank isMediaSpoiler category }
        streamingEpisodes { title thumbnail url site }
        format
        status
        season
        seasonYear
        source
        studios { nodes { name isAnimationStudio } }
        relations {
          edges {
            relationType
            node {
              id
              title { romaji english }
              type
              format
              episodes
              idMal
            }
          }
        }
        externalLinks { url site type language }
      }
    }`;

    try {
        const { data } = await axios.post(
            ANILIST_URL,
            { query, variables: { search: title, idMal: externalMalId ?? undefined } },
            { headers: { 'Content-Type': 'application/json' }, timeout: 20_000 }
        );
        if (data.errors) {
            console.warn('[AniList] GraphQL errors:', data.errors);
            return null;
        }
        console.log(`[AniList] Found: "${data.data.Media?.title?.english || data.data.Media?.title?.romaji}"`);
        return data.data.Media;
    } catch (e) {
        console.warn(`[AniList] Fetch failed — ${e.message}. Proceeding without AniList data.`);
        return null;
    }
}

// ─── Gemini AI Analysis ───────────────────────────────────────────────────────

/**
 * Uses Gemini 2.5 Flash to perform three critical analysis tasks:
 * 1. Classify every episode as CANON, FILLER, or MIXED.
 * 2. Map arcs (named story arcs with episode ranges).
 * 3. Build the chronological graph (parent-child relationships for branching universes).
 *
 * Returns structured JSON conforming to GeminiAnalysis schema.
 */
async function analyzeWithGemini(title, tmdbData, anilistData, allEpisodes) {
    while (currentGeminiKeyIndex < GEMINI_BURNER_KEYS.length) {
        console.log(`[Gemini] Running AI analysis with Key #${currentGeminiKeyIndex + 1}...`);

        const prompt = `You are an expert media analyst with deep knowledge of anime, TV shows, and movie franchises.
Analyze the following data for "${title}" and respond ONLY with a valid JSON object — no markdown fences, no commentary.

=== TMDB DATA ===
${JSON.stringify({
    title: tmdbData.name || tmdbData.title,
    genres: tmdbData.genres?.map((g) => g.name),
    overview: tmdbData.overview,
    numberOfEpisodes: tmdbData.number_of_episodes,
    numberOfSeasons: tmdbData.number_of_seasons,
    status: tmdbData.status,
    keywords: tmdbData.keywords?.results?.map((k) => k.name) || [],
    type: tmdbData.number_of_seasons ? 'TV' : 'MOVIE',
}, null, 2)}

=== ANILIST DATA ===
${JSON.stringify(anilistData ? {
    title: anilistData.title,
    episodes: anilistData.episodes,
    source: anilistData.source,
    format: anilistData.format,
    genres: anilistData.genres,
    tags: anilistData.tags?.slice(0, 20),
    relations: anilistData.relations?.edges?.map(e => ({
        type: e.relationType,
        title: e.node.title?.english || e.node.title?.romaji,
        episodes: e.node.episodes,
        format: e.node.format
    }))
} : { note: 'Not an anime or AniList data unavailable' }, null, 2)}

=== EPISODE LIST (first 50 and last 20 for context) ===
${JSON.stringify(allEpisodes.slice(0, 50).concat(allEpisodes.slice(-20)).map(e => ({
    s: e.seasonNumber,
    ep: e.episodeNumber,
    absEp: e.absoluteEpisodeNumber,
    title: e.name
})), null, 2)}

Respond with this exact JSON schema:
{
  "isAnime": <boolean>,
  "mediaType": "<MOVIE|TV_SHOW|ANIME>",
  "ageRating": "<G|PG|PG-13|TV-MA|TV-14|TV-Y7|R|NR>",
  "primaryStudio": "<studio name or null>",
  "canonFillerMap": [
    {
      "absoluteEpisodeNumber": <int or null — use null for non-anime>,
      "seasonNumber": <int>,
      "episodeNumber": <int>,
      "typeTag": "<CANON|FILLER|MIXED>",
      "arcName": "<arc name string or null>"
    }
  ],
  "arcs": [
    {
      "name": "<arc name>",
      "startAbsoluteEpisode": <int or null>,
      "endAbsoluteEpisode": <int or null>,
      "startSeason": <int>,
      "startEpisode": <int>,
      "endSeason": <int>,
      "endEpisode": <int>,
      "synopsis": "<1-2 sentence arc summary>"
    }
  ],
  "chronologicalGraph": {
    "entries": [
      {
        "entryId": "<unique string like 'main-series' or 'movie-1'>",
        "title": "<title>",
        "mediaType": "<MOVIE|SERIES|OVA|SPECIAL>",
        "chronoPosition": <float — lower = earlier>,
        "parentEntryId": "<entryId of direct predecessor or null>",
        "relationship": "<CONTINUATION|PREQUEL|SEQUEL|PARALLEL|SIDE_STORY|RETELLING>",
        "tmdbId": <int or null>,
        "anilistId": <int or null>,
        "notes": "<optional context string>"
      }
    ],
    "branchingPoints": [
      {
        "afterEntryId": "<entryId where timeline splits>",
        "branches": ["<entryId1>", "<entryId2>"],
        "description": "<why the timeline branches here>"
      }
    ]
  }
}

Rules:
- For anime, classify every single episode in canonFillerMap. Base this on the source material (manga/light novel vs anime-original).
- For live-action TV and movies, typeTag is always CANON unless it is a clearly non-canonical bonus episode.
- chronologicalGraph must include related media from AniList relations (prequels, sequels, side stories).
- arcs should reflect major story arcs with meaningful names (e.g., "Arlong Park Arc", "Alabasta Arc").
- Be extremely careful with episode numbers — use absolute episode numbers for anime.`;

        try {
            const model = getGeminiModel();
            const response = await model.generateContent(prompt);

            const rawText = response.response.text().trim();
            // Strip any accidental markdown fences the model might add despite instructions
            const jsonStr = rawText.replace(/^```json\n?/, '').replace(/\n?```$/, '');
            const result = JSON.parse(jsonStr);
            console.log('[Gemini] Analysis complete. Found', result.canonFillerMap?.length || 0, 'episode classifications.');
            return result;
        } catch (e) {
            console.error(`[Gemini] Analysis failed with Key #${currentGeminiKeyIndex + 1}:`, e.message);

            if (e.message.includes("429") || e.message.includes("quota") || e.message.includes("exhausted")) {
                console.warn("🔄 Quota exhausted. Rotating to next burner key...");
                currentGeminiKeyIndex++;
            } else {
                // Return a safe default so ingestion can continue without AI data
                return {
                    isAnime: false,
                    mediaType: 'TV_SHOW',
                    ageRating: 'NR',
                    primaryStudio: null,
                    canonFillerMap: [],
                    arcs: [],
                    chronologicalGraph: { entries: [], branchingPoints: [] },
                };
            }
        }
    }

    throw new Error("❌ All Gemini burner keys exhausted!");
}

// ─── Firestore Writers ────────────────────────────────────────────────────────

/**
 * Writes the main catalog document to Firestore.
 * All rich TMDB metadata is baked in here at ingestion time so the
 * Android app NEVER needs to make a live TMDB API call at runtime.
 */
async function writeCatalogDocument(tmdbData, anilistData, aiAnalysis, mediaType) {
    const docId = `tmdb_${tmdbData.id}`;
    const title = tmdbData.name || tmdbData.title || SEARCH_TITLE;

    const posterUrl  = tmdbData.poster_path  ? `https://image.tmdb.org/t/p/w342${tmdbData.poster_path}` : null;
    const backdropUrl = tmdbData.backdrop_path ? `https://image.tmdb.org/t/p/w780${tmdbData.backdrop_path}` : null;

    // Extract trailer (prefer official YouTube trailers)
    const trailer = tmdbData.videos?.results?.find(
        (v) => v.type === 'Trailer' && v.site === 'YouTube' && v.official
    ) || tmdbData.videos?.results?.find((v) => v.type === 'Trailer' && v.site === 'YouTube');

    // Top-billed cast (first 20)
    const cast = (tmdbData.credits?.cast || []).slice(0, 20).map((c) => ({
        tmdbId: c.id,
        name: c.name,
        character: c.character,
        profileUrl: c.profile_path ? `https://image.tmdb.org/t/p/w185${c.profile_path}` : null,
        order: c.order,
    }));

    // Recommendations (first 12)
    const recommendations = (tmdbData.recommendations?.results || []).slice(0, 12).map((r) => ({
        tmdbId: r.id,
        title: r.name || r.title,
        posterUrl: r.poster_path ? `https://image.tmdb.org/t/p/w185${r.poster_path}` : null,
        mediaType: r.media_type || mediaType,
        voteAverage: r.vote_average,
    }));

    // External IDs
    const externalIds = {
        imdbId:      tmdbData.external_ids?.imdb_id || null,
        tvdbId:      tmdbData.external_ids?.tvdb_id || null,
        facebookId:  tmdbData.external_ids?.facebook_id || null,
        instagramId: tmdbData.external_ids?.instagram_id || null,
    };
    if (anilistData) {
        externalIds.anilistId = anilistData.id;
        externalIds.malId     = anilistData.idMal;
    }

    // Runtime display
    const runtime = mediaType === 'movie'
        ? tmdbData.runtime
        : (tmdbData.episode_run_time?.[0] || anilistData?.averageEpisodeRuntime || null);

    const catalogDoc = {
        // ── Identity ──────────────────────────────────────────────────────────
        firestoreId:    docId,
        tmdbId:         tmdbData.id,
        anilistId:      anilistData?.id || null,
        malId:          anilistData?.idMal || null,
        universeId:     UNIVERSE || null,

        // ── Core Metadata (baked in — no client API calls needed) ─────────────
        title,
        originalTitle:  tmdbData.original_name || tmdbData.original_title || title,
        overview:       tmdbData.overview || anilistData?.description || '',
        tagline:        tmdbData.tagline || '',
        status:         tmdbData.status || 'Unknown',
        mediaType:      aiAnalysis.mediaType || (mediaType === 'movie' ? 'MOVIE' : 'TV_SHOW'),
        isAnime:        aiAnalysis.isAnime || false,
        ageRating:      aiAnalysis.ageRating || extractAgeRating(tmdbData, mediaType),
        primaryStudio:  aiAnalysis.primaryStudio || null,

        // ── Visual Assets ─────────────────────────────────────────────────────
        posterUrl,
        backdropUrl,
        anilistBannerUrl: anilistData?.bannerImage || null,
        trailerKey:       trailer?.key || null,
        trailerSite:      trailer?.site || null,

        // ── Classification ────────────────────────────────────────────────────
        genres: (tmdbData.genres || []).map((g) => g.name),
        keywords: (tmdbData.keywords?.results || tmdbData.keywords?.keywords || [])
            .slice(0, 30).map((k) => k.name),
        anilistGenres: anilistData?.genres || [],
        anilistTags:   (anilistData?.tags || []).filter((t) => !t.isMediaSpoiler).slice(0, 20).map((t) => t.name),

        // ── Stats ─────────────────────────────────────────────────────────────
        voteAverage:       tmdbData.vote_average || 0,
        voteCount:         tmdbData.vote_count || 0,
        runtime,
        numberOfSeasons:   tmdbData.number_of_seasons || null,
        numberOfEpisodes:  tmdbData.number_of_episodes || anilistData?.episodes || null,
        releaseDate:       tmdbData.release_date || tmdbData.first_air_date || null,

        // ── Cast & Recommendations ────────────────────────────────────────────
        cast,
        recommendations,
        externalIds,

        // ── Chronological Graph (AI-generated) ───────────────────────────────
        chronoEntries:     aiAnalysis.chronologicalGraph?.entries || [],
        branchingPoints:   aiAnalysis.chronologicalGraph?.branchingPoints || [],
        arcs:              aiAnalysis.arcs || [],

        // ── System ───────────────────────────────────────────────────────────
        lastIngested:      Timestamp.now(),
        ingestVersion:     '3.0.0',
    };

    try {
        await db.collection('catalog').doc(docId).set(catalogDoc, { merge: true });
        console.log(`[Firestore] ✓ Catalog document written: ${docId}`);
    } catch (e) {
        console.error(`[Firestore] Failed to write catalog doc: ${e.message}`);
    }
    return docId;
}

/**
 * Writes season documents to the `seasons` subcollection.
 * Uses Firestore batch writes (max 500 ops/batch) for efficiency.
 */
async function writeSeasons(catalogDocId, seasons, tmdbData) {
    console.log(`[Firestore] Writing ${seasons.length} season(s)...`);
    let batch = db.batch();
    let opCount = 0;

    for (const season of seasons) {
        const seasonDocId = `s${season.season_number}`;
        const seasonRef = db.collection('catalog').doc(catalogDocId)
            .collection('seasons').doc(seasonDocId);

        batch.set(seasonRef, {
            seasonNumber:  season.season_number,
            name:          season.name,
            overview:      season.overview || '',
            episodeCount:  season.episodes?.length || season.episode_count || 0,
            posterUrl:     season.poster_path ? `https://image.tmdb.org/t/p/w342${season.poster_path}` : null,
            airDate:       season.air_date || null,
            lastIngested:  Timestamp.now(),
        }, { merge: true });

        opCount++;
        if (opCount >= 490) { await batch.commit(); batch = db.batch(); opCount = 0; }
    }
    if (opCount > 0) {
        try {
            await batch.commit();
        } catch (e) {
            console.error(`[Firestore] Batch write seasons failed: ${e.message}`);
        }
    }
    console.log(`[Firestore] ✓ ${seasons.length} season(s) written.`);
}

/**
 * Writes all episodes to Firestore, enriched with:
 * - CANON/FILLER/MIXED typeTag (from Gemini analysis)
 * - Absolute episode numbers (for anime)
 * - Arc names (from Gemini analysis)
 *
 * Uses batched writes with automatic flush at 490 ops to stay under the 500 limit.
 */
async function writeEpisodes(catalogDocId, allEpisodes, canonFillerMap) {
    console.log(`[Firestore] Writing ${allEpisodes.length} episode(s)...`);

    // Build a quick-lookup map from the Gemini analysis
    // Key: "S{season}E{episode}" → typeTag + arcName
    const canonMap = new Map();
    for (const entry of canonFillerMap) {
        const key = `S${entry.seasonNumber}E${entry.episodeNumber}`;
        canonMap.set(key, { typeTag: entry.typeTag, arcName: entry.arcName });
        // Also index by absolute episode number for anime
        if (entry.absoluteEpisodeNumber !== null && entry.absoluteEpisodeNumber !== undefined) {
            canonMap.set(`ABS${entry.absoluteEpisodeNumber}`, { typeTag: entry.typeTag, arcName: entry.arcName });
        }
    }

    let batch = db.batch();
    let opCount = 0;
    let absoluteCounter = 1;

    for (const episode of allEpisodes) {
        const key = `S${episode.seasonNumber}E${episode.episodeNumber}`;
        const aiData = canonMap.get(key) || canonMap.get(`ABS${absoluteCounter}`) || {};

        const episodeDocId = `s${episode.seasonNumber}e${String(episode.episodeNumber).padStart(3, '0')}`;
        const episodeRef = db.collection('catalog').doc(catalogDocId)
            .collection('episodes').doc(episodeDocId);

        batch.set(episodeRef, {
            episodeNumber:          episode.episodeNumber,
            seasonNumber:           episode.seasonNumber,
            absoluteEpisodeNumber:  absoluteCounter,
            title:                  episode.name || `Episode ${episode.episodeNumber}`,
            overview:               episode.overview || '',
            airDate:                episode.air_date || null,
            runtime:                episode.runtime || null,
            stillUrl:               episode.still_path
                                        ? `https://image.tmdb.org/t/p/w300${episode.still_path}`
                                        : null,
            voteAverage:            episode.vote_average || 0,
            typeTag:                aiData.typeTag || 'CANON',
            arcName:                aiData.arcName || null,
            lastIngested:           Timestamp.now(),
        }, { merge: true });

        opCount++;
        absoluteCounter++;
        if (opCount >= 490) { await batch.commit(); batch = db.batch(); opCount = 0; }
    }
    if (opCount > 0) {
        try {
            await batch.commit();
        } catch (e) {
            console.error(`[Firestore] Batch write episodes failed: ${e.message}`);
        }
    }
    console.log(`[Firestore] ✓ ${allEpisodes.length} episode(s) written.`);
}

// ─── Main Orchestrator ────────────────────────────────────────────────────────

async function main() {
    console.log('\n╔══════════════════════════════════════╗');
    console.log(`║  Watch Order Engine — ETL Ingestion  ║`);
    console.log(`║  Target: "${SEARCH_TITLE}"${' '.repeat(Math.max(0, 25 - SEARCH_TITLE.length))}║`);
    console.log('╚══════════════════════════════════════╝\n');

    try {
        // ── Step 1: Search TMDB ───────────────────────────────────────────────
        const searchResult = await searchTmdb(SEARCH_TITLE);
        const { id: tmdbId, mediaType } = searchResult;

        // ── Step 2: Fetch full TMDB detail ────────────────────────────────────
        const tmdbData = await fetchTmdbDetail(tmdbId, mediaType);

        // ── Step 3: Fetch all seasons and episodes (TV only) ──────────────────
        let allSeasons   = [];
        let allEpisodes  = [];

        if (mediaType === 'tv' && tmdbData.seasons) {
            const seasonNumbers = tmdbData.seasons
                .filter((s) => s.season_number > 0) // Skip "Specials" season 0
                .map((s) => s.season_number);

            allSeasons = await fetchAllSeasons(tmdbId, seasonNumbers);

            // Flatten all episodes across seasons into a single array
            for (const season of allSeasons) {
                if (season.episodes) {
                    for (const ep of season.episodes) {
                        allEpisodes.push({
                            ...ep,
                            seasonNumber: season.season_number,
                        });
                    }
                }
            }
            console.log(`[ETL] Total episodes collected: ${allEpisodes.length}`);
        }

        // ── Step 4: Fetch AniList data (if likely anime) ──────────────────────
        const isLikelyAnime = mediaType === 'tv'
            && (tmdbData.origin_country?.includes('JP')
                || (tmdbData.genres || []).some((g) => g.name === 'Animation')
                || FORCE_TYPE === 'anime');

        const malId = tmdbData.external_ids?.imdb_id ? null : null; // MAL ID via external_ids
        const anilistData = isLikelyAnime
            ? await fetchAnilistData(SEARCH_TITLE, malId)
            : null;

        // ── Step 5: Gemini AI analysis ────────────────────────────────────────
        const aiAnalysis = await analyzeWithGemini(SEARCH_TITLE, tmdbData, anilistData, allEpisodes);

        // ── Step 6: Write to Firestore ────────────────────────────────────────
        const catalogDocId = await writeCatalogDocument(tmdbData, anilistData, aiAnalysis, mediaType);

        if (allSeasons.length > 0) {
            await writeSeasons(catalogDocId, allSeasons, tmdbData);
        }
        if (allEpisodes.length > 0) {
            await writeEpisodes(catalogDocId, allEpisodes, aiAnalysis.canonFillerMap || []);
        }

        // ── Step 7: Write chronology graph nodes (if part of a universe) ──────
        if (UNIVERSE && aiAnalysis.chronologicalGraph?.entries?.length > 0) {
            const graphRef = db.collection('catalog').doc(catalogDocId)
                .collection('chronologyGraph').doc('graph');
            try {
                await graphRef.set({
                    universeId:     UNIVERSE,
                    entries:        aiAnalysis.chronologicalGraph.entries,
                    branchingPoints: aiAnalysis.chronologicalGraph.branchingPoints,
                    arcs:           aiAnalysis.arcs,
                    lastUpdated:    Timestamp.now(),
                }, { merge: true });
                console.log(`[Firestore] ✓ Chronology graph written for universe: ${UNIVERSE}`);
            } catch (e) {
                console.error(`[Firestore] Failed to write chronology graph: ${e.message}`);
            }
        }

        console.log('\n✅ Ingestion complete!\n');
        console.log(`   Firestore document: catalog/${catalogDocId}`);
        console.log(`   Seasons written:    ${allSeasons.length}`);
        console.log(`   Episodes written:   ${allEpisodes.length}`);
        console.log(`   Arcs mapped:        ${aiAnalysis.arcs?.length || 0}`);
        console.log(`   Canon classified:   ${aiAnalysis.canonFillerMap?.filter(e => e.typeTag === 'CANON').length || 0}`);
        console.log(`   Filler classified:  ${aiAnalysis.canonFillerMap?.filter(e => e.typeTag === 'FILLER').length || 0}`);
        process.exit(0);

    } catch (err) {
        console.error('\n❌ Ingestion failed:', err.message);
        if (err.stack) console.error(err.stack);
        process.exit(1);
    }
}

main();
