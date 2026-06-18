import { useState, useEffect, useRef } from "react";
import { Link } from "react-router";
import { Settings, Search, X } from "lucide-react";
import { getWatchlistStatus } from "./Detail";
import { motion, AnimatePresence } from "motion/react";

function SkeletonCard({ aspect = "2/3" }: { aspect?: string }) {
  return (
    <div className="flex flex-col animate-pulse">
      <div className="w-full rounded-lg border-2 border-black/20 bg-white/10" style={{ aspectRatio: aspect }} />
      <div className="mt-2 h-3 w-3/4 rounded bg-white/10" />
      <div className="mt-1 h-2 w-1/2 rounded bg-white/10" />
    </div>
  );
}

const ALL_SHOWS = [
  { id: 1, title: "Naruto Special",      genres: ["Action", "Ninja", "Coming-of-age"], image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  { id: 2, title: "Cyber City X",        genres: ["Cyberpunk", "Mystery", "Thriller"],  image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "mixed" },
  { id: 3, title: "Fantasy Legends",     genres: ["Fantasy", "Adventure", "Epic"],       image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "mixed" },
  { id: 4, title: "Action Force",        genres: ["Military", "Drama", "Thriller"],      image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  { id: 5, title: "Neon Genesis: Redux", genres: ["Sci-Fi", "Psychological", "Mecha"],   image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
];

function readStatuses() {
  return Object.fromEntries(ALL_SHOWS.map(s => [s.id, getWatchlistStatus(String(s.id))]));
}

export function Home() {
  const categories = ["Watching", "Planned", "Completed", "Paused", "Dropped"];
  const [activeCategory, setActiveCategory] = useState("Watching");
  const [statuses, setStatuses] = useState<Record<number, string>>(readStatuses);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [loaded, setLoaded] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const t = setTimeout(() => setLoaded(true), 600);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    const sync = () => setStatuses(readStatuses());
    window.addEventListener("watchlist-changed", sync);
    window.addEventListener("focus", sync);
    return () => {
      window.removeEventListener("watchlist-changed", sync);
      window.removeEventListener("focus", sync);
    };
  }, []);

  useEffect(() => {
    if (searchOpen) searchRef.current?.focus();
  }, [searchOpen]);

  const closeSearch = () => { setSearchOpen(false); setQuery(""); };

  // Search results: match title or any genre, case-insensitive
  const searchResults = query.trim()
    ? ALL_SHOWS.filter(s =>
        s.title.toLowerCase().includes(query.toLowerCase()) ||
        s.genres.some(g => g.toLowerCase().includes(query.toLowerCase()))
      )
    : [];

  const continueWatching = ALL_SHOWS.filter(s => statuses[s.id] === "Watching");
  const categorized = ALL_SHOWS.filter(s => statuses[s.id] === activeCategory);
  const uncategorized = ALL_SHOWS.filter(s => !statuses[s.id] || statuses[s.id] === "none");
  const showsForCategory = activeCategory === "Watching" ? continueWatching : categorized;
  const isEmpty = showsForCategory.length === 0;

  return (
    <div className="flex flex-col h-full w-full pb-8">
      {/* Header */}
      <header className="flex items-center justify-between p-4 bg-background z-10 sticky top-0 border-b-4 border-black">
        <Link to="/app/profile" className="w-10 h-10 rounded-full border-2 border-black overflow-hidden bg-accent group shrink-0">
          <img src="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=100&h=100" alt="Avatar" className="w-full h-full object-cover group-active:scale-95 transition-transform" />
        </Link>

        <AnimatePresence mode="wait">
          {searchOpen ? (
            <motion.div
              key="search"
              initial={{ opacity: 0, scaleX: 0.8 }}
              animate={{ opacity: 1, scaleX: 1 }}
              exit={{ opacity: 0, scaleX: 0.8 }}
              className="flex-1 mx-2 flex items-center gap-2 bg-white border-2 border-black rounded-full px-3 py-1.5 shadow-[2px_2px_0_0_rgba(0,0,0,1)]"
            >
              <Search size={16} className="text-gray-400 shrink-0" />
              <input
                ref={searchRef}
                value={query}
                onChange={e => setQuery(e.target.value)}
                onKeyDown={e => e.key === "Escape" && closeSearch()}
                placeholder="Search title or genre…"
                className="flex-1 bg-transparent text-black text-sm focus:outline-none placeholder-gray-400"
              />
              {query && (
                <button onClick={() => setQuery("")} className="shrink-0 text-gray-400 hover:text-black">
                  <X size={14} />
                </button>
              )}
            </motion.div>
          ) : (
            <motion.h1
              key="title"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="font-black text-xl tracking-tighter uppercase italic drop-shadow-[2px_2px_0_rgba(0,0,0,1)] text-white"
              style={{ WebkitTextStroke: "1px black" }}
            >
              Watch Order
            </motion.h1>
          )}
        </AnimatePresence>

        <div className="flex gap-2 shrink-0">
          <button
            onClick={() => searchOpen ? closeSearch() : setSearchOpen(true)}
            className="w-10 h-10 rounded-full bg-white border-2 border-black flex items-center justify-center shadow-[2px_2px_0_0_rgba(0,0,0,1)] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-all"
          >
            {searchOpen ? <X size={18} className="text-black" /> : <Search size={18} className="text-black" />}
          </button>
          {!searchOpen && (
            <Link to="/app/settings" className="w-10 h-10 rounded-full bg-white border-2 border-black flex items-center justify-center shadow-[2px_2px_0_0_rgba(0,0,0,1)] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-all">
              <Settings size={18} className="text-black" />
            </Link>
          )}
        </div>
      </header>

      {/* Search Results overlay */}
      <AnimatePresence>
        {searchOpen && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className="absolute top-[73px] left-0 w-full z-30 bg-background border-b-4 border-black px-4 pb-4 pt-3"
          >
            {query.trim() === "" ? (
              <div>
                <p className="text-xs text-gray-500 font-bold uppercase mb-3">All Shows</p>
                <div className="space-y-2">
                  {ALL_SHOWS.map(show => (
                    <SearchRow key={show.id} show={show} status={statuses[show.id]} onTap={closeSearch} />
                  ))}
                </div>
              </div>
            ) : searchResults.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-sm font-bold text-gray-500">No results for "<span className="text-white">{query}</span>"</p>
                <p className="text-xs text-gray-600 mt-1">Try a genre like "Action", "Sci-Fi", or "Drama"</p>
              </div>
            ) : (
              <div>
                <p className="text-xs text-gray-500 font-bold uppercase mb-3">{searchResults.length} result{searchResults.length !== 1 ? "s" : ""}</p>
                <div className="space-y-2">
                  {searchResults.map(show => (
                    <SearchRow key={show.id} show={show} status={statuses[show.id]} onTap={closeSearch} highlight={query} />
                  ))}
                </div>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Main content (hidden while search open) */}
      {!searchOpen && (
        <>
          {/* Continue Watching */}
          {continueWatching.length > 0 && (
            <section className="px-4 pt-6 pb-2">
              <h2 className="font-bold text-lg mb-3 uppercase tracking-wider text-black bg-white inline-block px-2 border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform -skew-x-6">
                Continue Watching
              </h2>
              <div className="flex overflow-x-auto gap-4 pb-4 no-scrollbar -mx-4 px-4">
                {continueWatching.map(show => (
                  <Link key={show.id} to={`/app/detail/${show.id}`}
                    className="block min-w-[280px] h-[160px] relative rounded-xl border-4 border-black overflow-hidden shadow-[4px_4px_0_0_rgba(0,0,0,1)] active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0_0_rgba(0,0,0,1)] transition-all shrink-0">
                    <img src={show.image} className="absolute inset-0 w-full h-full object-cover" alt={show.title} />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/80 to-transparent flex flex-col justify-end p-3">
                      <h3 className="text-white font-bold text-lg drop-shadow-md">{show.title}</h3>
                      <p className="text-white/70 text-xs">{show.genres.join(" • ")}</p>
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          )}

          {/* Category tabs */}
          <section className="py-2 mt-2">
            <div className="flex overflow-x-auto gap-3 px-4 pb-2 no-scrollbar">
              {categories.map(cat => {
                const count = ALL_SHOWS.filter(s => statuses[s.id] === cat).length;
                return (
                  <button key={cat} onClick={() => setActiveCategory(cat)}
                    className={`whitespace-nowrap px-4 py-1.5 rounded-full border-2 border-black font-bold text-sm uppercase transition-all active:scale-95 flex items-center gap-1.5
                      ${activeCategory === cat ? "bg-black text-[#00ff00] shadow-[0_0_10px_#00ff00]" : "bg-white text-black shadow-[2px_2px_0_0_rgba(0,0,0,1)]"}`}>
                    {cat}
                    {count > 0 && (
                      <span className={`text-[10px] font-black rounded-full w-4 h-4 flex items-center justify-center
                        ${activeCategory === cat ? "bg-[#00ff00] text-black" : "bg-black text-white"}`}>
                        {count}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </section>

          {/* Grid */}
          <section className="px-4 py-4 flex-1">
            <h2 className="font-bold text-lg mb-4 uppercase tracking-wider text-black bg-[#ff00ff] inline-block px-2 text-white border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform skew-x-6">
              {activeCategory}
            </h2>
            <AnimatePresence mode="wait">
              {!loaded ? (
                <div key="skeleton" className="grid grid-cols-2 gap-4">
                  {[0,1,2,3].map(i => <SkeletonCard key={i} />)}
                </div>
              ) : isEmpty ? (
                <motion.div key="empty"
                  initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                  className="flex flex-col items-center justify-center py-12 text-center">
                  <div className="text-4xl mb-3">
                    {activeCategory === "Completed" ? "🏆" : activeCategory === "Planned" ? "📋" : activeCategory === "Paused" ? "⏸️" : activeCategory === "Dropped" ? "🗑️" : "📺"}
                  </div>
                  <p className="font-bold text-sm text-gray-500 uppercase">No shows here yet</p>
                  <p className="text-xs text-gray-600 mt-1">Open a show and set it to <span className="text-accent font-bold">{activeCategory}</span></p>
                </motion.div>
              ) : (
                <motion.div key={activeCategory}
                  initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }}
                  transition={{ duration: 0.2 }}
                  className="grid grid-cols-2 gap-4">
                  {showsForCategory.map((show, i) => (
                    <motion.div
                      key={show.id}
                      initial={{ opacity: 0, y: 16 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: i * 0.07, duration: 0.3, ease: "easeOut" }}
                    >
                      <Link to={`/app/detail/${show.id}`} className="group flex flex-col">
                        <motion.div
                          whileTap={{ scale: 0.95 }}
                          transition={{ type: "spring", stiffness: 400, damping: 25 }}
                          className="relative w-full aspect-[2/3] rounded-lg border-2 border-black overflow-hidden shadow-[4px_4px_0_0_rgba(0,0,0,1)] bg-black"
                        >
                          <img src={show.image} alt={show.title} className="w-full h-full object-cover opacity-90 group-hover:opacity-100 transition-opacity" />
                          <div className="absolute top-2 right-2">
                            <span className={`text-[10px] font-black uppercase px-2 py-0.5 border-2 border-black
                              ${show.badge === "canon" ? "bg-[#00ff00] text-black" : "bg-[#00ffff] text-black"}`}>
                              {show.badge}
                            </span>
                          </div>
                          {statuses[show.id] === "Completed" && (
                            <div className="absolute bottom-2 left-2 bg-[#00ff00] text-black text-[9px] font-black px-1.5 py-0.5 rounded border border-black">✓ DONE</div>
                          )}
                        </motion.div>
                        <h3 className="mt-2 font-bold text-sm leading-tight line-clamp-2">{show.title}</h3>
                        <p className="text-[10px] text-gray-500 mt-0.5">{show.genres[0]} • {show.genres[1]}</p>
                      </Link>
                    </motion.div>
                  ))}
                </motion.div>
              )}
            </AnimatePresence>

            {/* Discover section when Watching is empty */}
            {activeCategory === "Watching" && isEmpty && uncategorized.length > 0 && (
              <div className="mt-6">
                <h3 className="font-bold text-sm mb-3 uppercase text-gray-500 tracking-wider">Discover</h3>
                <div className="grid grid-cols-2 gap-4">
                  {uncategorized.map(show => (
                    <Link key={show.id} to={`/app/detail/${show.id}`} className="group flex flex-col">
                      <div className="relative w-full aspect-[2/3] rounded-lg border-2 border-black overflow-hidden shadow-[4px_4px_0_0_rgba(0,0,0,1)] group-active:translate-x-[2px] group-active:translate-y-[2px] group-active:shadow-[2px_2px_0_0_rgba(0,0,0,1)] bg-black">
                        <img src={show.image} alt={show.title} className="w-full h-full object-cover opacity-70 group-hover:opacity-90 transition-opacity" />
                        <div className="absolute top-2 right-2">
                          <span className={`text-[10px] font-black uppercase px-2 py-0.5 border-2 border-black
                            ${show.badge === "canon" ? "bg-[#00ff00] text-black" : "bg-[#00ffff] text-black"}`}>
                            {show.badge}
                          </span>
                        </div>
                      </div>
                      <h3 className="mt-2 font-bold text-sm leading-tight line-clamp-2">{show.title}</h3>
                      <p className="text-[10px] text-gray-500 mt-0.5">{show.genres[0]} • {show.genres[1]}</p>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}

function HighlightText({ text, query }: { text: string; query?: string }) {
  if (!query) return <span>{text}</span>;
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx === -1) return <span>{text}</span>;
  return (
    <span>
      {text.slice(0, idx)}
      <mark className="bg-[#00ff00] text-black rounded px-0.5">{text.slice(idx, idx + query.length)}</mark>
      {text.slice(idx + query.length)}
    </span>
  );
}

function SearchRow({ show, status, onTap, highlight }: {
  show: typeof ALL_SHOWS[0]; status: string; onTap: () => void; highlight?: string;
}) {
  const statusColor: Record<string, string> = {
    Watching: "text-accent", Completed: "text-[#00ff00]", Planned: "text-blue-400",
    Paused: "text-yellow-400", Dropped: "text-red-400",
  };
  return (
    <Link to={`/app/detail/${show.id}`} onClick={onTap}
      className="flex items-center gap-3 p-2 rounded-xl border-2 border-black bg-white active:bg-gray-100 shadow-[2px_2px_0_0_rgba(0,0,0,1)] transition-all">
      <div className="w-12 h-16 rounded-lg border-2 border-black overflow-hidden shrink-0 bg-black">
        <img src={show.image} alt={show.title} className="w-full h-full object-cover" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-black text-sm text-black truncate">
          <HighlightText text={show.title} query={highlight} />
        </p>
        <p className="text-[10px] text-gray-500 mt-0.5">
          {show.genres.map((g, i) => (
            <span key={g}>{i > 0 && " • "}<HighlightText text={g} query={highlight} /></span>
          ))}
        </p>
        {status && status !== "none" && (
          <p className={`text-[10px] font-bold mt-1 ${statusColor[status] ?? "text-gray-400"}`}>{status}</p>
        )}
      </div>
      <span className={`text-[10px] font-black uppercase px-2 py-0.5 border-2 border-black shrink-0
        ${show.badge === "canon" ? "bg-[#00ff00] text-black" : "bg-[#00ffff] text-black"}`}>
        {show.badge}
      </span>
    </Link>
  );
}
