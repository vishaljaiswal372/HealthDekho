# HealthDekho Frontend

React 19 single-page app (Vite 8, React Router 7) for the HealthDekho insurance management system.
It talks only to the Spring Boot API in `../backend`.

## Run it

1. Start the backend (see `../backend/README.md`). It listens on `http://localhost:8080`.
2. In this folder:

   ```powershell
   npm install
   npm run dev
   ```

3. Open http://localhost:5173, choose **Create an account**, sign up, then log in with the new account. Or log in with a sample customer such as `aarav.sharma@example.com`. The dev password is in the header of `backend/src/main/resources/db/seed/V100__sample_data.sql`.

In development, Vite forwards every `/api/...` call to the backend (`vite.config.js`), so the browser makes no cross-origin requests. To use another backend address, copy `.env.example` to `.env.local` and set `VITE_BACKEND_URL`.

| Script | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload on port 5173 |
| `npm run build` | Production build into `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm test` | Unit tests (Vitest) |

## Screens

| Route | Requirement | What it does |
|---|---|---|
| `/signup` | FR-1 | New customer account: name, email, date of birth, optional mobile, password and confirmation. Errors show next to each field. On success it goes to login with the email filled in |
| `/login` | FR-1 | Customer login. Shows the API's error message for invalid credentials, and links to sign-up |
| `/dashboard` | FR-1 | **My policies** table and **Available insurance products** table, with a **Register** button on each product |
| `/recommendations` | FR-2 | Date of birth, goals, preferred term and type in; AI-ranked products out (reason and suitability), each with **Register**. Also shows earlier recommendations |
| `/register/:productId` | FR-3 | Registration form pre-filled by the server: customer details, terms, maturity and total premium. The customer picks a start date and confirms, then sees the policy identifier |
| `/payments?policyNumber=` | FR-4 | Search a policy, see its details and premium schedule, select installments (partial amounts allowed), pay, and see receipts and payment history |
| `/search` | FR-5 | Search by policy identifier, customer name, or identifier + date of birth. Paged results with remaining premium |

## How it is put together

```
src/
  api/client.js           fetch wrapper: bearer token, JSON, ApiError (message + fieldErrors), 401 -> session end
  api/healthDekhoApi.js   one function per backend endpoint
  auth/                   session storage, AuthContext (login/logout/auto-expiry), ProtectedRoute
  hooks/useApi.js         loading / error / data state for page loads
  components/             Layout (top navigation), shared UI (Spinner, Alert, Badge, Pagination, DetailList)
  pages/                  one component per screen
  utils/                  formatting (₹, dates, terms), search-criteria and payment validation (unit-tested)
```

- **Loading, success and error feedback** on every screen. Spinners while loading, the API's own error messages, field-level validation errors, and success confirmations with policy numbers and transaction references.
- **No business calculations in the browser.** Totals, maturity dates, penalties and eligibility all come from the API. The client only checks input shape for quick feedback, and the server checks again.
- **Session:** the access token is kept in `sessionStorage` (cleared when the tab closes). The app logs out automatically when the token expires or the API returns 401.
