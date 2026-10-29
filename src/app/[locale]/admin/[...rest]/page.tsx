import { notFound } from 'next/navigation';

// Any unknown /admin/* path renders the localized 404 instead of a blank page.
export default function AdminCatchAll() {
  notFound();
}
