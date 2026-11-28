import { useCallback, useEffect, useRef, useState } from 'react';
import clsx from 'clsx';
import { Mic, MicOff } from 'lucide-react';
import { DICTATION_LANGS, type DictationLang } from '../../lib/dictation';
import { useToast } from '../../lib/toast';

interface RecognitionResult {
  isFinal: boolean;
  0: { transcript: string };
}
interface RecognitionEvent {
  resultIndex: number;
  results: { length: number; [i: number]: RecognitionResult };
}
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((e: RecognitionEvent) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}
type RecognitionCtor = new () => Recognition;

const LANG_KEY = 'hrms.dictationLang';

function recognitionCtor(): RecognitionCtor | null {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

function savedLang(): DictationLang {
  try {
    const v = localStorage.getItem(LANG_KEY);
    if (v === 'en-IN' || v === 'ta-IN') return v;
  } catch {
    /* storage can be blocked */
  }
  return 'en-IN';
}

const ERRORS: Record<string, string> = {
  network: 'Voice typing needs an internet connection.',
  'language-not-supported': 'This browser cannot type in the chosen language by voice.',
};

/** What is wrong with the microphone, so the person is told exactly what to switch on. */
type MicIssue = 'blocked' | 'missing' | 'busy';

function issueOf(error: unknown): MicIssue {
  const name = (error as { name?: string } | null)?.name;
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'missing';
  if (name === 'NotReadableError' || name === 'AbortError' || name === 'TrackStartError') return 'busy';
  return 'blocked';
}

/**
 * Asks the browser for the microphone first. If it is switched off for this site the browser shows its "Allow" box
 * right here; if it was blocked earlier the person gets steps to switch it on. Resolves to null when it is ready.
 */
async function checkMicrophone(): Promise<MicIssue | null> {
  if (!navigator.mediaDevices?.getUserMedia) return null; // let the speech engine try on its own
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((t) => t.stop());
    return null;
  } catch (e) {
    return issueOf(e);
  }
}

const ISSUE_TITLE: Record<MicIssue, string> = {
  blocked: 'The microphone is off for this site',
  missing: 'No microphone was found',
  busy: 'The microphone is being used by another app',
};
const ISSUE_STEPS: Record<MicIssue, string[]> = {
  blocked: [
    'Click the lock (or settings) icon at the left of the address bar.',
    'Switch Microphone to ON / Allow.',
    'Click Try again below. If it still says blocked: Windows Settings → Privacy & security → Microphone → turn it on.',
  ],
  missing: ['Plug in or connect your microphone or headset.', 'In Windows Settings → System → Sound → Input, choose it.', 'Click Try again below.'],
  busy: ['Close other apps that use the microphone (Zoom, Teams, Meet, recorders).', 'Click Try again below.'],
};

/**
 * Speak instead of typing: the recognised text is handed to onText (final pieces only) and the words being heard are
 * shown while the person talks. Works in Chrome and Edge (desktop and Android). The browser's speech service
 * (Google, in Chrome) turns the voice into text; the portal never receives or stores audio.
 */
