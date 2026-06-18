import { useState, useEffect, useRef } from "react";
import { Lock, Users, GitBranch, X, CheckCircle2, Clock, Film, AlertTriangle, ChevronRight, Search, Plus } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { toast } from "sonner";

type ArcFilter = "All" | "Canon" | "Filler" | "Mixed";

const BASE_NODES = [
  {
    id: 1, title: "Land of Waves", status: "completed", type: "main", pos: 0, isFiller: false,
    image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400",
    episodes: "Ep 1–19", duration: "~7h 55m", arc: "Part I — Arc 1",
    synopsis: "Naruto, Sasuke, and Sakura accompany Kakashi on a C-rank mission that escalates to an S-rank confrontation with Zabuza and Haku.",
    rating: 4.8, fillerRisk: "None", isCustom: false,
  },
  {
    id: 2, title: "Chunin Exams", status: "completed", type: "main", pos: 1, isFiller: false,
    image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400",
    episodes: "Ep 20–67", duration: "~19h 50m", arc: "Part I — Arc 2",
    synopsis: "The genin compete in the Chunin Selection Exams. Orochimaru appears, the Forbidden Jutsu is unsealed, and the invasion of Konoha begins.",
    rating: 4.9, fillerRisk: "Some filler (26–27)", isCustom: false,
  },
  {
    id: 3, title: "Search for Tsunade", status: "completed", type: "main", pos: 2, isFiller: false,
    image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400",
    episodes: "Ep 83–100", duration: "~7h 5m", arc: "Part I — Arc 4",
    synopsis: "Jiraiya and Naruto search for the legendary Sannin Tsunade to become the Fifth Hokage. Naruto masters the Rasengan.",
    rating: 4.7, fillerRisk: "Minor", isCustom: false,
  },
  {
    id: 4, title: "Curry of Life", status: "completed", type: "branch", pos: 3, isFiller: true,
    image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400",
    episodes: "Ep 162–167", duration: "~2h 30m", arc: "Part I — Filler",
    synopsis: "Team 7 investigates a missing ninja and stumbles upon the legendary Curry of Life with miraculous healing powers. Pure filler — safely skippable.",
    rating: 3.1, fillerRisk: "100% Filler — SKIP", isCustom: false,
  },
  {
    id: 5, title: "Sasuke Recovery", status: "locked", type: "main", pos: 4, isFiller: false,
    image: "https://images.unsplash.com/photo-1581833971358-2c8b550f87b3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400",
    episodes: "Ep 107–135", duration: "~11h 40m", arc: "Part I — Arc 5",
    synopsis: "Naruto and a team of rookies race to stop Sasuke from defecting to Orochimaru. Features the legendary Naruto vs. Sasuke rooftop clash.",
    rating: 4.9, fillerRisk: "None", isCustom: false,
  },
];

const FRIEND_PROGRESS = [true, true, true, true, false];

const ARC_FILTERS: ArcFilter[] = ["All", "Canon", "Filler", "Mixed"];

function loadCustomArcs() {
  try {
    const raw = localStorage.getItem("custom-arcs");
    if (!raw) return [];
    return JSON.parse(raw) as typeof BASE_NODES;
  } catch {
    return [];
  }
}

function saveCustomArcs(arcs: typeof BASE_NODES) {
  try {
    localStorage.setItem("custom-arcs", JSON.stringify(arcs));
  } catch {
    // ignore
  }
}

