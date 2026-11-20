import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, FileWarning, UserRound } from 'lucide-react';
import { ApiError, api } from '../../lib/api';
import { Modal } from '../ui/Modal';
import { ErrorState, LoadingBlock, Spinner } from '../ui/States';

export interface ViewTarget {
  title: string;
  /** API path that returns the file, e.g. /documents/12/download or /candidates/65/resume */
  path: string;
  fileName: string;
  /** When set, the viewer also offers "Open full profile" (and shows it when there is no file). */
  profileLink?: string;
}

/**
 * Shows a PDF or image inside the portal. The file is fetched with the user's session and displayed from a
 * temporary in-memory URL, so it never has a shareable link. Other types can only be downloaded.
 */
export function DocumentViewer({ target, onClose }: { target: ViewTarget | null; onClose: () => void }) {
  const [url, setUrl] = useState<string | null>(null);
  const [type, setType] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    if (!target) return;
    let objectUrl: string | null = null;
    let cancelled = false;
    setUrl(null);
    setError(null);
    api
      .blob(target.path, { inline: true })
      .then((b) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(b);
        setType(b.type);
        setUrl(objectUrl);
      })
      .catch((e) => !cancelled && setError(e));
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [target]);

  const download = async () => {
    if (!target) return;
    setDownloading(true);
    try {
      await api.download(target.path, {}, target.fileName);
    } finally {
      setDownloading(false);
    }
  };

  const missing = error instanceof ApiError && error.status === 404;
  const isPdf = type === 'application/pdf';
  const isImage = type === 'image/png' || type === 'image/jpeg';
  return (
    <Modal
      open={!!target}
      onClose={onClose}
      size="full"
      title={target?.title ?? ''}
      description={target?.fileName}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Close</button>
          {target?.profileLink && (
            <Link to={target.profileLink} className="btn-secondary" onClick={onClose}>
              <UserRound size={14} /> Open full profile
            </Link>
          )}
          {!missing && (
            <button className="btn-primary" onClick={download} disabled={downloading || !url}>
              {downloading ? <Spinner size={14} /> : <Download size={14} />} Download
            </button>
          )}
        </>
      }
    >
      {missing ? (
        <div className="flex flex-col items-center gap-2 py-10 text-center text-sm text-ink-2">
          <FileWarning size={28} className="text-ink-3" />
          No resume on file for this candidate yet.
          {target?.profileLink && <span className="text-xs text-ink-3">Open the full profile to upload one or see the other details.</span>}
        </div>
      ) : error ? (
        <ErrorState error={error} />
      ) : !url ? (
        <LoadingBlock label="Opening document…" />
      ) : isPdf ? (
        <iframe title={target?.fileName ?? 'Document'} src={url} className="min-h-[60vh] w-full flex-1 rounded-lg border border-line bg-white" />
      ) : isImage ? (
        <div className="flex min-h-0 flex-1 items-center justify-center">
          <img src={url} alt={target?.fileName ?? 'Document'} className="max-h-full max-w-full rounded-lg border border-line object-contain" />
        </div>
      ) : (
        <div className="flex flex-col items-center gap-2 py-10 text-center text-sm text-ink-2">
          <FileWarning size={28} className="text-ink-3" />
          This file type cannot be shown in the browser. Use Download to open it.
        </div>
      )}
    </Modal>
  );
}
