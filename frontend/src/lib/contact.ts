/**
 * Client-side mirror of the server's contact checks, used only to enable/disable buttons and show
 * hints. The server re-validates every action and is the source of truth.
 */
export function phoneProblem(mobile: string | null | undefined): string | null {
  if (!mobile || !mobile.trim()) return 'No mobile number on file';
  const t = mobile.trim();
  let digits = t.replace(/\D/g, '');
  if (t.startsWith('00')) digits = digits.slice(2);
  else if (!t.startsWith('+')) {
    if (digits.length === 10) digits = '91' + digits;
    else if (digits.length === 11 && digits.startsWith('0')) digits = '91' + digits.slice(1);
  }
  if (digits.length < 11 || digits.length > 15) return `"${mobile}" is not a valid mobile number`;
  if (digits.startsWith('91') && (digits.length !== 12 || !'6789'.includes(digits[2]))) return `"${mobile}" is not a valid Indian mobile number`;
  return null;
}

export function emailProblem(email: string | null | undefined): string | null {
  if (!email || !email.trim()) return 'No email address on file';
  return /^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email.trim()) ? null : `"${email}" is not a valid email address`;
}
