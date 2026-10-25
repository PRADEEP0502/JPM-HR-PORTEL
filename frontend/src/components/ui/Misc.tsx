import clsx from 'clsx';
import type { ReactNode } from 'react';
import { initials } from '../../lib/format';

/** Progress ring; the percentage is also printed in the centre so it never relies on the arc alone. */
export function RingGauge({ value, color, size = 56 }: { value: number; color: string; size?: number }) {
  const pct = Math.max(0, Math.min(100, Math.round(value)));
  const r = (size - 8) / 2;
  const c = 2 * Math.PI * r;
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`${pct}%`} className="shrink-0">
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#2b2c31" strokeWidth={6} />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={color}
        strokeWidth={6}
        strokeLinecap="round"
        strokeDasharray={`${(pct / 100) * c} ${c}`}
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
        style={{ transition: 'stroke-dasharray .6s ease' }}
      />
      <text x="50%" y="50%" dominantBaseline="central" textAnchor="middle" className="fill-ink-2" fontSize={size / 4.4} fontWeight={600}>
        {pct}%
      </text>
    </svg>
  );
}

const avatarTones = ['bg-[#3a4f7a]', 'bg-[#6b4a2e]', 'bg-[#2f5f49]', 'bg-[#5a3d6b]', 'bg-[#6b3a3a]', 'bg-[#4a5560]'];

export function Avatar({ name, size = 'md', ring }: { name: string; size?: 'sm' | 'md' | 'lg' | 'xl'; ring?: boolean }) {
  const idx = [...name].reduce((a, ch) => a + ch.charCodeAt(0), 0) % avatarTones.length;
  return (
    <span
      title={name}
      className={clsx(
        'inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white',
        avatarTones[idx],
        size === 'sm' && 'h-8 w-8 text-xs',
        size === 'md' && 'h-10 w-10 text-sm',
        size === 'lg' && 'h-14 w-14 text-lg',
        size === 'xl' && 'h-20 w-20 text-2xl',
        ring && 'ring-4 ring-white/25',
      )}
    >
      {initials(name)}
    </span>
  );
}

export function Field({ label, error, hint, children, className }: { label: string; error?: string; hint?: string; children: ReactNode; className?: string }) {
  return (
    <label className={clsx('block', className)}>
      <span className="label">{label}</span>
      {children}
      {error ? <span className="mt-1 block text-xs text-bad">{error}</span> : hint ? <span className="mt-1 block text-xs text-ink-3">{hint}</span> : null}
    </label>
  );
}

export function SectionTitle({ title, action, subtitle }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-start justify-between gap-3">
      <div>
        <h2 className="text-[15px] font-semibold text-ink">{title}</h2>
        {subtitle && <p className="mt-0.5 text-xs text-ink-3">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
