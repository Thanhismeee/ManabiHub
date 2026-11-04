import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './app/App';
import { HelmetProvider } from 'react-helmet-async';
import './styles/index.css';
import { cleanupLegacyVnptIdentityStorage } from './features/kyc/vnptIdentitySdk';

cleanupLegacyVnptIdentityStorage();

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <HelmetProvider>
      <App />
    </HelmetProvider>
  </React.StrictMode>,
);
