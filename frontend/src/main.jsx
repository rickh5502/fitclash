// File: frontend/src/main.jsx
import React from 'react';
import { createRoot } from 'react-dom/client';
import FitClash from './FitClash.jsx';
import './index.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <FitClash />
  </React.StrictMode>,
);
