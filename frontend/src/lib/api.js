// File: frontend/src/lib/api.js
//
// The live API client. FitClash.jsx runs on in-memory mock state so the
// prototype renders with no backend; swap the mutations in `bankSession`,
// `challenge`, `respond` and `settle` for these calls to go live.
//
// The token lives in memory, not localStorage: a JWT in localStorage is
// readable by any XSS on the page. For a real deployment, move it to an
// httpOnly cookie and drop the Authorization header entirely.

const BASE = import.meta.env?.VITE_API_BASE_URL ?? 'http://localhost:8080';

let token = null;

export const setToken = (value) => { token = value; };
export const getToken = () => token;

class ApiError extends Error {
  constructor(status, payload) {
    super(payload?.message ?? `Request failed (${status})`);
    this.status = status;
    this.code = payload?.error;
    this.details = payload?.details ?? [];
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) return null;

  const payload = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, payload);
  return payload;
}

export const api = {
  // auth - open endpoints
  register: (body) => request('/api/auth/register', { method: 'POST', body }),
  login: (body) => request('/api/auth/login', { method: 'POST', body }),

  // character
  me: () => request('/api/characters/me'),
  leaderboard: (limit = 25) => request(`/api/leaderboard?limit=${limit}`),

  // workouts
  logWorkout: (body) => request('/api/workouts', { method: 'POST', body }),
  recentWorkouts: () => request('/api/workouts'),
  dailyBudget: () => request('/api/workouts/daily-budget'),

  // duels
  duels: () => request('/api/duels'),
  challenge: (body) => request('/api/duels', { method: 'POST', body }),
  acceptDuel: (id) => request(`/api/duels/${id}/accept`, { method: 'POST' }),
  declineDuel: (id) => request(`/api/duels/${id}/decline`, { method: 'POST' }),
  withdrawDuel: (id) => request(`/api/duels/${id}/withdraw`, { method: 'POST' }),

  // friends
  friends: () => request('/api/friends'),
  searchUsers: (q) => request(`/api/friends/search?q=${encodeURIComponent(q)}`),
  requestFriend: (username) => request('/api/friends/requests', { method: 'POST', body: { username } }),
  acceptFriend: (id) => request(`/api/friends/requests/${id}/accept`, { method: 'POST' }),
  declineFriend: (id) => request(`/api/friends/requests/${id}/decline`, { method: 'POST' }),
};

export { ApiError };

/**
 * Shape of the body POST /api/workouts expects, for reference:
 *
 * {
 *   "workoutDate": "2026-09-21",
 *   "title": "Push day",
 *   "sets": [
 *     { "exerciseName": "Bench Press", "kind": "STRENGTH",
 *       "reps": 8, "weightKg": 80, "durationSec": 0, "intensity": 5 },
 *     { "exerciseName": "Run", "kind": "CARDIO",
 *       "reps": 0, "weightKg": 0, "durationSec": 1800, "intensity": 6 }
 *   ]
 * }
 */
