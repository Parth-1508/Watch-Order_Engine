import { useState, useEffect, useRef } from "react";
import { Link } from "react-router";
import { Settings, Shield, Zap, Target, Lock, Crown, Clock, Flame, XOctagon, Edit3, Palette, Check, X, Star, Trophy, BookOpen } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { BarChart, Bar, XAxis, PieChart, Pie, Cell, ResponsiveContainer } from "recharts";
import { toast } from "sonner";

// ── XP helpers ─────────────────────────────────────────────────────────────

const XP_PER_EPISODE = 25;
const XP_PER_LEVEL = 1000;

const ALL_SHOW_IDS = ["1", "2", "3", "4", "5"];
const ALL_EPISODE_KEYS = ALL_SHOW_IDS.map(id => `watched-eps-${id}`);

function countWatchedEpisodes(): number {
  return ALL_EPISODE_KEYS.reduce((total, key) => {
    try {
      const raw = localStorage.getItem(key);
      return total + (raw ? JSON.parse(raw).length : 0);
    } catch { return total; }
  }, 0);
}

function getXpStats() {
  const totalEps = countWatchedEpisodes();
  const rawXp = totalEps * XP_PER_EPISODE;
  const level = Math.floor(rawXp / XP_PER_LEVEL) + 1;
  const xpInLevel = rawXp % XP_PER_LEVEL;
  return { totalEps, rawXp, level, xpInLevel, xpPerLevel: XP_PER_LEVEL };
}

// ── Watchlist helpers ───────────────────────────────────────────────────────

function getWatchlistCounts() {
  const statuses = ALL_SHOW_IDS.map(id => localStorage.getItem(`watchlist-status-${id}`) ?? "none");
  return {
    completed: statuses.filter(s => s === "Completed").length,
    dropped: statuses.filter(s => s === "Dropped").length,
    watching: statuses.filter(s => s === "Watching").length,
    paused: statuses.filter(s => s === "Paused").length,
  };
}

// ── Activity feed ───────────────────────────────────────────────────────────

const SHOW_NAMES: Record<string, string> = {
  "1": "Naruto Special", "2": "Cyber City X",
  "3": "Fantasy Legends", "4": "Action Force", "5": "Neon Genesis: Redux",
};

function getActivity(): { text: string; time: string; icon: string }[] {
  const items: { text: string; time: string; icon: string }[] = [];
  ALL_SHOW_IDS.forEach(id => {
    const status = localStorage.getItem(`watchlist-status-${id}`);
    if (status && status !== "none") {
      const icon = status === "Completed" ? "🏆" : status === "Watching" ? "▶️" : status === "Planned" ? "📋" : status === "Paused" ? "⏸️" : "🗑️";
      items.push({ text: `Marked "${SHOW_NAMES[id]}" as ${status}`, time: "recently", icon });
    }
    try {
      const raw = localStorage.getItem(`watched-eps-${id}`);
      if (raw) {
        const count = JSON.parse(raw).length;
        if (count > 0) items.push({ text: `Watched ${count} episode${count > 1 ? "s" : ""} of ${SHOW_NAMES[id]}`, time: "recently", icon: "✅" });
      }
    } catch {}
  });
  return items.slice(0, 6);
}

// ── Badge definitions ───────────────────────────────────────────────────────

function getBadges(totalEps: number, completed: number, dropped: number, level: number) {
  return [
    {
      id: "otaku", label: "Otaku Rank", icon: Crown, color: "#00ff00",
      unlocked: level >= 1,
      condition: "Reach Level 1",
    },
    {
      id: "binger", label: "Binger", icon: Zap, color: "#ff00ff",
      unlocked: totalEps >= 5,
      condition: "Watch 5+ episodes",
    },
    {
      id: "completionist", label: "Completionist", icon: Target, color: "#00ffff",
      unlocked: completed >= 1,
      condition: "Complete 1 show",
    },
    {
      id: "avenger", label: "Avenger", icon: Shield, color: "#ff4500",
      unlocked: dropped >= 1,
      condition: "Drop a show",
    },
    {
      id: "veteran", label: "Veteran", icon: Star, color: "#facc15",
      unlocked: totalEps >= 20,
      condition: "Watch 20+ episodes",
    },
    {
      id: "scholar", label: "Scholar", icon: BookOpen, color: "#a78bfa",
      unlocked: level >= 3,
      condition: "Reach Level 3",
    },
  ];
}

