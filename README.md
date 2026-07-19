# Watch Order Engine (WOE)

Watch Order Engine (WOE) is a premium, offline-first Android application engineered to solve the "what order do I watch this in?" dilemma for massive, interconnected media franchises.

Instead of a flat list, shows and movies are laid out as a **Directed Acyclic Graph (DAG) / skill-tree**. This allows users to see the actual dependency structure of a universe (like the MCU, Star Wars, or long-running anime) and follow a coherent, visually mapped watch order through it.

---



https://github.com/user-attachments/assets/b3be8164-a33b-4a25-a625-f0d063dfe635




##  Architectural Highlights

*   **Offline-First Sync Engine:** Built around a robust **Room Database** caching layer. Users can navigate massive multi-season timelines entirely offline.
*   **High-Throughput Database Optimization:** Utilizes high-efficiency bulk-insert SQL operations to handle massive episode lists (1,000+ nodes), mitigating main-thread stuttering.
*   **Memory Management & Scaling:** Implements **Paging 3** for infinite scrolling in discovery and search tabs, ensuring smooth UI performance even with thousands of results.
*   **Graceful Degradation:** Integrates multiple third-party endpoints via **Retrofit**. Designed with resilient error handling—ensuring the UI remains stable during third-party network outages.
*   **Real-Time Data Layer:** Powered by **Firebase (Auth & Cloud Firestore)** to handle decentralized account synchronization, universal global feeds, and dynamic user streaks.
*   **Modern State-Driven UI:** Synthesized completely using **Jetpack Compose** and **Material 3**. Implements strict **MVVM** design patterns alongside Kotlin **Coroutines** and **StateFlow**.

---

##  Core Features

*   **Chronology / Branching Timeline View:** Visualizes a universe as an interactive graph of connected entries.
*   **Canon/Filler Tracking:** Flags filler episodes so long-running series (Naruto, One Piece, etc.) can be trimmed strictly to the canon path.
*   **AI-Assisted DAG Generation:** Gemini 2.5 Flash generates watch-order structure and canon/filler data on demand.
*   **Unique Stylized Themes:** Includes custom UI modes like **Comic**, **Manga**, and **Funk** that redefine the app's visual identity.
*   **Discovery & Search:** Browse and search the TMDB catalog seamlessly with swipable discovery decks.
*   **Character Lore:** Pulls anime character bios from AniList and non-anime character lore from Wikipedia.
*   **Community:** Shared timelines, global feeds, and public user profiles.

---

## ️ Tech Stack & APIs

**Core:** Kotlin, Jetpack Compose, Coroutines / StateFlow  
**Architecture:** Hilt (DI), Room (Local DB), Paging 3, Retrofit + Moshi (Networking), Coil (Image Loading)  
**Backend:** Firebase (Auth, Firestore, Storage)

| API | Purpose |
| :--- | :--- |
| [TMDB](https://www.themoviedb.org/documentation/api) | Catalog data (titles, posters, metadata) |
| [Tenrai](https://tenrai.org) | Anime episode / canon-filler data and reviews |
| [Gemini 2.5 Flash](https://ai.google.dev/) | AI-triggered DAG generation and classification |
| [AniList](https://anilist.co/graphiql) | Anime character bios |
| [Wikipedia](https://en.wikipedia.org/api/rest_v1/) | Character lore for non-anime universes |

---

##  Getting Started

### Prerequisites

*   Android Studio (latest stable)
*   JDK 17+
*   An Android device or emulator running **API 24 (Android 7.0)** or higher

### 1. Clone the Repo

```bash
git clone https://github.com/<your-username>/Watch-Order_Engine.git
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

---

##  Project Structure

```text
app/src/main/java/com/example/watchorderengine/
├── data/            # Repositories, Room Cache, DAG/Graph Logic
├── network/         # Retrofit services (TMDB, Tenrai, AniList, Gemini)
├── di/              # Hilt Providers
├── ui/              # Jetpack Compose Presentation Layer
│   ├── screens/     # Discovery, Search, Media Detail, Community
│   ├── timeline/    # Branching timeline rendering
│   ├── theme/       # Stylized themes (Comic, Manga, Funk)
│   └── viewmodel/   # UI State Management
└── util/            # Helpers and Retry Logic
```

---

## ️ Roadmap (V2.0)

*   [ ] **Dynamic Theme Engine:** adaptation of accent colors based on media poster palettes.
*   [ ] **Release Calendar:** Tracking upcoming episode air-dates for "Watching" titles.
*   [ ] **AI Context:** "Ask AI about this order" to explain chronology choices.
*   [ ] **Interactive Focus Mode:** Dims non-essential nodes to highlight specific watch paths.

---

##  License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
