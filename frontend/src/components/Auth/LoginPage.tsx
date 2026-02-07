import { useState } from 'react';
import { PlaneTakeoff } from 'lucide-react';

interface LoginPageProps {
  onLogin: (username: string, password: string) => Promise<unknown>;
  onRegister: (username: string, email: string, password: string) => Promise<unknown>;
}

export default function LoginPage({ onLogin, onRegister }: LoginPageProps) {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (isRegister) {
        await onRegister(username, email, password);
      } else {
        await onLogin(username, password);
      }
    } catch (err: any) {
      setError(err.message || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-2 mb-2">
            <PlaneTakeoff className="text-sky-400" size={36} />
            <h1 className="text-4xl font-bold text-white tracking-tight">
              <span className="text-sky-400">Flight</span>Forge
            </h1>
          </div>
          <p className="text-slate-400">Airline Simulation Game - V2</p>
        </div>

        <div className="bg-slate-900 rounded-xl border border-slate-800 p-6">
          <div className="flex mb-6 bg-slate-800 rounded-lg p-1">
            <button
              onClick={() => setIsRegister(false)}
              className={`flex-1 py-2 text-sm rounded-md transition-colors ${
                !isRegister ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-white'
              }`}
            >
              Sign In
            </button>
            <button
              onClick={() => setIsRegister(true)}
              className={`flex-1 py-2 text-sm rounded-md transition-colors ${
                isRegister ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-white'
              }`}
            >
              Register
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-red-900/50 border border-red-700 rounded-lg text-red-300 text-sm">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Username</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-sky-500 focus:outline-none"
                required
              />
            </div>

            {isRegister && (
              <div>
                <label className="block text-sm text-slate-400 mb-1">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-sky-500 focus:outline-none"
                  required
                />
              </div>
            )}

            <div>
              <label className="block text-sm text-slate-400 mb-1">Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-sky-500 focus:outline-none"
                required
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-medium transition-colors disabled:opacity-50"
            >
              {loading ? 'Please wait...' : isRegister ? 'Create Account' : 'Sign In'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