export function Timeline() {
  const [isCoOp, setIsCoOp] = useState(false);
  const [selectedNode, setSelectedNode] = useState<typeof BASE_NODES[0] | null>(null);
  const [watchedIds, setWatchedIds] = useState<Set<number>>(new Set([1, 2, 3, 4]));
  const [arcFilter, setArcFilter] = useState<ArcFilter>("All");
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [customArcs, setCustomArcs] = useState<typeof BASE_NODES>([]);
  const [addSheetOpen, setAddSheetOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newIsFiller, setNewIsFiller] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setCustomArcs(loadCustomArcs());
  }, []);

  useEffect(() => {
    if (searchOpen) {
      setTimeout(() => searchInputRef.current?.focus(), 50);
    } else {
      setSearchQuery("");
    }
  }, [searchOpen]);

  const NODES = [
    ...BASE_NODES,
    ...customArcs,
  ];

  const toggleWatched = (id: number) => {
    setWatchedIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  function nodeMatchesFilter(node: typeof BASE_NODES[0]) {
    if (arcFilter === "All" || arcFilter === "Mixed") return true;
    if (arcFilter === "Canon") return !node.isFiller;
    if (arcFilter === "Filler") return node.isFiller;
    return true;
  }

  function nodeMatchesSearch(node: typeof BASE_NODES[0]) {
    if (!searchQuery.trim()) return true;
    return node.title.toLowerCase().includes(searchQuery.toLowerCase());
  }

  function nodeIsDimmed(node: typeof BASE_NODES[0]) {
    return !nodeMatchesFilter(node) || !nodeMatchesSearch(node);
  }

  function handleAddArc() {
    if (!newTitle.trim()) return;
    const newId = Date.now();
    const newNode = {
      id: newId,
      title: newTitle.trim(),
      status: "completed",
      type: "main" as const,
      pos: NODES.length,
      isFiller: newIsFiller,
      image: "https://images.unsplash.com/photo-1578632767115-351597cf2477?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400",
      episodes: "—",
      duration: "—",
      arc: "Custom Arc",
      synopsis: "A custom arc added by you.",
      rating: 0,
      fillerRisk: newIsFiller ? "Filler" : "None",
      isCustom: true,
    };
    const updated = [...customArcs, newNode];
    setCustomArcs(updated);
    saveCustomArcs(updated);
    setWatchedIds(prev => new Set([...prev, newId]));
    setNewTitle("");
    setNewIsFiller(false);
    setAddSheetOpen(false);
    toast("✅ Arc added!", { position: "bottom-center" });
  }

  return (
    <div className="flex flex-col h-full w-full bg-[#0a0a0c] text-white relative">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-white/10 shrink-0 bg-[#0a0a0c]/90 backdrop-blur z-20">
        <AnimatePresence mode="wait" initial={false}>
          {searchOpen ? (
            <motion.div
              key="search"
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              className="flex items-center gap-2 flex-1 mr-3"
            >
              <Search size={16} className="text-[#00ffff] shrink-0" />
              <input
                ref={searchInputRef}
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder="Search arcs…"
                className="flex-1 bg-transparent text-white placeholder-gray-500 text-sm outline-none"
              />
              {searchQuery && (
                <button onClick={() => setSearchQuery("")} className="text-gray-500 hover:text-white">
                  <X size={14} />
                </button>
              )}
              <button
                onClick={() => setSearchOpen(false)}
                className="text-xs text-gray-400 hover:text-white font-medium"
              >
                Cancel
              </button>
            </motion.div>
          ) : (
            <motion.div
              key="title"
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 10 }}
              className="flex items-center gap-2"
            >
              <GitBranch size={20} className="text-[#00ffff]" />
              <h1 className="font-bold text-lg">Skill Tree</h1>
              <span className="text-xs text-gray-500 ml-1">{watchedIds.size}/{NODES.length} arcs</span>
            </motion.div>
          )}
        </AnimatePresence>

        <div className="flex items-center gap-2 shrink-0">
          {!searchOpen && (
            <button
              onClick={() => setSearchOpen(true)}
              className="w-8 h-8 flex items-center justify-center rounded-full bg-white/5 border border-white/10 hover:bg-white/10 transition-all"
            >
              <Search size={15} className="text-gray-400" />
            </button>
          )}
          <button
            onClick={() => setIsCoOp(!isCoOp)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-bold transition-all border
              ${isCoOp ? "bg-[#ff00ff]/20 text-[#ff00ff] border-[#ff00ff]/50" : "bg-white/5 text-gray-400 border-white/10 hover:bg-white/10"}
            `}
          >
            <Users size={14} />
            {isCoOp ? "Co-Op Active" : "Compare"}
          </button>
        </div>
      </div>

      {/* Filter Bar — single player only */}
      {!isCoOp && (
        <div className="shrink-0 px-4 py-2 border-b border-white/5 bg-[#0a0a0c]/90 z-10">
          <div className="flex gap-2 overflow-x-auto no-scrollbar">
            {ARC_FILTERS.map(f => (
              <button
                key={f}
                onClick={() => setArcFilter(f)}
                className={`shrink-0 px-3.5 py-1 rounded-full text-xs font-bold transition-all border
                  ${arcFilter === f
                    ? "bg-[#00ffff] text-black border-[#00ffff] shadow-[0_0_10px_rgba(0,255,255,0.4)]"
                    : "bg-white/5 text-gray-400 border-white/10 hover:bg-white/10"}
                `}
              >
                {f}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Main Graph */}
      <div className="flex-1 relative overflow-hidden">
        <div
          className="absolute inset-0 opacity-10 pointer-events-none"
          style={{ backgroundImage: "linear-gradient(#ffffff 1px, transparent 1px), linear-gradient(90deg, #ffffff 1px, transparent 1px)", backgroundSize: "20px 20px" }}
        />

        {isCoOp ? (
          <div className="flex h-full w-full relative">
            {/* YOU */}
            <div className="w-1/2 h-full border-r border-white/10 relative overflow-y-auto pb-24">
              <div className="sticky top-0 bg-black/80 py-2 text-center text-xs font-bold text-[#00ffff] border-b border-[#00ffff]/30 backdrop-blur z-10">YOU</div>
              <div className="relative pt-10 pb-20 flex flex-col items-center">
                <div className="absolute top-10 bottom-20 left-1/2 w-1 bg-gradient-to-b from-[#00ffff] to-gray-800 -translate-x-1/2 z-0" />
                {NODES.map((node) => (
                  <motion.div
                    key={node.id}
                    whileTap={{ scale: 0.95 }}
                    onClick={() => setSelectedNode(node)}
                    className="relative z-10 mb-12 flex flex-col items-center cursor-pointer"
                  >
                    <div className={`w-20 h-28 rounded-lg border-2 overflow-hidden shadow-lg transition-all
                      ${watchedIds.has(node.id) ? "border-[#00ffff] shadow-[0_0_15px_rgba(0,255,255,0.4)]" : "border-gray-700 opacity-40"}
                    `}>
                      <img src={node.image} className="w-full h-full object-cover" alt={node.title} />
                      {!watchedIds.has(node.id) && (
                        <div className="absolute inset-0 bg-black/60 flex items-center justify-center">
                          <Lock size={20} className="text-gray-400" />
                        </div>
                      )}
                    </div>
                    <span className="text-[10px] font-bold mt-2 text-center max-w-[80px]">{node.title}</span>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* ALEX */}
            <div className="w-1/2 h-full relative overflow-y-auto pb-24 bg-black/40">
              <div className="sticky top-0 bg-black/80 py-2 text-center text-xs font-bold text-[#ff00ff] border-b border-[#ff00ff]/30 backdrop-blur z-10">ALEX (Lv.42)</div>
              <div className="relative pt-10 pb-20 flex flex-col items-center">
                <div className="absolute top-10 bottom-20 left-1/2 w-1 bg-gradient-to-b from-[#ff00ff] to-gray-800 -translate-x-1/2 z-0" />
                {NODES.map((node, i) => (
                  <div key={`friend-${node.id}`} className="relative z-10 mb-12 flex flex-col items-center">
                    <div className={`w-20 h-28 rounded-lg border-2 overflow-hidden shadow-lg
                      ${FRIEND_PROGRESS[i] ? "border-[#ff00ff] shadow-[0_0_15px_rgba(255,0,255,0.4)]" : "border-gray-700 opacity-40"}
                    `}>
                      <img src={node.image} className="w-full h-full object-cover grayscale" alt={node.title} />
                    </div>
                  </div>
                ))}
              </div>
              {/* Spoiler Wall */}
              <div className="absolute top-[55%] left-0 w-full h-[45%] z-20 overflow-hidden backdrop-blur-md bg-black/80 border-t-4 border-yellow-400 flex flex-col items-center justify-start pt-8">
                <div className="absolute inset-0 opacity-20" style={{ backgroundImage: "repeating-linear-gradient(45deg, transparent, transparent 10px, #eab308 10px, #eab308 20px)" }} />
                <div className="relative z-10 bg-yellow-400 text-black px-4 py-1 rounded font-black text-xs tracking-widest uppercase flex items-center gap-2 mb-2 shadow-[0_0_20px_rgba(234,179,8,0.5)]">
                  <Lock size={14} /> Spoiler Wall
                </div>
                <p className="relative z-10 text-[10px] text-gray-300 font-bold px-4 text-center">Alex is ahead of you. Content blocked to prevent spoilers.</p>
              </div>
            </div>
          </div>
        ) : (
          /* Single Player */
          <div className="h-full w-full overflow-y-auto pb-24 relative px-8 pt-10">
            <div className="relative max-w-sm mx-auto" style={{ height: `${NODES.length * 180 + 80}px` }}>
              <div className="absolute top-4 bottom-0 left-1/2 w-1.5 bg-gray-800 -translate-x-1/2 rounded-full z-0" />
              <div
                className="absolute top-4 left-1/2 w-1.5 bg-[#00ffff] -translate-x-1/2 rounded-full z-0 shadow-[0_0_10px_#00ffff] transition-all duration-700"
                style={{ height: `${(watchedIds.size / NODES.length) * 100}%` }}
              />

              {NODES.map((node, i) => {
                const isBranch = node.type === "branch";
                const leftPos = isBranch ? (i % 2 === 0 ? "18%" : "82%") : "50%";
                const isWatched = watchedIds.has(node.id);
                const dimmed = nodeIsDimmed(node);

                return (
                  <motion.div
                    key={node.id}
                    initial={{ opacity: 0, scale: 0.8 }}
                    animate={{ opacity: dimmed ? 0.2 : 1, scale: 1 }}
                    transition={{ delay: i * 0.1, duration: 0.35, ease: "easeOut" }}
                    className="absolute z-10 flex flex-col items-center cursor-pointer"
                    style={{ top: `${i * 180}px`, left: leftPos, transform: "translateX(-50%)" }}
                    onClick={() => !dimmed && setSelectedNode(node)}
                    whileTap={dimmed ? undefined : { scale: 0.93 }}
                  >
                    {isBranch && (
                      <svg
                        className="absolute top-1/2 h-2 -z-10"
                        style={{
                          width: "80px",
                          left: i % 2 === 0 ? "100%" : "-80px",
                        }}
                      >
                        <line x1="0" y1="4" x2="80" y2="4" stroke={isWatched ? (node.isFiller ? "#ef4444" : "#00ffff") : "#374151"} strokeWidth="3" />
                      </svg>
                    )}

                    <div className={`w-24 h-32 rounded-lg border-2 overflow-hidden shadow-lg transition-all duration-300 relative
                      ${isWatched
                        ? node.isFiller
                          ? "border-[#ef4444] shadow-[0_0_20px_rgba(239,68,68,0.35)] scale-105"
                          : "border-[#00ffff] shadow-[0_0_20px_rgba(0,255,255,0.35)] scale-105"
                        : "border-gray-700 bg-gray-900 opacity-50 scale-95"}
                    `}>
                      <img src={node.image} className="w-full h-full object-cover" alt={node.title} />
                      {!isWatched && (
                        <div className="absolute inset-0 bg-black/60 flex items-center justify-center">
                          <Lock size={24} className="text-gray-500" />
                        </div>
                      )}
                      {isWatched && (
                        <div className="absolute top-1 right-1">
                          <CheckCircle2 size={16} className={node.isFiller ? "text-[#ef4444]" : "text-[#00ffff]"} />
                        </div>
                      )}
                      {/* Custom badge */}
                      {node.isCustom && (
                        <div className="absolute top-1 left-1 bg-purple-500 text-white text-[7px] font-black px-1 py-0.5 rounded uppercase tracking-wide">
                          custom
                        </div>
                      )}
                      {/* Tap hint */}
                      <div className="absolute bottom-1 right-1 opacity-60">
                        <ChevronRight size={12} className="text-white" />
                      </div>
                    </div>

                    <div className={`mt-2 px-2 py-1 rounded text-[10px] font-bold tracking-wider max-w-[100px] text-center
                      ${isWatched ? (node.isFiller ? "bg-[#ef4444] text-white" : "bg-[#00ffff] text-black") : "bg-gray-800 text-gray-400"}
                    `}>
                      {node.title}
                      {node.isFiller && <div className="text-[8px] opacity-80">FILLER</div>}
                    </div>
                  </motion.div>
                );
              })}
            </div>
          </div>
        )}
      </div>

      {/* FAB — Add Custom Arc (single player only) */}
      {!isCoOp && (
        <motion.button
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          whileTap={{ scale: 0.9 }}
          onClick={() => setAddSheetOpen(true)}
          className="absolute bottom-24 right-5 z-40 w-12 h-12 rounded-full bg-[#00ffff] text-black flex items-center justify-center shadow-[0_0_20px_rgba(0,255,255,0.5)] border-2 border-[#00ffff]"
        >
          <Plus size={22} strokeWidth={2.5} />
        </motion.button>
      )}

      {/* Node Detail Popup */}
      <AnimatePresence>
        {selectedNode && (
          <>
            <motion.div
              className="absolute inset-0 bg-black/60 z-30"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedNode(null)}
            />
            <motion.div
              className="absolute bottom-0 left-0 w-full z-40 bg-[#111114] rounded-t-2xl border-t border-white/10 overflow-hidden"
              initial={{ y: "100%" }}
              animate={{ y: 0 }}
              exit={{ y: "100%" }}
              transition={{ type: "spring", damping: 28, stiffness: 300 }}
            >
              {/* Handle */}
              <div className="flex justify-center pt-3 pb-1">
                <div className="w-10 h-1 rounded-full bg-white/20" />
              </div>

              {/* Backdrop image strip */}
              <div className="relative h-36 overflow-hidden">
                <img src={selectedNode.image} className="w-full h-full object-cover" alt={selectedNode.title} />
                <div className="absolute inset-0 bg-gradient-to-t from-[#111114] via-[#111114]/60 to-transparent" />
                <button
                  onClick={() => setSelectedNode(null)}
                  className="absolute top-3 right-3 w-8 h-8 rounded-full bg-black/60 border border-white/10 flex items-center justify-center backdrop-blur"
                >
                  <X size={16} className="text-white" />
                </button>
                {selectedNode.isFiller && (
                  <div className="absolute top-3 left-3 flex items-center gap-1 bg-[#ef4444] text-white text-[10px] font-black px-2 py-0.5 rounded">
                    <AlertTriangle size={10} /> FILLER
                  </div>
                )}
              </div>

              <div className="px-4 pb-6 -mt-4 relative z-10">
                <div className="flex items-start justify-between mb-1">
                  <div>
                    <h2 className="text-xl font-bold text-white">{selectedNode.title}</h2>
                    <p className="text-xs text-gray-400 font-medium">{selectedNode.arc}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <div className="flex items-center gap-1 text-yellow-400 text-xs font-bold">
                      ★ {selectedNode.rating}
                    </div>
                  </div>
                </div>

                {/* Meta row */}
                <div className="flex gap-3 mt-3 mb-4">
                  <div className="flex items-center gap-1.5 bg-white/5 rounded-lg px-3 py-2 flex-1">
                    <Film size={14} className="text-[#00ffff]" />
                    <div>
                      <p className="text-[10px] text-gray-500 font-medium">Episodes</p>
                      <p className="text-xs font-bold text-white">{selectedNode.episodes}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5 bg-white/5 rounded-lg px-3 py-2 flex-1">
                    <Clock size={14} className="text-[#00ffff]" />
                    <div>
                      <p className="text-[10px] text-gray-500 font-medium">Duration</p>
                      <p className="text-xs font-bold text-white">{selectedNode.duration}</p>
                    </div>
                  </div>
                  <div className={`flex items-center gap-1.5 rounded-lg px-3 py-2 flex-1 ${selectedNode.isFiller ? "bg-[#ef4444]/10" : "bg-white/5"}`}>
                    <AlertTriangle size={14} className={selectedNode.isFiller ? "text-[#ef4444]" : "text-gray-500"} />
                    <div>
                      <p className="text-[10px] text-gray-500 font-medium">Filler</p>
                      <p className={`text-[10px] font-bold ${selectedNode.isFiller ? "text-[#ef4444]" : "text-[#4ade80]"}`}>{selectedNode.fillerRisk}</p>
                    </div>
                  </div>
                </div>

                <p className="text-sm text-gray-300 leading-relaxed mb-5">{selectedNode.synopsis}</p>

                <button
                  onClick={() => {
                    toggleWatched(selectedNode.id);
                    setSelectedNode(null);
                  }}
                  className={`w-full py-3 rounded-xl font-bold text-sm transition-all active:scale-[0.98]
                    ${watchedIds.has(selectedNode.id)
                      ? "bg-white/10 text-gray-300 border border-white/10"
                      : selectedNode.isFiller
                        ? "bg-[#ef4444] text-white shadow-[0_0_20px_rgba(239,68,68,0.4)]"
                        : "bg-[#00ffff] text-black shadow-[0_0_20px_rgba(0,255,255,0.4)]"}
                  `}
                >
                  {watchedIds.has(selectedNode.id) ? "✓ Mark as Unwatched" : "Mark as Watched"}
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* Add Custom Arc Bottom Sheet */}
      <AnimatePresence>
        {addSheetOpen && (
          <>
            <motion.div
              className="absolute inset-0 bg-black/60 z-30"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setAddSheetOpen(false)}
            />
            <motion.div
              className="absolute bottom-0 left-0 w-full z-40 bg-[#111114] rounded-t-2xl border-t border-white/10"
              initial={{ y: "100%" }}
              animate={{ y: 0 }}
              exit={{ y: "100%" }}
              transition={{ type: "spring", damping: 28, stiffness: 300 }}
            >
              {/* Handle */}
              <div className="flex justify-center pt-3 pb-1">
                <div className="w-10 h-1 rounded-full bg-white/20" />
              </div>

              <div className="px-4 pt-2 pb-8">
                <div className="flex items-center justify-between mb-5">
                  <h2 className="text-lg font-bold text-white">Add Custom Arc</h2>
                  <button
                    onClick={() => setAddSheetOpen(false)}
                    className="w-8 h-8 rounded-full bg-white/5 border border-white/10 flex items-center justify-center"
                  >
                    <X size={15} className="text-gray-400" />
                  </button>
                </div>

                {/* Arc title input */}
                <label className="block text-xs text-gray-500 font-medium mb-1.5 uppercase tracking-wider">Arc Title</label>
                <input
                  value={newTitle}
                  onChange={e => setNewTitle(e.target.value)}
                  placeholder="e.g. Shippuden Finale"
                  className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-gray-600 outline-none focus:border-[#00ffff]/50 transition-colors mb-5"
                />

                {/* Canon / Filler toggle */}
                <label className="block text-xs text-gray-500 font-medium mb-2.5 uppercase tracking-wider">Type</label>
                <div className="flex gap-2 mb-6">
                  <button
                    onClick={() => setNewIsFiller(false)}
                    className={`flex-1 py-2.5 rounded-xl text-xs font-bold border transition-all
                      ${!newIsFiller
                        ? "bg-[#00ffff] text-black border-[#00ffff]"
                        : "bg-white/5 text-gray-400 border-white/10"}
                    `}
                  >
                    Canon
                  </button>
                  <button
                    onClick={() => setNewIsFiller(true)}
                    className={`flex-1 py-2.5 rounded-xl text-xs font-bold border transition-all
                      ${newIsFiller
                        ? "bg-[#ef4444] text-white border-[#ef4444]"
                        : "bg-white/5 text-gray-400 border-white/10"}
                    `}
                  >
                    Filler
                  </button>
                </div>

                <button
                  onClick={handleAddArc}
                  disabled={!newTitle.trim()}
                  className="w-full py-3 rounded-xl font-bold text-sm bg-[#00ffff] text-black shadow-[0_0_20px_rgba(0,255,255,0.35)] active:scale-[0.98] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Add Arc
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
