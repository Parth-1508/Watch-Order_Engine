import { useState } from "react";
import { Link, useParams } from "react-router";
import { ChevronLeft, ChevronDown, Star, CheckCircle2, Circle, Play, GitBranch, AlertTriangle, Clock, Send, Share2 } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { toast } from "sonner";

interface EpisodeData {
  id: number; num: number; title: string; type: string;
  image: string; duration: string; synopsis: string;
}
interface ArcData { title: string; eps: string; type: string; color: string; desc: string; }
interface CharacterData { name: string; behavior: string; desc: string; img: string; color: string; }
interface ShowData {
  title: string; year: string; rating: number; rating_label: string; backdrop: string;
  episodes: Record<string, EpisodeData[]>;
  chronology: ArcData[];
  characters: CharacterData[];
}

const SHOW_CATALOG: Record<string, ShowData> = {
  "1": {
    title: "Naruto Special",
    year: "2002",
    rating: 4.9,
    rating_label: "TV-14",
    backdrop: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=800",
    characters: [
      { name: "Naruto Uzumaki", behavior: "Energetic, determined, never gives up.", desc: "A boisterous ninja who seeks recognition from his peers and harbors the Nine-Tailed Fox.", img: "https://images.unsplash.com/photo-1578632767115-351597cf2477?crop=faces&fit=crop&w=200&h=200", color: "#f97316" },
      { name: "Sasuke Uchiha", behavior: "Cold, highly skilled, revenge-driven.", desc: "Last surviving member of the Uchiha clan. Sole goal: kill his brother Itachi.", img: "https://images.unsplash.com/photo-1542451313056-b7c8e626645f?crop=faces&fit=crop&w=200&h=200", color: "#818cf8" },
      { name: "Sakura Haruno", behavior: "Intelligent, emotionally strong.", desc: "A kunoichi of Team 7 who becomes one of the greatest medical-nin.", img: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?crop=faces&fit=crop&w=200&h=200", color: "#f472b6" },
    ],
    episodes: {
      "1-100": [
        { id: 101, num: 1, title: "Enter: Naruto Uzumaki!", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Naruto pulls his biggest prank yet and is lectured by Iruka-sensei." },
        { id: 102, num: 2, title: "My Name is Konohamaru!", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Naruto befriends the Hokage's grandson who wants to surpass his grandfather." },
        { id: 103, num: 9, title: "Kakashi: Sharingan Warrior", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Team 7 faces Zabuza — and Kakashi reveals his Sharingan eye for the first time." },
        { id: 104, num: 26, title: "Special Report: Live from the Forest of Death!", type: "FILLER", image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "A recap filler episode. Safely skippable." },
        { id: 105, num: 27, title: "The Chunin Exam Stage 2", type: "CANON", image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "The second stage exam begins in the dangerous Forest of Death." },
        { id: 106, num: 67, title: "Late for the Show, But Ready to Go!", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "The preliminary rounds end and the main matches are announced." },
      ],
      "101-200": [
        { id: 107, num: 101, title: "Gotta See! Gotta Know! Kakashi's True Face!", type: "FILLER", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Team 7 tries to unmask Kakashi. Pure comedic filler." },
        { id: 108, num: 106, title: "The Battle Begins: Naruto vs. Sasuke", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "An unforgettable clash between Naruto and Sasuke at the hospital rooftop." },
        { id: 109, num: 135, title: "The Promise That Could Not Be Kept", type: "CANON", image: "https://images.unsplash.com/photo-1581833971358-2c8b550f87b3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Naruto fails to bring Sasuke back. The arc ends bittersweet." },
      ],
      "201-300": [
        { id: 110, num: 220, title: "Departure (Part I Finale)", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Naruto departs to train with Jiraiya — promises to bring Sasuke home." },
      ],
      "301-400": [
        { id: 111, num: 302, title: "Ghosts of the Past", type: "CANON", image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Naruto Shippuden begins. Two and a half years have passed." },
        { id: 112, num: 350, title: "Obito Uchiha", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "One of the most important backstory episodes. Do NOT skip." },
      ],
    },
    chronology: [
      { title: "Academy & Team Formation", eps: "1–5", type: "CANON", color: "#00ffff", desc: "Naruto graduates and Team 7 is formed under Kakashi." },
      { title: "Land of Waves", eps: "6–19", type: "CANON", color: "#00ffff", desc: "First real mission turns deadly. Zabuza and Haku arc." },
      { title: "Chunin Exams", eps: "20–67", type: "MIXED", color: "#facc15", desc: "Ep 26–27 are filler. Finals feature Gaara vs. Rock Lee." },
      { title: "Invasion of Konoha", eps: "68–80", type: "CANON", color: "#00ffff", desc: "Orochimaru reveals his plan. Third Hokage sacrifices himself." },
      { title: "Search for Tsunade", eps: "81–100", type: "CANON", color: "#00ffff", desc: "Naruto masters the Rasengan. Tsunade becomes 5th Hokage." },
      { title: "Sasuke Recovery", eps: "107–135", type: "CANON", color: "#00ffff", desc: "Naruto vs Sasuke at the Valley of the End. Iconic." },
      { title: "Part I Filler Block", eps: "136–219", type: "FILLER", color: "#ef4444", desc: "Entirely skippable. Jump to Ep 220." },
      { title: "Part I Finale", eps: "220", type: "CANON", color: "#00ffff", desc: "Naruto departs with Jiraiya. Shippuden begins next." },
    ],
  },

  "2": {
    title: "Cyber City X",
    year: "2021",
    rating: 4.5,
    rating_label: "TV-MA",
    backdrop: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=800",
    characters: [
      { name: "Kael Voss", behavior: "Stoic, analytical, deadly precise.", desc: "A rogue cop turned mercenary navigating the neon-drenched undercity for answers.", img: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?crop=faces&fit=crop&w=200&h=200", color: "#00ffff" },
      { name: "ARIA-7", behavior: "Curious, evolving, dangerously empathetic.", desc: "A rogue AI who has developed emotions and seeks autonomy in a world that fears her.", img: "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?crop=faces&fit=crop&w=200&h=200", color: "#a78bfa" },
    ],
    episodes: {
      "1-12": [
        { id: 201, num: 1, title: "Neon Descent", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "48m", synopsis: "Kael wakes in the gutter. The city owes him a debt — and he's come to collect." },
        { id: 202, num: 2, title: "Ghost Protocol", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "46m", synopsis: "ARIA-7 makes first contact. Her offer is impossible to refuse." },
        { id: 203, num: 3, title: "Red Sector", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "51m", synopsis: "A raid on the Syn-Corp server farm goes sideways. Casualties are high." },
        { id: 204, num: 4, title: "Phantom Frequency", type: "CANON", image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "44m", synopsis: "ARIA intercepts a black-market signal that reveals a conspiracy at the top." },
        { id: 205, num: 5, title: "The Awakening", type: "CANON", image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "53m", synopsis: "ARIA achieves full sentience. The corporation declares her a threat to humanity." },
        { id: 206, num: 6, title: "Zero Hour", type: "CANON", image: "https://images.unsplash.com/photo-1581833971358-2c8b550f87b3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "58m", synopsis: "Season finale. Kael and ARIA storm the executive tower. No one leaves the same." },
      ],
    },
    chronology: [
      { title: "Season 1 — Act I: The Fall", eps: "1–2", type: "CANON", color: "#00ffff", desc: "Kael descends into the undercity. ARIA-7 makes contact." },
      { title: "Season 1 — Act II: The Conspiracy", eps: "3–4", type: "CANON", color: "#00ffff", desc: "Syn-Corp's reach extends deeper than anyone suspected." },
      { title: "Season 1 — Act III: Zero Hour", eps: "5–6", type: "CANON", color: "#00ffff", desc: "ARIA achieves sentience. The final confrontation begins." },
    ],
  },

  "3": {
    title: "Fantasy Legends",
    year: "2019",
    rating: 4.3,
    rating_label: "TV-PG",
    backdrop: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=800",
    characters: [
      { name: "Aevyn Stormcrest", behavior: "Brave, impulsive, fiercely loyal.", desc: "An exiled prince on a quest to reclaim his throne by summoning the 12 legendary beasts.", img: "https://images.unsplash.com/photo-1552058544-f2b08422138a?crop=faces&fit=crop&w=200&h=200", color: "#facc15" },
      { name: "Lyria Dusk", behavior: "Calculating, ancient wisdom, mysterious.", desc: "A thousand-year-old sorceress who joins Aevyn's quest for reasons she keeps secret.", img: "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?crop=faces&fit=crop&w=200&h=200", color: "#c084fc" },
    ],
    episodes: {
      "1-10": [
        { id: 301, num: 1, title: "The Exile's Road", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "42m", synopsis: "Prince Aevyn is banished after his father's assassination. His journey begins alone." },
        { id: 302, num: 2, title: "The First Beast: Ironwing", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "40m", synopsis: "Aevyn encounters and bonds with Ironwing — a colossal armored griffin." },
        { id: 303, num: 3, title: "The Sorcerer of Dusk", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "43m", synopsis: "Lyria Dusk joins the quest, carrying secrets that stretch back a millennium." },
        { id: 304, num: 4, title: "Side Quest: The Merchant's Debt", type: "FILLER", image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "39m", synopsis: "A standalone episode following a village merchant. Enjoyable but non-canon." },
        { id: 305, num: 5, title: "The Iron Throne Speaks", type: "CANON", image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "45m", synopsis: "Aevyn receives a vision from the ancient throne — a warning about the 12th beast." },
      ],
      "11-20": [
        { id: 306, num: 11, title: "The Crimson Labyrinth", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "46m", synopsis: "The party enters the deadly Crimson Labyrinth to claim Beast #7." },
        { id: 307, num: 12, title: "Lyria's Secret", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "48m", synopsis: "The truth about Lyria's origin is revealed. Everything changes." },
      ],
    },
    chronology: [
      { title: "The Exile — Beasts 1–3", eps: "1–3", type: "CANON", color: "#facc15", desc: "Aevyn begins his quest and bonds the first three legendary beasts." },
      { title: "Merchant's Debt (Filler)", eps: "4", type: "FILLER", color: "#ef4444", desc: "Standalone filler. Safe to skip." },
      { title: "The Vision & Labyrinth", eps: "5–11", type: "CANON", color: "#facc15", desc: "The ancient throne warns Aevyn. The crimson labyrinth trial begins." },
      { title: "Lyria's Truth", eps: "12", type: "CANON", color: "#facc15", desc: "A lore-defining episode. Do not skip." },
    ],
  },

  "4": {
    title: "Action Force",
    year: "2023",
    rating: 4.6,
    rating_label: "TV-14",
    backdrop: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=800",
    characters: [
      { name: "Commander Rex", behavior: "Disciplined, tactical, leads from the front.", desc: "A decorated military commander who rebuilds an elite unit after a devastating ambush.", img: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?crop=faces&fit=crop&w=200&h=200", color: "#f97316" },
      { name: "Zara 'Ghost' Nile", behavior: "Silent, lethal, unpredictable.", desc: "The unit's infiltration specialist with a classified past and unmatched skills.", img: "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?crop=faces&fit=crop&w=200&h=200", color: "#34d399" },
    ],
    episodes: {
      "1-8": [
        { id: 401, num: 1, title: "Ambush at Sector 9", type: "CANON", image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "50m", synopsis: "Rex's unit is decimated in a surprise attack. He must rebuild from scratch." },
        { id: 402, num: 2, title: "Recruitment Drive", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "47m", synopsis: "Rex recruits four specialists with very different skill sets and attitudes." },
        { id: 403, num: 3, title: "Ghost Protocol", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "52m", synopsis: "Zara is introduced on a solo mission. Her methods raise red flags." },
        { id: 404, num: 4, title: "Field Exercise", type: "FILLER", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "44m", synopsis: "A team-building training montage. Light on plot but fun character moments." },
        { id: 405, num: 5, title: "The Mole", type: "CANON", image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "54m", synopsis: "Someone inside the unit is leaking intel. Paranoia grips the team." },
        { id: 406, num: 6, title: "Scorched Earth", type: "CANON", image: "https://images.unsplash.com/photo-1581833971358-2c8b550f87b3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "49m", synopsis: "The mole is exposed. A brutal firefight follows." },
        { id: 407, num: 7, title: "No Man's Land", type: "CANON", image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "55m", synopsis: "The unit is pinned behind enemy lines with no extraction window." },
        { id: 408, num: 8, title: "Extraction", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "62m", synopsis: "Season finale. Rex makes an impossible choice that defines the unit forever." },
      ],
    },
    chronology: [
      { title: "Formation Arc", eps: "1–3", type: "CANON", color: "#f97316", desc: "Rex assembles the team. Zara makes a striking first impression." },
      { title: "Field Exercise (Filler)", eps: "4", type: "FILLER", color: "#ef4444", desc: "Fun but skippable. No canon impact." },
      { title: "The Mole Arc", eps: "5–6", type: "CANON", color: "#f97316", desc: "Trust shatters. The team is tested from within." },
      { title: "No Man's Land Finale", eps: "7–8", type: "CANON", color: "#f97316", desc: "The season's climax. Every decision costs something." },
    ],
  },
};

SHOW_CATALOG["5"] = {
  title: "Neon Genesis: Redux",
  year: "2023",
  rating: 4.7,
  rating_label: "TV-MA",
  backdrop: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=800",
  characters: [
    { name: "Shinji Arata", behavior: "Reluctant, introspective, haunted by duty.", desc: "A teenage pilot forced to pilot a biomechanical titan to defend what remains of humanity from the Angels.", img: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?crop=faces&fit=crop&w=200&h=200", color: "#6366f1" },
    { name: "Commander Ikari", behavior: "Cold, calculating, secretive.", desc: "The ruthless director of NERV whose real motives are buried beneath layers of classified files.", img: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?crop=faces&fit=crop&w=200&h=200", color: "#dc2626" },
    { name: "Rei Ayanami", behavior: "Emotionless, obedient, mysterious.", desc: "Pilot of Unit 00 with an origin no one fully understands — not even herself.", img: "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?crop=faces&fit=crop&w=200&h=200", color: "#38bdf8" },
  ],
  episodes: {
    "1-13": [
      { id: 501, num: 1, title: "Angel Attack", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Shinji arrives in Neo Tokyo-3 and is immediately thrust into the cockpit of Evangelion Unit-01." },
      { id: 502, num: 2, title: "The Beast", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Shinji struggles to pilot Unit-01 but something primal awakens inside the machine." },
      { id: 503, num: 5, title: "Rei, Beyond her Heart", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Shinji visits Rei at home. The encounter raises more questions than it answers." },
      { id: 504, num: 9, title: "Both of You, Dance Like You Want to Win!", type: "CANON", image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Shinji and Asuka must synchronize perfectly to defeat a perfectly symmetric Angel. One of the series' most beloved episodes." },
      { id: 505, num: 13, title: "Lilliputian Hitcher", type: "CANON", image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "An Angel infiltrates NERV's computer systems. The battle is fought entirely inside a virtual space." },
    ],
    "14-26": [
      { id: 506, num: 16, title: "Splitting of the Breast", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Shinji is absorbed into Unit-01. What happens inside will change him permanently." },
      { id: 507, num: 19, title: "Introjection", type: "CANON", image: "https://images.unsplash.com/photo-1581833971358-2c8b550f87b3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Unit-01 goes berserk. The most terrifying and awe-inspiring display of Eva power in the series." },
      { id: 508, num: 24, title: "The Beginning and the End", type: "CANON", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "Kaworu arrives. His bond with Shinji and its devastating conclusion define the series." },
      { id: 509, num: 25, title: "Do you love me?", type: "CANON", image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "The world collapses inward. Instrumentality begins. Reality is questioned at every frame." },
      { id: 510, num: 26, title: "Take care of yourself.", type: "CANON", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=200", duration: "24m", synopsis: "The finale. Entirely internal. Controversial, beautiful, irreplaceable." },
    ],
  },
  chronology: [
    { title: "First Children — The Call", eps: "1–6", type: "CANON", color: "#6366f1", desc: "Shinji arrives, meets Rei, and fights the first Angels. The world-building is dense and intentional." },
    { title: "Asuka Arrives", eps: "7–13", type: "CANON", color: "#6366f1", desc: "The second pilot joins. Ep 9 (Dance) is a fan-favourite. Tension between all three pilots rises." },
    { title: "The Descent", eps: "14–24", type: "CANON", color: "#6366f1", desc: "Everything darkens. Secrets surface. The Angels grow more existential. Do not skip a single episode." },
    { title: "Instrumentality", eps: "25–26", type: "CANON", color: "#6366f1", desc: "The psychological finale. Watch End of Evangelion after for the alternate ending." },
  ],
};

const DEFAULT_SHOW = SHOW_CATALOG["1"];

function getStorageKey(showId: string) { return `watched-eps-${showId}`; }

function getWatched(showId: string): Set<number> {
  try {
    const raw = localStorage.getItem(getStorageKey(showId));
    return raw ? new Set(JSON.parse(raw)) : new Set();
  } catch { return new Set(); }
}

function saveWatched(showId: string, ids: Set<number>) {
  localStorage.setItem(getStorageKey(showId), JSON.stringify([...ids]));
}

function getRatings(showId: string): Record<number, number> {
  try { return JSON.parse(localStorage.getItem(`ep-ratings-${showId}`) ?? "{}"); }
  catch { return {}; }
}
function saveRatings(showId: string, ratings: Record<number, number>) {
  localStorage.setItem(`ep-ratings-${showId}`, JSON.stringify(ratings));
}

const RELATED: Record<string, { id: string; title: string; image: string; badge: string }[]> = {
  "1": [
    { id: "3", title: "Fantasy Legends", image: "https://images.unsplash.com/photo-1506260408121-e353d10b87c7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "mixed" },
    { id: "4", title: "Action Force",    image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  ],
  "2": [
    { id: "5", title: "Neon Genesis: Redux", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
    { id: "4", title: "Action Force",        image: "https://images.unsplash.com/photo-1616530940355-351fabd9524b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  ],
  "3": [
    { id: "1", title: "Naruto Special",   image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
    { id: "5", title: "Neon Genesis: Redux", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  ],
  "4": [
    { id: "2", title: "Cyber City X",    image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "mixed" },
    { id: "5", title: "Neon Genesis: Redux", image: "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  ],
  "5": [
    { id: "2", title: "Cyber City X",    image: "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "mixed" },
    { id: "1", title: "Naruto Special",  image: "https://images.unsplash.com/photo-1694276971921-ff8f103752eb?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=400", badge: "canon" },
  ],
};

export function getWatchlistStatus(showId: string): string {
  return localStorage.getItem(`watchlist-status-${showId}`) ?? "none";
}

export function setWatchlistStatus(showId: string, status: string) {
  localStorage.setItem(`watchlist-status-${showId}`, status);
  window.dispatchEvent(new Event("watchlist-changed"));
}

export function Detail() {
  const { id = "1" } = useParams();
  const show: ShowData = SHOW_CATALOG[id] ?? DEFAULT_SHOW;

  const chunks = Object.keys(show.episodes);
  const [activeTab, setActiveTab] = useState("episodes");
  const [activeChunk, setActiveChunk] = useState(chunks[0]);
  const [watchedIds, setWatchedIds] = useState<Set<number>>(() => getWatched(id));
  const [expandedEp, setExpandedEp] = useState<number | null>(null);
  const [chatMessages, setChatMessages] = useState([
    { user: false, text: `Welcome to the ${show.title} fan chat! Ask me anything. 🎬` },
  ]);
  const [chatInput, setChatInput] = useState("");
  const [watchlistLabel, setWatchlistLabel] = useState(() => {
    const saved = getWatchlistStatus(id);
    return saved !== "none" ? saved : "Add to Planned";
  });
  const [showWatchlistMenu, setShowWatchlistMenu] = useState(false);
  const [ratings, setRatings] = useState<Record<number, number>>(() => getRatings(id));
  const [ratingEp, setRatingEp] = useState<number | null>(null);

  const rateEpisode = (epId: number, stars: number) => {
    const next = { ...ratings, [epId]: stars };
    setRatings(next);
    saveRatings(id, next);
    setRatingEp(null);
    toast(`Rated ${stars}★`, { position: "bottom-center" });
  };

  const handleShare = () => {
    const text = `Watching ${show.title} — ${progress}% through. Check it out on Watch Order Engine!`;
    if (navigator.share) {
      navigator.share({ title: show.title, text }).catch(() => {});
    } else {
      navigator.clipboard?.writeText(text);
      toast("Copied to clipboard!", { position: "bottom-center" });
    }
  };

  const related = RELATED[id] ?? [];

  const allEps = Object.values(show.episodes).flat();
  const episodes = show.episodes[activeChunk] ?? [];
  const totalEps = allEps.length;
  const watchedCount = allEps.filter(e => watchedIds.has(e.id)).length;
  const progress = Math.round((watchedCount / totalEps) * 100);

  const applyWatchlistLabel = (next: Set<number>, label?: string) => {
    const allWatched = allEps.every(e => next.has(e.id));
    const newLabel = allWatched ? "Completed" : (label ?? watchlistLabel);
    setWatchlistLabel(newLabel);
    setWatchlistStatus(id, newLabel);
  };

  const toggleWatched = (epId: number) => {
    setWatchedIds(prev => {
      const next = new Set(prev);
      next.has(epId) ? next.delete(epId) : next.add(epId);
      saveWatched(id, next);
      applyWatchlistLabel(next);
      return next;
    });
  };

  const markAllInChunk = () => {
    setWatchedIds(prev => {
      const next = new Set(prev);
      episodes.forEach(e => next.add(e.id));
      saveWatched(id, next);
      applyWatchlistLabel(next);
      return next;
    });
  };

  const sendChat = () => {
    const text = chatInput.trim();
    if (!text) return;
    setChatMessages(prev => [...prev, { user: true, text }]);
    setChatInput("");
    const replies = [
      `${show.title} is one of the greats for sure.`,
      "Great question! The lore runs super deep on this one.",
      "That episode has one of the best twists in the whole series.",
      "Skip the filler and stick to the canon arcs — trust me.",
    ];
    setTimeout(() => {
      setChatMessages(prev => [...prev, { user: false, text: replies[Math.floor(Math.random() * replies.length)] }]);
    }, 900);
  };

  const watchlistOptions = ["Watching", "Planned", "Completed", "Paused", "Dropped"];

  return (
    <div className="flex flex-col min-h-full w-full bg-background text-white relative">
      {/* Top Nav */}
      <div className="absolute top-0 left-0 w-full z-20 flex justify-between items-center p-4">
        <Link to="/app" className="w-10 h-10 rounded-full bg-black/50 backdrop-blur-md flex items-center justify-center border border-white/10 text-white">
          <ChevronLeft size={24} />
        </Link>
        <button onClick={handleShare}
          className="w-10 h-10 rounded-full bg-black/50 backdrop-blur-md flex items-center justify-center border border-white/10 text-white hover:bg-black/70 transition-colors">
          <Share2 size={18} />
        </button>
      </div>

      {/* Backdrop with progress ring */}
      <div className="relative w-full h-[40vh] shrink-0">
        <img src={show.backdrop} className="w-full h-full object-cover" alt="Backdrop" />
        <div className="absolute inset-0 bg-gradient-to-t from-background via-background/80 to-transparent" />

        {/* Progress ring — bottom-right of backdrop */}
        {progress > 0 && (
          <div className="absolute bottom-24 right-4 w-14 h-14">
            <svg className="w-full h-full -rotate-90" viewBox="0 0 56 56">
              <circle cx="28" cy="28" r="24" fill="none" stroke="rgba(255,255,255,0.15)" strokeWidth="4" />
              <motion.circle
                cx="28" cy="28" r="24"
                fill="none"
                stroke={progress === 100 ? "#00ff00" : "var(--accent, #e50914)"}
                strokeWidth="4"
                strokeLinecap="round"
                strokeDasharray={`${2 * Math.PI * 24}`}
                initial={{ strokeDashoffset: 2 * Math.PI * 24 }}
                animate={{ strokeDashoffset: 2 * Math.PI * 24 * (1 - progress / 100) }}
                transition={{ duration: 1, ease: "easeOut" }}
              />
            </svg>
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="text-[10px] font-black text-white">{progress}%</span>
            </div>
          </div>
        )}
      </div>

      {/* Header Info */}
      <div className="px-4 -mt-20 relative z-10 shrink-0">
        <h1 className="text-3xl font-bold tracking-tight mb-1 text-white drop-shadow-lg">{show.title}</h1>
        <div className="flex items-center text-sm text-gray-300 gap-3 mb-3">
          <span>{show.year}</span>
          <span className="flex items-center gap-1 text-yellow-400"><Star size={14} fill="currentColor" /> {show.rating}</span>
          <span className="border border-white/20 rounded px-1.5 py-0.5 text-[10px]">{show.rating_label}</span>
          <span className="text-[10px] text-gray-400">{watchedCount}/{totalEps} eps</span>
        </div>

        <div className="w-full h-1.5 bg-white/10 rounded-full mb-3 overflow-hidden">
          <motion.div
            className="h-full bg-accent rounded-full"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          />
        </div>

        <div className="relative">
          <button
            onClick={() => setShowWatchlistMenu(v => !v)}
            className="w-full bg-surface hover:bg-surface-hover transition-colors border border-white/10 rounded-lg py-3 px-4 flex items-center justify-between"
          >
            <span className="font-semibold text-white">{watchlistLabel}</span>
            <ChevronDown size={20} className={`text-gray-400 transition-transform ${showWatchlistMenu ? "rotate-180" : ""}`} />
          </button>
          <AnimatePresence>
            {showWatchlistMenu && (
              <motion.div
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                className="absolute top-full left-0 w-full mt-1 bg-[#1a1a1f] border border-white/10 rounded-lg overflow-hidden z-30 shadow-xl"
              >
                {watchlistOptions.map(opt => (
                  <button key={opt} onClick={() => { setWatchlistLabel(opt); setWatchlistStatus(id, opt); setShowWatchlistMenu(false); }}
                    className={`w-full text-left px-4 py-3 text-sm transition-colors hover:bg-white/5 ${watchlistLabel === opt ? "text-accent font-bold" : "text-gray-300"}`}>
                    {opt}
                  </button>
                ))}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex px-4 mt-6 border-b border-white/10 shrink-0">
        {["episodes", "characters", "chronology"].map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            className={`pb-3 px-2 text-sm font-medium mr-6 transition-colors relative capitalize ${activeTab === tab ? "text-white" : "text-gray-500 hover:text-gray-300"}`}>
            {tab}
            {activeTab === tab && <motion.div layoutId={`tab-ul-${id}`} className="absolute bottom-0 left-0 w-full h-0.5 bg-accent" />}
          </button>
        ))}
      </div>

      {/* EPISODES */}
      {activeTab === "episodes" && (
        <div className="flex flex-col">
          <div className="flex items-center justify-between px-4 pt-3 pb-1 shrink-0">
            <div className="flex overflow-x-auto gap-2 no-scrollbar">
              {chunks.map(chunk => (
                <button key={chunk} onClick={() => { setActiveChunk(chunk); setExpandedEp(null); }}
                  className={`whitespace-nowrap px-4 py-1.5 rounded-full text-xs font-semibold transition-colors
                    ${activeChunk === chunk ? "bg-white text-black" : "bg-surface text-gray-300 border border-white/10 hover:bg-surface-hover"}`}>
                  [{chunk}]
                </button>
              ))}
            </div>
            <button onClick={markAllInChunk} className="text-[10px] text-accent font-bold whitespace-nowrap ml-2 hover:opacity-80">
              Mark all ✓
            </button>
          </div>

          <div className="px-4 pb-4 no-scrollbar pt-2">
            <AnimatePresence mode="wait">
              <motion.div key={activeChunk} initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.2 }} className="space-y-2">
                {episodes.map((ep) => {
                  const watched = watchedIds.has(ep.id);
                  const expanded = expandedEp === ep.id;
                  return (
                    <motion.div key={ep.id} layout className={`rounded-xl border overflow-hidden transition-colors ${watched ? "border-white/10 bg-surface/30" : "border-white/5 bg-surface/50"}`}>
                      <div className="flex gap-3 p-2 items-center cursor-pointer" onClick={() => setExpandedEp(expanded ? null : ep.id)}>
                        <div className="relative w-24 h-16 rounded-md overflow-hidden shrink-0 bg-black/50">
                          <img src={ep.image} alt={ep.title} className={`w-full h-full object-cover transition-opacity ${watched ? "opacity-40" : "opacity-80"}`} />
                          <div className="absolute inset-0 flex items-center justify-center">
                            <div className="w-6 h-6 rounded-full bg-black/50 border border-white/50 flex items-center justify-center backdrop-blur-sm">
                              <Play size={10} className="text-white ml-0.5" />
                            </div>
                          </div>
                          {ep.type === "FILLER" && (
                            <div className="absolute top-1 left-1"><AlertTriangle size={10} className="text-[#ef4444]" /></div>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-xs text-gray-400 font-medium mb-0.5">Episode {ep.num} · {ep.duration}</p>
                          <p className={`text-sm font-semibold truncate mb-1 ${watched ? "text-gray-500 line-through" : "text-white"}`}>{ep.title}</p>
                          <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded
                            ${ep.type === "CANON" ? "bg-[#4ade80]/20 text-[#4ade80] border border-[#4ade80]/50" : "bg-[#f87171]/20 text-[#f87171] border border-[#f87171]/50"}`}>
                            {ep.type}
                          </span>
                        </div>
                        <button className="p-2 text-gray-400 hover:text-white transition-colors shrink-0"
                          onClick={e => { e.stopPropagation(); toggleWatched(ep.id); }}>
                          {watched ? <CheckCircle2 size={24} className="text-[#4ade80]" /> : <Circle size={24} />}
                        </button>
                      </div>
                      <AnimatePresence>
                        {expanded && (
                          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: 0.2 }} className="overflow-hidden">
                            <div className="px-3 pb-3 border-t border-white/5">
                              <p className="text-xs text-gray-300 leading-relaxed mt-2 mb-3">{ep.synopsis}</p>
                              {ep.type === "FILLER" && (
                                <div className="mb-3 flex items-center gap-1.5 text-[10px] text-[#f87171] font-bold bg-[#f87171]/10 rounded px-2 py-1">
                                  <AlertTriangle size={10} /> Filler — safe to skip
                                </div>
                              )}
                              {/* Star rating */}
                              <div className="flex items-center gap-2">
                                <span className="text-[10px] text-gray-500 font-medium">Your rating:</span>
                                <div className="flex gap-0.5">
                                  {[1, 2, 3, 4, 5].map(star => (
                                    <motion.button
                                      key={star}
                                      whileTap={{ scale: 0.8 }}
                                      onClick={e => { e.stopPropagation(); rateEpisode(ep.id, star); }}
                                      className="p-0.5"
                                    >
                                      <Star
                                        size={16}
                                        className={star <= (ratings[ep.id] ?? 0) ? "text-yellow-400" : "text-gray-600"}
                                        fill={star <= (ratings[ep.id] ?? 0) ? "currentColor" : "none"}
                                      />
                                    </motion.button>
                                  ))}
                                </div>
                                {ratings[ep.id] && (
                                  <span className="text-[10px] text-yellow-400 font-bold">{ratings[ep.id]}★</span>
                                )}
                              </div>
                            </div>
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </motion.div>
                  );
                })}
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      )}

      {/* CHARACTERS */}
      {activeTab === "characters" && (
        <div className="px-4 pb-4 space-y-4 pt-4 no-scrollbar">
          {show.characters.map((char, i) => (
            <div key={i} className="bg-surface/50 border border-white/5 rounded-xl p-3">
              <div className="flex gap-3 items-center mb-3">
                <img src={char.img} alt={char.name} className="w-14 h-14 rounded-full object-cover"
                  style={{ border: `2px solid ${char.color}`, boxShadow: `0 0 12px ${char.color}55` }} />
                <div>
                  <h3 className="font-bold text-white text-sm">{char.name}</h3>
                  <p className="text-xs text-gray-400">{char.behavior}</p>
                </div>
              </div>
              <p className="text-xs text-gray-300 mb-3 leading-relaxed">{char.desc}</p>
              <div className="bg-black/30 rounded-lg p-2">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[10px] font-bold text-accent uppercase tracking-wider">Fan Chat</span>
                  <span className="text-[10px] text-gray-500 flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-green-400 inline-block" /> {Math.floor(Math.random() * 900 + 100)} Online
                  </span>
                </div>
                <div className="space-y-2 mb-2 max-h-24 overflow-y-auto no-scrollbar">
                  {chatMessages.slice(-4).map((m, j) => (
                    <div key={j} className="flex gap-2">
                      <div className={`w-4 h-4 rounded-full shrink-0 ${m.user ? "bg-accent" : "bg-blue-500"}`} />
                      <p className="text-[10px] text-gray-300">
                        <span className={`font-bold ${m.user ? "text-accent" : "text-blue-400"}`}>{m.user ? "You:" : "Bot:"}</span> {m.text}
                      </p>
                    </div>
                  ))}
                </div>
                <div className="flex gap-2">
                  <input type="text" value={chatInput} onChange={e => setChatInput(e.target.value)}
                    onKeyDown={e => e.key === "Enter" && sendChat()}
                    placeholder="Join the discussion..."
                    className="flex-1 bg-surface border border-white/10 rounded px-2 py-1 text-[10px] text-white focus:outline-none focus:border-accent" />
                  <button onClick={sendChat} className="w-6 h-6 rounded bg-accent flex items-center justify-center">
                    <Send size={10} className="text-black" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* CHRONOLOGY */}
      {activeTab === "chronology" && (
        <div className="px-4 pb-6 pt-4 no-scrollbar">
          <div className="flex items-center gap-2 mb-4">
            <GitBranch size={16} className="text-accent" />
            <h2 className="font-bold text-white text-sm">Watch Order Guide</h2>
            <Link to="/app/timeline/1" className="ml-auto text-xs text-accent font-bold hover:opacity-80 flex items-center gap-1">
              Skill Tree <ChevronLeft size={12} className="rotate-180" />
            </Link>
          </div>

          <div className="flex gap-3 mb-4">
            {[{ label: "Canon", color: "#00ffff" }, { label: "Mixed", color: "#facc15" }, { label: "Filler", color: "#ef4444" }].map(l => (
              <div key={l.label} className="flex items-center gap-1.5 text-[10px] text-gray-400 font-medium">
                <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: l.color }} />
                {l.label}
              </div>
            ))}
          </div>

          <div className="relative pl-6">
            <div className="absolute left-2.5 top-2 bottom-2 w-0.5 bg-white/10 rounded-full" />
            <div className="space-y-0">
              {show.chronology.map((arc, i) => (
                <motion.div key={i} initial={{ opacity: 0, x: -12 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.07, duration: 0.3 }} className="relative pb-5">
                  <div className="absolute -left-[14px] top-1.5 w-3.5 h-3.5 rounded-full border-2 border-[#0a0a0c]"
                    style={{ backgroundColor: arc.color, boxShadow: `0 0 8px ${arc.color}88` }} />
                  <div className={`rounded-xl p-3 border ${arc.type === "FILLER" ? "bg-[#ef4444]/5 border-[#ef4444]/20" : arc.type === "MIXED" ? "bg-yellow-500/5 border-yellow-500/20" : "bg-white/3 border-white/8"}`}>
                    <div className="flex items-center justify-between mb-1">
                      <h3 className="text-xs font-bold text-white">{arc.title}</h3>
                      <span className="text-[9px] font-bold px-1.5 py-0.5 rounded"
                        style={{ backgroundColor: `${arc.color}22`, color: arc.color, border: `1px solid ${arc.color}44` }}>
                        {arc.type}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 mb-1.5">
                      <Clock size={10} className="text-gray-500" />
                      <span className="text-[10px] text-gray-500 font-medium">Ep {arc.eps}</span>
                      {arc.type === "FILLER" && <span className="text-[9px] text-[#ef4444] font-bold bg-[#ef4444]/10 px-1.5 py-0.5 rounded">SKIP</span>}
                    </div>
                    <p className="text-[11px] text-gray-400 leading-relaxed">{arc.desc}</p>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Related Shows */}
      {related.length > 0 && (
        <div className="px-4 pt-6 pb-8">
          <div className="flex items-center gap-2 mb-4">
            <div className="h-px flex-1 bg-white/10" />
            <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">You might also like</span>
            <div className="h-px flex-1 bg-white/10" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            {related.map((r, i) => (
              <motion.div
                key={r.id}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.1, duration: 0.3 }}
              >
                <Link to={`/app/detail/${r.id}`} className="group flex flex-col">
                  <motion.div
                    whileTap={{ scale: 0.95 }}
                    transition={{ type: "spring", stiffness: 400, damping: 25 }}
                    className="relative w-full aspect-[2/3] rounded-xl overflow-hidden border border-white/10 bg-black shadow-lg"
                  >
                    <img src={r.image} alt={r.title} className="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity" />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent" />
                    <div className="absolute top-2 right-2">
                      <span className={`text-[9px] font-black uppercase px-1.5 py-0.5 rounded border border-black
                        ${r.badge === "canon" ? "bg-[#00ff00] text-black" : "bg-[#00ffff] text-black"}`}>
                        {r.badge}
                      </span>
                    </div>
                    <div className="absolute bottom-2 left-2 right-2">
                      <p className="text-white text-xs font-bold line-clamp-2 drop-shadow">{r.title}</p>
                    </div>
                  </motion.div>
                </Link>
              </motion.div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