const TITLES = ["Master Tracker", "Binge Warrior", "Canon Guardian", "Filler Slayer", "Arc Architect", "Lore Keeper"];
const AURA_COLORS = ["#ff00ff", "#00ffff", "#00ff00", "#ff4500", "#facc15", "#a78bfa"];

// ── Streak helpers ─────────────────────────────────────────────────────────

function getStreak(): { count: number; active: boolean } {
  try {
    const raw = localStorage.getItem("watch-streak");
    if (!raw) return { count: 0, active: false };
    const { count, lastDate } = JSON.parse(raw) as { count: number; lastDate: string };
    const today = new Date().toISOString().slice(0, 10);
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    if (lastDate === today) return { count, active: true };
    if (lastDate === yesterday) return { count, active: true };
    // Streak broken — reset
    localStorage.setItem("watch-streak", JSON.stringify({ count: 0, lastDate: today }));
    return { count: 0, active: false };
  } catch {
    return { count: 0, active: false };
  }
}

// ── Milestone toasts ───────────────────────────────────────────────────────

const MILESTONES = [
  { xp: 500, message: "🎉 500 XP! You're on fire, keep watching!" },
  { xp: 1000, message: "🚀 1,000 XP reached! Level up energy activated!" },
  { xp: 2000, message: "👑 2,000 XP! You're a true Binge Legend!" },
];

function checkMilestones(rawXp: number) {
  try {
    const shown: number[] = JSON.parse(localStorage.getItem("shown-milestones") ?? "[]");
    MILESTONES.forEach(m => {
      if (rawXp >= m.xp && !shown.includes(m.xp)) {
        toast(m.message, { duration: 5000 });
        shown.push(m.xp);
      }
    });
    localStorage.setItem("shown-milestones", JSON.stringify(shown));
  } catch {}
}

// ── Stats tab data ─────────────────────────────────────────────────────────

const WATCH_TIME_DATA = [
  { day: "Mon", hours: 1.5 },
  { day: "Tue", hours: 0 },
  { day: "Wed", hours: 2.3 },
  { day: "Thu", hours: 0.8 },
  { day: "Fri", hours: 3.1 },
  { day: "Sat", hours: 4.2 },
  { day: "Sun", hours: 1.0 },
];

const GENRE_DATA = [
  { name: "Action", value: 35, color: "#f97316" },
  { name: "Sci-Fi", value: 25, color: "#6366f1" },
  { name: "Fantasy", value: 20, color: "#facc15" },
  { name: "Drama", value: 15, color: "#34d399" },
  { name: "Other", value: 5, color: "#9ca3af" },
];

const COMPLETION_SHOWS = [
  { id: "1", initials: "NS" },
  { id: "2", initials: "CC" },
  { id: "3", initials: "FL" },
  { id: "4", initials: "AF" },
  { id: "5", initials: "NG" },
];

function getCompletionPercent(id: string): number {
  const status = localStorage.getItem(`watchlist-status-${id}`);
  if (status === "Completed") return 100;
  if (status === "Watching") return 50;
  return 0;
}

// ── Completion Ring (small SVG) ─────────────────────────────────────────────

function CompletionRing({ id, initials }: { id: string; initials: string }) {
  const pct = getCompletionPercent(id);
  const r = 22;
  const circ = 2 * Math.PI * r;
  const dash = (pct / 100) * circ;

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="relative w-14 h-14">
        <svg width="56" height="56" viewBox="0 0 56 56" className="-rotate-90" style={{ display: "block" }}>
          <circle cx="28" cy="28" r={r} fill="none" stroke="#333" strokeWidth="5" />
          <circle
            cx="28" cy="28" r={r}
            fill="none"
            stroke={pct === 100 ? "#00ff00" : pct > 0 ? "#ff00ff" : "#444"}
            strokeWidth="5"
            strokeDasharray={`${dash} ${circ - dash}`}
            strokeLinecap="round"
          />
        </svg>
        <div className="absolute inset-0 flex items-center justify-center text-[9px] font-black text-white">
          {initials}
        </div>
      </div>
      <span className="text-[9px] font-bold text-gray-400">{pct}%</span>
    </div>
  );
}

