import { PublicSettingsDTO, ShippingMethodDTO, ProvinceDTO } from "@/types/api";

export const mockPublicSettings: PublicSettingsDTO = {
  storeName: "KhmerMart",
  khrRate: 4100,
  khqrMode: "MOCK",
  defaultCurrency: "USD",
  freeShippingOverCents: 5000,
  seasonalBanner: {
    title: "Khmer New Year Mega Deals",
    titleKm: "មហាប្រូម៉ូសិនពិសេស ពិធីបុណ្យចូលឆ្នាំខ្មែរ",
    imageUrl: "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=1600&auto=format&fit=crop&q=80",
    link: "/products?onSale=true",
  },
  flashSale: {
    id: 1,
    name: "Midnight Tech Madness",
    endsAt: new Date(Date.now() + 86400000 * 2).toISOString(),
  },
};

export const mockShippingMethods: ShippingMethodDTO[] = [
  {
    code: "STANDARD",
    name: "Standard Delivery (2-4 Days)",
    nameKm: "សេវាដឹកជញ្ជូនធម្មតា (២-៤ ថ្ងៃ)",
    feeCents: 150,
    freeOverCents: 5000,
    etaDaysMin: 2,
    etaDaysMax: 4,
    phnomPenhOnly: false,
  },
  {
    code: "EXPRESS",
    name: "Express Delivery (1-2 Days)",
    nameKm: "សេវាដឹកជញ្ជូនរហ័ស (១-២ ថ្ងៃ)",
    feeCents: 300,
    etaDaysMin: 1,
    etaDaysMax: 2,
    phnomPenhOnly: false,
  },
  {
    code: "SAME_DAY",
    name: "Same Day Delivery (Phnom Penh Only)",
    nameKm: "សេវាដឹកជញ្ជូនភ្លាមៗក្នុងថ្ងៃ (រាជធានីភ្នំពេញប៉ុណ្ណោះ)",
    feeCents: 250,
    etaDaysMin: 0,
    etaDaysMax: 1,
    phnomPenhOnly: true,
  },
];

export const mockProvinces: ProvinceDTO[] = [
  { code: "PP", name: "Phnom Penh", nameKm: "រាជធានីភ្នំពេញ" },
  { code: "KD", name: "Kandal", nameKm: "ខេត្តកណ្តាល" },
  { code: "SR", name: "Siem Reap", nameKm: "ខេត្តសៀមរាប" },
  { code: "BB", name: "Battambang", nameKm: "ខេត្តបាត់ដំបង" },
  { code: "KP", name: "Kampot", nameKm: "ខេត្តកំពត" },
  { code: "SHV", name: "Preah Sihanouk", nameKm: "ខេត្តព្រះសីហនុ" },
  { code: "TK", name: "Takeo", nameKm: "ខេត្តតាកែវ" },
  { code: "KC", name: "Kampong Cham", nameKm: "ខេត្តកំពង់ចាម" },
  { code: "KN", name: "Kampong Chhnang", nameKm: "ខេត្តកំពង់ឆ្នាំង" },
  { code: "KS", name: "Kampong Speu", nameKm: "ខេត្តកំពង់ស្ពឺ" },
  { code: "KT", name: "Kampong Thom", nameKm: "ខេត្តកំពង់ធំ" },
  { code: "KK", name: "Koh Kong", nameKm: "ខេត្តកោះកុង" },
  { code: "KR", name: "Kratie", nameKm: "ខេត្តក្រចេះ" },
  { code: "MK", name: "Mondulkiri", nameKm: "ខេត្តមណ្ឌលគិរី" },
  { code: "OD", name: "Oddar Meanchey", nameKm: "ខេត្តឧត្តរមានជ័យ" },
  { code: "PL", name: "Pailin", nameKm: "ខេត្តប៉ៃលិន" },
  { code: "PV", name: "Preah Vihear", nameKm: "ខេត្តព្រះវិហារ" },
  { code: "PS", name: "Pursat", nameKm: "ខេត្តពោធិ៍សាត់" },
  { code: "PR", name: "Prey Veng", nameKm: "ខេត្តព្រៃវែង" },
  { code: "RK", name: "Ratanakiri", nameKm: "ខេត្តរតនគិរី" },
  { code: "SG", name: "Stung Treng", nameKm: "ខេត្តស្ទឹងត្រែង" },
  { code: "SV", name: "Svay Rieng", nameKm: "ខេត្តស្វាយរៀង" },
  { code: "TB", name: "Tboung Khmum", nameKm: "ខេត្តត្បូងឃ្មុំ" },
  { code: "KP-KEP", name: "Kep", nameKm: "ខេត្តកែប" },
  { code: "BM", name: "Banteay Meanchey", nameKm: "ខេត្តបន្ទាយមានជ័យ" },
];
