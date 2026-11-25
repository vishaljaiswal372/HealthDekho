import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getRecommendationHistory, requestRecommendations } from '../api/healthDekhoApi.js';
import { useApi } from '../hooks/useApi.js';
import { Alert, Badge, EmptyState, ErrorAlert, Spinner } from '../components/ui.jsx';
import { formatDateTime, formatEnum, formatMoney, formatTerm, todayIso } from '../utils/format.js';

const TERM_OPTIONS = [1, 5, 10, 15, 20, 25, 30];
const POLICY_TYPES = ['TERM', 'HEALTH', 'SAVINGS', 'LIFE'];
const MAX_GOALS = 500;

function RecommendationTable({ items }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Product Code</th>
            <th>Policy Name</th>
            <th>Policy Type</th>
            <th>Policy Term</th>
            <th className="num">Sum Insured</th>
            <th>Premium Frequency</th>
            <th className="num">Premium Amount</th>
            <th>Recommendation Reason</th>
            <th>Suitability</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.recommendationItemId}>
              <td>{item.rank}</td>
              <td className="mono">{item.productCode}</td>
              <td>{item.policyName}</td>
              <td>{formatEnum(item.policyType)}</td>
              <td>{formatTerm(item.termMonths)}</td>
              <td className="num">{formatMoney(item.sumInsured)}</td>
              <td>{formatEnum(item.premiumFrequency)}</td>
              <td className="num">{formatMoney(item.premiumAmount)}</td>
              <td className="reason">{item.recommendationReason}</td>
              <td><Badge value={item.suitability} /></td>
              <td>
                {item.selectedForRegistration ? (
                  <span className="muted small">Registered</span>
                ) : (
                  <Link
                    className="btn btn--primary btn--small"
                    to={`/register/${item.productId}?recommendationItemId=${item.recommendationItemId}`}
                  >
                    Register
                  </Link>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** FR-2: personalised, AI-ranked recommendations from the products in the database. */
export default function RecommendationsPage() {
  const [form, setForm] = useState({ dateOfBirth: '', insuranceGoals: '', preferredTermYears: '', preferredPolicyType: '' });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const history = useApi(getRecommendationHistory, []);

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    const inputs = {
      dateOfBirth: form.dateOfBirth || undefined,
      insuranceGoals: form.insuranceGoals.trim() || undefined,
      preferredTermMonths: form.preferredTermYears ? Number(form.preferredTermYears) * 12 : undefined,
      preferredPolicyType: form.preferredPolicyType || undefined,
    };
    if (Object.values(inputs).every((v) => v === undefined)) {
      setError({ message: 'Fill in at least one field so we can tailor the recommendations.' });
      return;
    }
    setSubmitting(true);
    setResult(null);
    try {
      setResult(await requestRecommendations(inputs));
      history.reload();
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const pastRequests = (history.data ?? []).filter((r) => r.requestId !== result?.requestId);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>AI policy recommendations</h1>
          <p className="muted">
            Tell us a little about your needs. Our AI ranks the products you are eligible for. Prices and eligibility
            always come from HealthDekho, not the AI.
          </p>
        </div>
      </div>

      <form className="card form-columns" onSubmit={handleSubmit} noValidate>
        <label className="field">
          <span className="field__label">Date of Birth</span>
          <input type="date" max={todayIso()} value={form.dateOfBirth} onChange={update('dateOfBirth')} />
          <span className="field__hint">Optional. Your profile date of birth is used if left empty.</span>
        </label>
        <label className="field">
          <span className="field__label">Preferred Policy Term</span>
          <select value={form.preferredTermYears} onChange={update('preferredTermYears')}>
            <option value="">No preference</option>
            {TERM_OPTIONS.map((y) => (
              <option key={y} value={y}>
                {y} {y === 1 ? 'year' : 'years'}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">Insurance Type</span>
          <select value={form.preferredPolicyType} onChange={update('preferredPolicyType')}>
            <option value="">Any type</option>
            {POLICY_TYPES.map((t) => (
              <option key={t} value={t}>
                {formatEnum(t)}
              </option>
            ))}
          </select>
        </label>
        <label className="field field--wide">
          <span className="field__label">Insurance Goals and Preferences</span>
          <textarea
            rows={3}
            maxLength={MAX_GOALS}
            value={form.insuranceGoals}
            onChange={update('insuranceGoals')}
            placeholder="e.g. Protect my family's income, and build some savings for my child's education"
          />
          <span className="field__hint">
            {form.insuranceGoals.length}/{MAX_GOALS}
          </span>
        </label>
        <div className="field--wide actions">
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Generating recommendations…' : '✨ Get recommendations'}
          </button>
        </div>
      </form>

      <ErrorAlert error={error} onClose={() => setError(null)} />
      {submitting && <Spinner label="Asking the AI advisor… this can take a few seconds." />}

      {result && (
        <section className="card">
          <div className="card__header">
            <h2>Recommended for you</h2>
            <span className="muted small">Based on age {result.calculatedAge}</span>
          </div>
          {result.recommendations.length === 0 ? (
            <Alert type="info">{result.message}</Alert>
          ) : (
            <RecommendationTable items={result.recommendations} />
          )}
        </section>
      )}

      <section className="card">
        <h2>Previous recommendations</h2>
        <ErrorAlert error={history.error} />
        {history.loading && <Spinner label="Loading history…" />}
        {!history.loading && !history.error && pastRequests.length === 0 && (
          <EmptyState>No earlier recommendations.</EmptyState>
        )}
        {pastRequests.map((request) => (
          <details key={request.requestId} className="history">
            <summary>
              {formatDateTime(request.requestedAt)} · {request.recommendations.length} product(s) · age{' '}
              {request.calculatedAge}
            </summary>
            {request.recommendations.length === 0 ? (
              <EmptyState>{request.message}</EmptyState>
            ) : (
              <RecommendationTable items={request.recommendations} />
            )}
          </details>
        ))}
      </section>
    </>
  );
}
