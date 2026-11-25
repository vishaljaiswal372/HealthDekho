import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getInstallments, getPaymentHistory, getPolicyDetails, payInstallments } from '../api/healthDekhoApi.js';
import { Alert, Badge, DetailList, EmptyState, ErrorAlert, Spinner } from '../components/ui.jsx';
import { formatDate, formatEnum, formatMoney, formatTerm } from '../utils/format.js';
import { selectedOutstanding, validatePayment, visibleInstallments } from '../utils/payments.js';

const PAYMENT_METHODS = ['UPI', 'NET_BANKING', 'CARD', 'CASH', 'CHEQUE'];

/** FR-4: find a policy, view its premium schedule and pay one or more installments. */
export default function PaymentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const policyNumber = searchParams.get('policyNumber') ?? '';
  const [query, setQuery] = useState(policyNumber);

  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [policy, setPolicy] = useState(null);
  const [installments, setInstallments] = useState([]);
  const [history, setHistory] = useState([]);

  const [selected, setSelected] = useState(new Set());
  const [amount, setAmount] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('UPI');
  const [showPaid, setShowPaid] = useState(false);
  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState(null);
  const [result, setResult] = useState(null);

  const load = useCallback(async (number) => {
    setLoading(true);
    setLoadError(null);
    try {
      const [details, schedule, payments] = await Promise.all([
        getPolicyDetails(number),
        getInstallments(number),
        getPaymentHistory(number),
      ]);
      setPolicy(details);
      setInstallments(schedule);
      setHistory(payments);
    } catch (err) {
      setPolicy(null);
      setInstallments([]);
      setHistory([]);
      setLoadError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setQuery(policyNumber);
    setSelected(new Set());
    setAmount('');
    setResult(null);
    setPayError(null);
    if (policyNumber) load(policyNumber);
    else setPolicy(null);
  }, [policyNumber, load]);

  const maxAmount = useMemo(() => selectedOutstanding(installments, selected), [installments, selected]);
  const rows = useMemo(() => visibleInstallments(installments, showPaid), [installments, showPaid]);

  const handleSearch = (event) => {
    event.preventDefault();
    const value = query.trim().toUpperCase();
    if (!value) {
      setLoadError({ message: 'Enter a policy identifier, e.g. HDP-2026-0001.' });
      return;
    }
    if (value === policyNumber) load(value);
    else setSearchParams({ policyNumber: value });
  };

  const toggle = (installment) => {
    const next = new Set(selected);
    if (next.has(installment.installmentId)) next.delete(installment.installmentId);
    else next.add(installment.installmentId);
    setSelected(next);
    // Default to paying everything selected; the customer can lower it for a partial payment.
    setAmount(next.size ? selectedOutstanding(installments, next).toFixed(2) : '');
    setPayError(null);
  };

  const handlePay = async (event) => {
    event.preventDefault();
    setResult(null);
    const problem = validatePayment({ selectedIds: selected, amount, maxAmount, paymentMethod });
    if (problem) {
      setPayError({ message: problem });
      return;
    }
    setPaying(true);
    setPayError(null);
    try {
      const response = await payInstallments(policy.policyNumber, {
        installmentIds: [...selected],
        amountPaid: Number(amount),
        paymentMethod,
      });
      setResult(response);
      setSelected(new Set());
      setAmount('');
      await load(policy.policyNumber);
    } catch (err) {
      setPayError(err);
    } finally {
      setPaying(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Premium payments</h1>
          <p className="muted">Find a policy by its identifier to see its premium schedule and record payments.</p>
        </div>
      </div>

      <form className="card search-bar" onSubmit={handleSearch}>
        <label className="field field--grow">
          <span className="field__label">Policy Identifier</span>
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="HDP-2026-0001" />
        </label>
        <button type="submit" className="btn btn--primary" disabled={loading}>
          {loading ? 'Searching…' : 'Search'}
        </button>
      </form>

      <ErrorAlert error={loadError} />
      {loading && !policy && <Spinner label="Loading policy…" />}

      {policy && (
        <>
          <section className="card">
            <div className="card__header">
              <h2>
                <span className="mono">{policy.policyNumber}</span> · {policy.policyName}
              </h2>
              <Badge value={policy.status} />
            </div>
            <DetailList
              items={[
                ['Policy Identifier', <span className="mono">{policy.policyNumber}</span>],
                ['Customer Name', policy.customerName],
                ['Policy Name', policy.policyName],
                ['Policy Term', formatTerm(policy.termMonths)],
                ['Policy Start Date', formatDate(policy.startDate)],
                ['Maturity Date', formatDate(policy.maturityDate)],
                ['Premium', `${formatMoney(policy.premiumAmount)} (${formatEnum(policy.premiumFrequency)})`],
                ['Total Premium', formatMoney(policy.totalPremium)],
                ['Paid So Far', formatMoney(policy.totalPaid)],
                ['Late Penalties', formatMoney(policy.totalPenalty)],
                ['Outstanding', <strong>{formatMoney(policy.outstandingAmount)}</strong>],
                ['Installments Paid', `${policy.installmentsPaid} of ${policy.installmentsTotal}`],
              ]}
            />
          </section>

          {result && (
            <Alert type="success" title={result.message} onClose={() => setResult(null)}>
              <ul className="alert__list">
                {result.receipts.map((r) => (
                  <li key={r.transactionReference}>
                    <span className="mono">{r.transactionReference}</span>: installment #{r.installmentNo},{' '}
                    {formatMoney(r.amountApplied)} applied
                    {Number(r.penaltyAmount) > 0 && ` (late penalty ${formatMoney(r.penaltyAmount)})`} ·{' '}
                    {formatEnum(r.installmentStatus)}
                  </li>
                ))}
              </ul>
              Outstanding on the policy is now {formatMoney(result.policyOutstandingAmount)}.
            </Alert>
          )}

          <section className="card">
            <div className="card__header">
              <h2>Premium payment schedule</h2>
              <label className="checkbox">
                <input type="checkbox" checked={showPaid} onChange={(e) => setShowPaid(e.target.checked)} />
                Show paid installments
              </label>
            </div>
            <p className="muted small">
              Select the installments to pay. Payments are applied to the oldest installment first. A 10% late penalty
              applies to premium paid after the grace period.
            </p>
            {rows.length === 0 ? (
              <EmptyState>No unpaid installments. 🎉</EmptyState>
            ) : (
              <div className="table-wrap table-wrap--tall">
                <table>
                  <thead>
                    <tr>
                      <th aria-label="Select" />
                      <th>Installment Number</th>
                      <th>Period</th>
                      <th className="num">Premium Amount</th>
                      <th>Due Date</th>
                      <th>Grace Period End Date</th>
                      <th className="num">Penalty</th>
                      <th className="num">Paid</th>
                      <th className="num">Outstanding</th>
                      <th>Payment Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((i) => (
                      <tr key={i.installmentId} className={selected.has(i.installmentId) ? 'is-selected' : ''}>
                        <td>
                          <input
                            type="checkbox"
                            aria-label={`Select installment ${i.installmentNo}`}
                            checked={selected.has(i.installmentId)}
                            disabled={!i.payable || paying}
                            onChange={() => toggle(i)}
                          />
                        </td>
                        <td>#{i.installmentNo}</td>
                        <td>{i.period}</td>
                        <td className="num">{formatMoney(i.premiumAmount)}</td>
                        <td>{formatDate(i.dueDate)}</td>
                        <td>{formatDate(i.graceEndDate)}</td>
                        <td className="num">{Number(i.penaltyAmount) > 0 ? formatMoney(i.penaltyAmount) : '—'}</td>
                        <td className="num">{formatMoney(i.paidAmount)}</td>
                        <td className="num">{formatMoney(i.outstandingAmount)}</td>
                        <td><Badge value={i.paymentStatus} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <form className="pay-bar" onSubmit={handlePay}>
              <div className="pay-bar__summary">
                {selected.size} selected · outstanding {formatMoney(maxAmount)}
              </div>
              <label className="field">
                <span className="field__label">Amount paid (₹)</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  disabled={selected.size === 0 || paying}
                />
              </label>
              <label className="field">
                <span className="field__label">Payment method</span>
                <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)} disabled={paying}>
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>
                      {formatEnum(m)}
                    </option>
                  ))}
                </select>
              </label>
              <button type="submit" className="btn btn--primary" disabled={selected.size === 0 || paying}>
                {paying ? 'Processing…' : 'Pay now'}
              </button>
            </form>
            <ErrorAlert error={payError} onClose={() => setPayError(null)} />
          </section>

          <section className="card">
            <h2>Payment history</h2>
            {history.length === 0 ? (
              <EmptyState>No payments recorded yet.</EmptyState>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Transaction Reference</th>
                      <th>Installment</th>
                      <th>Payment Date</th>
                      <th className="num">Amount Paid</th>
                      <th className="num">Penalty</th>
                      <th>Method</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((p) => (
                      <tr key={p.transactionReference}>
                        <td className="mono">{p.transactionReference}</td>
                        <td>#{p.installmentNo}</td>
                        <td>{formatDate(p.paymentDate)}</td>
                        <td className="num">{formatMoney(p.amountPaid)}</td>
                        <td className="num">{Number(p.penaltyAmount) > 0 ? formatMoney(p.penaltyAmount) : '—'}</td>
                        <td>{formatEnum(p.paymentMethod)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </>
  );
}
