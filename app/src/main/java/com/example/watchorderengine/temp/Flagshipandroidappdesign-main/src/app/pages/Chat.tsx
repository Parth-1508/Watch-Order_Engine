import { useState, useRef, useEffect } from "react";
import { Mic, MicOff, Send, Bot, ShieldAlert } from "lucide-react";
import { useTheme } from "../context/ThemeContext";

interface Message {
  id: number;
  text: string;
  sender: "user" | "ai";
}

const AI_REPLIES = [
  "Interesting take! That arc really does change the tone of the whole series.",
  "Spoiler-free answer: the character you're asking about gets a lot more development later — worth watching.",
  "Based on where you are, I'd say skip the next two filler episodes and jump straight to episode 107.",
  "That fight scene is considered one of the best in the genre — the animators went all out.",
  "Great question. I can say without spoilers: your theory is on the right track.",
  "The music in that arc is doing a lot of heavy lifting. Composer really delivered.",
];

export function Chat() {
  const { theme } = useTheme();
  const isComic = theme === "comic";
  const [messages, setMessages] = useState<Message[]>([
    { id: 1, text: "Initializing Spoiler-Free Zone…", sender: "ai" },
    { id: 2, text: "Welcome to Lounge AI. I'm your companion for navigating watch orders, skipping filler, and discussing any series — spoiler-free. Where are you in your current watch?", sender: "ai" },
  ]);
  const [input, setInput] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const [micActive, setMicActive] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isTyping]);

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;
    setMessages(prev => [...prev, { id: Date.now(), text, sender: "user" }]);
    setInput("");
    setIsTyping(true);
    setTimeout(() => {
      setIsTyping(false);
      setMessages(prev => [...prev, {
        id: Date.now(),
        text: AI_REPLIES[Math.floor(Math.random() * AI_REPLIES.length)],
        sender: "ai",
      }]);
    }, 1200);
  };

  const toggleMic = () => {
    setMicActive(v => !v);
    if (!micActive) {
      setTimeout(() => {
        setMicActive(false);
        setInput("Which episodes can I skip in Naruto?");
      }, 2000);
    }
  };

  return (
    <div className="flex flex-col h-full bg-background relative">
      {/* Header */}
      <div className="px-4 py-3 bg-surface/80 backdrop-blur-md sticky top-0 z-20 border-b border-border flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-full ${isComic ? "bg-accent border-2 border-black" : "bg-surface-hover"}`}>
            <Bot size={20} className={isComic ? "text-white" : "text-accent"} />
          </div>
          <div>
            <h1 className="font-bold text-sm leading-tight">Lounge AI</h1>
            <p className="text-xs text-text-secondary">Spoiler-Safe Mode</p>
          </div>
        </div>
        <div className="flex items-center gap-1 text-xs font-semibold text-status-canon bg-status-canon/10 px-2 py-1 rounded-full">
          <ShieldAlert size={12} /> Spoiler-Free
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 p-4 overflow-y-auto flex flex-col gap-3 no-scrollbar pb-24">
        {messages.map(msg => {
          const isUser = msg.sender === "user";
          return (
            <div key={msg.id} className={`max-w-[80%] ${isUser ? "self-end" : "self-start"}`}>
              <div className={`px-4 py-2.5 text-sm leading-relaxed
                ${isUser
                  ? isComic ? "bg-accent text-white border-2 border-black shadow-[2px_2px_0_0_black]" : "bg-accent text-white rounded-t-2xl rounded-bl-2xl"
                  : isComic ? "bg-white text-black border-2 border-black shadow-[2px_2px_0_0_black]" : "bg-surface-hover text-text-primary rounded-t-2xl rounded-br-2xl border border-border"
                }`}>
                {msg.text}
              </div>
            </div>
          );
        })}

        {/* Typing indicator */}
        {isTyping && (
          <div className="self-start max-w-[80%]">
            <div className={`px-4 py-3 ${isComic ? "bg-white border-2 border-black shadow-[2px_2px_0_0_black]" : "bg-surface-hover rounded-t-2xl rounded-br-2xl border border-border"}`}>
              <div className="flex gap-1 items-center">
                {[0, 1, 2].map(i => (
                  <div key={i} className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: `${i * 0.15}s` }} />
                ))}
              </div>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="absolute bottom-0 left-0 right-0 p-4 bg-surface/90 backdrop-blur-lg border-t border-border">
        <div className="flex items-center gap-2">
          <button
            onClick={toggleMic}
            className={`p-3 transition-all rounded-full border-2 border-black
              ${micActive ? "bg-red-500 text-white animate-pulse" : isComic ? "bg-white text-black" : "bg-surface-hover text-text-secondary hover:text-text-primary"}`}
          >
            {micActive ? <MicOff size={20} /> : <Mic size={20} />}
          </button>

          <div className={`flex-1 flex items-center px-4 py-2 ${isComic ? "bg-white border-2 border-black" : "bg-surface-hover rounded-full"}`}>
            <input
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleSend()}
              placeholder={micActive ? "Listening…" : "Ask a spoiler-free question…"}
              className="bg-transparent border-none outline-none w-full text-sm text-text-primary placeholder:text-text-secondary"
            />
          </div>

          <button
            onClick={handleSend}
            disabled={!input.trim()}
            className={`p-3 transition-colors rounded-full disabled:opacity-40
              ${isComic ? "bg-accent border-2 border-black text-white" : "bg-accent text-white hover:bg-accent/90"}`}
          >
            <Send size={20} />
          </button>
        </div>
      </div>
    </div>
  );
}
