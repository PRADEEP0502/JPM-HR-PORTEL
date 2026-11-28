/** Voice typing helpers: what the speech service heard becomes tidy note text. */

export type DictationLang = 'en-IN' | 'ta-IN';

export const DICTATION_LANGS: { value: DictationLang; label: string }[] = [
  { value: 'en-IN', label: 'English' },
  { value: 'ta-IN', label: 'தமிழ் (Tamil)' },
];

const capitalise = (s: string) => s.replace(/^(\s*)([a-z])/, (_, space: string, c: string) => space + c.toUpperCase());

/**
 * Cleans one piece of recognised speech. In English the spoken words "full stop", "comma", "question mark" and
 * "new line" become the punctuation. ("period" is not used: it is a normal word, as in "probation period".)
 */
export function polishSpeech(text: string, lang: DictationLang): string {
  let t = text.replace(/\s+/g, ' ').trim();
  if (lang === 'en-IN') {
    t = t
      .replace(/\s*\bfull stop\b\s*/gi, '. ')
      .replace(/\s*\bcomma\b\s*/gi, ', ')
      .replace(/\s*\bquestion mark\b\s*/gi, '? ')
      .replace(/\s*\b(?:new line|next line)\b\s*/gi, '\n');
  }
  return t
    .replace(/[ \t]+([.,?!])/g, '$1')
    .replace(/[ \t]{2,}/g, ' ')
    .trim();
}

/**
 * Adds spoken text to the end of the note: one space between pieces, a capital letter after a full stop or a new
 * line, punctuation stuck to the previous word, and never longer than `max` characters.
 */
export function appendDictation(current: string, spoken: string, lang: DictationLang, max: number): string {
  const t = polishSpeech(spoken, lang);
  if (!t) return current;
  let out: string;
  if (!current.trim()) {
    out = capitalise(t);
  } else if (/^[.,?!]/.test(t)) {
    out = current.trimEnd() + t;
  } else if (/\n\s*$/.test(current)) {
    out = current + capitalise(t);
  } else if (/[.!?]\s*$/.test(current)) {
    out = current.trimEnd() + ' ' + capitalise(t);
  } else {
    out = current.trimEnd() + ' ' + t;
  }
  return out.slice(0, max);
}
