import {RouterProvider} from 'react-router-dom';
import {AppProviders} from './providers/AppProviders';
import {router} from './router';
import {AppShell} from '../shared/layouts/AppShell';

export function App() {
    return (
        <AppProviders>
            <AppShell>
                <RouterProvider router={router}/>
            </AppShell>
        </AppProviders>
    );
}
