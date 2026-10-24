import { UserDTO, AddressDTO } from "@/types/api";

export const mockUsers: (UserDTO & { password: string })[] = [
  {
    id: 1,
    email: "admin@khmermart.local",
    password: "Admin@12345",
    fullName: "System Administrator",
    phone: "+85512345678",
    role: "ADMIN",
    isActive: true,
    emailVerified: true,
    createdAt: "2026-01-01T00:00:00Z",
  },
  {
    id: 2,
    email: "staff@khmermart.local",
    password: "Staff@12345",
    fullName: "Store Staff Member",
    phone: "+85512345679",
    role: "STAFF",
    isActive: true,
    emailVerified: true,
    createdAt: "2026-01-02T00:00:00Z",
  },
  {
    id: 3,
    email: "customer1@khmermart.local",
    password: "Customer@123",
    fullName: "Sokha Chan",
    phone: "+85512888999",
    role: "CUSTOMER",
    isActive: true,
    emailVerified: true,
    createdAt: "2026-01-03T00:00:00Z",
  },
];

export const mockAddresses: AddressDTO[] = [
  {
    id: 1,
    label: "Home (Phnom Penh)",
    recipientName: "Sokha Chan",
    phone: "+85512888999",
    line1: "House 45, Street 271, Sangkat Boeng Tumpun",
    line2: "Near Russian Market",
    district: "Khan Mean Chey",
    province: "Phnom Penh",
    postalCode: "12000",
    isDefault: true,
  },
  {
    id: 2,
    label: "Office (Siem Reap)",
    recipientName: "Sokha Chan",
    phone: "+85512888999",
    line1: "Building B, Sivutha Blvd",
    district: "Krong Siem Reap",
    province: "Siem Reap",
    postalCode: "17000",
    isDefault: false,
  },
];
