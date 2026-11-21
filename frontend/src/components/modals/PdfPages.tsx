import { useEffect, useRef, useState } from 'react';
import { Minus, Plus, Scan } from 'lucide-react';
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist/legacy/build/pdf.mjs';
import { ErrorState, LoadingBlock } from '../ui/States';

type PdfJs = typeof import('pdfjs-dist/legacy/build/pdf.mjs');
let pdfjsPromise: Promise<PdfJs> | null = null;

/** pdf.js is loaded only when a PDF is opened (the legacy build also works on older iPads and Android tablets). */
function loadPdfJs(): Promise<PdfJs> {
  pdfjsPromise ??= Promise.all([
    import('pdfjs-dist/legacy/build/pdf.mjs'),
    import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'),
  ]).then(([pdfjs, worker]) => {
    pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
    return pdfjs;
  });
  return pdfjsPromise;
}

const ZOOMS = [0.75, 1, 1.25, 1.5, 2, 3];

/**
 * Every page of a PDF, one below the other, drawn by the portal itself. Built-in browser viewers on tablets and
 * phones show only the first page of a PDF inside a page (or nothing at all), so the browser viewer is not used.
 */
export function PdfPages({ data, name }: { data: Blob; name: string }) {
  const scroller = useRef<HTMLDivElement>(null);
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [sizes, setSizes] = useState<{ w: number; h: number }[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [width, setWidth] = useState(0);
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    const el = scroller.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    let cancelled = false;
    let loaded: PDFDocumentProxy | null = null;
    setDoc(null);
    setSizes([]);
    setError(null);
    (async () => {
      const pdfjs = await loadPdfJs();
      const pdf = await pdfjs.getDocument({ data: new Uint8Array(await data.arrayBuffer()), isEvalSupported: false }).promise;
      loaded = pdf;
      const all: { w: number; h: number }[] = [];
      for (let i = 1; i <= pdf.numPages; i++) {
        const vp = (await pdf.getPage(i)).getViewport({ scale: 1 });
        all.push({ w: vp.width, h: vp.height });
      }
      if (cancelled) return;
      setSizes(all);
      setDoc(pdf);
    })().catch((e) => !cancelled && setError(e));
    return () => {
      cancelled = true;
      void loaded?.destroy();
    };
  }, [data]);

  // Page width on screen: the space available (minus padding), at most 1000px at 100 %, times the zoom.
  const fit = Math.max(200, Math.min(width - 24, 1000));
  const pageWidth = Math.round(fit * zoom);
  const zoomIndex = ZOOMS.indexOf(zoom);

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-line">
      <div className="flex flex-wrap items-center gap-2 border-b border-line bg-panel px-3 py-1.5 text-xs text-ink-2">
        <span>{doc ? `${doc.numPages} page${doc.numPages === 1 ? '' : 's'}` : 'Loading pages…'}</span>
        <span className="flex-1" />
        <button className="btn-ghost p-1.5" aria-label="Zoom out" disabled={zoomIndex <= 0} onClick={() => setZoom(ZOOMS[zoomIndex - 1])}>
          <Minus size={15} />
        </button>
        <span className="w-11 text-center tabular-nums">{Math.round(zoom * 100)}%</span>
        <button className="btn-ghost p-1.5" aria-label="Zoom in" disabled={zoomIndex >= ZOOMS.length - 1} onClick={() => setZoom(ZOOMS[zoomIndex + 1])}>
          <Plus size={15} />
        </button>
        <button className="btn-ghost p-1.5" aria-label="Fit to width" title="Fit to width" disabled={zoom === 1} onClick={() => setZoom(1)}>
          <Scan size={15} />
        </button>
      </div>
      <div ref={scroller} className="min-h-[60vh] flex-1 overflow-auto bg-[#3b3c40] p-3" style={{ WebkitOverflowScrolling: 'touch' }}>
        {error ? (
          <div className="rounded-lg bg-panel p-4"><ErrorState error={error} /></div>
        ) : !doc || width === 0 ? (
          <LoadingBlock label="Opening document…" />
        ) : (
          <div className="mx-auto flex w-max min-w-full flex-col items-center gap-3">
            {sizes.map((s, i) => (
              <PdfPage key={`${i}-${pageWidth}`} doc={doc} number={i + 1} cssWidth={pageWidth} cssHeight={Math.round((pageWidth * s.h) / s.w)} root={scroller.current} label={`${name} - page ${i + 1}`} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** One page: a white placeholder of the right size, drawn when it scrolls near the screen (saves tablet memory). */
function PdfPage({ doc, number, cssWidth, cssHeight, root, label }: {
  doc: PDFDocumentProxy;
  number: number;
  cssWidth: number;
  cssHeight: number;
  root: Element | null;
  label: string;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = canvas.current;
    if (!el || visible) return;
    const io = new IntersectionObserver(([entry]) => entry.isIntersecting && setVisible(true), { root, rootMargin: '600px 0px' });
    io.observe(el);
    return () => io.disconnect();
  }, [root, visible]);

  useEffect(() => {
    if (!visible) return;
    let task: RenderTask | null = null;
    let cancelled = false;
    (async () => {
      const page = await doc.getPage(number);
      const el = canvas.current;
      if (cancelled || !el) return;
      const base = page.getViewport({ scale: 1 });
      // sharp on high-density screens, but capped so large zooms do not run tablets out of memory
      const ratio = Math.min(window.devicePixelRatio || 1, 2, 4096 / cssWidth);
      const viewport = page.getViewport({ scale: (cssWidth / base.width) * ratio });
      el.width = Math.floor(viewport.width);
      el.height = Math.floor(viewport.height);
      const ctx = el.getContext('2d');
      if (!ctx) return;
      task = page.render({ canvasContext: ctx, viewport });
      await task.promise;
    })().catch(() => {
      // a cancelled render (zoom changed or viewer closed) is expected
    });
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [visible, doc, number, cssWidth]);

  return (
    <canvas
      ref={canvas}
      role="img"
      aria-label={label}
      className="block bg-white shadow-card"
      style={{ width: cssWidth, height: cssHeight }}
    />
  );
}
