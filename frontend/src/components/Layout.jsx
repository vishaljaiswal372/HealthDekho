import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

const LINKS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/recommendations', label: 'AI Recommendations' },
  { to: '/payments', label: 'Premium Payments' },
  { to: '/search', label: 'Policy Search' },
];

export default function Layout() {
  const { customer, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar__inner">
          <NavLink to="/dashboard" className="brand">
            <span className="brand__mark" aria-hidden="true">+</span>
            HealthDekho
          </NavLink>
          <nav className="nav" aria-label="Main">
            {LINKS.map((link) => (
              <NavLink key={link.to} to={link.to} className={({ isActive }) => `nav__link${isActive ? ' is-active' : ''}`}>
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="topbar__user">
            <span className="topbar__name">{customer?.fullName}</span>
            <button type="button" className="btn btn--ghost btn--small" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
