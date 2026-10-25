import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import clsx from 'clsx';
import {
  ArrowUpRight,
  BadgeCheck,
  Building2,
  CalendarCheck2,
  CalendarClock,
  ClipboardList,
  FileClock,
  RefreshCw,
  UserPlus,
  Users,
} from 'lucide-react';
import { useDashboard, useSyncStatus } from '../lib/queries';
import {
  CATEGORICAL,
  applicationTone,
  fmtNumber,
  fmtRelative,
  fmtTime,
  fmtDate,
  interviewTone,
  toneHex,
  INTERVIEW_MODES,
  labelOf,
} from '../lib/format';
import type { ApplicationStatus, Dashboard, InterviewStatus, Spotlight } from '../lib/types';
import { Avatar, RingGauge, SectionTitle } from '../components/ui/Misc';
import { InterviewPill } from '../components/ui/Pill';
import { EmptyState, ErrorState, Skeleton } from '../components/ui/States';
import { Donut, HorizontalBars, TrendArea, VerticalBars, WeekColumns } from '../components/charts/Charts';

export default function DashboardPage() {
  const { data, isLoading, error, refetch } = useDashboard();
  if (isLoading) return <DashboardSkeleton />;
  if (error || !data) return <div className="panel"><ErrorState error={error} onRetry={() => refetch()} /></div>;
  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
      <div className="space-y-6 xl:col-span-9">
        <SummaryStrip d={data} />
        <SpotlightCards spotlights={data.spotlights} />
        <InterviewTable d={data} />
        <Analytics d={data} />
      </div>
      <aside className="space-y-6 xl:col-span-3">
        <DepartmentRail d={data} />
        <InterviewersToday names={data.interviewersToday} />
        <WeekReport d={data} />
        <SyncCard />
      </aside>
    </div>
  );
}

// ------------------------------------------------------------------ summary

function SummaryStrip({ d }: { d: Dashboard }) {
  const s = d.summary;
  const total = s.totalCandidates;
  const share = (n: number) => (total ? (n / total) * 100 : 0);
  const week = d.lastSevenDays.reduce((a, x) => a + x.count, 0);
  const tiles = [
    { label: 'Total Candidates', value: total, sub: `${fmtNumber(week)} in the last 7 days`, icon: Users, to: '/candidates' },
    { label: "Today's Applications", value: s.todayApplications, sub: 'Submitted today', ring: share(s.todayApplications), color: toneHex.blue, to: '/candidates?sort=applicationDate' },
    { label: 'Shortlisted', value: s.shortlisted, sub: 'of all candidates', ring: share(s.shortlisted), color: toneHex.aqua, to: '/candidates?applicationStatus=SHORTLISTED' },
    { label: 'Interviews Scheduled', value: s.interviewsScheduled, sub: 'Upcoming', icon: CalendarClock, to: '/interviews' },
    { label: 'Selected', value: s.selected, sub: 'of all candidates', ring: share(s.selected), color: toneHex.green, to: '/candidates?applicationStatus=SELECTED' },
    { label: 'Pending Verification', value: s.pendingVerification, sub: 'of all candidates', ring: share(s.pendingVerification), color: toneHex.orange, to: '/candidates?verificationStatus=PENDING' },
  ];
  return (
    // Max three columns: six tiles in one row are too cramped next to the right rail.
    // gap-px over a line-coloured background draws even dividers at every breakpoint.
    <section className="panel grid grid-cols-1 gap-px overflow-hidden bg-line/70 sm:grid-cols-2 lg:grid-cols-3" aria-label="Summary">
      {tiles.map((t, i) => (
        <Link
          key={t.label}
          to={t.to}
          className="group flex min-h-[112px] items-center justify-between gap-4 bg-panel px-5 py-5 transition hover:bg-card animate-fade-up"
          style={{ animationDelay: `${i * 40}ms` }}
        >
          <div className="min-w-0">
            <p className="truncate whitespace-nowrap text-sm font-medium text-ink-2" title={t.label}>{t.label}</p>
            <p className="mt-1.5 text-3xl font-semibold leading-none tracking-tight">{fmtNumber(t.value)}</p>
            <p className="mt-2 truncate whitespace-nowrap text-xs text-ink-3">{t.sub}</p>
          </div>
          {t.ring !== undefined ? (
            <RingGauge value={t.ring} color={t.color!} />
          ) : (
            t.icon && (
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-raised text-ink-2 group-hover:text-ink">
                <t.icon size={20} />
              </span>
            )
          )}
        </Link>
      ))}
    </section>
  );
}

