import { request } from './client.js';

/** One function per backend endpoint, so pages never build URLs themselves. */

// FR-1 authentication & dashboard
export const signup = ({ fullName, email, password, dateOfBirth, phone }) =>
  request('/api/auth/signup', {
    method: 'POST',
    body: { fullName, email, password, dateOfBirth, phone: phone || undefined },
    auth: false,
  });
export const login = (email, password) =>
  request('/api/auth/login', { method: 'POST', body: { email, password }, auth: false });
export const getProfile = () => request('/api/auth/me');
export const getMyPolicies = () => request('/api/dashboard/policies');
export const getAvailableProducts = () => request('/api/dashboard/available-products');

// FR-2 AI recommendations
export const requestRecommendations = (inputs) =>
  request('/api/recommendations', { method: 'POST', body: inputs });
export const getRecommendationHistory = () => request('/api/recommendations');

// FR-3 registration
export const getRegistrationPreview = (productId, startDate) =>
  request('/api/policies/registration-preview', { params: { productId, startDate } });
export const registerPolicy = ({ productId, startDate, recommendationItemId }) =>
  request('/api/policies', { method: 'POST', body: { productId, startDate, recommendationItemId } });

// FR-4 premium payments
const policyPath = (policyNumber) => `/api/policies/${encodeURIComponent(policyNumber.trim())}`;
export const getPolicyDetails = (policyNumber) => request(policyPath(policyNumber));
export const getInstallments = (policyNumber) => request(`${policyPath(policyNumber)}/installments`);
export const getPaymentHistory = (policyNumber) => request(`${policyPath(policyNumber)}/payments`);
export const payInstallments = (policyNumber, { installmentIds, amountPaid, paymentMethod }) =>
  request(`${policyPath(policyNumber)}/payments`, {
    method: 'POST',
    body: { installmentIds, amountPaid, paymentMethod },
  });

// FR-5 search
export const searchPolicies = ({ policyNumber, customerName, dateOfBirth, page = 0, size = 10 }) =>
  request('/api/policy-search', { params: { policyNumber, customerName, dateOfBirth, page, size } });
