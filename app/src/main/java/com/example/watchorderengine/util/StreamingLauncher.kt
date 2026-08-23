package com.example.watchorderengine.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.watchorderengine.data.model.WatchProviderItem

/**
 * One entry in the provider deep-link table: the official Android package for a
 * streaming app, plus (where the provider's app supports it) a web URL template
 * for a title search that Android will route straight into the app via an
 * App Link — *if* the app is installed — instead of opening a browser tab.
 *
 * TMDB's `/watch/providers` endpoint only tells us which providers carry a
 * title, not that title's ID inside each provider's own catalog (that mapping
 * isn't publicly available), so a search deep link is the most reliable
 * "1-tap" experience achievable without a private integration with every
 * provider. `%s` is replaced with the URL-encoded show/movie title.
 */
private data class StreamingApp(
    val packageName: String,
    val searchUrlTemplate: String? = null
)

/**
 * TMDB `provider_id` → the provider's official Android app.
 *
 * Package names are each provider's real, current Google Play package (not
 * anything TMDB gives us) — verified against their current Play Store
 * listings, since several of these have changed with rebrands (e.g. HBO Max's
 * `com.hbo.hbonow` → Max's `com.wbd.stream`).
 */
private val STREAMING_APPS: Map<Int, StreamingApp> = mapOf(
    8    to StreamingApp("com.netflix.mediaclient", "https://www.netflix.com/search?q=%s"),
    119  to StreamingApp("com.amazon.avod.thirdpartyclient", "https://app.primevideo.com/search/ref=atv_nb_sr?phrase=%s"),
    337  to StreamingApp("com.disney.disneyplus", "https://www.disneyplus.com/search/%s"),
    283  to StreamingApp("com.crunchyroll.crunchyroid", "https://www.crunchyroll.com/search?q=%s"),
    350  to StreamingApp("com.apple.atve.androidtv.appletv"), // Apple TV app — no public search deep link, just opens the app
    1899 to StreamingApp("com.wbd.stream"), // Max (formerly HBO Max) — package changed with the 2023 rebrand
    122  to StreamingApp("in.startv.hotstar", "https://www.hotstar.com/in/search?q=%s"), // JioHotstar; package unchanged since the rebrand
    15   to StreamingApp("com.hulu.plus", "https://www.hulu.com/search?q=%s"),
    192  to StreamingApp("com.google.android.youtube", "https://www.youtube.com/results?search_query=%s"),
    3    to StreamingApp("com.google.android.videos"), // Google TV (formerly Google Play Movies & TV) — package unchanged
)

/**
 * Attempts, in order, to get the person watching [title] as fast as possible:
 *
 *  1. If [provider]'s app is installed, open it directly — via a title search
 *     deep link where the provider supports one (Android routes the App Link
 *     straight to the installed app), or just its home screen otherwise.
 *  2. If the app isn't installed, open its Play Store listing so installing
 *     it is one tap away.
 *  3. If we don't recognize the provider at all, fall back to [fallbackUrl]
 *     (TMDB's JustWatch link), same as before this feature existed.
 */
fun launchStreamingProvider(
    context: Context,
    provider: WatchProviderItem,
    title: String,
    fallbackUrl: String?
) {
    val app = STREAMING_APPS[provider.providerId]

    if (app != null && isAppInstalled(context, app.packageName)) {
        val searchIntent = app.searchUrlTemplate
            ?.format(Uri.encode(title))
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply { setPackage(app.packageName) } }
        val intent = searchIntent ?: context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null && tryStart(context, intent)) return
    }

    if (app != null && tryStart(context, playStoreIntent(app.packageName))) return

    fallbackUrl?.let { tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
}

private fun isAppInstalled(context: Context, packageName: String): Boolean =
    try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

private fun playStoreIntent(packageName: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

/** @return true if the Intent was launched, false if nothing could handle it. */
private fun tryStart(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
