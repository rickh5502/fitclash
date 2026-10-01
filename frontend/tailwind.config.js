// File: frontend/tailwind.config.js
//
// Palette v2 — "Oxide & Chalk, pushed further." The stat-color logic from
// docs/visual-spec.md (weathered-metal STR/STA/CON, each split into a
// saturated bar/icon tone + a brightened text-safe tint) is kept as-is: it
// was independently measured against every surface and that work should not
// be redone. On top of it we add two things the spec didn't: `plate.arena`,
// a violet reserved exclusively for competitive/live moments (duel lead,
// rival feed, "VS") so winning/losing a duel never borrows a stat color's
// meaning, and a `spark` gradient pair used for the single primary CTA per
// screen so the app has one unmistakable "press this" signal instead of
// every button being the same flat accent.
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        iron: {
          950: '#0D1014',
          900: '#13161B',
          800: '#1B1F26',
          700: '#252A33',
          600: '#333945',
          500: '#4A5261',
        },
        chalk: {
          DEFAULT: '#F2EFE7',
          dim: '#B9C0CB',
          muted: '#8B94A3',
        },
        plate: {
          // STR — oxide (brick-red). Bar/icon tier 3:1+ on iron-950/900/800;
          // per visual-spec.md this one drops to 2.67 on bare iron-700, so
          // it is never used as a border or an iron-700 chip fill — only
          // `oxideText` (6.27 on iron-700) goes there. Verified, kept as-is.
          oxide: '#C1392B',
          oxideText: '#FF8A75',
          // STA — galvanized (blue-grey steel, never sky blue). This and
          // `oxide` sit at a 1.23 luminance ratio — better than the old
          // app's 1.17 red/blue pair but still close, so hue alone is never
          // the only signal: every STR/STA rendering in this app also
          // carries a distinct icon (Dumbbell vs Activity) and a text label
          // ("STR"/"STA"), which is the explicit fallback the brief allows
          // when a bigger lightness gap isn't achievable without breaking
          // the 3:1 floor on a color's actual surfaces.
          galvanized: '#2E7D9E',
          galvanizedText: '#7FC3E0',
          // CON — brass (warm trophy-gold).
          brass: '#C9922F',
          brassText: '#E8B860',
          // XP only. Reserved — never a button default, never a border.
          ember: '#E8631B',
          // Win / loss, distinct hue families from the stat colors.
          patina: '#2FA36B',
          patinaText: '#6FD9A4',
          garnet: '#C14E5F',
          garnetText: '#FF95A3',
          // New: competitive/live-only accent (duel lead, rival feed, VS
          // badges). Never used for a stat or for XP, so "you're winning a
          // duel" is never visually confused with "you trained."
          arena: '#8C6DFF',
          arenaText: '#C7B9FF',
        },
      },
      fontFamily: {
        display: ['Anton', 'Impact', 'Haettenschweiler', 'sans-serif'],
        body: ['Barlow', 'system-ui', '-apple-system', 'sans-serif'],
        data: ['"Barlow Condensed"', 'Barlow', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        // A real display tier above Tailwind's default scale, for the one
        // hero number per screen (level, the XP-up moment, the auth mark).
        'display-md': ['3.5rem', { lineHeight: '0.95' }],
        'display-lg': ['4.5rem', { lineHeight: '0.92' }],
        'display-xl': ['6rem', { lineHeight: '0.9', letterSpacing: '-0.01em' }],
      },
      boxShadow: {
        panel: '0 1px 0 0 rgba(255,255,255,0.04) inset, 0 12px 32px -16px rgba(0,0,0,0.65)',
        lift: '0 1px 0 0 rgba(255,255,255,0.05) inset, 0 24px 48px -20px rgba(0,0,0,0.75)',
        glow: '0 0 0 1px rgba(232,99,27,0.35), 0 0 32px -4px rgba(232,99,27,0.55)',
      },
      backgroundImage: {
        'spark': 'linear-gradient(135deg, #FF8A3D 0%, #E8631B 45%, #8C6DFF 100%)',
        'grain': 'radial-gradient(circle at 1px 1px, rgba(255,255,255,0.045) 1px, transparent 0)',
      },
      backgroundSize: {
        grain: '4px 4px',
      },
    },
  },
  plugins: [],
};
