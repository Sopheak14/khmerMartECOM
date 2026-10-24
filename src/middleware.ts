import createMiddleware from 'next-intl/middleware';
import { locales, defaultLocale } from './i18n/request';

export default createMiddleware({
  locales,
  defaultLocale,
  localePrefix: 'always',
});

export const config = {
  // Match only internationalized pathnames, ignore API routes, static files, uploads, etc.
  matcher: ['/((?!api|_next|_vercel|.*\\..*).*)'],
};
