import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { BarChart3, Download, FileBarChart } from 'lucide-react';
import { api } from '../lib/api';
import { useToast } from '../lib/toast';
import { CATEGORICAL, fmtDate, fmtDateTime } from '../lib/format';
import type { Report, ReportType } from '../lib/types';
import { SectionTitle } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { HorizontalBars, TrendArea, VerticalBars } from '../components/charts/Charts';

const REPORTS: { type: ReportType; label: string; desc: string }[] = [
  { type: 'DEPARTMENT', label: 'Department-wise', desc: 'Candidates and outcomes per department' },
  { type: 'MONTHLY', label: 'Monthly applications', desc: 'Applications received per month' },
  { type: 'SELECTED', label: 'Selected', desc: 'Candidates with status Selected' },
  { type: 'REJECTED', label: 'Rejected', desc: 'Candidates with status Rejected' },
  { type: 'PENDING_VERIFICATION', label: 'Pending verification', desc: 'Documents / data not yet verified' },
  { type: 'INTERVIEW_STATUS', label: 'Interview status', desc: 'All interviews and their outcome' },
];

export default function ReportsPage() {
  const toast = useToast();
  const [type, setType] = useState<ReportType>('DEPARTMENT');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [exporting, setExporting] = useState(false);
  const invalid = !!from && !!to && from > to;

  const query = useQuery({
    queryKey: ['reports', type, from, to],
    queryFn: () => api.get<Report>(`/reports/${type}`, { from, to }),
    enabled: !invalid,
  });
  const r = query.data;

  const exportExcel = async () => {
    setExporting(true);
    try {
      await api.download(`/reports/${type}/export`, { from, to }, `${type.toLowerCase()}-report.xlsx`);
      toast('success', 'Report exported');
    } catch (e) {
      toast('error', (e as Error).message);
    } finally {
      setExporting(false);
    }
  };

  const dateLabel = type === 'INTERVIEW_STATUS' ? 'Interview date' : 'Application date';

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
      <nav className="panel h-fit p-2 xl:col-span-3" aria-label="Reports">
        <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2 xl:grid-cols-1">
          {REPORTS.map((rep) => (
            <li key={rep.type}>
              <button
                onClick={() => setType(rep.type)}
                className={clsx('flex w-full items-start gap-3 rounded-xl px-3 py-2.5 text-left transition', type === rep.type ? 'bg-ink text-canvas' : 'hover:bg-raised')}
              >
                <FileBarChart size={17} className="mt-0.5 shrink-0" />
                <span>
                  <span className="block text-sm font-medium">{rep.label}</span>
                  <span className={clsx('block text-xs', type === rep.type ? 'text-canvas/60' : 'text-ink-3')}>{rep.desc}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </nav>

      <div className="space-y-6 xl:col-span-9">
        <section className="panel flex flex-wrap items-end gap-3 p-4">
          <label className="text-sm">
            <span className="label">{dateLabel} from</span>
            <input type="date" className="input" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="text-sm">
            <span className="label">to</span>
            <input type="date" className="input" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
          </label>
          <button className="btn-ghost" disabled={!from && !to} onClick={() => { setFrom(''); setTo(''); }}>Clear dates</button>
          <div className="flex-1" />
          <button className="btn-primary" onClick={exportExcel} disabled={exporting || !r || invalid}>
            {exporting ? <Spinner size={15} /> : <Download size={15} />} Export to Excel
          </button>
          {invalid && <p className="w-full text-sm text-bad">“From” must be on or before “to”.</p>}
        </section>

        {query.isLoading ? (
          <div className="panel"><LoadingBlock label="Building report…" /></div>
        ) : query.error ? (
          <div className="panel"><ErrorState error={query.error} onRetry={() => query.refetch()} /></div>
        ) : r ? (
          <>
            <section className="panel p-5">
              <SectionTitle
                title={r.title}
                subtitle={`${r.rows.length} row${r.rows.length === 1 ? '' : 's'} · ${r.from || r.to ? `${r.from ? fmtDate(r.from) : '…'} – ${r.to ? fmtDate(r.to) : 'today'}` : 'all dates'} · generated ${fmtDateTime(r.generatedAt)}`}
              />
              {r.chart.length === 0 || r.chart.every((c) => c.value === 0) ? (
                <EmptyState icon={<BarChart3 size={20} />} title="Nothing to chart for this selection" />
              ) : type === 'MONTHLY' ? (
                <TrendArea data={r.chart.map((c) => ({ label: c.label, value: c.value }))} />
              ) : type === 'INTERVIEW_STATUS' ? (
                <VerticalBars data={r.chart.map((c) => ({ label: c.label, value: c.value }))} color={CATEGORICAL[0]} />
              ) : (
                <>
                  {type !== 'DEPARTMENT' && <p className="mb-2 text-xs text-ink-3">By department</p>}
                  <HorizontalBars data={r.chart.map((c) => ({ label: c.label, value: c.value }))} color={CATEGORICAL[0]} />
                </>
              )}
            </section>
            <section className="panel p-5">
              {r.rows.length === 0 ? (
                <EmptyState title="No records for this report" hint="Try a wider date range." />
              ) : (
                <div className="-mx-5 max-h-[560px] overflow-auto">
                  <table className="w-full min-w-[720px]">
                    <thead className="sticky top-0 bg-panel">
                      <tr className="border-b border-line">
                        {r.columns.map((c, i) => (
                          <th key={c.key} className={clsx('th', i === 0 && 'pl-5')}>{c.label}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {r.rows.map((row, idx) => (
                        <tr key={idx} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
                          {r.columns.map((c, i) => {
                            const v = row[c.key];
                            const text = v === null || v === undefined || v === '' ? '—' : c.key === 'applicationDate' ? fmtDate(String(v)) : String(v);
                            return (
                              <td key={c.key} className={clsx('td max-w-[260px] truncate', i === 0 && 'pl-5')} title={typeof v === 'string' ? v : undefined}>
                                {(c.key === 'fullName' || c.key === 'candidateCode') && row.id ? (
                                  <Link to={`/candidates/${row.id}`} className="text-ink hover:underline">{text}</Link>
                                ) : (
                                  text
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        ) : null}
      </div>
    </div>
  );
}
