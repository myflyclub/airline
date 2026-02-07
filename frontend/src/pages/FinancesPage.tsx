import { useEffect, useState } from 'react';
import { airlines as airlinesApi } from '../services/api';
import type { AirlineSummary, Loan } from '../types';
import { DollarSign, TrendingUp, Landmark, Plus } from 'lucide-react';

export default function FinancesPage() {
  const [airline, setAirline] = useState<AirlineSummary | null>(null);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [showLoanForm, setShowLoanForm] = useState(false);
  const [loanAmount, setLoanAmount] = useState(10_000_000);
  const [loanTerm, setLoanTerm] = useState(12);
  const [loanError, setLoanError] = useState('');

  useEffect(() => {
    airlinesApi.mine().then((a) => {
      const airline = a as AirlineSummary;
      setAirline(airline);
      airlinesApi.getLoans(airline.id).then((l) => setLoans(l as Loan[]));
    });
  }, []);

  const handleTakeLoan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!airline) return;
    setLoanError('');
    try {
      const loan = (await airlinesApi.takeLoan(airline.id, {
        amount: loanAmount,
        term_cycles: loanTerm,
      })) as Loan;
      setLoans((prev) => [...prev, loan]);
      const a = (await airlinesApi.mine()) as AirlineSummary;
      setAirline(a);
      setShowLoanForm(false);
    } catch (err: any) {
      setLoanError(err.message);
    }
  };

  if (!airline) {
    return <div className="p-8 text-slate-400 animate-pulse">Loading finances...</div>;
  }

  const totalDebt = loans.reduce((sum, l) => sum + l.remaining, 0);

  return (
    <div className="p-6 max-w-4xl">
      <h1 className="text-2xl font-bold text-white mb-6">Finances</h1>

      {/* Financial overview */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center gap-2 mb-2">
            <DollarSign size={18} className="text-emerald-400" />
            <span className="text-sm text-slate-400">Cash Balance</span>
          </div>
          <p className="text-2xl font-bold text-emerald-400">
            ${(airline.balance / 1_000_000).toFixed(2)}M
          </p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center gap-2 mb-2">
            <Landmark size={18} className="text-amber-400" />
            <span className="text-sm text-slate-400">Total Debt</span>
          </div>
          <p className="text-2xl font-bold text-amber-400">
            ${(totalDebt / 1_000_000).toFixed(2)}M
          </p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center gap-2 mb-2">
            <TrendingUp size={18} className="text-sky-400" />
            <span className="text-sm text-slate-400">Net Worth</span>
          </div>
          <p className="text-2xl font-bold text-sky-400">
            ${((airline.balance - totalDebt) / 1_000_000).toFixed(2)}M
          </p>
        </div>
      </div>

      {/* Loans section */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-white flex items-center gap-2">
            <Landmark size={18} className="text-sky-400" />
            Loans
          </h3>
          <button
            onClick={() => setShowLoanForm(!showLoanForm)}
            className="flex items-center gap-2 px-3 py-1.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-sm transition-colors"
          >
            <Plus size={14} />
            Take Loan
          </button>
        </div>

        {showLoanForm && (
          <form
            onSubmit={handleTakeLoan}
            className="mb-4 p-4 bg-slate-800 rounded-lg space-y-3"
          >
            {loanError && (
              <div className="p-2 bg-red-900/50 border border-red-700 rounded text-red-300 text-sm">
                {loanError}
              </div>
            )}
            <div>
              <label className="block text-xs text-slate-400 mb-1">
                Amount: ${(loanAmount / 1_000_000).toFixed(0)}M
              </label>
              <input
                type="range"
                min={1_000_000}
                max={100_000_000}
                step={1_000_000}
                value={loanAmount}
                onChange={(e) => setLoanAmount(Number(e.target.value))}
                className="w-full accent-sky-500"
              />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">
                Term: {loanTerm} cycles
              </label>
              <input
                type="range"
                min={4}
                max={52}
                value={loanTerm}
                onChange={(e) => setLoanTerm(Number(e.target.value))}
                className="w-full accent-sky-500"
              />
            </div>
            <button
              type="submit"
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-sm transition-colors"
            >
              Confirm Loan
            </button>
          </form>
        )}

        {loans.length === 0 ? (
          <p className="text-sm text-slate-500">No active loans.</p>
        ) : (
          <div className="space-y-2">
            {loans.map((loan) => (
              <div
                key={loan.id}
                className="flex items-center justify-between py-3 border-b border-slate-800 last:border-0"
              >
                <div>
                  <p className="text-sm text-white">
                    Principal: ${(loan.principal / 1_000_000).toFixed(1)}M
                  </p>
                  <p className="text-xs text-slate-500">
                    Rate: {(loan.interest_rate * 100).toFixed(1)}% | Term:{' '}
                    {loan.term_cycles} cycles
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-amber-400">
                    ${(loan.remaining / 1_000_000).toFixed(1)}M remaining
                  </p>
                  <p className="text-xs text-slate-500">
                    {loan.remaining_cycles} cycles left
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
