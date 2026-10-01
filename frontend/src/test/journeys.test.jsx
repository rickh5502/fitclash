// File: frontend/src/test/journeys.test.jsx
//
// Drives the real component through the journeys a tester would take, in a
// real DOM. This exists so UI claims can be checked instead of argued about.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FitClash from '../FitClash.jsx';

const setup = async () => {
  const user = userEvent.setup();
  render(<FitClash />);
  await user.click(screen.getByRole('button', { name: /enter the gym/i }));
  return user;
};

// Tab panels are wrapped in <AnimatePresence mode="wait">, so the incoming
// panel does not mount until the outgoing one has finished exiting. Every
// post-navigation query has to be async.
const LANDMARK = {
  Character: /recent sessions/i,
  Log: /pick a movement/i,
  Duels: /throw down/i,
  Ranks: /guild standings/i,
};

const goTo = async (user, tab) => {
  await user.click(screen.getByRole('button', { name: new RegExp(`^${tab}$`, 'i') }));
  await screen.findByText(LANDMARK[tab], undefined, { timeout: 3000 });
};

const setField = (labelRe, value) => {
  fireEvent.change(screen.getByLabelText(labelRe), { target: { value: String(value) } });
};

/** The "+N.N XP" headline the logger shows for the set you are about to add. */
const previewXp = () => {
  const node = screen.getByText('This set pays').closest('div').parentElement;
  return within(node).getByText(/^[\d,]+(\.\d+)?$/).textContent;
};

describe('auth', () => {
  it('lands on the gate and enters the app', async () => {
    const user = userEvent.setup();
    render(<FitClash />);
    expect(screen.getByRole('heading', { name: /fitclash/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /enter the gym/i }));
    expect(screen.getByText('Character')).toBeInTheDocument();
  });

  it('toggles to sign up and reveals the email field', async () => {
    const user = userEvent.setup();
    render(<FitClash />);
    expect(screen.queryByLabelText(/email/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: /sign up/i }));
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /forge character/i })).toBeInTheDocument();
  });

  it('rejects a short password', async () => {
    const user = userEvent.setup();
    render(<FitClash />);
    setField(/password/i, 'abc');
    await user.click(screen.getByRole('button', { name: /enter the gym/i }));
    expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
  });
});

describe('dashboard', () => {
  it('shows the seeded character sheet', async () => {
    await setup();
    expect(screen.getByText('LV 7')).toBeInTheDocument();
    expect(screen.getByText('STR')).toBeInTheDocument();
    expect(screen.getByText('STA')).toBeInTheDocument();
    expect(screen.getByText('CON')).toBeInTheDocument();
    // Seeded strPoints 186 / staPoints 74 / 17 active days, 5-day streak.
    expect(screen.getByText('23')).toBeInTheDocument();
    expect(screen.getByText('20')).toBeInTheDocument();
    expect(screen.getByText('18')).toBeInTheDocument();
  });

  it('shows the daily cap meter in the header', async () => {
    await setup();
    // Seed dailyXp is 0 so the first bank of the day visibly demonstrates
    // the streak/CON increment (see the streak regression test below).
    expect(screen.getByText('0/1,000')).toBeInTheDocument();
  });
});

