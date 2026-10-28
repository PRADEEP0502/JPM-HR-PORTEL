import { useEffect, useState } from 'react';
import { onServerWaking } from '../../lib/api';
import { Spinner } from './States';

/** Thin bar shown while requests wait for a sleeping backend to start (free hosting). */
export function ServerWakingBar() {
  const [waking, setWaking] = useState(false);
  useEffect(() => onServerWaking(setWaking), []);
  if (!waking) return null;
  return (
    <div className="fixed inset-x-0 top-0 z-[60] flex justify-center px-4 pt-3" role="status" aria-live="polite">
      <div className="flex items-center gap-2 rounded-full border border-info/40 bg-raised px-4 py-2 text-sm text-ink shadow-card">
        <Spinner size={15} />
        Starting the server… this can take up to a minute after a quiet period. Please wait.
      </div>
    </div>
  );
}