export function DictationButton({ onText, stopToken, className }: {
  onText: (spoken: string, lang: DictationLang) => void;
  /** Change this number to stop listening (for example when the note is saved). */
  stopToken?: number;
  className?: string;
}) {
  const toast = useToast();
  const [supported] = useState(() => recognitionCtor() !== null);
  const [lang, setLang] = useState<DictationLang>(savedLang);
  const [listening, setListening] = useState(false);
  const [heard, setHeard] = useState('');
  const [issue, setIssue] = useState<MicIssue | null>(null);
  const [asking, setAsking] = useState(false);
  const rec = useRef<Recognition | null>(null);
  const wanted = useRef(false);
  const onTextRef = useRef(onText);
  onTextRef.current = onText;

  /**
   * Stops listening. A normal stop still lets the words just spoken arrive; with discard (the note was saved) they are
   * thrown away, so nothing spoken before saving is typed into the next note.
   */
  const stop = useCallback((discard = false) => {
    wanted.current = false;
    const r = rec.current;
    if (discard) {
      rec.current = null;
      r?.abort();
    } else {
      r?.stop();
    }
    setListening(false);
    setHeard('');
  }, []);

  const start = useCallback((language: DictationLang) => {
    const Ctor = recognitionCtor();
    if (!Ctor) return;
    const r = new Ctor();
    r.lang = language;
    r.continuous = true;
    r.interimResults = true;
    r.maxAlternatives = 1;
    let lastStart = Date.now();
    let quickEnds = 0;
    r.onresult = (e) => {
      if (rec.current !== r) return;
      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const piece = e.results[i];
        if (piece.isFinal) onTextRef.current(piece[0].transcript, language);
        else interim += piece[0].transcript;
      }
      setHeard(interim);
    };
    r.onerror = (e) => {
      if (e.error === 'no-speech' || e.error === 'aborted') return;
      wanted.current = false;
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') setIssue('blocked');
      else if (e.error === 'audio-capture') setIssue('missing');
      else toast('error', ERRORS[e.error] ?? `Voice typing stopped (${e.error}).`);
    };
    r.onend = () => {
      // browsers end a session after a few quiet seconds: carry on while the person is still dictating
      if (wanted.current && rec.current === r) {
        quickEnds = Date.now() - lastStart < 1000 ? quickEnds + 1 : 0;
        if (quickEnds >= 3) {
          wanted.current = false;
        } else {
          lastStart = Date.now();
          window.setTimeout(() => {
            try {
              if (wanted.current && rec.current === r) r.start();
            } catch {
              wanted.current = false;
              setListening(false);
            }
          }, 250);
          return;
        }
      }
      setListening(false);
      setHeard('');
    };
    rec.current = r;
    wanted.current = true;
    try {
      r.start();
      setListening(true);
    } catch {
      wanted.current = false;
      toast('error', 'Could not start the microphone. Try again.');
    }
  }, [toast]);

  useEffect(() => {
    if (stopToken !== undefined) stop(true);
  }, [stopToken, stop]);

  // when the person switches the microphone on from the address bar, drop the warning at once
  useEffect(() => {
    let status: PermissionStatus | null = null;
    let alive = true;
    navigator.permissions
      ?.query({ name: 'microphone' as PermissionName })
      .then((st) => {
        if (!alive) return;
        status = st;
        st.onchange = () => {
          if (st.state === 'granted') setIssue(null);
        };
      })
      .catch(() => undefined);
    return () => {
      alive = false;
      if (status) status.onchange = null;
    };
  }, []);

  /** Checks the microphone (showing the browser's Allow box if needed), then starts listening. */
  const begin = useCallback(async (language: DictationLang) => {
    setAsking(true);
    const problem = await checkMicrophone();
    setAsking(false);
    setIssue(problem);
    if (!problem) start(language);
  }, [start]);

  useEffect(() => () => {
    wanted.current = false;
    rec.current?.abort();
  }, []);

  const changeLang = (next: DictationLang) => {
    setLang(next);
    try {
      localStorage.setItem(LANG_KEY, next);
    } catch {
      /* storage can be blocked */
    }
    if (listening) {
      stop();
      window.setTimeout(() => void begin(next), 300);
    }
  };

  if (!supported) {
    return (
      <p className={clsx('text-[11px] text-ink-3', className)}>
        Voice typing works in Chrome or Edge. Open the portal in one of them to speak your notes.
      </p>
    );
  }

  return (
    <div className={clsx('space-y-1.5', className)}>
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          disabled={asking}
          onClick={() => (listening ? stop() : void begin(lang))}
          aria-pressed={listening}
          className={clsx(
            'inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold ring-1 transition',
            listening ? 'bg-bad/15 text-bad ring-bad/40' : 'bg-panel text-ink-2 ring-line hover:text-ink',
          )}
          title={listening ? 'Stop voice typing' : 'Speak your note instead of typing'}
        >
          {listening ? (
            <>
              <span className="relative flex h-2 w-2"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-bad opacity-75" /><span className="relative inline-flex h-2 w-2 rounded-full bg-bad" /></span>
              Stop
            </>
          ) : (
            <>
              <Mic size={13} /> Speak
            </>
          )}
        </button>
        <select
          className="input w-auto py-1 text-xs"
          aria-label="Language to speak in"
          value={lang}
          onChange={(e) => changeLang(e.target.value as DictationLang)}
        >
          {DICTATION_LANGS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
        </select>
        {listening && <span className="text-[11px] text-bad">Listening… speak one language at a time</span>}
      </div>
      {asking && (
        <p className="rounded-lg bg-info/10 px-3 py-2 text-xs text-info" role="status">
          Click <strong>Allow</strong> in the box at the top-left of the browser to switch the microphone on. Voice typing starts right after.
        </p>
      )}
      {issue && !asking && (
        <div className="space-y-2 rounded-lg border border-warn/40 bg-warn/10 px-3 py-2.5 text-xs text-[#f3c08a]" role="alert">
          <p className="flex items-center gap-1.5 font-semibold"><MicOff size={14} /> {ISSUE_TITLE[issue]}</p>
          <ol className="list-decimal space-y-0.5 pl-4">
            {ISSUE_STEPS[issue].map((step) => <li key={step}>{step}</li>)}
          </ol>
          <button type="button" className="btn-secondary px-2.5 py-1 text-xs" onClick={() => void begin(lang)}>Try again</button>
        </div>
      )}
      {listening && heard && <p className="text-xs italic text-ink-3" aria-live="polite">{heard}…</p>}
      {!listening && (
        <p className="text-[11px] text-ink-3">
          Your voice is converted to text by the browser's speech service (Google, in Chrome). Check the text before saving.
        </p>
      )}
    </div>
  );
}
