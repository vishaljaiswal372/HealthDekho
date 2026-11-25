import { formatEnum } from '../utils/format.js';

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="spinner" role="status" aria-live="polite">
      <span className="spinner__circle" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

/** type: 'error' | 'success' | 'info' | 'warning'. Shows field errors from the API as a list. */
export function Alert({ type = 'info', title, children, fieldErrors, onClose }) {
  const entries = Object.entries(fieldErrors ?? {});
  return (
    <div className={`alert alert--${type}`} role={type === 'error' ? 'alert' : 'status'}>
      <div className="alert__body">
        {title && <strong className="alert__title">{title}</strong>}
        {children && <div>{children}</div>}
        {entries.length > 0 && (
          <ul className="alert__list">
            {entries.map(([field, message]) => (
              <li key={field}>{message}</li>
            ))}
          </ul>
        )}
      </div>
      {onClose && (
        <button type="button" className="alert__close" onClick={onClose} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}

/** Shows an ApiError (or any Error) as an error alert. */
export function ErrorAlert({ error, onClose }) {
  if (!error) return null;
  return (
    <Alert type="error" fieldErrors={error.fieldErrors} onClose={onClose}>
      {error.message}
    </Alert>
  );
}

const BADGE_TONES = {
  ACTIVE: 'blue',
  FULLY_PAID: 'green',
  PAID: 'green',
  PENDING: 'grey',
  PARTIALLY_PAID: 'amber',
  OVERDUE: 'red',
  MATURED: 'grey',
  CANCELLED: 'grey',
  HIGH: 'green',
  MEDIUM: 'amber',
  LOW: 'grey',
};

export function Badge({ value }) {
  return <span className={`badge badge--${BADGE_TONES[value] ?? 'grey'}`}>{formatEnum(value)}</span>;
}

export function EmptyState({ children }) {
  return <p className="empty">{children}</p>;
}

export function Pagination({ page, totalPages, totalElements, onPageChange, disabled }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="btn btn--ghost" disabled={disabled || page === 0} onClick={() => onPageChange(page - 1)}>
        ← Previous
      </button>
      <span>
        Page {page + 1} of {totalPages} · {totalElements} results
      </span>
      <button
        type="button"
        className="btn btn--ghost"
        disabled={disabled || page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        Next →
      </button>
    </nav>
  );
}

/** Label/value pairs, used for read-only details (policy, registration form). */
export function DetailList({ items }) {
  return (
    <dl className="details">
      {items.map(([label, value]) => (
        <div key={label} className="details__row">
          <dt>{label}</dt>
          <dd>{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
