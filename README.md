# 🚀 Watch Order Engine (WOE) v2.0.0

Watch Order Engine (WOE) is a premium, offline-first Android application engineered to solve the "what order do I watch this in?" dilemma for massive, interconnected media franchises.

Instead of a flat list, shows and movies are laid out as a **Directed Acyclic Graph (DAG) / skill-tree**. This allows users to see the actual dependency structure of a universe (like the MCU, Star Wars, or long-running anime) and follow a coherent, visually mapped watch order through it.

---

https://github.com/user-attachments/assets/b3be8164-a33b-4a25-a625-f0d063dfe635

---

## 🌟 V2.0.0 Capabilities & Feature Breakdown

Designed and developed as a solo project by **Parth**, Watch Order Engine V2 is a major release packed with high-power features:

### 🎭 1. Standalone Actor Search & Profile Hub
- **Global Actor Search**: Search real-life actors directly via TMDB (`/search/person`).
- **Actor Detail Screen**: Full actor biography, photo gallery carousel, filmography tabs (Movies & TV Shows), and "Top Works" sorted by popularity.
- **Favorite Actors Persistence**: Save favorite actors locally in Room (`favorite_actors` table) with cloud sync to Firestore.
- **Home Showcase Row**: "Starring Your Favorite Actors" horizontal carousel on the Home feed.
- **1-Tap Actor Navigation**: Tap any real-life actor's photo or name on `CharacterDetailScreen` or profile cards to jump directly to their `ActorDetailScreen`.

### 🗓️ 2. Multi-Source Live Release Calendar
- **Multi-Source Airing Schedule**: Ingests live schedules across **AniList GraphQL**, **TMDB** (`/tv/on_the_air` & `/movie/upcoming`), and **TVMaze**.
- **Live Air Times & Countdowns**: Displays exact local airing times (`HH:mm`) and live status badges.
- **1-Tap Notification Bell (🔔)**: Schedule local push reminders 15 minutes before any episode drops.
- **Streaming App Launcher**: Direct badges for *Netflix, Crunchyroll, Disney+, Prime Video, HBO Max, Sony LIV* that open provider apps directly via `StreamingLauncher`.
- **Interactive Month/Year Picker**: Tap `SEPTEMBER 2026 ▼` in the calendar grid to open a 4x3 month & year selector.
- **Nearest-Date Jump & Heatmap**: Jump to nearest airing dates with daily release density badges (`• ACTIVE`, `•• RELEASES`).

### 📊 3. Multi-Score Ratings Benchmark & Awards Showcase (OMDb + TVMaze)
- **Ratings Benchmark Card**: View **IMDb Ratings (★)**, **Rotten Tomatoes Fresh Scores (🍅)**, and **Metacritic Scores (🎯)** side-by-side on any show or movie detail screen.
- **Awards & Accolades Card**: Displays Oscar wins, Emmy awards, and nomination summaries (*"Won 4 Oscars. 128 wins & 186 nominations"*).
- **Network Badges**: Displays network/platform source badges (*HBO, Netflix, Apple TV+*) on TV shows.

### 💬 4. Community Discussion Comments & Social Follow Graph
- **Threaded Discussion Comments**: Post, reply to, and like comments under shared community posts and timelines (`global_feed/{postId}/comments`).
- **O(1) Social Follow Graph**: Follow and unfollow users with O(1) document lookups (`follows/{followerId}_{followingId}`) and instant push notifications.
- **Followers & Following Management Sheet**: Two-tab `FollowsListSheet` (Following with Unfollow buttons, Followers with Follow Back buttons).
- **Public Profile Preview**: Tap "Public Preview" on your Profile screen to view the exact public layout other users see.

### 📈 5. Watchlist Metrics Analytics & Taste Vector Engine
- **Advanced Viewing Analytics**: Track **Completion Rate %** (e.g., `84%`), **Canon Purity %** (e.g., `96% Canon`), **Decade/Release Era Breakdown** (`2020s`, `2010s`, `2000s`, `Classic`), and format distribution.
- **Multi-Feature Taste Vector Engine**: TF-IDF + Cosine similarity algorithm calculating **Match Affinity Percentages** (`94% Match`) for recommendations.

### 🛠️ 6. Custom DAG Graph Builder & Personal Universe Publishing
- **Interactive Canvas**: Draggable nodes, tap-to-draw edge lines, poster-height alignment, and high-resolution timeline image export.
- **Private Personal Universe Publishing**: Save custom hand-built timelines privately (`publishCustomUniverse`) without publishing to the public feed.

