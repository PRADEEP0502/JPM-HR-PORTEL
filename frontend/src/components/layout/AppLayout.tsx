import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  BarChart3,
  Bell,
  Building2,
  CalendarClock,
  ChevronDown,
  FlaskConical,
  KanbanSquare,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Settings,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '../../lib/auth';
import { api } from '../../lib/api';
import { useDashboard, useNotifications } from '../../lib/queries';
import { ROLES, fmtRelative, labelOf } from '../../lib/format';
import { Avatar } from '../ui/Misc';

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/candidates', label: 'Candidates', icon: Users },
  { to: '/pipeline', label: 'Pipeline', icon: KanbanSquare },
  { to: '/interviews', label: 'Interview Schedule', icon: CalendarClock },
  { to: '/departments', label: 'Departments', icon: Building2 },
  { to: '/reports', label: 'Reports', icon: BarChart3 },
  { to: '/settings', label: 'Settings', icon: Settings },
];

/** Interviewers only work with their own assigned interviews. */
const INTERVIEWER_NAV = [
  { to: '/', label: 'My Interviews', icon: CalendarClock, end: true },
  { to: '/settings', label: 'Settings', icon: Settings },
];

const TITLES: Record<string, string> = {
  candidates: 'Candidates',
  pipeline: 'Recruitment Pipeline',
  interviews: 'Interview Schedule',
  departments: 'Departments',
  reports: 'Reports',
  settings: 'Settings',
};

export function Logo() {
  return (
    <div className="flex items-center gap-3">
      <img src="/logo.png" alt="" width={40} height={40} className="h-10 w-10 rounded-full bg-white object-contain" />
      <div className="leading-tight">
        <div className="text-[15px] font-bold tracking-wide">JPM HR Portal</div>
        <div className="text-[11px] text-ink-3">Recruitment</div>
      </div>
    </div>
  );
}

function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, logout, isInterviewer } = useAuth();
  const [menu, setMenu] = useState(false);
  const nav = isInterviewer ? INTERVIEWER_NAV : NAV;
  return (
    <>
      <div className={clsx('fixed inset-0 z-30 bg-black/60 lg:hidden', open ? 'block' : 'hidden')} onClick={onClose} />
      <aside
        className={clsx(
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-line/60 bg-canvas px-4 py-5 transition-transform lg:sticky lg:top-0 lg:h-screen lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center justify-between px-2">
          <Logo />
          <button className="icon-btn h-8 w-8 lg:hidden" onClick={onClose} aria-label="Close menu">
            <X size={16} />
          </button>
        </div>
        <nav className="mt-10 flex flex-1 flex-col gap-1.5" aria-label="Main">
          {nav.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition',
                  isActive ? 'bg-ink text-canvas shadow' : 'text-ink-2 hover:bg-raised hover:text-ink',
                )
              }
            >
              <Icon size={18} strokeWidth={1.9} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="relative">
          {menu && (
            <div className="absolute bottom-full mb-2 w-full rounded-xl border border-line bg-raised p-1 shadow-card">
              <div className="px-3 py-2 text-xs text-ink-3">
                Signed in as <span className="text-ink-2">{user?.username}</span>
              </div>
              <Link to="/settings" onClick={() => setMenu(false)} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-ink-2 hover:bg-line hover:text-ink">
                <Settings size={15} /> Profile & settings
              </Link>
              <button onClick={logout} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-ink-2 hover:bg-line hover:text-bad">
                <LogOut size={15} /> Log out
              </button>
            </div>
          )}
          <button
            onClick={() => setMenu((m) => !m)}
            className="flex w-full items-center gap-3 rounded-xl bg-ink px-2.5 py-2 text-canvas"
            aria-expanded={menu}
          >
            <Avatar name={user?.fullName ?? '?'} size="sm" />
            <span className="min-w-0 flex-1 text-left">
              <span className="block truncate text-sm font-semibold">{user?.fullName}</span>
              <span className="block text-[11px] text-canvas/60">{labelOf(ROLES, user?.role)}</span>
            </span>
            <ChevronDown size={16} className={clsx('transition', menu && 'rotate-180')} />
          </button>
        </div>
      </aside>
    </>
  );
}

