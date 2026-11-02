import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-admin-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatTabsModule],
  template: `
    <div class="page">
      <h1>Administracja</h1>
      <nav mat-tab-nav-bar [tabPanel]="panel" aria-label="Sekcje administracji">
        @for (l of links; track l.path) {
          <a mat-tab-link [routerLink]="l.path" routerLinkActive #rla="routerLinkActive" [active]="rla.isActive">{{ l.label }}</a>
        }
      </nav>
      <mat-tab-nav-panel #panel>
        <div class="panel"><router-outlet /></div>
      </mat-tab-nav-panel>
    </div>
  `,
  styles: `.panel { padding-top: 1rem; }`,
})
export class AdminShell {
  readonly links = [
    { path: 'series', label: 'Serie' },
    { path: 'sensors', label: 'Czujniki' },
    { path: 'account', label: 'Konto' },
  ];
}
