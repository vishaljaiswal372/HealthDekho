const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });

export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '—';
  return inr.format(Number(value));
}

const dateFormat = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });

/** Formats an ISO date (yyyy-MM-dd) without timezone shifts. */
export function formatDate(iso) {
  if (!iso) return '—';
  const [y, m, d] = String(iso).slice(0, 10).split('-').map(Number);
  return dateFormat.format(new Date(y, m - 1, d));
}

export function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

/** 240 -> "20 years", 18 -> "1 year 6 months", 6 -> "6 months". */
export function formatTerm(months) {
  if (!months && months !== 0) return '—';
  const years = Math.floor(months / 12);
  const rest = months % 12;
  const parts = [];
  if (years) parts.push(`${years} ${years === 1 ? 'year' : 'years'}`);
  if (rest || !years) parts.push(`${rest} ${rest === 1 ? 'month' : 'months'}`);
  return parts.join(' ');
}

/** "HALF_YEARLY" -> "Half-Yearly", "NET_BANKING" -> "Net Banking". */
export function formatEnum(value) {
  if (!value) return '—';
  const special = { HALF_YEARLY: 'Half-Yearly', PARTIALLY_PAID: 'Partially Paid', FULLY_PAID: 'Fully Paid', UPI: 'UPI' };
  if (special[value]) return special[value];
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

/** Today's date as yyyy-MM-dd in the user's local timezone. */
export function todayIso(now = new Date()) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