function NotificationsMenu() {
  const { data } = useNotifications();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const qc = useQueryClient();
  const navigate = useNavigate();
  const markRead = useMutation({
    mutationFn: () => api.post('/notifications/read'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
  useEffect(() => {
    const onDoc = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);
  const unread = data?.unreadCount ?? 0;
  return (
    <div className="relative" ref={ref}>
      <button className="icon-btn relative" onClick={() => setOpen((o) => !o)} aria-label={`Notifications (${unread} unread)`}>
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-bad px-1 text-[10px] font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-[min(22rem,calc(100vw-2rem))] rounded-2xl border border-line bg-raised shadow-card animate-fade-up">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <span className="text-sm font-semibold">Notifications</span>
            <button className="text-xs text-info hover:underline disabled:opacity-40" disabled={unread === 0 || markRead.isPending} onClick={() => markRead.mutate()}>
              Mark all as read
            </button>
          </div>
          <ul className="max-h-96 overflow-y-auto py-1">
            {!data?.items.length && <li className="px-4 py-8 text-center text-sm text-ink-3">You're all caught up.</li>}
            {data?.items.map((n) => (
              <li key={n.id}>
                <button
                  onClick={() => {
                    setOpen(false);
                    navigate(n.link);
                  }}
                  className="flex w-full gap-3 px-4 py-2.5 text-left hover:bg-line/60"
                >
                  <span className={clsx('mt-1.5 h-2 w-2 shrink-0 rounded-full', n.unread ? (n.type === 'SYNC_FAILED' ? 'bg-bad' : 'bg-info') : 'bg-transparent')} />
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-ink">{n.title}</span>
                    <span className="block truncate text-xs text-ink-2">{n.message}</span>
                    <span className="block text-[11px] text-ink-3">{fmtRelative(n.at)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Header({ onMenu }: { onMenu: () => void }) {
  const { user, logout, isInterviewer } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [q, setQ] = useState('');
  const section = location.pathname.split('/')[1];
  const isHome = section === '';
  const title = isHome && isInterviewer ? 'My Interviews' : isHome ? `Hello, ${user?.fullName.split(' ')[0] ?? ''}` : section === 'candidates' && location.pathname.split('/')[2] ? 'Candidate Profile' : TITLES[section] ?? 'JPM HR Portal';

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(`/candidates${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`);
  };

  return (
    <header className="sticky top-0 z-20 flex flex-wrap items-center gap-3 bg-canvas/85 px-4 py-4 backdrop-blur sm:px-6 lg:px-8">
      <button className="icon-btn lg:hidden" onClick={onMenu} aria-label="Open menu">
        <Menu size={18} />
      </button>
      <div className="min-w-0 flex-1">
        <h1 className="truncate text-xl font-semibold sm:text-2xl">
          {title} {isHome && <span aria-hidden>👋</span>}
        </h1>
        {isHome && !isInterviewer && <p className="hidden text-sm text-ink-3 sm:block">Here's what's happening in recruitment today.</p>}
      </div>
      {!isInterviewer && (
      <form onSubmit={submit} className="order-last w-full sm:order-none sm:w-64 lg:w-72" role="search">
        <label className="relative block">
          <span className="sr-only">Search candidates by name or ID</span>
          <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-3" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name or candidate ID" className="input rounded-full py-2.5 pl-10" />
        </label>
      </form>
      )}
      {!isInterviewer && <NotificationsMenu />}
      <Link to="/settings" className="icon-btn hidden sm:inline-flex" aria-label="Settings">
        <Settings size={18} />
      </Link>
      <button className="icon-btn" onClick={logout} aria-label="Log out" title="Log out">
        <LogOut size={18} />
      </button>
    </header>
  );
}

function DemoBanner() {
  const { data } = useDashboard();
  if (!data?.demoMode && !data?.demoCandidates) return null;
  return (
    <div className="mx-4 mb-2 flex items-start gap-3 rounded-xl border border-warn/40 bg-warn/10 px-4 py-2.5 text-sm text-[#f3c08a] sm:mx-6 lg:mx-8">
      <FlaskConical size={18} className="mt-0.5 shrink-0" />
      <p>
        <strong>Demo mode</strong> — {data.demoCandidates} sample candidate records are shown (tagged <em>DEMO</em>). They are not real applicants.
        {!data.demoMode && ' Demo mode is off; an administrator can remove them under Settings → Demo data.'}
      </p>
    </div>
  );
}

export function AppLayout() {
  const [open, setOpen] = useState(false);
  const { isInterviewer } = useAuth();
  return (
    <div className="flex min-h-full">
      <Sidebar open={open} onClose={() => setOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header onMenu={() => setOpen(true)} />
        {!isInterviewer && <DemoBanner />}
        <main className="flex-1 px-4 pb-10 pt-2 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
