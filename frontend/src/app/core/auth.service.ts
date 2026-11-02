import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LoginResponse } from './models';

const STORAGE_KEY = 'pomiary.session';

interface StoredSession {
  token: string;
  expiresAt: number;
}

// Token trzymany w sessionStorage: znika po zamknięciu karty, wygasa razem z tokenem serwera
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<StoredSession | null>(this.restore());

  readonly loggedIn = computed(() => {
    const s = this.session();
    return !!s && s.expiresAt > Date.now();
  });

  token(): string | null {
    const s = this.session();
    if (!s) return null;
    if (s.expiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return s.token;
  }

  async login(username: string, password: string): Promise<void> {
    const res = await firstValueFrom(this.http.post<LoginResponse>('/api/auth/login', { username, password }));
    const s: StoredSession = { token: res.accessToken, expiresAt: Date.now() + res.expiresIn * 1000 };
    this.session.set(s);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    } catch {
      /* brak dostępu do storage - sesja tylko w pamięci */
    }
  }

  async logout(): Promise<void> {
    if (this.token()) {
      try {
        await firstValueFrom(this.http.post<void>('/api/auth/logout', null));
      } catch {
        /* token i tak usuwamy lokalnie */
      }
    }
    this.clear();
  }

  clear(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignoruj */
    }
  }

  private restore(): StoredSession | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as StoredSession;
      return s.expiresAt > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
