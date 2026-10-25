import { redirect } from 'next/navigation';

export default function AccountOrderDetailRedirect({
  params,
}: {
  params: { locale: string; orderNumber: string };
}) {
  redirect(`/${params.locale}/orders/${params.orderNumber}`);
}
