import { create } from "zustand";
import { UserDTO } from "@/types/api";

interface AuthState {
  token: string | null;
  user: UserDTO | null;
  isLoading: boolean;
  isInitialized: boolean;
  setAuth: (token: string, user: UserDTO) => void;
  clearAuth: () => void;
  setUser: (user: UserDTO) => void;
  setLoading: (isLoading: boolean) => void;
  setInitialized: (isInitialized: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null, // Stored strictly in memory!
  user: null,
  isLoading: true,
  isInitialized: false,
  setAuth: (token, user) => set({ token, user, isLoading: false, isInitialized: true }),
  clearAuth: () => set({ token: null, user: null, isLoading: false, isInitialized: true }),
  setUser: (user) => set({ user }),
  setLoading: (isLoading) => set({ isLoading }),
  setInitialized: (isInitialized) => set({ isInitialized }),
}));