### 📱 7. Modern Glance Android Home Widgets
- **Continue Watching Widget**: Dark glassmorphic card with poster artwork, progress bars, friendly "Season 2 Ep 4" labels, and a 1-tap "RESUME" button.
- **Release Calendar Widget**: Modern calendar widget showing the next 3 upcoming releases with urgency badges (*AIRING TODAY*, *AIRING TOMORROW*).
- **Watch Streak Widget**: Habit-forming ring widget showing daily watch streak (🔥 + day count + progress ring) and active show counts.

### 🌐 8. Regional Language Filters & Customizable Carousels
- **Regional Home Carousels**: Dedicated horizontal sections for regional languages (*Japanese Anime, Korean Drama, Hindi Cinema, Tamil, Telugu, Marathi, Malayalam, Kannada, Bengali, Gujarati, Punjabi*).
- **Global & Screen Filters**: Filter search and discovery deck by language codes.

---

## 🏗️ Architectural Highlights

*   **Offline-First Sync Engine:** Built around a robust **Room Database** caching layer. Users can navigate massive multi-season timelines entirely offline.
*   **High-Throughput Database Optimization:** Utilizes high-efficiency bulk-insert SQL operations to handle massive episode lists (1,000+ nodes), mitigating main-thread stuttering.
*   **Memory Management & Scaling:** Implements **Paging 3** for infinite scrolling in discovery and search tabs, ensuring smooth UI performance even with thousands of results.
*   **Graceful Degradation:** Integrates multiple third-party endpoints via **Retrofit**. Designed with resilient error handling—ensuring the UI remains stable during third-party network outages.
*   **Real-Time Data Layer:** Powered by **Firebase (Auth & Cloud Firestore)** to handle decentralized account synchronization, universal global feeds, and dynamic user streaks.
*   **Modern State-Driven UI:** Synthesized completely using **Jetpack Compose** and **Material 3**. Implements strict **MVVM** design patterns alongside Kotlin **Coroutines** and **StateFlow**.

---

## 🛠️ Tech Stack & APIs

**Core:** Kotlin, Jetpack Compose, Coroutines / StateFlow  
**Architecture:** Hilt (DI), Room (v18 Local DB), Paging 3, Retrofit + Moshi (Networking), Coil (Image Loading)  
**Backend:** Firebase (Auth, Firestore, Storage)

| API | Purpose |
| :--- | :--- |
| [TMDB](https://www.themoviedb.org/documentation/api) | Catalog data, actor search, TV on-air & upcoming movies |
| [OMDb](https://www.omdbapi.com/) | IMDb ratings (★), Rotten Tomatoes %, Metacritic, Awards & Accolades |
| [TVMaze](https://www.tvmaze.com/api) | Global TV schedules, episode synopses & network badges |
| [AniList](https://anilist.co/graphiql) | Live anime airing schedules, character bios & franchise relations |
| [Tenrai / Jikan](https://tenrai.org) | Anime episode / canon-filler data and MAL reviews |
| [Gemini 3.6 Flash](https://ai.google.dev/) | AI-triggered DAG generation, chronology explanations & lore synthesis |
| [Wikipedia](https://en.wikipedia.org/api/rest_v1/) | Character lore for non-anime universes |

---

## 🚀 Getting Started

### Prerequisites

*   Android Studio (latest stable)
*   JDK 17+
*   An Android device or emulator running **API 24 (Android 7.0)** or higher

### 1. Clone the Repo

```bash
git clone https://github.com/Parth-1508/Watch-Order_Engine.git
cd Watch-Order_Engine
```

### 2. Add Your API Keys

Create a `local.properties` file in the project root and add:

```properties
TMDB_API_KEY=your_tmdb_api_key
TMDB_READ_ACCESS_TOKEN=your_tmdb_read_access_token
GEMINI_API_KEY=your_gemini_api_key
```

> **Note:** The build also supports `GEMINI_BURNER_KEY_1` through `GEMINI_BURNER_KEY_7` for quota rotation.

### 3. Set Up Firebase

1.  Create a project at the [Firebase Console](https://console.firebase.google.com/).
2.  Add an Android app with package name `com.example.watchorderengine`.
3.  Download `google-services.json` and place it in `app/google-services.json`.
4.  Enable **Authentication** (Anonymous & Email), **Firestore**, and **Storage**.

### 4. Deploy Firestore Security Rules

To deploy the production security rules to your Firebase project:

```bash
firebase deploy --only firestore:rules
```

---

## 👤 Developer

Designed & developed as a solo project by **Parth-1508**.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
