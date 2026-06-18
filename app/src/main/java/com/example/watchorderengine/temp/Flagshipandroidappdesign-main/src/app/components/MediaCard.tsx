import { useTheme } from "../context/ThemeContext";
import { Play, Plus } from "lucide-react";
import { ImageWithFallback } from "./figma/ImageWithFallback";

export interface MediaProps {
  id: string | number;
  title: string;
  image: string;
  progress?: number;
  total?: number;
  type?: string;
}

export function MediaCard({ id, title, image, progress, total, type }: MediaProps) {
  const { theme } = useTheme();
  const isComic = theme === "comic";

  const cardStyle = isComic 
    ? "theme-border bg-surface" 
    : "rounded-[var(--app-radius)] bg-surface-hover overflow-hidden glass-panel";

  const hasProgress = progress !== undefined && total !== undefined;
  const percentage = hasProgress ? Math.round((progress / total) * 100) : 0;

  return (
    <div className={`relative flex flex-col w-[140px] shrink-0 snap-start ${cardStyle} cursor-pointer group`}>
      <div className="relative w-full aspect-[2/3] overflow-hidden">
        <ImageWithFallback 
          src={image} 
          alt={title} 
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
        />
        {type && (
          <div className="absolute top-2 right-2 bg-black/60 backdrop-blur-sm text-white text-[10px] px-1.5 py-0.5 rounded font-medium">
            {type}
          </div>
        )}
      </div>

      <div className="p-2 flex flex-col flex-1 justify-between">
        <h3 className="text-sm font-semibold text-text-primary line-clamp-2 leading-tight mb-1">{title}</h3>
        
        {hasProgress ? (
          <div className="mt-auto space-y-1">
            <div className="flex justify-between text-[10px] text-text-secondary">
              <span>{progress} / {total} EP</span>
              <span>{percentage}%</span>
            </div>
            <div className={`h-1 w-full bg-border ${isComic ? 'border border-black' : 'rounded-full overflow-hidden'}`}>
              <div 
                className="h-full bg-accent transition-all duration-500" 
                style={{ width: `${percentage}%` }}
              />
            </div>
          </div>
        ) : (
          <div className="mt-auto flex justify-between items-center">
            <button className="text-accent hover:text-accent/80 p-1">
              <Plus size={16} />
            </button>
            <button className="bg-accent text-white p-1 rounded-full">
              <Play size={14} className="ml-0.5" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
