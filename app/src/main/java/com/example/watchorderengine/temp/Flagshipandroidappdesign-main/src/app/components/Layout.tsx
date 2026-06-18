import { Outlet, NavLink } from "react-router";
import { Home, Compass, GitMerge, User, Users } from "lucide-react";
import { useTheme } from "../context/ThemeContext";
import { motion } from "motion/react";

const NAV_ITEMS = [
  { to: "/app", icon: Home, label: "Home", end: true },
  { to: "/app/discovery", icon: Compass, label: "Discover" },
  { to: "/app/timeline/1", icon: GitMerge, label: "Graph" },
  { to: "/app/community", icon: Users, label: "Community" },
  { to: "/app/profile", icon: User, label: "Profile" },
];

export function Layout() {
  const { theme } = useTheme();

  const isComic = theme === "comic";

  return (
    <div className="flex justify-center items-center h-screen w-full bg-black/5 p-0 sm:p-4 md:p-8">
      <div className={`flex flex-col h-full w-full max-w-md bg-background text-text-primary overflow-hidden shadow-2xl theme-transition relative
        ${isComic ? "border-4 border-black" : "sm:rounded-[2rem] sm:border sm:border-border"}`}>

        {/* Main content */}
        <main className="flex-1 relative w-full h-full overflow-y-auto no-scrollbar">
          <Outlet />
        </main>

        {/* Bottom Nav */}
        <nav className={`h-16 border-t border-border flex items-center justify-around px-2 z-50 shrink-0 pb-safe
          ${isComic ? "bg-surface border-t-4 border-black" : "bg-surface/90 backdrop-blur-md"}`}>
          {NAV_ITEMS.map(({ to, icon: Icon, label, end }) => (
            <NavLink key={to} to={to} end={end}
              className={({ isActive }) =>
                `flex flex-col items-center justify-center w-full h-full gap-0.5 transition-colors relative
                ${isActive ? "text-accent" : "text-text-secondary"}`
              }
            >
              {({ isActive }) => (
                <>
                  {/* Active pill indicator */}
                  {isActive && (
                    <motion.div
                      layoutId="nav-pill"
                      className={`absolute top-0 left-1/2 -translate-x-1/2 h-0.5 w-8 rounded-full
                        ${isComic ? "bg-black" : "bg-accent"}`}
                      transition={{ type: "spring", stiffness: 500, damping: 35 }}
                    />
                  )}
                  <motion.div
                    animate={{ scale: isActive ? 1.15 : 1, y: isActive ? -1 : 0 }}
                    transition={{ type: "spring", stiffness: 400, damping: 25 }}
                  >
                    <Icon size={20} />
                  </motion.div>
                  <span className="text-[10px] font-medium tracking-wide">{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  );
}
