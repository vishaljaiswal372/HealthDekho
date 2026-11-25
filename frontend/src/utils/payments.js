/** Pure helpers for the premium payment screen (kept separate so they can be unit-tested). */

/** Sum of outstanding amounts for the selected installments, as a number with 2 decimals. */
export function selectedOutstanding(installments, selectedIds) {
  const total = installments
    .filter((i) => selectedIds.has(i.installmentId))
    .reduce((sum, i) => sum + Math.round(Number(i.outstandingAmount) * 100), 0);
  return total / 100;
}

/**
 * Client-side checks before calling the API (the server re-checks everything).
 * Returns an error message, or null when the payment can be submitted.
 */
export function validatePayment({ selectedIds, amount, maxAmount, paymentMethod }) {
  if (selectedIds.size === 0) return 'Select at least one installment to pay.';
  const value = Number(amount);
  if (!amount || Number.isNaN(value) || value <= 0) return 'Enter an amount greater than zero.';
  if (!/^\d+(\.\d{1,2})?$/.test(String(amount).trim())) return 'The amount can have at most 2 decimal places.';
  if (value > maxAmount + 1e-9) return `The amount cannot exceed the outstanding ${maxAmount.toFixed(2)}.`;
  if (!paymentMethod) return 'Choose a payment method.';
  return null;
}

/** Installments to show: unpaid only by default, oldest first. */
export function visibleInstallments(installments, showPaid) {
  return showPaid ? installments : installments.filter((i) => i.paymentStatus !== 'PAID');
}
