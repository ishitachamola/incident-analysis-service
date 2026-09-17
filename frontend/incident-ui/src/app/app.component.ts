import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <nav class="topbar">
      <a routerLink="/incidents" class="brand">Incident Analysis</a>
      @if (auth.currentUser(); as user) {
        <div class="session">
          <span class="mono">{{ user.username }}</span>
          @for (role of user.roles; track role) { <span class="chip role">{{ role }}</span> }
          <button class="ghost" (click)="signOut()">Sign out</button>
        </div>
      }
    </nav>
    <main><router-outlet /></main>
  `,
  styles: [
    `
      .topbar { display: flex; justify-content: space-between; align-items: center; gap: 1rem;
                padding: 0.85rem 1.5rem; border-bottom: 1px solid var(--line);
                background: var(--surface); }
      .brand { font-weight: 600; text-decoration: none; color: var(--ink); }
      .session { display: flex; align-items: center; gap: 0.5rem; font-size: 0.82rem; }
      main { max-width: 78rem; margin: 0 auto; padding: 1.6rem 1.5rem 4rem; }
    `,
  ],
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  signOut(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
