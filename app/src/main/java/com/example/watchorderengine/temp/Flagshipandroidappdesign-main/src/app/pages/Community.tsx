import { useState } from "react";
import { MessageSquare, Heart, Share2, MoreHorizontal, Send } from "lucide-react";
import { toast } from "sonner";
import { motion, AnimatePresence } from "motion/react";

const INITIAL_POSTS = [
  {
    id: 1,
    user: { name: "Alex_99", avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=100&h=100" },
    content: "Just finished the Chunin Exams arc in Naruto. The Rock Lee vs Gaara fight was completely unhinged!! 🤯",
    show: "Naruto Special", likes: 124, comments: 42, time: "2h ago", liked: false,
  },
  {
    id: 2,
    user: { name: "OtakuQueen", avatar: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?crop=faces&fit=crop&w=100&h=100" },
    content: "Can we talk about how good the new Cyber City X opening is? The animation is smooth as butter.",
    show: "Cyber City X", likes: 89, comments: 12, time: "5h ago", liked: false,
  },
  {
    id: 3,
    user: { name: "MechaFanatic", avatar: "https://images.unsplash.com/photo-1599566150163-29194dcaad36?crop=faces&fit=crop&w=100&h=100" },
    content: "Unpopular opinion: Action Force S2 is better than S1. The character development is insane.",
    show: "Action Force", likes: 256, comments: 108, time: "1d ago", liked: false,
  },
];

export function Community() {
  const [activeFilter, setActiveFilter] = useState<"Latest" | "Following">("Latest");
  const [posts, setPosts] = useState(INITIAL_POSTS);
  const [draft, setDraft] = useState("");

  const submitPost = () => {
    const text = draft.trim();
    if (!text) return;
    const newPost = {
      id: Date.now(),
      user: {
        name: localStorage.getItem("profile-username") ?? "You",
        avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=100&h=100",
      },
      content: text,
      show: "General",
      likes: 0,
      comments: 0,
      time: "just now",
      liked: false,
    };
    setPosts(prev => [newPost, ...prev]);
    setDraft("");
    toast("Post shared! 🎉", { position: "bottom-center" });
  };

  const toggleLike = (id: number) => {
    setPosts(prev => prev.map(p =>
      p.id === id ? { ...p, liked: !p.liked, likes: p.liked ? p.likes - 1 : p.likes + 1 } : p
    ));
  };

  const filtered = activeFilter === "Following"
    ? posts.filter(p => p.user.name === (localStorage.getItem("profile-username") ?? "You") || p.user.name === "Alex_99")
    : posts;

  return (
    <div className="flex flex-col h-full w-full bg-background text-white pb-16 font-sans">
      <div className="sticky top-0 z-20 bg-background/90 backdrop-blur-md p-4 border-b border-white/10 flex justify-between items-center">
        <h1 className="text-xl font-bold">Community Lounge</h1>
        <div className="flex gap-2">
          {(["Latest", "Following"] as const).map(f => (
            <button key={f} onClick={() => setActiveFilter(f)}
              className={`px-3 py-1 rounded-full text-xs font-bold transition-colors
                ${activeFilter === f ? "bg-accent text-white" : "bg-surface text-gray-400 hover:text-white"}`}>
              {f}
            </button>
          ))}
        </div>
      </div>

      <div className="p-4 space-y-4 overflow-y-auto no-scrollbar">
        {/* Create Post */}
        <div className="bg-surface/50 border border-white/10 rounded-xl p-4 flex gap-3">
          <img src="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=100&h=100" alt="You" className="w-10 h-10 rounded-full border border-gray-600 shrink-0" />
          <div className="flex-1">
            <input
              type="text"
              value={draft}
              onChange={e => setDraft(e.target.value)}
              onKeyDown={e => e.key === "Enter" && submitPost()}
              placeholder="Share your thoughts on an episode..."
              className="w-full bg-transparent border-b border-white/20 pb-2 mb-2 text-sm focus:outline-none focus:border-accent placeholder-gray-500"
            />
            <div className="flex justify-end">
              <button
                onClick={submitPost}
                disabled={!draft.trim()}
                className="bg-accent text-white px-4 py-1.5 rounded-full text-xs font-bold flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed transition-opacity"
              >
                <Send size={12} /> Post
              </button>
            </div>
          </div>
        </div>

        {/* Feed */}
        {filtered.length === 0 ? (
          <div className="text-center py-12 text-gray-500 text-sm font-bold uppercase">
            No posts from people you follow yet
          </div>
        ) : filtered.map((post, i) => (
          <motion.div
            key={post.id}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06, duration: 0.28, ease: "easeOut" }}
            className="bg-surface/30 border border-white/5 rounded-xl p-4"
          >
            <div className="flex justify-between items-start mb-3">
              <div className="flex gap-2 items-center">
                <img src={post.user.avatar} alt={post.user.name} className="w-8 h-8 rounded-full border border-gray-600" />
                <div>
                  <h3 className="font-bold text-sm text-white">{post.user.name}</h3>
                  <p className="text-[10px] text-gray-500">{post.time} · {post.show}</p>
                </div>
              </div>
              <button onClick={() => toast("Reported", { position: "bottom-center" })} className="text-gray-500 hover:text-gray-300 transition-colors">
                <MoreHorizontal size={16} />
              </button>
            </div>

            <p className="text-sm text-gray-200 mb-4">{post.content}</p>

            <div className="flex gap-4 border-t border-white/5 pt-3">
              <motion.button
                whileTap={{ scale: 0.8 }}
                transition={{ type: "spring", stiffness: 500, damping: 20 }}
                onClick={() => toggleLike(post.id)}
                className={`flex items-center gap-1.5 transition-colors ${post.liked ? "text-accent" : "text-gray-400 hover:text-accent"}`}>
                <Heart size={16} fill={post.liked ? "currentColor" : "none"} />
                <span className="text-xs">{post.likes}</span>
              </motion.button>
              <button className="flex items-center gap-1.5 text-gray-400 hover:text-white transition-colors">
                <MessageSquare size={16} />
                <span className="text-xs">{post.comments}</span>
              </button>
              <button onClick={() => toast("Link copied!", { position: "bottom-center" })}
                className="flex items-center gap-1.5 text-gray-400 hover:text-white transition-colors ml-auto">
                <Share2 size={16} />
              </button>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}
