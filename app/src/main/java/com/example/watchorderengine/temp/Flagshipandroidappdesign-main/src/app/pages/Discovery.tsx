import { useState } from "react";
import { Mic, ChevronDown, ShieldCheck, ChevronUp, X, Heart, Clock, ThumbsDown } from "lucide-react";
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer } from "recharts";
import { motion, useMotionValue, useTransform, AnimatePresence } from "motion/react";
import { toast } from "sonner";
import { setWatchlistStatus } from "./Detail";

// showId maps to the same IDs used in Home/Detail catalog
const DISCOVERY_DECK = [
  {
    id: 1, showId: "5",
    title: "NEON GENESIS: REDUX",
    tags: "Sci-Fi • Psychological • Mecha",
    desc: "Teenage pilots defend what remains of humanity inside colossal biomechanical titans — at great personal cost.",
    image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080",
    stats: [
      { subject: "Power", A: 130 }, { subject: "Speed", A: 95 }, { subject: "Intelligence", A: 140 },
      { subject: "Durability", A: 110 }, { subject: "Stamina", A: 80 }, { subject: "Magic", A: 60 }
    ]
  },
  {
    id: 2, showId: "2",
    title: "CYBER CITY X",
    tags: "Cyberpunk • Mystery • Thriller",
    desc: "A rogue cop and a sentient AI tear through a neon-drenched undercity hunting the conspiracy that built it.",
    image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080",
    stats: [
      { subject: "Power", A: 80 }, { subject: "Speed", A: 130 }, { subject: "Intelligence", A: 140 },
      { subject: "Durability", A: 70 }, { subject: "Stamina", A: 90 }, { subject: "Magic", A: 20 }
    ]
  },
  {
    id: 3, showId: "3",
    title: "FANTASY LEGENDS",
    tags: "Fantasy • Adventure • Epic",
    desc: "An exiled prince bonds 12 legendary beasts to reclaim his throne from the shadow that stole it.",
    image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080",
    stats: [
      { subject: "Power", A: 140 }, { subject: "Speed", A: 80 }, { subject: "Intelligence", A: 70 },
      { subject: "Durability", A: 110 }, { subject: "Stamina", A: 100 }, { subject: "Magic", A: 150 }
    ]
  },
  {
    id: 4, showId: "4",
    title: "ACTION FORCE",
    tags: "Military • Drama • Thriller",
    desc: "A decimated unit rebuilt from scratch — until someone inside starts leaking their every move.",
    image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080",
    stats: [
      { subject: "Power", A: 120 }, { subject: "Speed", A: 110 }, { subject: "Intelligence", A: 100 },
      { subject: "Durability", A: 130 }, { subject: "Stamina", A: 120 }, { subject: "Magic", A: 10 }
    ]
  },
  {
    id: 5, showId: "1",
    title: "NARUTO SPECIAL",
    tags: "Action • Ninja • Coming-of-age",
    desc: "A loud, overlooked ninja vows to become Hokage — and somehow, episode by episode, you start believing he will.",
    image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080",
    stats: [
      { subject: "Power", A: 110 }, { subject: "Speed", A: 105 }, { subject: "Intelligence", A: 70 },
      { subject: "Durability", A: 120 }, { subject: "Stamina", A: 150 }, { subject: "Magic", A: 130 }
    ]
  },
];

const ALL_GENRES = ["All", "Sci-Fi", "Action", "Cyberpunk", "Fantasy", "Military", "Psychological", "Thriller", "Ninja", "Mecha", "Drama", "Adventure"];