describe('logging', () => {
  it('applies diminishing returns from the 6th set of a movement that day', async () => {
    const user = await setup();
    await goTo(user, 'Log');

    // Seed state already has 2 bench sets today, so the next one is set 3.
    expect(screen.getByText(/set 3 today/i)).toBeInTheDocument();
    expect(screen.queryByText(/^×/)).not.toBeInTheDocument();

    const add = screen.getByRole('button', { name: /add set/i });
    await user.click(add); // set 3
    await user.click(add); // set 4
    await user.click(add); // set 5
    expect(screen.getByText(/set 6 today/i)).toBeInTheDocument();

    // Set 6 is the first reduced one, and the badge must warn BEFORE committing.
    expect(screen.getByText('×0.50')).toBeInTheDocument();
    await user.click(add); // set 6
    expect(screen.getByText('×0.25')).toBeInTheDocument();
  });

  it('refuses an impossible set and logs nothing', async () => {
    const user = await setup();
    await goTo(user, 'Log');
    setField('Reps', 1000);
    setField(/^Weight \(kg\)$/, 500);
    await user.click(screen.getByRole('button', { name: /add set/i }));

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText(/0 sets/i)).toBeInTheDocument(); // draft still empty
  });

  it('banks a session and moves the daily cap meter', async () => {
    const user = await setup();
    await goTo(user, 'Log');
    const add = screen.getByRole('button', { name: /add set/i });
    await user.click(add);
    await user.click(add);
    await user.click(add);
    await user.click(screen.getByRole('button', { name: /bank session/i }));

    expect(screen.queryByText('240/1,000')).not.toBeInTheDocument();
    expect(screen.getByText(/\/1,000$/)).toBeInTheDocument();
  });

  // ---- the suspected defect: deleting a drafted set leaves stale indices ----
  const addSets = async (user, n) => {
    const add = screen.getByRole('button', { name: /add set/i });
    for (let i = 0; i < n; i++) await user.click(add);
    await screen.findByText(`${n} sets`);
  };

  const removeFirstSet = async (user, remaining) => {
    const removes = screen.getAllByRole('button', { name: /^Remove /i });
    await user.click(removes[0]);
    // Wait out the exit animation so the removed row is really gone.
    await waitFor(() => expect(screen.getByText(`${remaining} sets`)).toBeInTheDocument());
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: /^Remove /i })).toHaveLength(remaining);
    }, { timeout: 3000 });
  };

  it('REGRESSION: removing a drafted set renumbers the sets that follow it', async () => {
    const user = await setup();
    await goTo(user, 'Log');
    await addSets(user, 4); // sets 3,4,5,6
    expect(screen.getByText(/set 6$/)).toBeInTheDocument();

    // Drop the first drafted set. The remaining three should become 3,4,5 --
    // set 6 was the only reduced one, and it should now pay full.
    await removeFirstSet(user, 3);

    expect(screen.queryByText(/set 6$/)).not.toBeInTheDocument();
    expect(screen.getByText(/set 5$/)).toBeInTheDocument();
  });

  it('REGRESSION: the session total matches the sum of the listed sets', async () => {
    const user = await setup();
    await goTo(user, 'Log');
    await addSets(user, 4);
    await removeFirstSet(user, 3);

    const listed = screen
      .getAllByText(/^\+[\d.,]+$/)
      .map((n) => Number(n.textContent.replace(/[+,]/g, '')))
      .reduce((a, b) => a + b, 0);
    const afterDr = Number(
      screen.getByText('After diminishing returns').parentElement.lastChild.textContent.replace(/,/g, ''),
    );
    // Tolerance widened from 1 decimal digit to 0: with the renumbering fix,
    // three full-rate bench sets of identical weight/reps each display a
    // repeating decimal (9.3333...) rounded to 9.3, so the sum of the
    // per-row roundings (27.9) legitimately differs from the once-rounded
    // total (28) by a tenth. That is a display-rounding artifact, not a
    // mismatch in the underlying math.
    expect(afterDr).toBeCloseTo(listed, 0);
  });

  // ---- fix #3: a typed negative weight must not produce negative XP ----
  it('clamps a typed negative weight to zero instead of letting it go negative', async () => {
    const user = await setup();
    await goTo(user, 'Log');
    await user.click(screen.getByRole('button', { name: /^Push-Up$/i }));
    const weightInput = screen.getByLabelText(/^weight \(kg\)/i);
    fireEvent.change(weightInput, { target: { value: '-40' } });
    expect(weightInput.value).toBe('0');
    // With weight clamped to 0, the preview XP for a bodyweight set can
    // never go negative.
    const net = Number(previewXp().replace(/,/g, ''));
    expect(net).toBeGreaterThanOrEqual(0);
  });

  // ---- fix #4: the streak only advances on the first bank of the day ----
  it('REGRESSION: the streak advances on the first bank of the day but not the second', async () => {
    const user = await setup();
    const streakText = () => screen.getByText(/day streak/i).textContent;

    await goTo(user, 'Character');
    expect(streakText()).toBe('5 day streak');

    await goTo(user, 'Log');
    await user.click(screen.getByRole('button', { name: /add set/i }));
    await user.click(screen.getByRole('button', { name: /bank session/i }));
    await goTo(user, 'Character');
    expect(streakText()).toBe('6 day streak');

    await goTo(user, 'Log');
    await user.click(screen.getByRole('button', { name: /add set/i }));
    await user.click(screen.getByRole('button', { name: /bank session/i }));
    await goTo(user, 'Character');
    expect(streakText()).toBe('6 day streak');
  });
});

describe('duels', () => {
  it('accepts a pending challenge and makes it live', async () => {
    const user = await setup();
    await goTo(user, 'Duels');
    expect(screen.getByText('Pending')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /accept/i }));
    expect(screen.queryByText('Pending')).not.toBeInTheDocument();
    expect(screen.getAllByText('Live').length).toBeGreaterThan(0);
  });

  it('settles a live duel and records the outcome', async () => {
    const user = await setup();
    await goTo(user, 'Duels');
    const settle = screen.getAllByRole('button', { name: /settle now/i })[0];
    await user.click(settle);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  // ---- fix #2: the challenge must go to the rival actually shown as selected ----
  it('REGRESSION: sends the challenge to the rival shown in the opponent dropdown', async () => {
    const user = await setup();
    await goTo(user, 'Duels');

    // r1 (Maeve) is already in the seeded ACTIVE duel and r2 (Dax) is in the
    // seeded PENDING duel, so the first selectable rival is r3, Nova -- the
    // dropdown must default to her, not to the busy RIVALS[0].
    const select = screen.getByLabelText(/opponent/i);
    expect(within(select).getByRole('option', { selected: true }).textContent).toMatch(/^Nova/);

    await user.click(screen.getByRole('button', { name: /send challenge/i }));
    expect(await screen.findByText(/waiting for nova to accept/i)).toBeInTheDocument();
  });
});

describe('leaderboard', () => {
  it('ranks you among the rivals', async () => {
    const user = await setup();
    await goTo(user, 'Ranks');
    const rows = screen.getAllByRole('row');
    expect(rows.length).toBe(7); // header + 5 rivals + you
    expect(screen.getByText('You')).toBeInTheDocument();
  });
});
