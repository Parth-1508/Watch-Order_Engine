import { Link, useNavigate } from "react-router";
import { motion } from "motion/react";
import { useState } from "react";
import { ArrowLeft, Key, Mail } from "lucide-react";
import { toast } from "sonner";

export function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!email.trim() || !password.trim()) {
      setError("Please fill in both fields.");
      return;
    }
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      navigate("/app");
    }, 900);
  };

  return (
    <div className="flex flex-col h-screen w-full bg-white text-black p-6 relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-1/3 bg-gray-100 rounded-b-[40px] -z-10 shadow-sm" />

      <header className="flex items-center pt-8 pb-4">
        <Link to="/" className="w-10 h-10 rounded-full bg-white shadow-sm border border-gray-100 flex items-center justify-center text-gray-500 hover:text-black hover:bg-gray-50 transition-all">
          <ArrowLeft size={20} />
        </Link>
      </header>

      <div className="flex-1 flex flex-col justify-center max-w-sm mx-auto w-full">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
          <h1 className="text-4xl font-bold tracking-tight mb-2">Welcome back.</h1>
          <p className="text-gray-500 text-sm mb-8">Access your synchronized watch timelines.</p>

          <form onSubmit={handleLogin} className="space-y-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider ml-1">Email</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Mail size={18} className="text-gray-400" />
                </div>
                <input type="email"
                  className="block w-full pl-10 pr-3 py-3 border border-gray-200 rounded-xl bg-gray-50 focus:bg-white focus:ring-2 focus:ring-black focus:border-transparent transition-all outline-none"
                  placeholder="pilot@domain.com"
                  value={email}
                  onChange={e => { setEmail(e.target.value); setError(""); }}
                />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider ml-1">Password</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Key size={18} className="text-gray-400" />
                </div>
                <input type="password"
                  className="block w-full pl-10 pr-3 py-3 border border-gray-200 rounded-xl bg-gray-50 focus:bg-white focus:ring-2 focus:ring-black focus:border-transparent transition-all outline-none"
                  placeholder="••••••••"
                  value={password}
                  onChange={e => { setPassword(e.target.value); setError(""); }}
                />
              </div>
            </div>

            {error && (
              <p className="text-xs text-red-500 font-semibold">{error}</p>
            )}

            <button
              type="button"
              onClick={() => toast("Password reset link sent!", { position: "bottom-center" })}
              className="text-xs font-semibold text-gray-500 hover:text-black w-full text-right mt-1 transition-colors"
            >
              Forgot password?
            </button>

            <div className="pt-4">
              <button type="submit" disabled={loading}
                className="w-full py-4 rounded-xl bg-black text-white font-bold tracking-wide hover:bg-gray-800 transition-colors shadow-md active:scale-[0.98] disabled:opacity-60 flex items-center justify-center gap-2">
                {loading ? (
                  <><span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" /> Signing in…</>
                ) : "Sign In"}
              </button>
            </div>
          </form>

          <div className="mt-8 text-center">
            <p className="text-sm text-gray-500">
              New pilot?{" "}
              <button
                onClick={() => toast("Access requests open soon — stay tuned!", { position: "bottom-center" })}
                className="font-bold text-black hover:underline"
              >
                Request access
              </button>
            </p>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
