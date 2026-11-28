import { useCallback, useEffect, useRef, useState } from 'react';
import clsx from 'clsx';
import { Mic } from 'lucide-react';
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
  'not-allowed': 'The microphone is blocked. Click the lock icon next to the address, allow the microphone, and try again.',
  'service-not-allowed': 'The microphone is blocked. Click the lock icon next to the address, allow the microphone, and try again.',
  'audio-capture': 'No microphone was found on this device.',
  network: 'Voice typing needs an internet connection.',
  'language-not-supported': 'This browser cannot type in the chosen language by voice.',
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
      toast('error', ERRORS[e.error] ?? `Voice typing stopped (${e.error}).`);
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
      window.setTimeout(() => start(next), 300);
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
          onClick={() => (listening ? stop() : start(lang))}
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
      {listening && heard && <p className="text-xs italic text-ink-3" aria-live="polite">{heard}…</p>}
      {!listening && (
        <p className="text-[11px] text-ink-3">
          Your voice is converted to text by the browser's speech service (Google, in Chrome). Check the text before saving.
        </p>
      )}
    </div>
  );
}
