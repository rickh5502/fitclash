// File: frontend/tailwind.config.js
//
// The palette is taken from calibrated competition plates - red 25 kg, blue
// 20 kg, yellow 15 kg - so STR / STA / CON read as equipment, not as a generic
// chart legend. Ember is hot steel: it is the only colour XP ever uses.
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
          str: '#D8322C',
          sta: '#2C6FD1',
          con: '#E9B424',
          ember: '#FF7A1A',
          jade: '#2FA36B',
          rust: '#B4462F',
          // Text-safe tints of str/sta for use as TEXT (AA 4.5:1+ on both
          // iron-800 and iron-900). The saturated plate.str/sta stay for
          // bars, icons and borders where 3:1 is sufficient.
          strText: '#FF6B5E',
          staText: '#6FA8E8',
        },
      },
      fontFamily: {
        display: ['Anton', 'Impact', 'Haettenschweiler', 'sans-serif'],
        body: ['Barlow', 'system-ui', '-apple-system', 'sans-serif'],
        data: ['"Barlow Condensed"', 'Barlow', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
