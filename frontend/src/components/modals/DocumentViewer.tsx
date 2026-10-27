import { useEffect, useState } from 'react';
import { Download, FileWarning } from 'lucide-react';
import { api } from '../../lib/api';
import { Modal } from '../ui/Modal';
import { ErrorState, LoadingBlock, Spinner } from '../ui/States';

export interface ViewTarget {
  title: string;
  /** API path that returns the file, e.g. /documents/12/download or /candidates/65/resume */
  path: string;
  fileName: string;
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

  const isPdf = type === 'application/pdf';
  const isImage = type === 'image/png' || type === 'image/jpeg';
  return (
    <Modal
      open={!!target}
      onClose={onClose}
      size="lg"
      title={target?.title ?? ''}
      description={target?.fileName}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Close</button>
          <button className="btn-primary" onClick={download} disabled={downloading}>
            {downloading ? <Spinner size={14} /> : <Download size={14} />} Download
          </button>
        </>
      }
    >
      {error ? (
        <ErrorState error={error} />
      ) : !url ? (
        <LoadingBlock label="Opening document…" />
      ) : isPdf ? (
        <iframe title={target?.fileName ?? 'Document'} src={url} className="h-[70vh] w-full rounded-lg border border-line bg-white" />
      ) : isImage ? (
        <img src={url} alt={target?.fileName ?? 'Document'} className="mx-auto max-h-[70vh] rounded-lg border border-line" />
      ) : (
        <div className="flex flex-col items-center gap-2 py-10 text-center text-sm text-ink-2">
          <FileWarning size={28} className="text-ink-3" />
          This file type cannot be shown in the browser. Use Download to open it.
        </div>
      )}
    </Modal>
  );
}
