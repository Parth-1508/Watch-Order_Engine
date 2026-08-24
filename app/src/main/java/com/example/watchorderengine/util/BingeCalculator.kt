package com.example.watchorderengine.util

import com.example.watchorderengine.data.model.EpisodeItem
import com.example.watchorderengine.data.model.EpisodeType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of a "how long until I'm caught up" calculation for a show — see
 * [calculateCatchUp].
 */
data class CatchUpResult(
    /** Unwatched, already-released episodes counted toward the total below. */
    val remainingEpisodeCount: Int,
    /** Sum of those episodes' runtimes, in minutes. */
    val remainingRuntimeMinutes: Int,
    /**
     * True if at least one counted episode had no runtime of its own and
     * fell back to the show's average — the UI should render this as an
     * estimate ("~4h 20m") rather than implying second-accurate precision.
     */
    val isEstimate: Boolean,
    /**
     * How many FILLER episodes exist in the remaining, unwatched, released
     * set. Tallied regardless of [CatchUpResult] not carrying the toggle
     * state itself, so the UI can show "skipping filler saves 1h 40m" even
     * while the toggle is off.
     */
    val fillerEpisodeCount: Int,
    /** Sum of those filler episodes' runtimes, in minutes. */
    val fillerRuntimeMinutes: Int
) {
    val remainingRuntimeHours: Int get() = remainingRuntimeMinutes / 60
    val remainingRuntimeMinutesPart: Int get() = remainingRuntimeMinutes % 60
    val hasFiller: Boolean get() = fillerEpisodeCount > 0
}

/** TMDB's episode air_date format — also how [EpisodeItem.airDate] is stored. */
private fun airDateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/**
 * Sums the exact runtime of every episode still standing between "watched so
 * far" and "fully caught up with what's actually out" — i.e. every unwatched
 * episode that has already aired. Unaired/future episodes are never counted;
 * you can't binge something that isn't released yet, so the total always
 * reflects "time to reach the latest release," not "time to finish the
 * show" for an still-airing series.
 *
 * When [excludeFiller] is true, [EpisodeType.FILLER] episodes are excluded
 * from the running total entirely (both count and minutes) — but they're
 * always tallied separately in the result, so the UI can show what turning
 * the toggle on *would* save even while it's off.
 *
 * @param fallbackRuntimeMinutes Used for the rare episode with no runtime of
 *        its own (common right after an episode airs, before TMDB backfills
 *        it) — normally the show's own average episode runtime. Using it for
 *        even one counted episode sets [CatchUpResult.isEstimate].
 */
fun calculateCatchUp(
    episodes: List<EpisodeItem>,
    excludeFiller: Boolean,
    fallbackRuntimeMinutes: Int = 24
): CatchUpResult {
    // A fresh SimpleDateFormat per call — the class is not thread-safe, and
    // this function may run on a background dispatcher.
    val todayStr = airDateFormat().format(Date())

    fun isReleased(airDate: String?): Boolean = !airDate.isNullOrBlank() && airDate <= todayStr
    fun runtimeOrFallback(episode: EpisodeItem): Int =
        episode.runtime?.takeIf { it > 0 } ?: fallbackRuntimeMinutes

    val remaining = episodes.filter { !it.isWatched && isReleased(it.airDate) }
    val fillerRemaining = remaining.filter { it.episodeType == EpisodeType.FILLER }
    val counted = if (excludeFiller) remaining - fillerRemaining.toSet() else remaining

    val usedFallback = counted.any { it.runtime == null || it.runtime <= 0 }

    return CatchUpResult(
        remainingEpisodeCount = counted.size,
        remainingRuntimeMinutes = counted.sumOf { runtimeOrFallback(it) },
        isEstimate = usedFallback,
        fillerEpisodeCount = fillerRemaining.size,
        fillerRuntimeMinutes = fillerRemaining.sumOf { runtimeOrFallback(it) }
    )
}
