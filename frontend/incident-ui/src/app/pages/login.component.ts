import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="login-wrap">
      <form class="card login" (ngSubmit)="submit()">
        <h1>Incident Analysis</h1>
        <p class="muted">Sign in to investigate production incidents.</p>

        <label for="username">Username</label>
        <input id="username" name="username" [(ngModel)]="username" autocomplete="username" required />

        <label for="password">Password</label>
        <input id="password" name="password" type="password" [(ngModel)]="password"
               autocomplete="current-password" required />

        @if (error()) {
          <p class="error">{{ error() }}</p>
        }

        <button type="submit" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>

        <p class="hint">
          Development accounts: <code>admin/admin123</code>, <code>sre/sre123</code>,
          <code>viewer/viewer123</code>. A viewer can read incidents but cannot run an analysis.
        </p>
      </form>
    </div>
  `,
  styles: [
    `
      .login-wrap { display: grid; place-items: center; min-height: 80vh; }
      .login { width: min(26rem, 92vw); display: flex; flex-direction: column; gap: 0.5rem; }
      h1 { margin: 0; font-size: 1.4rem; }
      label { font-size: 0.78rem; color: var(--ink-muted); margin-top: 0.6rem; }
      input { padding: 0.6rem 0.7rem; border-radius: 6px; border: 1px solid var(--line);
              background: var(--ground); color: var(--ink); font: inherit; }
      button { margin-top: 1.1rem; }
      .error { color: var(--danger); font-size: 0.86rem; margin: 0.6rem 0 0; }
      .hint { font-size: 0.76rem; color: var(--ink-muted); margin-top: 1.2rem; line-height: 1.6; }
    `,
  ],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = 'sre';
  password = 'sre123';
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigate(['/incidents']);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(
          err.status === 401 ? 'Incorrect username or password.' : 'Could not reach the incident service.',
        );
      },
    });
  }
}
