import { useState, useEffect, useCallback } from 'react';
import { auth } from '../services/api';
import type { User, TokenResponse } from '../types';

export function useAuth() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      auth.me()
        .then((u) => setUser(u as User))
        .catch(() => {
          localStorage.removeItem('token');
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const res = (await auth.login({ username, password })) as TokenResponse;
    localStorage.setItem('token', res.access_token);
    setUser(res.user);
    return res.user;
  }, []);

  const register = useCallback(async (username: string, email: string, password: string) => {
    const res = (await auth.register({ username, email, password })) as TokenResponse;
    localStorage.setItem('token', res.access_token);
    setUser(res.user);
    return res.user;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    setUser(null);
  }, []);

  return { user, loading, login, register, logout };
}
