import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { Alert, ErrorAlert } from '../components/ui.jsx';

export default function LoginPage() {
  const { isAuthenticated, login, expiredNotice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const signedUpEmail = location.state?.signedUpEmail;
  const [email, setEmail] = useState(signedUpEmail ?? '');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const redirectTo = location.state?.from?.pathname
    ? `${location.state.from.pathname}${location.state.from.search ?? ''}`
    : '/dashboard';

  if (isAuthenticated) return <Navigate to={redirectTo} replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    if (!email.trim() || !password) {
      setError({ message: 'Enter your email and password.' });
      return;
    }
    setSubmitting(true);
    try {
      await login(email, password);
      void navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(err);
      setPassword('');
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
          <p>Manage your insurance policies, premiums and recommendations in one place.</p>
        </div>

        <form className="card login__form" onSubmit={handleSubmit} noValidate>
          <h2>Customer login</h2>
          {signedUpEmail && !error && (
            <Alert type="success" title="Account created">
              Welcome{location.state?.signedUpName ? `, ${location.state.signedUpName}` : ''}! Log in with your new password.
            </Alert>
          )}
          {expiredNotice && !error && !signedUpEmail && (
            <Alert type="info">Your session has ended. Please log in again.</Alert>
          )}
          <ErrorAlert error={error} />

          <label className="field">
            <span className="field__label">Email</span>
            <input
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              autoFocus={!signedUpEmail}
            />
          </label>
          <label className="field">
            <span className="field__label">Password</span>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoFocus={Boolean(signedUpEmail)}
            />
          </label>
          <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
          <p className="login__switch">
            New to HealthDekho? <Link to="/signup">Create an account</Link>
          </p>
        </form>
      </div>
    </div>
  );
}
