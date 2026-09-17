import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API } from './api.config';
import { AuthSession } from './models';

const STORAGE_KEY = 'incident-platform.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<AuthSession | null>(this.restore());

  readonly currentUser = computed(() => this.session());
  readonly isLoggedIn = computed(() => this.session() !== null);
  /** Running an analysis or asking a question spends a model call, so it needs the SRE role. */
  readonly canAnalyse = computed(() => this.hasAnyRole('SRE', 'ADMIN'));

  login(username: string, password: string): Observable<AuthSession> {
    return this.http
      .post<AuthSession>(`${API.incidents}/api/auth/token`, { username, password })
      .pipe(tap((session) => this.store(session)));
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  token(): string | null {
    return this.session()?.accessToken ?? null;
  }

  private hasAnyRole(...roles: string[]): boolean {
    const held = this.session()?.roles ?? [];
    return roles.some((role) => held.includes(role));
  }

  private store(session: AuthSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
  }

  private restore(): AuthSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as AuthSession;
      // A stored token that has already expired is worse than none: it produces confusing 401s.
      return new Date(session.expiresAt) > new Date() ? session : null;
    } catch {
      return null;
    }
  }
}
