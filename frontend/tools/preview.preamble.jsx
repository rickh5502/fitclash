/* ------------------------------------------------------------------------
   Preview shim.

   The component source below is byte-for-byte the shared body of
   frontend/src/FitClash.jsx. Only the module preamble differs: in the Vite
   app these names come from `react`, `framer-motion` and `lucide-react`;
   here they come from the UMD globals plus a small inline icon set drawn in
   lucide's geometry (24x24, 2px stroke, round caps).
   ------------------------------------------------------------------------ */

const { useState, useEffect, useMemo, useRef, useCallback } = React;
const { motion, AnimatePresence, MotionConfig, useReducedMotion } = window.Motion;

const icon = (name, markup) => {
  const Icon = ({ size = 24, className = '', style, ...rest }) =>
    React.createElement('svg', {
      xmlns: 'http://www.w3.org/2000/svg',
      width: size, height: size, viewBox: '0 0 24 24',
      fill: 'none', stroke: 'currentColor', strokeWidth: 2,
      strokeLinecap: 'round', strokeLinejoin: 'round',
      className, style, ...rest,
      dangerouslySetInnerHTML: { __html: markup },
    });
  Icon.displayName = name;
  return Icon;
};

const Dumbbell      = icon('Dumbbell', '<path d="M4 8v8"/><path d="M8 5.5v13"/><path d="M16 5.5v13"/><path d="M20 8v8"/><path d="M8 12h8"/>');
const Swords        = icon('Swords', '<path d="M2.5 2.5 15 15"/><path d="M21.5 2.5 9 15"/><path d="m15 15 2 2-3 3-2-2"/><path d="m9 15-2 2 3 3 2-2"/>');
const Trophy        = icon('Trophy', '<path d="M8 21h8"/><path d="M12 17v4"/><path d="M7 4h10v5a5 5 0 0 1-10 0V4z"/><path d="M17 5h3a3 3 0 0 1-3 3"/><path d="M7 5H4a3 3 0 0 0 3 3"/>');
const Flame         = icon('Flame', '<path d="M12 2c2 4 5 5.2 5 9a5 5 0 0 1-10 0c0-2 1-3.2 2-4.2 0 2 1 3.2 2 3.2 0-3 1-6 1-8z"/>');
const Shield        = icon('Shield', '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>');
const Zap           = icon('Zap', '<path d="M13 2 4 14h7l-1 8 9-12h-7l1-8z"/>');
const Plus          = icon('Plus', '<path d="M12 5v14"/><path d="M5 12h14"/>');
const Minus         = icon('Minus', '<path d="M5 12h14"/>');
const X             = icon('X', '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>');
const Check         = icon('Check', '<path d="M20 6 9 17l-5-5"/>');
const LogOut        = icon('LogOut', '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>');
const TrendingUp    = icon('TrendingUp', '<path d="M22 7 13.5 15.5 8.5 10.5 2 17"/><path d="M16 7h6v6"/>');
const AlertTriangle = icon('AlertTriangle', '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/>');
const Timer         = icon('Timer', '<path d="M10 2h4"/><path d="M12 14v-4"/><circle cx="12" cy="14" r="8"/>');
const Activity      = icon('Activity', '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>');
const Crown         = icon('Crown', '<path d="M2 19h20"/><path d="m3 6 4 5 5-7 5 7 4-5-2 10H5L3 6z"/>');
const Sparkles      = icon('Sparkles', '<path d="m12 3 1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z"/><path d="M19 15v4"/><path d="M17 17h4"/>');