// ── Component ───────────────────────────────────────────────────────────────

export function Profile() {
  const [activeTab, setActiveTab] = useState<"profile" | "stats">("profile");
  const [xp, setXp] = useState(getXpStats);
  const [counts, setCounts] = useState(getWatchlistCounts);
  const [activity, setActivity] = useState(getActivity);
  const [username, setUsername] = useState(() => localStorage.getItem("profile-username") ?? "Alex_99");
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState(username);
  const [titleIndex, setTitleIndex] = useState(() => Number(localStorage.getItem("profile-title") ?? 0));
  const [editingTitle, setEditingTitle] = useState(false);
  const [auraColor, setAuraColor] = useState(() => localStorage.getItem("profile-aura") ?? "#ff00ff");
  const [newBadge, setNewBadge] = useState<string | null>(null);
  const [streak, setStreak] = useState(getStreak);
  const [pausedDismissed, setPausedDismissed] = useState(false);
  const prevBadgesRef = useRef<Set<string>>(new Set());
  const nameInputRef = useRef<HTMLInputElement>(null);

  const badges = getBadges(xp.totalEps, counts.completed, counts.dropped, xp.level);

  // Check milestones on mount
  useEffect(() => {
    checkMilestones(xp.rawXp);
  }, [xp.rawXp]);

  // Sync from localStorage on focus / watchlist-changed
  useEffect(() => {
    const sync = () => {
      const nextXp = getXpStats();
      const nextCounts = getWatchlistCounts();
      setXp(nextXp);
      setCounts(nextCounts);
      setActivity(getActivity());
      setStreak(getStreak());
      checkMilestones(nextXp.rawXp);

      // Check for newly unlocked badges
      const nextBadges = getBadges(nextXp.totalEps, nextCounts.completed, nextCounts.dropped, nextXp.level);
      nextBadges.forEach(b => {
        if (b.unlocked && !prevBadgesRef.current.has(b.id)) {
          setNewBadge(b.label);
          setTimeout(() => setNewBadge(null), 3000);
        }
      });
      prevBadgesRef.current = new Set(nextBadges.filter(b => b.unlocked).map(b => b.id));
    };

    // Seed initial known badges silently
    prevBadgesRef.current = new Set(badges.filter(b => b.unlocked).map(b => b.id));

    window.addEventListener("watchlist-changed", sync);
    window.addEventListener("focus", sync);
    return () => {
      window.removeEventListener("watchlist-changed", sync);
      window.removeEventListener("focus", sync);
    };
  }, []);

  const saveName = () => {
    const trimmed = nameInput.trim() || "Alex_99";
    setUsername(trimmed);
    localStorage.setItem("profile-username", trimmed);
    setEditingName(false);
  };

  const saveTitle = (i: number) => {
    setTitleIndex(i);
    localStorage.setItem("profile-title", String(i));
    setEditingTitle(false);
  };

  const saveAura = (color: string) => {
    setAuraColor(color);
    localStorage.setItem("profile-aura", color);
  };

  const totalHours = Math.floor(xp.totalEps * 0.4);
  const hasPaused = counts.paused > 0 && !pausedDismissed;

  return (
    <div className="flex flex-col h-full w-full bg-background pb-8 overflow-y-auto no-scrollbar font-sans">

      {/* Badge unlock toast */}
      <AnimatePresence>
        {newBadge && (
          <motion.div
            initial={{ y: -60, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: -60, opacity: 0 }}
            className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-[#00ff00] text-black font-black text-sm px-4 py-2 rounded-full border-2 border-black shadow-[4px_4px_0_0_rgba(0,0,0,1)] flex items-center gap-2"
          >
            <Trophy size={16} /> Badge Unlocked: {newBadge}!
          </motion.div>
        )}
      </AnimatePresence>

      {/* Header */}
      <header className="flex items-center justify-between p-4 sticky top-0 bg-background z-20 border-b-4 border-black">
        <h1 className="font-black text-2xl tracking-tighter uppercase italic drop-shadow-[2px_2px_0_rgba(0,0,0,1)] text-white" style={{ WebkitTextStroke: "1px black" }}>
          Player
        </h1>
        <Link to="/app/settings" className="w-10 h-10 rounded-full bg-white border-2 border-black flex items-center justify-center shadow-[2px_2px_0_0_rgba(0,0,0,1)] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-all text-black">
          <Settings size={20} />
        </Link>
      </header>

      {/* Player Card */}
      <section className="p-4">
        <div className="relative bg-white border-4 border-black rounded-xl p-4 shadow-[8px_8px_0_0_rgba(0,0,0,1)] overflow-hidden">
          <div className="absolute inset-0 opacity-10 pointer-events-none" style={{ backgroundImage: "radial-gradient(black 2px, transparent 2px)", backgroundSize: "10px 10px" }} />

          <div className="relative z-10 flex gap-4 items-center">
            {/* Avatar with aura */}
            <div className="relative w-20 h-20 rounded-full shrink-0" style={{ boxShadow: `0 0 20px ${auraColor}99, 0 0 40px ${auraColor}44` }}>
              <div className="w-full h-full rounded-full border-4 border-black overflow-hidden">
                <img src="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=200&h=200" alt="Avatar" className="w-full h-full object-cover" />
              </div>
            </div>

            <div className="flex-1 min-w-0">
              {/* Username row */}
              <div className="flex items-center gap-2 mb-1">
                {editingName ? (
                  <div className="flex items-center gap-1 flex-1">
                    <input
                      ref={nameInputRef}
                      value={nameInput}
                      onChange={e => setNameInput(e.target.value)}
                      onKeyDown={e => e.key === "Enter" && saveName()}
                      className="font-black text-xl uppercase tracking-tight text-black bg-gray-100 border-2 border-black rounded px-2 py-0.5 w-full focus:outline-none"
                      autoFocus
                    />
                    <button onClick={saveName} className="w-7 h-7 bg-[#00ff00] border-2 border-black rounded flex items-center justify-center shrink-0"><Check size={14} /></button>
                    <button onClick={() => { setEditingName(false); setNameInput(username); }} className="w-7 h-7 bg-gray-200 border-2 border-black rounded flex items-center justify-center shrink-0"><X size={14} /></button>
                  </div>
                ) : (
                  <>
                    <h2 className="font-black text-2xl uppercase tracking-tight text-black leading-none truncate">{username}</h2>
                    <button onClick={() => { setEditingName(true); setNameInput(username); }} className="text-gray-400 hover:text-black transition-colors shrink-0"><Edit3 size={14} /></button>
                  </>
                )}
                <div className="bg-[#ff00ff] text-white px-2 py-0.5 border-2 border-black font-black text-sm shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform rotate-2 shrink-0 ml-auto">
                  Lv. {xp.level}
                </div>
              </div>

              {/* Title */}
              <p className="text-xs font-bold text-gray-600 mb-2 uppercase tracking-wider">{TITLES[titleIndex]}</p>

              {/* Streak badge */}
              {streak.count > 0 && (
                <div className="inline-flex items-center gap-1 bg-[#ff4500] text-white px-2 py-0.5 border-2 border-black font-black text-xs shadow-[2px_2px_0_0_rgba(0,0,0,1)] rounded-full mb-2">
                  🔥 {streak.count} day streak
                </div>
              )}

              {/* XP Bar */}
              <div className="w-full h-4 bg-gray-200 border-2 border-black rounded-full overflow-hidden relative shadow-[inset_0_2px_4px_rgba(0,0,0,0.1)]">
                <motion.div
                  className="h-full border-r-2 border-black relative"
                  style={{ backgroundColor: "#00ff00" }}
                  initial={{ width: 0 }}
                  animate={{ width: `${(xp.xpInLevel / xp.xpPerLevel) * 100}%` }}
                  transition={{ duration: 0.8, ease: "easeOut" }}
                >
                  <div className="absolute top-0 left-0 right-0 h-1/2 bg-white/30" />
                </motion.div>
                <div className="absolute inset-0 flex items-center justify-center text-[8px] font-black tracking-widest text-black">
                  {xp.xpInLevel.toLocaleString()} / {xp.xpPerLevel.toLocaleString()} XP
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Tab Bar */}
      <div className="px-4 pb-2">
        <div className="flex bg-white border-4 border-black rounded-xl overflow-hidden shadow-[4px_4px_0_0_rgba(0,0,0,1)]">
          {(["profile", "stats"] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`flex-1 py-2.5 font-black uppercase tracking-wider text-sm transition-all
                ${activeTab === tab
                  ? "bg-black text-white"
                  : "bg-white text-black hover:bg-gray-100"}`}
            >
              {tab === "profile" ? "Profile" : "Stats"}
            </button>
          ))}
        </div>
      </div>

      {/* ── PROFILE TAB ─────────────────────────────────────────────────────── */}
      {activeTab === "profile" && (
        <>
          {/* Paused shows banner */}
          <AnimatePresence>
            {hasPaused && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="mx-4 mb-2 flex items-center gap-3 bg-[#facc15] border-4 border-black rounded-xl px-4 py-3 shadow-[4px_4px_0_0_rgba(0,0,0,1)]"
              >
                <span className="text-lg shrink-0">⏸️</span>
                <p className="font-bold text-black text-xs flex-1">You have paused shows — pick up where you left off!</p>
                <Link
                  to="/app"
                  className="shrink-0 bg-black text-white text-xs font-black px-3 py-1.5 rounded-lg border-2 border-black shadow-[2px_2px_0_0_rgba(255,255,255,0.3)] active:scale-95 transition-all"
                >
                  Resume
                </Link>
                <button
                  onClick={() => setPausedDismissed(true)}
                  className="shrink-0 text-black hover:opacity-60 transition-opacity"
                >
                  <X size={16} />
                </button>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Stats Dashboard */}
          <section className="px-4 py-2">
            <h3 className="font-black text-lg mb-3 uppercase tracking-wider text-black bg-[#00ffff] inline-block px-2 border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform -skew-x-6">
              Battle Stats
            </h3>
            <div className="grid grid-cols-3 gap-3">
              {[
                { icon: <Clock className="text-[#00ffff]" />, label: "Watch Time", value: `${totalHours}h` },
                { icon: <Flame className="text-[#ff00ff]" />, label: "Episodes", value: xp.totalEps.toLocaleString() },
                { icon: <XOctagon className="text-[#ff4500]" />, label: "Dropped", value: String(counts.dropped) },
              ].map((s, i) => (
                <motion.div key={s.label}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.08, duration: 0.3 }}
                  whileTap={{ scale: 0.94 }}
                >
                  <StatBox icon={s.icon} label={s.label} value={s.value} />
                </motion.div>
              ))}
            </div>
          </section>

          {/* Trophy Room */}
          <section className="px-4 py-4">
            <h3 className="font-black text-lg mb-4 uppercase tracking-wider text-black bg-[#00ff00] inline-block px-2 border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform skew-x-6">
              Trophy Room
            </h3>
            <div className="grid grid-cols-3 gap-4">
              {badges.map(badge => {
                const Icon = badge.icon;
                return (
                  <motion.div
                    key={badge.id}
                    className="flex flex-col items-center"
                    animate={badge.unlocked ? { scale: [1, 1.08, 1] } : {}}
                    transition={{ duration: 0.4 }}
                  >
                    <div className={`w-20 h-20 rounded-full border-4 flex items-center justify-center mb-3 relative transition-all duration-500
                      ${badge.unlocked
                        ? "border-black bg-white shadow-[2px_2px_0_0_rgba(0,0,0,1)]"
                        : "border-dashed border-gray-400 bg-gray-200 opacity-50"}`}
                      style={badge.unlocked ? { boxShadow: `0 0 16px ${badge.color}88, 2px 2px 0 0 rgba(0,0,0,1)` } : {}}>
                      <Icon size={32} style={{ color: badge.unlocked ? badge.color : "#9ca3af" }} />
                      {!badge.unlocked && (
                        <div className="absolute inset-0 rounded-full bg-black/10 flex items-center justify-center">
                          <Lock size={14} className="text-gray-500" />
                        </div>
                      )}
                      <div className={`absolute -bottom-2 text-[9px] font-black px-1.5 py-0.5 border-2 uppercase tracking-wider whitespace-nowrap
                        ${badge.unlocked ? "bg-white text-black border-black" : "bg-gray-300 text-gray-500 border-gray-400"}`}>
                        {badge.label}
                      </div>
                    </div>
                    <p className="text-[9px] text-gray-500 text-center mt-1 leading-tight">{badge.unlocked ? "✓ Unlocked" : badge.condition}</p>
                  </motion.div>
                );
              })}
            </div>
          </section>

          {/* Recent Activity */}
          <section className="px-4 py-2">
            <h3 className="font-black text-lg mb-3 uppercase tracking-wider text-black bg-[#ff00ff] inline-block px-2 text-white border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform -skew-x-6">
              Activity Log
            </h3>
            <div className="bg-white border-4 border-black rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] overflow-hidden">
              {activity.length === 0 ? (
                <div className="p-6 text-center text-gray-400 text-sm font-bold uppercase">
                  No activity yet — start watching!
                </div>
              ) : (
                activity.map((item, i) => (
                  <div key={i} className={`flex items-center gap-3 px-4 py-3 ${i < activity.length - 1 ? "border-b-2 border-black/10" : ""}`}>
                    <span className="text-xl shrink-0">{item.icon}</span>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-bold text-black truncate">{item.text}</p>
                      <p className="text-[10px] text-gray-400">{item.time}</p>
                    </div>
                    <span className="text-[10px] font-black text-[#00ff00] shrink-0">+{XP_PER_EPISODE} XP</span>
                  </div>
                ))
              )}
            </div>
          </section>

          {/* Customize */}
          <section className="px-4 py-2 mt-2">
            <h3 className="font-black text-lg mb-3 uppercase tracking-wider text-black bg-[#00ffff] inline-block px-2 text-black border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform skew-x-6">
              Customize
            </h3>
            <div className="bg-white border-4 border-black rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] overflow-hidden">

              {/* Aura Color */}
              <div className="p-4 border-b-2 border-black">
                <p className="font-black uppercase text-xs text-gray-500 mb-3 tracking-wider">Aura Color</p>
                <div className="flex gap-3 flex-wrap">
                  {AURA_COLORS.map(color => (
                    <button
                      key={color}
                      onClick={() => saveAura(color)}
                      className="relative w-10 h-10 rounded-full border-2 border-black transition-transform active:scale-90"
                      style={{
                        backgroundColor: color,
                        boxShadow: auraColor === color ? `0 0 12px ${color}, 0 0 0 3px black` : "2px 2px 0 0 rgba(0,0,0,1)",
                        transform: auraColor === color ? "scale(1.2)" : "scale(1)",
                      }}
                    >
                      {auraColor === color && (
                        <div className="absolute inset-0 flex items-center justify-center">
                          <Check size={16} className="text-black drop-shadow-md" strokeWidth={3} />
                        </div>
                      )}
                    </button>
                  ))}
                </div>
              </div>

              {/* Title picker — inline expandable */}
              <div className="p-4">
                <div className="flex items-center justify-between mb-3">
                  <p className="font-black uppercase text-xs text-gray-500 tracking-wider">Title Drop</p>
                  <button
                    onClick={() => setEditingTitle(v => !v)}
                    className="flex items-center gap-1.5 text-xs font-black uppercase bg-[#ff00ff] text-white px-3 py-1.5 rounded-full border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] active:translate-x-[1px] active:translate-y-[1px] active:shadow-none transition-all"
                  >
                    <Palette size={12} /> {TITLES[titleIndex]}
                  </button>
                </div>

                <AnimatePresence>
                  {editingTitle && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25 }}
                      className="overflow-hidden"
                    >
                      <div className="grid grid-cols-2 gap-2 pt-1">
                        {TITLES.map((t, i) => (
                          <button
                            key={t}
                            onClick={() => saveTitle(i)}
                            className={`px-3 py-2.5 rounded-lg border-2 border-black font-bold text-xs text-left transition-all active:scale-95
                              ${titleIndex === i
                                ? "bg-[#ff00ff] text-white shadow-[2px_2px_0_0_rgba(0,0,0,1)]"
                                : "bg-gray-100 text-black hover:bg-gray-200"}`}
                          >
                            {titleIndex === i && "✓ "}{t}
                          </button>
                        ))}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </div>
          </section>
        </>
      )}

      {/* ── STATS TAB ───────────────────────────────────────────────────────── */}
      {activeTab === "stats" && (
        <div className="px-4 py-2 flex flex-col gap-6">

          {/* Watch Time Bar Chart */}
          <section>
            <h3 className="font-black text-lg mb-3 uppercase tracking-wider text-white bg-black inline-block px-2 border-2 border-black shadow-[2px_2px_0_0_rgba(255,255,0,0.5)] transform -skew-x-6">
              Weekly Watch Time
            </h3>
            <div className="bg-[#111] border-4 border-black rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] p-4 overflow-hidden">
              <ResponsiveContainer width="100%" height={160}>
                <BarChart data={WATCH_TIME_DATA} margin={{ top: 8, right: 4, left: -24, bottom: 0 }}>
                  <XAxis
                    dataKey="day"
                    tick={{ fill: "#9ca3af", fontSize: 10, fontWeight: 700 }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <Bar dataKey="hours" fill="#ff00ff" radius={[4, 4, 0, 0]} maxBarSize={32} />
                </BarChart>
              </ResponsiveContainer>
              <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mt-1">Hours per day</p>
            </div>
          </section>

          {/* Genre Breakdown Donut */}
          <section>
            <h3 className="font-black text-lg mb-3 uppercase tracking-wider text-white bg-black inline-block px-2 border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform skew-x-6">
              Genre Breakdown
            </h3>
            <div className="bg-[#111] border-4 border-black rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] p-4 flex flex-col items-center gap-4">
              <PieChart width={180} height={180}>
                <Pie
                  data={GENRE_DATA}
                  cx={90}
                  cy={90}
                  innerRadius={50}
                  outerRadius={80}
                  dataKey="value"
                  strokeWidth={2}
                  stroke="#000"
                >
                  {GENRE_DATA.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
              </PieChart>
              <div className="grid grid-cols-2 gap-x-6 gap-y-2 w-full">
                {GENRE_DATA.map(g => (
                  <div key={g.name} className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-sm border border-black shrink-0" style={{ backgroundColor: g.color }} />
                    <span className="text-xs font-bold text-gray-300">{g.name}</span>
                    <span className="text-xs font-black text-white ml-auto">{g.value}%</span>
                  </div>
                ))}
              </div>
            </div>
          </section>

          {/* Completion Rate */}
          <section>
            <h3 className="font-black text-lg mb-3 uppercase tracking-wider text-white bg-black inline-block px-2 border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform -skew-x-6">
              Completion Rate
            </h3>
            <div className="bg-[#111] border-4 border-black rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] p-4">
              <div className="flex justify-around items-start">
                {COMPLETION_SHOWS.map(show => (
                  <CompletionRing key={show.id} id={show.id} initials={show.initials} />
                ))}
              </div>
              <div className="flex gap-4 justify-center mt-4">
                <div className="flex items-center gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-[#00ff00]" />
                  <span className="text-[10px] font-bold text-gray-400">Completed</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-[#ff00ff]" />
                  <span className="text-[10px] font-bold text-gray-400">Watching</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-[#444]" />
                  <span className="text-[10px] font-bold text-gray-400">Not started</span>
                </div>
              </div>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

function StatBox({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="bg-white border-2 border-black p-3 rounded-lg shadow-[2px_2px_0_0_rgba(0,0,0,1)] flex flex-col items-center justify-center text-center">
      <div className="mb-1">{icon}</div>
      <div className="font-black text-xl text-black leading-none mb-1">{value}</div>
      <div className="text-[9px] font-bold text-gray-500 uppercase tracking-wider leading-tight">{label}</div>
    </div>
  );
}
