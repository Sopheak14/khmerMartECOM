'use client';

import React, { useState, useEffect } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { useRouter, usePathname } from '@/navigation';
import { AdminSidebar } from '@/components/admin/AdminSidebar';
import { AdminTopBar } from '@/components/admin/AdminTopBar';
import { AdminForbidden } from '@/components/admin/AdminForbidden';
import { Loader2 } from 'lucide-react';

const ADMIN_ONLY_ROUTES = [
  '/admin/payments',
  '/admin/users',
  '/admin/reports',
  '/admin/settings',
  '/admin/audit-logs',
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, isInitialized } = useAuthStore();
  const router = useRouter();
  const pathname = usePathname();
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  useEffect(() => {
    if (isInitialized && !user) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [isInitialized, user, router, pathname]);

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </div>
    );
  }

  // Not logged in -> wait for redirect
  if (!user) {
    return null;
  }

  // Customer guard -> 403 page
  if (user.role === 'CUSTOMER') {
    return <AdminForbidden />;
  }

  // Staff accessing admin-only routes -> 403 page
  const isAdminOnlyRoute = ADMIN_ONLY_ROUTES.some((route) => pathname.startsWith(route));
  if (user.role === 'STAFF' && isAdminOnlyRoute) {
    return (
      <div className="min-h-screen flex bg-background">
        <AdminSidebar
          userRole={user.role}
          isOpenMobile={mobileSidebarOpen}
          onCloseMobile={() => setMobileSidebarOpen(false)}
        />
        <div className="flex-1 flex flex-col min-w-0">
          <AdminTopBar onToggleMobileSidebar={() => setMobileSidebarOpen(true)} />
          <main className="flex-1 p-6">
            <AdminForbidden message="This backoffice module requires full administrator privileges." />
          </main>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex bg-background">
      {/* Navy Sidebar */}
      <AdminSidebar
        userRole={user.role}
        isOpenMobile={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
      />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-x-hidden">
        <AdminTopBar onToggleMobileSidebar={() => setMobileSidebarOpen(true)} />
        <main className="flex-1 p-4 sm:p-6 lg:p-8 overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  );
}
