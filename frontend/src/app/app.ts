import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  template: `
    <a class="skip-link" href="#main" (click)="skipToMain($event)">Przejdź do treści</a>
    <header>
      <mat-toolbar class="toolbar">
        <a class="brand" routerLink="/">
          <img src="favicon.svg" alt="" width="28" height="28" />
          <span>Pomiary temperatury</span>
        </a>
        <span class="spacer"></span>
        <nav aria-label="Nawigacja główna" class="nav">
          <a mat-button routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"
             ariaCurrentWhenActive="page">Dane</a>
          @if (auth.loggedIn()) {
            <a mat-button routerLink="/admin" routerLinkActive="active" ariaCurrentWhenActive="page">Administracja</a>
            <button mat-stroked-button type="button" (click)="logout()">Wyloguj</button>
          } @else {
            <a mat-stroked-button routerLink="/login" routerLinkActive="active" ariaCurrentWhenActive="page">Zaloguj</a>
          }
        </nav>
      </mat-toolbar>
    </header>
    <main id="main" tabindex="-1">
      <router-outlet />
    </main>
  `,
  styles: `
    .skip-link {
      position: absolute; left: .5rem; top: -3rem; z-index: 100;
      background: #fff; color: #0b57d0; padding: .5rem 1rem; border-radius: 6px;
      box-shadow: 0 2px 6px rgba(0,0,0,.3);
    }
    .skip-link:focus { top: .5rem; }
    .toolbar {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      gap: .5rem;
      flex-wrap: wrap;
      height: auto;
      min-height: 64px;
      padding-block: .25rem;
    }
    .brand {
      display: inline-flex; align-items: center; gap: .5rem;
      color: inherit; text-decoration: none; font-weight: 600; font-size: 1.15rem;
    }
    .spacer { flex: 1 1 auto; }
    .nav { display: flex; gap: .25rem; align-items: center; flex-wrap: wrap; }
    .nav .active { font-weight: 700; text-decoration: underline; text-underline-offset: 4px; }
    main:focus { outline: none; }
    @media (max-width: 480px) {
      .brand span { font-size: 1rem; }
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigateByUrl('/');
  }

  skipToMain(ev: Event): void {
    ev.preventDefault();
    document.getElementById('main')?.focus();
  }
}
