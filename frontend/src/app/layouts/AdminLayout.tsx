import { Outlet } from 'react-router-dom';

export function AdminLayout() {
  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-white text-gray-900 shadow px-4 py-3">
        <h1 className="text-xl font-bold">ManabiHub - Admin Panel</h1>
      </header>
      <main className="flex-grow p-4">
        <Outlet />
      </main>
    </div>
  );
}
