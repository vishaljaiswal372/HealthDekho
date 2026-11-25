import { useState } from 'react';
import { searchPolicies } from '../api/healthDekhoApi.js';
import { Alert, Badge, ErrorAlert, Pagination, Spinner } from '../components/ui.jsx';
import { formatDate, formatEnum, formatMoney, todayIso } from '../utils/format.js';
import { buildSearchCriteria } from '../utils/search.js';

const PAGE_SIZE = 10;

/** FR-5: search policies by identifier, customer name, or identifier + date of birth. */
export default function SearchPage() {
  const [form, setForm] = useState({ policyNumber: '', customerName: '', dateOfBirth: '' });
  const [criteria, setCriteria] = useState(null);
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  const runSearch = async (params, page) => {
    setLoading(true);
    setError(null);
    try {
      setResults(await searchPolicies({ ...params, page, size: PAGE_SIZE }));
      setCriteria(params);
    } catch (err) {
      setError(err);
      setResults(null);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    const { error: problem, params } = buildSearchCriteria(form);
    if (problem) {
      setError({ message: problem });
      setResults(null);
      return;
    }
    runSearch(params, 0);
  };

  const handleClear = () => {
    setForm({ policyNumber: '', customerName: '', dateOfBirth: '' });
    setResults(null);
    setCriteria(null);
    setError(null);
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Policy search</h1>
          <p className="muted">
            Search by policy identifier, by customer name, or by policy identifier together with date of birth.
          </p>
        </div>
      </div>

      <form className="card search-grid" onSubmit={handleSubmit} noValidate>
        <label className="field">
          <span className="field__label">Customer Name</span>
          <input value={form.customerName} onChange={update('customerName')} placeholder="e.g. Sharma" />
        </label>
        <label className="field">
          <span className="field__label">Policy Identifier</span>
          <input value={form.policyNumber} onChange={update('policyNumber')} placeholder="HDP-2026-0001" />
        </label>
        <label className="field">
          <span className="field__label">Date of Birth</span>
          <input type="date" max={todayIso()} value={form.dateOfBirth} onChange={update('dateOfBirth')} />
        </label>
        <div className="search-grid__actions">
          <button type="submit" className="btn btn--primary" disabled={loading}>
            {loading ? 'Searching…' : 'Search'}
          </button>
          <button type="button" className="btn btn--ghost" onClick={handleClear} disabled={loading}>
            Clear
          </button>
        </div>
      </form>

      <ErrorAlert error={error} onClose={() => setError(null)} />
      {loading && !results && <Spinner label="Searching policies…" />}

      {results && (
        <section className="card">
          <div className="card__header">
            <h2>Results</h2>
            <span className="muted small">{results.totalElements} found</span>
          </div>
          {results.content.length === 0 ? (
            <Alert type="info">{results.message ?? 'No policies match the search criteria.'}</Alert>
          ) : (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Policy Identifier</th>
                      <th>Customer Name</th>
                      <th>Date of Birth</th>
                      <th>Policy Name</th>
                      <th>Policy Type</th>
                      <th>Policy Start Date</th>
                      <th>Maturity Date</th>
                      <th>Premium</th>
                      <th className="num">Sum Insured</th>
                      <th>Status</th>
                      <th className="num">Remaining Premium</th>
                    </tr>
                  </thead>
                  <tbody>
                    {results.content.map((r) => (
                      <tr key={r.policyNumber}>
                        <td className="mono">{r.policyNumber}</td>
                        <td>{r.customerName}</td>
                        <td>{formatDate(r.dateOfBirth)}</td>
                        <td>{r.policyName}</td>
                        <td>{formatEnum(r.policyType)}</td>
                        <td>{formatDate(r.startDate)}</td>
                        <td>{formatDate(r.maturityDate)}</td>
                        <td>
                          {formatMoney(r.premiumAmount)} <span className="muted small">{formatEnum(r.premiumFrequency)}</span>
                        </td>
                        <td className="num">{formatMoney(r.sumInsured)}</td>
                        <td><Badge value={r.status} /></td>
                        <td className="num">{formatMoney(r.remainingPremium)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={results.page}
                totalPages={results.totalPages}
                totalElements={results.totalElements}
                disabled={loading}
                onPageChange={(page) => runSearch(criteria, page)}
              />
            </>
          )}
        </section>
      )}
    </>
  );
}
