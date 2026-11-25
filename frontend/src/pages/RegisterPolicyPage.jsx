import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { getRegistrationPreview, registerPolicy } from '../api/healthDekhoApi.js';
import { useApi } from '../hooks/useApi.js';
import { Alert, DetailList, ErrorAlert, Spinner } from '../components/ui.jsx';
import { formatDate, formatEnum, formatMoney, formatTerm, todayIso } from '../utils/format.js';

/**
 * FR-3: registration form pre-populated by the server. All figures (maturity, totals) are
 * calculated by the backend; the customer only chooses the start date and confirms.
 */
export default function RegisterPolicyPage() {
  const { productId } = useParams();
  const [searchParams] = useSearchParams();
  const recommendationItemId = searchParams.get('recommendationItemId');
  const today = todayIso();

  const [startDate, setStartDate] = useState(today);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [confirmation, setConfirmation] = useState(null);

  const preview = useApi(
    () => getRegistrationPreview(productId, startDate),
    [productId, startDate],
    { enabled: Boolean(startDate) && !confirmation },
  );
  const form = preview.data;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError(null);
    setSubmitting(true);
    try {
      const result = await registerPolicy({
        productId: Number(productId),
        startDate,
        recommendationItemId: recommendationItemId ? Number(recommendationItemId) : undefined,
      });
      setConfirmation(result);
    } catch (err) {
      setSubmitError(err);
    } finally {
      setSubmitting(false);
    }
  };

  if (confirmation) {
    return (
      <>
        <div className="page-header">
          <h1>Policy registered</h1>
        </div>
        <section className="card">
          <Alert type="success" title={confirmation.message}>
            Your policy identifier is <strong className="mono">{confirmation.policyNumber}</strong>.
          </Alert>
          <DetailList
            items={[
              ['Policy Identifier', <span className="mono">{confirmation.policyNumber}</span>],
              ['Policy Name', confirmation.policyName],
              ['Start Date', formatDate(confirmation.startDate)],
              ['Maturity Date', formatDate(confirmation.maturityDate)],
              ['Premium', `${formatMoney(confirmation.premiumAmount)} (${formatEnum(confirmation.premiumFrequency)})`],
              ['Sum Insured', formatMoney(confirmation.sumInsured)],
              ['Total Premium Payable', formatMoney(confirmation.totalPremium)],
              ['Installments', confirmation.numberOfInstallments],
              ['First Premium Due', formatDate(confirmation.firstDueDate)],
            ]}
          />
          <div className="actions">
            <Link
              className="btn btn--primary"
              to={`/payments?policyNumber=${encodeURIComponent(confirmation.policyNumber)}`}
            >
              Pay first premium
            </Link>
            <Link className="btn btn--ghost" to="/dashboard">
              Back to dashboard
            </Link>
          </div>
        </section>
      </>
    );
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Register a policy</h1>
          <p className="muted">
            Check the details below. Premium totals and dates are calculated for you.
            {recommendationItemId && ' This product was recommended for you.'}
          </p>
        </div>
        <Link className="btn btn--ghost" to="/dashboard">
          Cancel
        </Link>
      </div>

      <form className="card" onSubmit={handleSubmit}>
        <ErrorAlert error={preview.error} />
        <ErrorAlert error={submitError} onClose={() => setSubmitError(null)} />

        <label className="field field--inline">
          <span className="field__label">Policy Start Date</span>
          <input
            type="date"
            value={startDate}
            min={today}
            onChange={(e) => setStartDate(e.target.value)}
            required
          />
        </label>

        {preview.loading && <Spinner label="Preparing your registration form…" />}

        {form && !preview.loading && (
          <>
            {!form.eligible && (
              <Alert type="warning" title="Not eligible">
                {form.ineligibilityReason}
              </Alert>
            )}
            <div className="form-grid">
              <fieldset>
                <legend>Customer</legend>
                <DetailList
                  items={[
                    ['Customer Name', form.customerName],
                    ['Date of Birth', formatDate(form.dateOfBirth)],
                    ['Contact Information', `${form.email}${form.phone ? ` · ${form.phone}` : ''}`],
                    ['Age on Start Date', `${form.ageAtStart} years`],
                  ]}
                />
              </fieldset>
              <fieldset>
                <legend>Policy</legend>
                <DetailList
                  items={[
                    ['Policy Type', formatEnum(form.policyType)],
                    ['Policy Name', form.policyName],
                    ['Policy Term', formatTerm(form.termMonths)],
                    ['Policy Start Date', formatDate(form.startDate)],
                    ['Policy Maturity Date', formatDate(form.maturityDate)],
                  ]}
                />
              </fieldset>
              <fieldset>
                <legend>Premium</legend>
                <DetailList
                  items={[
                    ['Premium Payment Frequency', formatEnum(form.premiumFrequency)],
                    ['Premium Amount', formatMoney(form.premiumAmount)],
                    ['Sum Insured', formatMoney(form.sumInsured)],
                    ['Number of Installments', form.numberOfInstallments],
                    ['Total Premium Payable', <strong>{formatMoney(form.totalPremiumPayable)}</strong>],
                  ]}
                />
              </fieldset>
            </div>
            <div className="actions">
              <button type="submit" className="btn btn--primary" disabled={!form.eligible || submitting}>
                {submitting ? 'Registering…' : 'Confirm registration'}
              </button>
            </div>
          </>
        )}
      </form>
    </>
  );
}
