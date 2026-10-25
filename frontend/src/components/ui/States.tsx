import { AlertTriangle, Inbox, Loader2, RotateCw } from 'lucide-react';
import type { ReactNode } from 'react';
import clsx from 'clsx';

export const Spinner = ({ size = 18, className }: { size?: number; className?: string }) => (
  <Loader2 size={size} className={clsx('animate-spin', className)} aria-label="Loading" />
);

export function LoadingBlock({ label = 'Loading…', className }: { label?: string; className?: string }) {
  return (
    <div className={clsx('flex items-center justify-center gap-2 py-12 text-sm text-ink-3', className)}>
      <Spinner /> {label}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-xl bg-raised', className)} />;
}

export function EmptyState({ title, hint, action, icon }: { title: string; hint?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center">
      <div className="mb-1 flex h-11 w-11 items-center justify-center rounded-full bg-raised text-ink-3">{icon ?? <Inbox size={20} />}</div>
      <p className="text-sm font-medium text-ink">{title}</p>
      {hint && <p className="max-w-sm text-sm text-ink-3">{hint}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong.';
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center" role="alert">
      <div className="mb-1 flex h-11 w-11 items-center justify-center rounded-full bg-bad/15 text-bad">
        <AlertTriangle size={20} />
      </div>
      <p className="text-sm font-medium">Could not load data</p>
      <p className="max-w-md text-sm text-ink-3">{message}</p>
      {onRetry && (
        <button className="btn-secondary mt-2" onClick={onRetry}>
          <RotateCw size={14} /> Try again
        </button>
      )}
    </div>
  );
}
