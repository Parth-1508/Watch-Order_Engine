import { useTheme } from "../context/ThemeContext";

type StatusType = "canon" | "filler" | "mixed";

export function StatusBadge({ type, className = "" }: { type: StatusType; className?: string }) {
  const { theme } = useTheme();
  
  let label = "";
  let colorVar = "";

  switch (type) {
    case "canon":
      label = "Canon";
      colorVar = "var(--status-canon)";
      break;
    case "filler":
      label = "Filler";
      colorVar = "var(--status-filler)";
      break;
    case "mixed":
      label = "Mixed";
      colorVar = "var(--status-mixed)";
      break;
  }

  const isComic = theme === "comic";

  return (
    <span 
      className={`
        inline-flex items-center justify-center px-2 py-0.5 text-xs font-bold uppercase tracking-wider
        ${isComic ? 'border-2 border-black shadow-[2px_2px_0_0_black] text-black' : 'rounded-full text-white'}
        ${className}
      `}
      style={{ backgroundColor: colorVar }}
    >
      {label}
    </span>
  );
}
