/**
 * Client-side checks for the sign-up form, mirroring the backend rules for instant feedback.
 * The server validates everything again. Returns { field: message } (empty when valid).
 */
export function validateSignup({ fullName, email, password, confirmPassword, dateOfBirth, phone }, today = new Date()) {
  const errors = {};
  const name = (fullName ?? '').trim();
  if (!name) errors.fullName = 'Full name is required';
  else if (name.length < 2) errors.fullName = 'Full name must be 2 to 150 characters';
  else if (!/^[\p{L} .'-]+$/u.test(name)) errors.fullName = "Full name can contain only letters, spaces, . ' and -";

  if (!(email ?? '').trim()) errors.email = 'Email is required';
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) errors.email = 'Enter a valid email address';

  if (!password) errors.password = 'Password is required';
  else if (password.length < 8 || password.length > 72) errors.password = 'Password must be 8 to 72 characters';
  else if (!/[A-Za-z]/.test(password) || !/\d/.test(password))
    errors.password = 'Password must contain at least one letter and one number';

  if (password && confirmPassword !== password) errors.confirmPassword = 'Passwords do not match';

  if (!dateOfBirth) {
    errors.dateOfBirth = 'Date of birth is required';
  } else {
    const [y, m, d] = dateOfBirth.split('-').map(Number);
    const eighteenth = new Date(y + 18, m - 1, d);
    if (new Date(y, m - 1, d) >= today) errors.dateOfBirth = 'Date of birth must be in the past';
    else if (eighteenth > today) errors.dateOfBirth = 'You must be at least 18 years old to create an account';
  }

  if (phone && !/^\d{10}$/.test(phone.trim())) errors.phone = 'Enter a 10-digit mobile number';
  return errors;
}
