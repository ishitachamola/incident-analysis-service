import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthService } from './core/auth.service';

/** Sends anyone without a session to the sign-in page. */
const requireLogin: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'incidents' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'incidents',
    canActivate: [requireLogin],
    loadComponent: () => import('./pages/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'incidents/:id',
    canActivate: [requireLogin],
    loadComponent: () =>
      import('./pages/incident-detail.component').then((m) => m.IncidentDetailComponent),
  },
  { path: '**', redirectTo: 'incidents' },
];
