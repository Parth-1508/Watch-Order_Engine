import { Link, useNavigate } from "react-router";
import { useState } from "react";
import { useTheme } from "../context/ThemeContext";
import { ArrowLeft, Bell, Shield, Smartphone, Moon, Sun, Wand2, LogOut } from "lucide-react";
import * as Switch from "@radix-ui/react-switch";
import { toast } from "sonner";
import { motion } from "motion/react";

type Theme = "light" | "dark" | "comic" | "manga" | "retro" | "bollywood" | "naruto";

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: "light",     label: "Light Mode",  icon: Sun  },
  { value: "dark",      label: "Dark Mode",   icon: Moon },
  { value: "comic",     label: "Comic-Con",   icon: Wand2 },
  { value: "manga",     label: "Manga",       icon: Wand2 },
  { value: "retro",     label: "Retro",       icon: Wand2 },
  { value: "bollywood", label: "Bollywood",   icon: Wand2 },
  { value: "naruto",    label: "Naruto",      icon: Wand2 },
];

export function Settings() {
  const { theme, setTheme } = useTheme();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState(true);
  const [spoilerProtection, setSpoilerProtection] = useState(true);

  const handleLogOut = () => {
    // Clear session data
    Object.keys(localStorage).forEach(k => {
      if (k.startsWith("watched-eps-") || k.startsWith("watchlist-status-") || k.startsWith("profile-")) {
        localStorage.removeItem(k);
      }
    });
    toast("Logged out. See you next time!", { position: "bottom-center" });
    setTimeout(() => navigate("/"), 800);
  };

  const handleSync = () => {
    toast("Syncing devices… done!", { position: "bottom-center" });
  };

  return (
    <div className="flex flex-col h-full w-full bg-background pb-8 overflow-y-auto no-scrollbar font-sans">
      <header className="flex items-center gap-4 p-4 sticky top-0 bg-background z-20 border-b-4 border-black">
        <Link to="/app/profile" className="w-10 h-10 rounded-full bg-white border-2 border-black flex items-center justify-center shadow-[2px_2px_0_0_rgba(0,0,0,1)] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-all text-black">
          <ArrowLeft size={20} />
        </Link>
        <h1 className="font-black text-2xl tracking-tighter uppercase italic drop-shadow-[2px_2px_0_rgba(0,0,0,1)] text-white" style={{ WebkitTextStroke: "1px black" }}>
          Settings
        </h1>
      </header>

      <div className="p-4 space-y-6">
        {/* Appearance */}
        <section>
          <h2 className="font-black text-lg mb-3 uppercase tracking-wider bg-[#ff00ff] inline-block px-2 text-white border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform -skew-x-6">
            Appearance
          </h2>
          <div className="bg-white border-4 border-black rounded-xl p-4 shadow-[4px_4px_0_0_rgba(0,0,0,1)] space-y-4">
            {THEMES.map(({ value, label, icon: Icon }, i) => (
              <div key={value}>
                {i > 0 && <div className="w-full h-0.5 bg-black/10 mb-4" />}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Icon size={20} className="text-black" />
                    <span className="font-bold text-black uppercase">{label}</span>
                  </div>
                  <button
                    onClick={() => setTheme(value)}
                    className={`w-6 h-6 rounded-full border-2 border-black flex items-center justify-center transition-colors ${theme === value ? "bg-[#00ff00]" : "bg-gray-200"}`}
                  >
                    {theme === value && <div className="w-2 h-2 bg-black rounded-full" />}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Preferences */}
        <section>
          <h2 className="font-black text-lg mb-3 uppercase tracking-wider bg-[#00ffff] inline-block px-2 text-black border-2 border-black shadow-[2px_2px_0_0_rgba(0,0,0,1)] transform skew-x-6">
            Preferences
          </h2>
          <div className="bg-white border-4 border-black rounded-xl p-4 shadow-[4px_4px_0_0_rgba(0,0,0,1)] space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Bell size={20} className="text-black" />
                <div>
                  <p className="font-bold text-black uppercase text-sm">Notifications</p>
                  <p className="text-[10px] font-bold text-gray-500 uppercase">
                    {notifications ? "Enabled" : "Disabled"}
                  </p>
                </div>
              </div>
              <Switch.Root checked={notifications} onCheckedChange={setNotifications}
                className="w-12 h-6 bg-gray-200 rounded-full relative border-2 border-black data-[state=checked]:bg-[#00ff00] outline-none cursor-pointer shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
                <Switch.Thumb className="block w-4 h-4 bg-white rounded-full border-2 border-black transition-transform duration-100 translate-x-0.5 will-change-transform data-[state=checked]:translate-x-[22px]" />
              </Switch.Root>
            </div>
            <div className="w-full h-0.5 bg-black/10" />
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Shield size={20} className="text-black" />
                <div>
                  <p className="font-bold text-black uppercase text-sm">Spoiler Wall</p>
                  <p className="text-[10px] font-bold text-gray-500 uppercase">
                    {spoilerProtection ? "Hiding unseen episodes" : "Spoilers visible"}
                  </p>
                </div>
              </div>
              <Switch.Root checked={spoilerProtection} onCheckedChange={setSpoilerProtection}
                className="w-12 h-6 bg-gray-200 rounded-full relative border-2 border-black data-[state=checked]:bg-[#00ff00] outline-none cursor-pointer shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
                <Switch.Thumb className="block w-4 h-4 bg-white rounded-full border-2 border-black transition-transform duration-100 translate-x-0.5 will-change-transform data-[state=checked]:translate-x-[22px]" />
              </Switch.Root>
            </div>
          </div>
        </section>

        {/* Account */}
        <section>
          <div className="flex flex-col gap-3">
            <motion.button
              whileTap={{ scale: 0.97, x: 2, y: 2 }}
              transition={{ type: "spring", stiffness: 400, damping: 20 }}
              onClick={handleSync}
              className="w-full bg-white border-4 border-black p-4 rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] flex items-center gap-3"
            >
              <Smartphone size={20} className="text-black" />
              <span className="font-bold text-black uppercase">Sync Devices</span>
            </motion.button>
            <motion.button
              whileTap={{ scale: 0.97, x: 2, y: 2 }}
              transition={{ type: "spring", stiffness: 400, damping: 20 }}
              onClick={handleLogOut}
              className="w-full bg-[#ff4500] border-4 border-black p-4 rounded-xl shadow-[4px_4px_0_0_rgba(0,0,0,1)] flex items-center justify-center gap-3"
            >
              <LogOut size={20} className="text-white" />
              <span className="font-black text-white uppercase tracking-wider">Log Out</span>
            </motion.button>
          </div>
        </section>
      </div>
    </div>
  );
}
