import { ChevronLeft, ChevronRight } from 'lucide-react';

interface Props {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPage: (p: number) => void;
  onSize?: (s: number) => void;
}

export function Pagination({ page, totalPages, totalElements, size, onPage, onSize }: Props) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 px-1 pt-4 text-sm text-ink-3">
      <div className="flex items-center gap-3">
        <span>
          {from}–{to} of {totalElements}
        </span>
        {onSize && (
          <label className="flex items-center gap-2">
            <span className="sr-only sm:not-sr-only">Rows</span>
            <select className="input w-auto py-1" value={size} onChange={(e) => onSize(Number(e.target.value))}>
              {[10, 20, 50].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <div className="flex items-center gap-1">
        <button className="btn-ghost px-2" disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page">
          <ChevronLeft size={16} />
        </button>
        <span className="px-2 text-ink-2">
          Page {totalPages === 0 ? 0 : page + 1} / {totalPages}
        </span>
        <button className="btn-ghost px-2" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page">
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  );
}
