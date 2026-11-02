import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Pomiary temperatury',
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'login',
    title: 'Logowanie – Pomiary temperatury',
    loadComponent: () => import('./auth/login').then((m) => m.Login),
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadComponent: () => import('./admin/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'series' },
      {
        path: 'series',
        title: 'Serie – Administracja',
        loadComponent: () => import('./admin/series-admin').then((m) => m.SeriesAdmin),
      },
      {
        path: 'sensors',
        title: 'Czujniki – Administracja',
        loadComponent: () => import('./admin/sensors-admin').then((m) => m.SensorsAdmin),
      },
      {
        path: 'account',
        title: 'Konto – Administracja',
        loadComponent: () => import('./admin/account').then((m) => m.Account),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