// ------------------------------------------------------------------ spotlights

/** Each card links to the full candidate list filtered exactly like its count. */
const SPOT_STYLE: Record<string, { bg: string; icon: typeof Users; to: string; caption: string; empty: string }> = {
  PENDING_VERIFICATION: {
    bg: 'bg-warn-card',
    icon: FileClock,
    to: '/candidates?verificationStatus=PENDING',
    caption: 'awaiting document verification',
    empty: 'Nothing waiting for verification.',
  },
  INTERVIEW: {
    bg: 'bg-info-card',
    icon: CalendarCheck2,
    to: '/candidates?interviewStatus=SCHEDULED,RESCHEDULED',
    caption: 'with an upcoming interview',
    empty: 'No candidates in the interview stage.',
  },
  SELECTED: {
    bg: 'bg-good-card',
    icon: BadgeCheck,
    to: '/candidates?applicationStatus=SELECTED',
    caption: 'selected for hire',
    empty: 'No candidates selected yet.',
  },
};

function SpotlightCards({ spotlights }: { spotlights: Spotlight[] }) {
  return (
    <section className="grid grid-cols-1 gap-4 md:grid-cols-3" aria-label="Pipeline stages">
      {spotlights.map((s, i) => {
        const st = SPOT_STYLE[s.key];
        const Icon = st.icon;
        const shown = s.recent.slice(0, 4);
        const more = s.count - shown.length;
        return (
          <Link
            key={s.key}
            to={st.to}
            aria-label={`${s.label}: ${s.count} candidates. Open the full list`}
            className={clsx(
              'group relative flex min-h-[236px] flex-col overflow-hidden rounded-2xl p-5 text-white shadow-card transition duration-200 animate-fade-up',
              'hover:-translate-y-0.5 hover:shadow-[0_16px_32px_-16px_rgba(0,0,0,0.8)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70',
              st.bg,
            )}
            style={{ animationDelay: `${120 + i * 60}ms` }}
          >
            <div className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rotate-12 rounded-3xl bg-white/[0.07] transition group-hover:bg-white/[0.11]" />
            <div className="relative flex items-center justify-between">
              <span className="flex items-center gap-2 text-sm font-medium text-white/90">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-black/15"><Icon size={16} /></span>
                {s.label}
              </span>
              <ArrowUpRight size={18} className="text-white/70 transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-white" />
            </div>

            <div className="relative mt-5">
              <p className="text-5xl font-semibold leading-none tracking-tight">{fmtNumber(s.count)}</p>
              <p className="mt-2 text-sm text-white/75">{s.count === 0 ? st.empty : st.caption}</p>
            </div>

            <div className="relative mt-auto flex items-center justify-between gap-3 pt-5">
              {shown.length > 0 ? (
                <div className="flex items-center">
                  <div className="flex -space-x-2.5">
                    {shown.map((c) => (
                      <span key={c.id} className="rounded-full ring-2 ring-white/40" title={`${c.fullName} · ${c.candidateCode}`}>
                        <Avatar name={c.fullName} size="sm" />
                      </span>
                    ))}
                  </div>
                  {more > 0 && <span className="ml-2 whitespace-nowrap text-xs font-medium text-white/80">+{fmtNumber(more)} more</span>}
                </div>
              ) : (
                <span />
              )}
              <span className="whitespace-nowrap rounded-xl border border-white/40 px-3 py-1.5 text-sm font-medium transition group-hover:bg-white/15">
                View all
              </span>
            </div>
          </Link>
        );
      })}
    </section>
  );
}

// ------------------------------------------------------------------ interviews

