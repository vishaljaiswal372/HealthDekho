import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { getAvailableProducts, getMyPolicies } from '../api/healthDekhoApi.js';
import { useApi } from '../hooks/useApi.js';
import { Badge, EmptyState, ErrorAlert, Spinner } from '../components/ui.jsx';
import { formatDate, formatEnum, formatMoney, formatTerm } from '../utils/format.js';

/** FR-1: the customer's registered policies and the products they can still register for. */
export default function DashboardPage() {
  const { customer } = useAuth();
  const policies = useApi(getMyPolicies, []);
  const products = useApi(getAvailableProducts, []);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Welcome, {customer?.fullName?.split(' ')[0]}</h1>
          <p className="muted">Your insurance policies and products available to you.</p>
        </div>
        <Link to="/recommendations" className="btn btn--primary">
          ✨ Get AI recommendations
        </Link>
      </div>

      <section className="card">
        <h2>My policies</h2>
        <ErrorAlert error={policies.error} />
        {policies.loading && <Spinner label="Loading your policies…" />}
        {!policies.loading && !policies.error && policies.data?.length === 0 && (
          <EmptyState>You have not registered any policies yet. Choose a product below to get started.</EmptyState>
        )}
        {!policies.loading && policies.data?.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Policy Identifier</th>
                  <th>Policy Name</th>
                  <th>Policy Type</th>
                  <th>Policy Term</th>
                  <th>Start Date</th>
                  <th>Maturity Date</th>
                  <th>Premium Frequency</th>
                  <th className="num">Premium Amount</th>
                  <th className="num">Sum Insured</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {policies.data.map((p) => (
                  <tr key={p.policyNumber}>
                    <td className="mono">{p.policyNumber}</td>
                    <td>{p.policyName}</td>
                    <td>{formatEnum(p.policyType)}</td>
                    <td>{formatTerm(p.termMonths)}</td>
                    <td>{formatDate(p.startDate)}</td>
                    <td>{formatDate(p.maturityDate)}</td>
                    <td>{formatEnum(p.premiumFrequency)}</td>
                    <td className="num">{formatMoney(p.premiumAmount)}</td>
                    <td className="num">{formatMoney(p.sumInsured)}</td>
                    <td><Badge value={p.status} /></td>
                    <td>
                      <Link className="btn btn--ghost btn--small" to={`/payments?policyNumber=${encodeURIComponent(p.policyNumber)}`}>
                        Premiums
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card">
        <h2>Available insurance products</h2>
        <ErrorAlert error={products.error} />
        {products.loading && <Spinner label="Loading products…" />}
        {!products.loading && !products.error && products.data?.length === 0 && (
          <EmptyState>You already hold every product we currently offer.</EmptyState>
        )}
        {!products.loading && products.data?.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Product Code</th>
                  <th>Policy Name</th>
                  <th>Policy Type</th>
                  <th>Policy Term</th>
                  <th className="num">Sum Insured</th>
                  <th>Premium Frequency</th>
                  <th className="num">Premium Amount</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {products.data.map((p) => (
                  <tr key={p.productId}>
                    <td className="mono">{p.productCode}</td>
                    <td>{p.policyName}</td>
                    <td>{formatEnum(p.policyType)}</td>
                    <td>{formatTerm(p.termMonths)}</td>
                    <td className="num">{formatMoney(p.sumInsured)}</td>
                    <td>{formatEnum(p.premiumFrequency)}</td>
                    <td className="num">{formatMoney(p.premiumAmount)}</td>
                    <td>
                      <Link className="btn btn--primary btn--small" to={`/register/${p.productId}`}>
                        Register
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
