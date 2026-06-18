import { Link } from "react-router";
import { motion } from "motion/react";

const DAG_NODES = [
  { id: 0, cx: "8%",  cy: "15%" },
  { id: 1, cx: "25%", cy: "8%"  },
  { id: 2, cx: "72%", cy: "12%" },
  { id: 3, cx: "90%", cy: "22%" },
  { id: 4, cx: "15%", cy: "48%" },
  { id: 5, cx: "42%", cy: "30%" },
  { id: 6, cx: "68%", cy: "40%" },
  { id: 7, cx: "88%", cy: "60%" },
  { id: 8, cx: "20%", cy: "78%" },
  { id: 9, cx: "55%", cy: "72%" },
];

const DAG_EDGES = [
  [0, 1], [1, 2], [2, 3], [1, 5], [3, 6],
  [4, 5], [5, 6], [6, 7], [4, 8], [8, 9], [9, 7], [5, 9],
];

const TITLE_WORDS = ["Watch", "Order", "Engine"];

const FEATURES = [
  {
    emoji: "🗺️",
    title: "Skill Tree",
    subtitle: "Map any series as a visual DAG timeline",
  },
  {
    emoji: "🃏",
    title: "Discovery Deck",
    subtitle: "Swipe to build your watchlist",
  },
  {
    emoji: "🏆",
    title: "Player Profile",
    subtitle: "Earn XP and badges as you watch",
  },
];

function DagBackground() {
  return (
    <svg
      className="absolute inset-0 w-full h-full pointer-events-none"
      aria-hidden="true"
    >
      {DAG_EDGES.map(([a, b], i) => {
        const nodeA = DAG_NODES[a];
        const nodeB = DAG_NODES[b];
        return (
          <motion.line
            key={i}
            x1={nodeA.cx}
            y1={nodeA.cy}
            x2={nodeB.cx}
            y2={nodeB.cy}
            stroke="currentColor"
            strokeWidth="1"
            className="text-accent"
            initial={{ opacity: 0 }}
            animate={{ opacity: [0.06, 0.18, 0.06] }}
            transition={{
              duration: 4 + (i % 3),
              repeat: Infinity,
              delay: i * 0.3,
              ease: "easeInOut",
            }}
          />
        );
      })}
      {DAG_NODES.map((node, i) => (
        <motion.circle
          key={node.id}
          cx={node.cx}
          cy={node.cy}
          r="4"
          className="fill-accent"
          initial={{ opacity: 0, scale: 0 }}
          animate={{ opacity: [0.3, 0.9, 0.3], scale: [0.8, 1.2, 0.8] }}
          transition={{
            duration: 3 + (i % 4),
            repeat: Infinity,
            delay: i * 0.25,
            ease: "easeInOut",
          }}
        />
      ))}
    </svg>
  );
}

export function Opening() {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen w-full bg-black text-white p-6 relative overflow-hidden">
      {/* Dark radial gradient wash */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-zinc-900 via-black to-black" />

      {/* Animated DAG background */}
      <DagBackground />

      {/* Skip link */}
      <motion.div
        className="absolute top-5 right-6 z-20"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 2.4, duration: 0.5 }}
      >
        <Link
          to="/login"
          className="text-xs text-white/40 hover:text-white/70 transition-colors tracking-widest uppercase"
        >
          Skip
        </Link>
      </motion.div>

      {/* Main content */}
      <div className="z-10 flex flex-col items-center justify-center text-center gap-8 w-full max-w-sm">

        {/* Logo */}
        <motion.div
          className="relative flex items-center justify-center"
          initial={{ opacity: 0, scale: 0.5 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.7, ease: [0.34, 1.56, 0.64, 1] }}
        >
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 22, repeat: Infinity, ease: "linear" }}
            className="w-36 h-36 rounded-full border-t-2 border-r-2 border-accent opacity-40 absolute"
          />
          <div className="w-24 h-24 rounded-full bg-surface border border-white/10 flex items-center justify-center shadow-[0_0_40px_rgba(229,9,20,0.2)]">
            <span className="font-black italic text-3xl tracking-tighter">WO</span>
          </div>
        </motion.div>

        {/* Title — word-by-word stagger */}
        <div className="space-y-2">
          <h1 className="text-4xl font-black italic tracking-tighter uppercase drop-shadow-xl flex flex-wrap justify-center gap-x-3">
            {TITLE_WORDS.map((word, i) => (
              <motion.span
                key={word}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.7 + i * 0.18, duration: 0.45, ease: "easeOut" }}
                className={i === TITLE_WORDS.length - 1 ? "text-accent" : ""}
              >
                {word}
              </motion.span>
            ))}
          </h1>

          <motion.p
            className="text-gray-400 text-sm max-w-[260px] mx-auto leading-relaxed"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 1.4, duration: 0.6 }}
          >
            The ultimate DAG-powered ecosystem for mapping and tracking your viewing timelines.
          </motion.p>
        </div>

        {/* Feature cards */}
        <motion.div
          className="w-full"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 1.7, duration: 0.5 }}
        >
          <div className="flex gap-3 overflow-x-auto pb-2 snap-x snap-mandatory scrollbar-none -mx-2 px-2">
            {FEATURES.map((f, i) => (
              <motion.div
                key={f.title}
                className="flex-none snap-center w-40 rounded-2xl border border-white/8 bg-white/4 backdrop-blur-sm p-4 flex flex-col gap-2 text-left"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 1.9 + i * 0.12, duration: 0.4, ease: "easeOut" }}
              >
                <span className="text-2xl">{f.emoji}</span>
                <p className="font-bold text-sm text-white leading-tight">{f.title}</p>
                <p className="text-xs text-white/45 leading-snug">{f.subtitle}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>

        {/* CTA button */}
        <motion.div
          className="w-full"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 2.2, duration: 0.5 }}
        >
          <Link
            to="/login"
            className="block w-full py-4 rounded-xl bg-accent text-white font-bold uppercase tracking-widest text-sm hover:bg-accent/90 active:scale-95 transition-all shadow-[0_0_24px_rgba(229,9,20,0.4)]"
          >
            Enter the Engine
          </Link>
        </motion.div>
      </div>
    </div>
  );
}
