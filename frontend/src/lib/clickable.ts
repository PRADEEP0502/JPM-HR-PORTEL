import type React from 'react';
import { useNavigate } from 'react-router-dom';

/** Clicks on these (inside the card or row) keep their own action instead of opening the card. */
const OWN_ACTION =
  'a,button,input,select,textarea,label,summary,[role="button"],[role="menu"],[role="menuitem"],[role="dialog"],[data-no-open],[data-open-card]';

type Props = {
  role: 'link';
  tabIndex: number;
  'data-open-card': '';
  onClick: (e: React.MouseEvent<HTMLElement>) => void;
  onAuxClick: (e: React.MouseEvent<HTMLElement>) => void;
  onKeyDown: (e: React.KeyboardEvent<HTMLElement>) => void;
};

/** True when the click belongs to the card itself (not a button, link or field inside it, not a pop-up, not a text selection). */
function isCardClick(e: React.MouseEvent<HTMLElement>) {
  const target = e.target as HTMLElement;
  // events from pop-ups rendered elsewhere in the page still bubble through React; ignore them
  if (!e.currentTarget.contains(target)) return false;
  const own = target.closest(OWN_ACTION);
  if (own && own !== e.currentTarget && e.currentTarget.contains(own)) return false;
  return !window.getSelection()?.toString();
}

/**
 * Makes a whole card or table row act like a link: click (or Enter) runs {@code action}; Ctrl / ⌘ / Shift-click and
 * middle-click pass {@code newTab = true}. Buttons, links and fields inside it keep working as before.
 * Add `cursor-pointer` to the element's class.
 */
export function clickable(action: (newTab: boolean) => void): Props {
  return {
    role: 'link',
    tabIndex: 0,
    'data-open-card': '',
    onClick: (e) => {
      if (isCardClick(e)) action(e.ctrlKey || e.metaKey || e.shiftKey);
    },
    onAuxClick: (e) => {
      if (e.button === 1 && isCardClick(e)) action(true);
    },
    onKeyDown: (e) => {
      if (e.key === 'Enter' && e.target === e.currentTarget) action(false);
    },
  };
}

/** `open(path)` gives the props that make a card or row open that page (see {@link clickable}); no path, no props. */
export function useOpenOnClick() {
  const navigate = useNavigate();
  return (to: string | null | undefined): Partial<Props> =>
    to ? clickable((newTab) => (newTab ? window.open(to, '_blank', 'noopener') : navigate(to))) : {};
}