export function Discovery() {
  const [isLoungeOpen, setIsLoungeOpen] = useState(false);
  const [activePersona, setActivePersona] = useState(0);
  const [cards, setCards] = useState(DISCOVERY_DECK);
  const [activeGenre, setActiveGenre] = useState("All");

  const personas = ["Talk to a Jedi", "Talk to a Shinobi", "Talk to a Saiyan"];

  const filteredCards = activeGenre === "All"
    ? cards
    : cards.filter(c => c.tags.toLowerCase().includes(activeGenre.toLowerCase()));

  const handleDragEnd = (event: any, info: any, card: typeof DISCOVERY_DECK[0]) => {
    const swipeThreshold = 100;
    if (info.offset.x > swipeThreshold) {
      removeCard(card, "Watching");
    } else if (info.offset.x < -swipeThreshold) {
      removeCard(card, "Dropped");
    } else if (info.offset.y < -swipeThreshold) {
      removeCard(card, "Planned");
    } else if (info.offset.y > swipeThreshold) {
      removeCard(card, "Paused");
    }
  };

  const removeCard = (card: typeof DISCOVERY_DECK[0], status: string) => {
    setWatchlistStatus(card.showId, status);
    const emoji = status === "Watching" ? "👀" : status === "Planned" ? "📋" : status === "Paused" ? "⏸️" : "🗑️";
    toast(`${emoji} ${card.title} → ${status}`, { position: "bottom-center" });
    setCards(prev => prev.filter(c => c.id !== card.id));
  };

  return (
    <div className="relative h-full w-full bg-black overflow-hidden text-white flex flex-col">
      {/* Genre filter bar */}
      <div className="absolute top-0 left-0 w-full z-30 pt-3 pb-2 px-2 flex overflow-x-auto gap-2 no-scrollbar bg-gradient-to-b from-black/80 to-transparent pointer-events-auto">
        {ALL_GENRES.map(genre => (
          <motion.button
            key={genre}
            whileTap={{ scale: 0.9 }}
            transition={{ type: "spring", stiffness: 500, damping: 20 }}
            onClick={() => setActiveGenre(genre)}
            className={`whitespace-nowrap px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-wider border transition-all shrink-0
              ${activeGenre === genre
                ? "bg-white text-black border-white shadow-[0_0_8px_rgba(255,255,255,0.5)]"
                : "bg-white/10 text-white/70 border-white/20 hover:bg-white/20"}`}
          >
            {genre}
          </motion.button>
        ))}
      </div>

      <div className="flex-1 flex items-center justify-center">
      {filteredCards.length === 0 ? (
        <div className="text-center p-8 z-10 flex flex-col items-center">
          <div className="w-20 h-20 bg-white/10 rounded-full flex items-center justify-center mb-4">
            <Mic size={32} className="text-gray-400" />
          </div>
          <h2 className="text-2xl font-black italic tracking-tighter">
            {activeGenre === "All" ? "You're all caught up!" : `No ${activeGenre} shows left`}
          </h2>
          <p className="text-gray-400 mt-2">
            {activeGenre === "All" ? "Come back later for more." : "Try a different genre filter."}
          </p>
          <div className="flex gap-3 mt-6">
            {activeGenre !== "All" && (
              <button onClick={() => setActiveGenre("All")}
                className="px-6 py-2 rounded-full bg-white/10 border-2 border-white/20 text-sm font-bold uppercase tracking-wider hover:bg-white/20">
                Show All
              </button>
            )}
            <button
              onClick={() => { setCards(DISCOVERY_DECK); setActiveGenre("All"); toast("Deck reset!", { position: "bottom-center" }); }}
              className="px-6 py-2 rounded-full border-2 border-white/20 text-sm font-bold uppercase tracking-wider hover:bg-white/10"
            >
              Reset Deck
            </button>
          </div>
        </div>
      ) : (
        <AnimatePresence>
          {filteredCards.map((card, index) => {
            const isTop = index === filteredCards.length - 1;
            return (
              <motion.div
                key={card.id}
                className="absolute inset-0 w-full h-full"
                style={{ zIndex: index }}
                drag={isTop ? true : false}
                dragConstraints={{ left: 0, right: 0, top: 0, bottom: 0 }}
                dragElastic={0.8}
                whileDrag={{ scale: 1.02 }}
                onDragEnd={(e, info) => handleDragEnd(e, info, card)}
                initial={{ scale: 0.95, opacity: 0 }}
                animate={{ scale: isTop ? 1 : 0.95, opacity: 1 }}
                exit={{ scale: 0.9, opacity: 0, transition: { duration: 0.2 } }}
              >
                <img 
                  src={card.image} 
                  alt={card.title} 
                  className="w-full h-full object-cover opacity-90 pointer-events-none"
                />
                
                {/* Floating Action / Top Right UI */}
                <div className="absolute top-6 right-4 z-20 flex flex-col gap-4 items-center">
                  <button 
                    onClick={(e) => { e.stopPropagation(); setIsLoungeOpen(true); }}
                    className="w-12 h-12 rounded-full bg-surface/50 backdrop-blur-md border border-white/20 flex flex-col items-center justify-center text-accent hover:bg-surface transition-all animate-pulse shadow-[0_0_15px_rgba(229,9,20,0.5)]"
                  >
                    <Mic size={20} />
                    <span className="text-[8px] font-bold mt-0.5 text-white">AI</span>
                  </button>
                  <button
                    onClick={() => removeCard(card, "Planned")}
                    className="w-10 h-10 rounded-full bg-surface/50 backdrop-blur-md border border-white/20 flex items-center justify-center hover:bg-white/20 transition-colors"
                    title="Add to Planned"
                  >
                    <ChevronUp size={20} />
                  </button>
                </div>

                {/* Lore Overlay (Bottom) */}
                <div className="absolute bottom-0 left-0 w-full bg-gradient-to-t from-black via-black/80 to-transparent pt-32 pb-6 px-4 z-10 pointer-events-none">
                  <div className="mb-4">
                    <h2 className="text-3xl font-black italic tracking-tight drop-shadow-lg">{card.title}</h2>
                    <p className="text-sm text-gray-300 mb-2">{card.tags}</p>
                    <p className="text-xs text-gray-400 line-clamp-2 w-3/4 mb-3">{card.desc}</p>
                    
                    {/* Community Reviews */}
                    <div className="flex items-center gap-2 bg-white/10 backdrop-blur-sm border border-white/20 rounded-lg p-2 mb-2 w-3/4">
                      <div className="flex -space-x-2 shrink-0">
                        <img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?crop=faces&w=50&h=50" className="w-5 h-5 rounded-full border border-black" />
                        <img src="https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?crop=faces&w=50&h=50" className="w-5 h-5 rounded-full border border-black" />
                      </div>
                      <p className="text-[10px] text-gray-300 italic">"Absolute masterpiece, a must watch!" — <span className="font-bold text-accent">@otaku_master</span></p>
                    </div>
                  </div>

                  {/* RPG Radar Chart */}
                  <div className="bg-white/5 backdrop-blur-md border border-white/10 rounded-xl p-3 h-48 w-full flex items-center justify-center relative overflow-hidden">
                    <div className="absolute top-2 left-3 text-xs font-bold text-gray-400 tracking-wider">LORE STATS</div>
                    <div className="w-full h-full pt-4 pointer-events-none">
                      <ResponsiveContainer width="100%" height="100%">
                        <RadarChart id={`radar-${card.id}`} cx="50%" cy="50%" outerRadius="70%" data={card.stats}>
                          <PolarGrid key="grid" stroke="rgba(255,255,255,0.2)" />
                          <PolarAngleAxis key="axis" dataKey="subject" tick={{ fill: 'rgba(255,255,255,0.6)', fontSize: 9 }} />
                          <Radar key="radar" name="Stats" dataKey="A" stroke="#00ffff" strokeWidth={2} fill="#00ffff" fillOpacity={0.3} />
                        </RadarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                </div>

                {/* Direction Indicators overlay */}
                {isTop && (
                  <div className="absolute inset-0 pointer-events-none flex flex-col justify-between p-6 opacity-30 z-30 mix-blend-overlay">
                    <div className="text-center pt-8 font-black uppercase text-2xl tracking-widest text-white drop-shadow-md flex items-center justify-center gap-2">
                      <ChevronUp size={24} /> Planning
                    </div>
                    <div className="flex justify-between items-center w-full px-2 flex-1">
                      <div className="font-black uppercase text-2xl tracking-widest text-white drop-shadow-md flex items-center gap-2 rotate-[-90deg] -ml-8">
                         Skip
                      </div>
                      <div className="font-black uppercase text-2xl tracking-widest text-white drop-shadow-md flex items-center gap-2 rotate-[90deg] -mr-8">
                        Watching
                      </div>
                    </div>
                    <div className="text-center pb-24 font-black uppercase text-2xl tracking-widest text-white drop-shadow-md flex items-center justify-center gap-2">
                      Paused <ChevronDown size={24} />
                    </div>
                  </div>
                )}
              </motion.div>
            );
          })}
        </AnimatePresence>
      )}
      </div>

      {/* AI Voice Lounge Modal */}
      <div 
        className={`absolute inset-0 z-50 bg-black/95 backdrop-blur-xl transition-transform duration-500 ease-in-out flex flex-col ${isLoungeOpen ? 'translate-y-0' : 'translate-y-full'}`}
      >
        <div className="flex items-center justify-between p-4 border-b border-white/10">
          <button onClick={() => setIsLoungeOpen(false)} className="p-2 -ml-2 text-gray-400 hover:text-white">
            <ChevronDown size={24} />
          </button>
          <div className="flex items-center gap-1.5 px-3 py-1 bg-[#4ade80]/10 border border-[#4ade80]/30 rounded-full">
            <ShieldCheck size={14} className="text-[#4ade80]" />
            <span className="text-[10px] font-bold text-[#4ade80] uppercase tracking-wider">Spoiler-Safe</span>
          </div>
          <div className="w-8"></div>
        </div>

        <div className="flex overflow-x-auto gap-4 px-6 py-8 no-scrollbar snap-x">
          {personas.map((persona, idx) => (
            <button
              key={idx}
              onClick={() => setActivePersona(idx)}
              className={`snap-center shrink-0 px-6 py-2 rounded-full border transition-all ${
                activePersona === idx 
                  ? 'bg-white text-black border-white font-bold' 
                  : 'bg-transparent text-gray-400 border-gray-600 hover:border-gray-400'
              }`}
            >
              {persona}
            </button>
          ))}
        </div>

        <div className="flex-1 flex flex-col items-center justify-center relative">
          <p className="text-gray-400 mb-8 text-sm text-center px-8">
            "I've analyzed your watch history. Ask me anything up to Episode 12."
          </p>
          
          <div className="flex items-center justify-center gap-1.5 h-32 w-full max-w-[280px]">
            {[...Array(15)].map((_, i) => (
              <div 
                key={i} 
                className="w-3 rounded-full animate-pulse"
                style={{ 
                  height: `${Math.max(10, Math.random() * 100)}%`,
                  background: `linear-gradient(to top, #ff00ff, #00ffff, #00ff00)`,
                  animationDuration: `${0.5 + Math.random()}s`,
                  animationDelay: `${Math.random() * 0.5}s`
                }}
              />
            ))}
          </div>

          <div className="mt-16 w-20 h-20 rounded-full bg-red-600/20 flex items-center justify-center border border-red-500/50 hover:bg-red-600/40 transition-colors shadow-[0_0_30px_rgba(220,38,38,0.3)]">
            <Mic size={32} className="text-red-500" />
          </div>
          <p className="mt-4 text-xs text-gray-500 uppercase tracking-widest font-semibold">Tap to Speak</p>
        </div>
      </div>
    </div>
  );
}