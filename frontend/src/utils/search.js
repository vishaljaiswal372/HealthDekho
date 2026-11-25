/**
 * FR-5 allows three search modes: policy identifier, customer name, or policy identifier + date of birth.
 * Mirrors the backend rules so the customer gets immediate feedback; the server re-validates.
 * Returns { error } or { params } ready for the API.
 */
export function buildSearchCriteria({ policyNumber = '', customerName = '', dateOfBirth = '' }) {
  const number = policyNumber.trim().toUpperCase();
  const name = customerName.trim().replace(/\s+/g, ' ');
  const dob = dateOfBirth.trim();

  if (!number && !name && !dob) {
    return { error: 'Enter a policy identifier, a customer name, or a policy identifier with date of birth.' };
  }
  if (number && name) {
    return { error: 'Search by policy identifier or by customer name, not both.' };
  }
  if (dob && !number) {
    return { error: 'Date of birth must be combined with a policy identifier.' };
  }
  if (name && name.length < 2) {
    return { error: 'Enter at least 2 characters of the customer name.' };
  }
  if (number) {
    return { params: dob ? { policyNumber: number, dateOfBirth: dob } : { policyNumber: number } };
  }
  return { params: { customerName: name } };
}
