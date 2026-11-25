import { describe, expect, it } from 'vitest';
import { formatDate, formatEnum, formatMoney, formatTerm, todayIso } from './format.js';
import { buildSearchCriteria } from './search.js';
import { selectedOutstanding, validatePayment, visibleInstallments } from './payments.js';
import { validateSignup } from './signup.js';

describe('validateSignup', () => {
  const today = new Date(2026, 10, 25);
  const valid = {
    fullName: 'Priya Nair',
    email: 'priya@example.com',
    password: 'Secure123',
    confirmPassword: 'Secure123',
    dateOfBirth: '1995-06-15',
    phone: '',
  };

  it('accepts a valid form (phone optional)', () => {
    expect(validateSignup(valid, today)).toEqual({});
    expect(validateSignup({ ...valid, phone: '9876500001' }, today)).toEqual({});
  });

  it('flags each invalid field with the backend wording', () => {
    const errors = validateSignup(
      { fullName: 'R2D2', email: 'nope', password: 'letters', confirmPassword: 'x', dateOfBirth: '', phone: '123' },
      today,
    );
    expect(errors).toEqual({
      fullName: "Full name can contain only letters, spaces, . ' and -",
      email: 'Enter a valid email address',
      password: 'Password must be 8 to 72 characters',
      confirmPassword: 'Passwords do not match',
      dateOfBirth: 'Date of birth is required',
      phone: 'Enter a 10-digit mobile number',
    });
    expect(validateSignup({ ...valid, password: 'lettersonly', confirmPassword: 'lettersonly' }, today).password).toBe(
      'Password must contain at least one letter and one number',
    );
  });

  it('requires an adult, counting the 18th birthday itself', () => {
    expect(validateSignup({ ...valid, dateOfBirth: '2008-11-25' }, today)).toEqual({});
    expect(validateSignup({ ...valid, dateOfBirth: '2008-11-26' }, today).dateOfBirth).toMatch(/at least 18/);
    expect(validateSignup({ ...valid, dateOfBirth: '2030-01-01' }, today).dateOfBirth).toMatch(/in the past/);
  });
});

describe('format', () => {
  it('formats rupees with Indian grouping', () => {
    expect(formatMoney(5000000)).toBe('₹50,00,000.00');
    expect(formatMoney(null)).toBe('—');
  });

  it('formats ISO dates without timezone shifts', () => {
    expect(formatDate('2026-11-20')).toBe('20 Nov 2026');
  });

  it('formats terms in months', () => {
    expect(formatTerm(240)).toBe('20 years');
    expect(formatTerm(12)).toBe('1 year');
    expect(formatTerm(18)).toBe('1 year 6 months');
    expect(formatTerm(6)).toBe('6 months');
  });

  it('formats enum values', () => {
    expect(formatEnum('HALF_YEARLY')).toBe('Half-Yearly');
    expect(formatEnum('NET_BANKING')).toBe('Net Banking');
    expect(formatEnum('PARTIALLY_PAID')).toBe('Partially Paid');
    expect(formatEnum('TERM')).toBe('Term');
  });

  it('gives today as a local yyyy-MM-dd', () => {
    expect(todayIso(new Date(2027, 0, 5))).toBe('2027-01-05');
  });
});

describe('buildSearchCriteria (FR-5 modes)', () => {
  it('accepts a policy identifier, normalised to upper case', () => {
    expect(buildSearchCriteria({ policyNumber: ' hdp-2026-0001 ' })).toEqual({ params: { policyNumber: 'HDP-2026-0001' } });
  });

  it('accepts a customer name with whitespace collapsed', () => {
    expect(buildSearchCriteria({ customerName: '  Meera   Iyer ' })).toEqual({ params: { customerName: 'Meera Iyer' } });
  });

  it('accepts policy identifier + date of birth', () => {
    expect(buildSearchCriteria({ policyNumber: 'HDP-2026-0002', dateOfBirth: '1985-09-22' })).toEqual({
      params: { policyNumber: 'HDP-2026-0002', dateOfBirth: '1985-09-22' },
    });
  });

  it('rejects the combinations the backend rejects', () => {
    expect(buildSearchCriteria({}).error).toMatch(/Enter a policy identifier/);
    expect(buildSearchCriteria({ dateOfBirth: '1985-09-22' }).error).toMatch(/combined with a policy identifier/);
    expect(buildSearchCriteria({ policyNumber: 'X', customerName: 'Aarav' }).error).toMatch(/not both/);
    expect(buildSearchCriteria({ customerName: 'a' }).error).toMatch(/at least 2 characters/);
  });
});

describe('payments helpers', () => {
  const installments = [
    { installmentId: 1, outstandingAmount: 0, paymentStatus: 'PAID' },
    { installmentId: 2, outstandingAmount: 750.1, paymentStatus: 'PARTIALLY_PAID' },
    { installmentId: 3, outstandingAmount: 1250.2, paymentStatus: 'PENDING' },
  ];

  it('totals the selected outstanding without float drift', () => {
    expect(selectedOutstanding(installments, new Set([2, 3]))).toBe(2000.3);
    expect(selectedOutstanding(installments, new Set())).toBe(0);
  });

  it('hides paid installments unless asked', () => {
    expect(visibleInstallments(installments, false).map((i) => i.installmentId)).toEqual([2, 3]);
    expect(visibleInstallments(installments, true)).toHaveLength(3);
  });

  it('validates the payment form', () => {
    const base = { selectedIds: new Set([2]), amount: '750.10', maxAmount: 750.1, paymentMethod: 'UPI' };
    expect(validatePayment(base)).toBeNull();
    expect(validatePayment({ ...base, amount: '300' })).toBeNull(); // partial payment
    expect(validatePayment({ ...base, selectedIds: new Set() })).toMatch(/Select at least one/);
    expect(validatePayment({ ...base, amount: '0' })).toMatch(/greater than zero/);
    expect(validatePayment({ ...base, amount: '10.555' })).toMatch(/2 decimal places/);
    expect(validatePayment({ ...base, amount: '750.11' })).toMatch(/cannot exceed/);
    expect(validatePayment({ ...base, paymentMethod: '' })).toMatch(/payment method/);
  });
});
