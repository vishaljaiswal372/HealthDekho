import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { signup } from '../api/healthDekhoApi.js';
import { ErrorAlert } from '../components/ui.jsx';
import { todayIso } from '../utils/format.js';
import { validateSignup } from '../utils/signup.js';

const EMPTY = { fullName: '', email: '', phone: '', dateOfBirth: '', password: '', confirmPassword: '' };

function Field({ label, error, hint, children }) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      {children}
      {error ? <span className="field__error">{error}</span> : hint && <span className="field__hint">{hint}</span>}
    </label>
  );
}

/** New customer registration. On success the customer is sent to the login page with their email filled in. */
export default function SignupPage() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  const update = (field) => (event) => {
    setForm({ ...form, [field]: event.target.value });
    if (fieldErrors[field]) setFieldErrors({ ...fieldErrors, [field]: undefined });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    const problems = validateSignup(form);
    setFieldErrors(problems);
    if (Object.keys(problems).length) return;

    setSubmitting(true);
    try {
      const customer = await signup({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        dateOfBirth: form.dateOfBirth,
        phone: form.phone.trim(),
      });
      void navigate('/login', { replace: true, state: { signedUpEmail: customer.email, signedUpName: customer.fullName } });
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {});
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login">
      <div className="login__panel">
        <div className="login__brand">
          <span className="brand__mark brand__mark--large" aria-hidden="true">+</span>
          <h1>HealthDekho</h1>
          <p>Create your account to view your policies, pay premiums and get personalised recommendations.</p>
        </div>

        <form className="card login__form" onSubmit={handleSubmit} noValidate>
          <h2>Create an account</h2>
          {/* Field errors appear under each field; the alert carries the overall message (e.g. email already used). */}
          {error && <ErrorAlert error={{ message: error.message }} />}

          <Field label="Full name" error={fieldErrors.fullName}>
            <input value={form.fullName} onChange={update('fullName')} autoComplete="name" autoFocus />
          </Field>
          <Field label="Email" error={fieldErrors.email}>
            <input type="email" value={form.email} onChange={update('email')} autoComplete="email" />
          </Field>
          <div className="field-row">
            <Field label="Date of birth" error={fieldErrors.dateOfBirth}>
              <input type="date" max={todayIso()} value={form.dateOfBirth} onChange={update('dateOfBirth')} />
            </Field>
            <Field label="Mobile number" error={fieldErrors.phone} hint="Optional">
              <input inputMode="numeric" maxLength={10} value={form.phone} onChange={update('phone')} autoComplete="tel" />
            </Field>
          </div>
          <Field label="Password" error={fieldErrors.password} hint="At least 8 characters, with a letter and a number">
            <input type="password" value={form.password} onChange={update('password')} autoComplete="new-password" />
          </Field>
          <Field label="Confirm password" error={fieldErrors.confirmPassword}>
            <input
              type="password"
              value={form.confirmPassword}
              onChange={update('confirmPassword')}
              autoComplete="new-password"
            />
          </Field>

          <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Sign up'}
          </button>
          <p className="login__switch">
            Already have an account? <Link to="/login">Log in</Link>
          </p>
        </form>
      </div>
    </div>
  );
}