function InterviewTable({ d }: { d: Dashboard }) {
  const [range, setRange] = useState<'today' | 'upcoming'>('today');
  const rows = range === 'today' ? d.todaysInterviews : d.upcomingInterviews;
  return (
    <section className="panel p-5">
      <SectionTitle
        title="Interview Schedule"
        subtitle={range === 'today' ? 'All interviews scheduled for today' : 'Next upcoming interviews'}
        action={
          <div className="flex items-center gap-2">
            <select className="input w-auto rounded-full py-1.5" value={range} onChange={(e) => setRange(e.target.value as 'today' | 'upcoming')} aria-label="Interview range">
              <option value="today">Today</option>
              <option value="upcoming">Upcoming</option>
            </select>
            <Link to="/interviews" className="btn-ghost px-2.5 py-1.5 text-xs">View all</Link>
          </div>
        }
      />
      {rows.length === 0 ? (
        <EmptyState
          icon={<CalendarClock size={20} />}
          title={range === 'today' ? 'No interviews today' : 'No upcoming interviews'}
          hint="Schedule interviews from the Interview Schedule page."
          action={<Link to="/interviews" className="btn-secondary">Schedule interview</Link>}
        />
      ) : (
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead>
              <tr className="border-b border-line/70">
                <th className="th">Candidate</th>
                <th className="th">Department</th>
                <th className="th">Round</th>
                <th className="th">Interviewer</th>
                <th className="th">{range === 'today' ? 'Time' : 'Date & time'}</th>
                <th className="th text-right">Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((i) => (
                <tr key={i.id} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
                  <td className="td">
                    <Link to={`/candidates/${i.candidateId}`} className="flex items-center gap-3 text-ink hover:underline">
                      <Avatar name={i.candidateName} size="sm" /> {i.candidateName}
                    </Link>
                  </td>
                  <td className="td">{i.departmentName ?? '—'}</td>
                  <td className="td">{i.roundName}</td>
                  <td className="td">
                    {i.interviewer}
                    <span className="block text-xs text-ink-3">{labelOf(INTERVIEW_MODES, i.mode)}</span>
                  </td>
                  <td className="td tabular-nums">{range === 'today' ? fmtTime(i.scheduledAt) : `${fmtDate(i.scheduledAt)}, ${fmtTime(i.scheduledAt)}`}</td>
                  <td className="td text-right"><InterviewPill status={i.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ------------------------------------------------------------------ analytics

function Analytics({ d }: { d: Dashboard }) {
  const navigate = useNavigate();
  const empty = d.summary.totalCandidates === 0;
  return (
    <section className="grid grid-cols-1 gap-6 lg:grid-cols-2" aria-label="Analytics">
      <div className="panel p-5 lg:col-span-2">
        <SectionTitle title="Monthly Applications" subtitle="Applications received per month, last 12 months" />
        <TrendArea data={d.monthly.map((m) => ({ label: m.label, value: m.count }))} />
      </div>
      <div className="panel p-5">
        <SectionTitle title="Candidates by Department" subtitle="Click a bar to see that department's candidates" />
        {d.departmentWise.length === 0 ? (
          <EmptyState icon={<Building2 size={20} />} title="No candidates yet" />
        ) : (
          <HorizontalBars
            data={d.departmentWise.map((x) => ({ label: x.label, value: x.count, key: x.key }))}
            onSelect={(x) => navigate(`/candidates?departmentId=${x.key}`)}
          />
        )}
      </div>
      <div className="panel p-5">
        <SectionTitle title="Candidate Selection Status" subtitle="Current application status of all candidates" />
        {empty ? (
          <EmptyState icon={<ClipboardList size={20} />} title="No candidates yet" />
        ) : (
          <Donut
            centerLabel="candidates"
            data={d.applicationStatus.map((x) => ({ label: x.label, value: x.count, key: x.key, color: toneHex[applicationTone[x.key as ApplicationStatus]] }))}
            onSelect={(x) => navigate(`/candidates?applicationStatus=${x.key}`)}
          />
        )}
      </div>
      <div className="panel p-5">
        <SectionTitle title="Interview Completion Status" subtitle="Candidates by their latest interview status" />
        <VerticalBars data={d.interviewStatus.map((x) => ({ label: x.label, value: x.count, color: toneHex[interviewTone[x.key as InterviewStatus]] }))} />
      </div>
      <div className="panel p-5">
        <SectionTitle title="Experience Distribution" subtitle="Total experience declared by candidates" />
        <VerticalBars data={d.experience.map((x) => ({ label: x.label, value: x.count }))} color={CATEGORICAL[0]} />
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ right rail

function DepartmentRail({ d }: { d: Dashboard }) {
  const top = d.departments.slice(0, 4);
  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-[15px] font-semibold">Departments</h2>
        <Link to="/departments" className="text-sm text-ink-2 hover:text-ink">See all</Link>
      </div>
      {top.length === 0 ? (
        <div className="panel"><EmptyState icon={<Building2 size={20} />} title="No departments configured" /></div>
      ) : (
        <div className="space-y-3">
          {top.map((dep, i) => (
            <Link
              key={dep.name}
              to={`/candidates?departmentId=${dep.departmentId ?? 0}`}
              className="panel flex items-center gap-3 p-4 transition hover:border-line hover:bg-card"
            >
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-white" style={{ background: CATEGORICAL[i % CATEGORICAL.length] }}>
                <Building2 size={18} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold">{dep.name}</span>
                <span className="mt-1.5 flex flex-wrap gap-1.5 text-[11px]">
                  <span className="rounded-md bg-info/20 px-1.5 py-0.5 text-[#8fb4f5]">{dep.total} total</span>
                  <span className="rounded-md bg-warn/20 px-1.5 py-0.5 text-[#f3b877]">{dep.open} open</span>
                  {dep.today > 0 && <span className="rounded-md bg-good/20 px-1.5 py-0.5 text-[#6fd99a]">+{dep.today} today</span>}
                </span>
              </span>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function InterviewersToday({ names }: { names: string[] }) {
  return (
    <section className="panel p-4">
      <h2 className="text-[15px] font-semibold">Interview Panel Today</h2>
      {names.length === 0 ? (
        <p className="mt-2 text-sm text-ink-3">No interviewers booked for the rest of today.</p>
      ) : (
        <>
          <div className="mt-3 flex -space-x-2">
            {names.slice(0, 6).map((n) => (
              <span key={n} className="rounded-full ring-2 ring-panel"><Avatar name={n} size="md" /></span>
            ))}
            {names.length > 6 && (
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-raised text-xs ring-2 ring-panel">+{names.length - 6}</span>
            )}
          </div>
          <p className="mt-2 text-xs text-ink-3">{names.join(', ')}</p>
        </>
      )}
    </section>
  );
}

function WeekReport({ d }: { d: Dashboard }) {
  const total = d.lastSevenDays.reduce((a, x) => a + x.count, 0);
  return (
    <section className="panel p-4">
      <div className="flex items-baseline justify-between">
        <h2 className="text-[15px] font-semibold">Applications This Week</h2>
        <span className="text-sm font-semibold">{total}</span>
      </div>
      <p className="text-xs text-ink-3">Daily submissions, last 7 days</p>
      <div className="mt-3">
        <WeekColumns data={d.lastSevenDays.map((x) => ({ label: x.label, value: x.count }))} />
      </div>
    </section>
  );
}

function SyncCard() {
  const { data } = useSyncStatus();
  if (!data) return null;
  const tone = data.lastStatus === 'FAILED' ? 'text-bad' : data.lastStatus === 'PARTIAL' ? 'text-warn' : 'text-good';
  return (
    <section className="panel p-4 text-sm">
      <h2 className="flex items-center gap-2 text-[15px] font-semibold">
        <RefreshCw size={15} className={clsx(data.running && 'animate-spin')} /> Google Form Sync
      </h2>
      {!data.configured ? (
        <p className="mt-2 text-ink-3">Not connected yet. An administrator can connect the response sheet in Settings → Integration.</p>
      ) : (
        <dl className="mt-2 space-y-1 text-ink-2">
          <div className="flex justify-between"><dt>Last sync</dt><dd className="text-ink">{fmtRelative(data.lastSyncAt)}</dd></div>
          <div className="flex justify-between"><dt>Result</dt><dd className={tone}>{data.lastStatus ?? '—'}</dd></div>
          <div className="flex justify-between"><dt>Auto-sync</dt><dd>{data.autoSyncEnabled ? `every ${data.syncIntervalMinutes} min` : 'off'}</dd></div>
        </dl>
      )}
      {data.formUrl && (
        <a href={data.formUrl} target="_blank" rel="noreferrer noopener" className="mt-3 inline-flex items-center gap-1 text-info hover:underline">
          <UserPlus size={14} /> Open application form
        </a>
      )}
    </section>
  );
}

function DashboardSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-12" aria-busy="true">
      <div className="space-y-6 xl:col-span-9">
        <Skeleton className="h-28" />
        <div className="grid gap-4 md:grid-cols-3">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-64" />)}
        </div>
        <Skeleton className="h-72" />
      </div>
      <div className="space-y-4 xl:col-span-3">
        {[0, 1, 2].map((i) => <Skeleton key={i} className="h-24" />)}
      </div>
    </div>
  );
}
