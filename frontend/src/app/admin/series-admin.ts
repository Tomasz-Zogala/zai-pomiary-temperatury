import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../core/api.service';
import { Series } from '../core/models';
import { errorMessage } from '../core/errors';
import { shapeLabel, shapeOf } from '../core/shapes';
import { SeriesGlyph } from '../shared/series-glyph';
import { ConfirmData, ConfirmDialog } from '../shared/confirm-dialog';
import { SeriesFormDialog } from './series-form-dialog';

@Component({
  selector: 'app-series-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatButtonModule, MatProgressBarModule, SeriesGlyph],
  template: `
    <section class="card" aria-labelledby="series-heading">
      <div class="head">
        <h2 id="series-heading">Serie pomiarowe</h2>
        <button mat-flat-button type="button" (click)="edit(null)">Dodaj serię</button>
      </div>
      @if (loading()) { <mat-progress-bar mode="indeterminate" aria-label="Wczytywanie serii" /> }
      @if (error(); as err) {
        <div class="error-box" role="alert">
          <p>{{ err }}</p>
          <button mat-stroked-button type="button" (click)="load()">Spróbuj ponownie</button>
        </div>
      }
      @if (!loading() && series().length === 0 && !error()) { <p>Brak serii. Dodaj pierwszą serię.</p> }
      @if (series().length) {
        <div class="table-scroll">
          <table class="data-table">
            <caption class="visually-hidden">Lista serii</caption>
            <thead>
              <tr>
                <th scope="col">Wygląd</th>
                <th scope="col">Nazwa</th>
                <th scope="col" class="num">Min</th>
                <th scope="col" class="num">Max</th>
                <th scope="col">Jednostka</th>
                <th scope="col">Akcje</th>
              </tr>
            </thead>
            <tbody>
              @for (s of series(); track s.id; let i = $index) {
                <tr>
                  <td>
                    <app-series-glyph [color]="s.color" [shape]="shape(s, i)" />
                    <span class="visually-hidden">{{ s.color }}, {{ shapeName(s, i) }}</span>
                  </td>
                  <td>{{ s.name }}</td>
                  <td class="num">{{ s.minValue | number: '1.0-3' }}</td>
                  <td class="num">{{ s.maxValue | number: '1.0-3' }}</td>
                  <td>{{ s.unit ?? '–' }}</td>
                  <td>
                    <div class="actions">
                      <button mat-stroked-button type="button" (click)="edit(s)" [attr.aria-label]="'Edytuj serię ' + s.name">Edytuj</button>
                      <button mat-button type="button" class="danger" (click)="remove(s)" [attr.aria-label]="'Usuń serię ' + s.name">Usuń</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: center; gap: .5rem; flex-wrap: wrap; margin-bottom: .5rem; }
    .head h2 { margin: 0; }
    .danger { color: #b3261e; }
  `,
})
export class SeriesAdmin implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly series = signal<Series[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  shape(s: Series, i: number) { return shapeOf(s.icon, i); }
  shapeName(s: Series, i: number) { return shapeLabel(shapeOf(s.icon, i)); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.series.set(await firstValueFrom(this.api.listSeries()));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  async edit(s: Series | null): Promise<void> {
    const ref = this.dialog.open(SeriesFormDialog, { data: s, width: '560px', maxWidth: '95vw', autoFocus: 'first-tabbable' });
    const saved = await firstValueFrom(ref.afterClosed());
    if (saved) {
      this.snack.open(s ? 'Zapisano zmiany serii.' : 'Dodano serię.', 'OK', { duration: 4000 });
      await this.load();
    }
  }

  async remove(s: Series): Promise<void> {
    const data: ConfirmData = {
      title: 'Usunąć serię?',
      message: `Seria „${s.name}” zostanie usunięta razem ze wszystkimi wynikami i przypisanymi czujnikami. Tej operacji nie można cofnąć.`,
      confirmLabel: 'Usuń serię',
    };
    const ok = await firstValueFrom(this.dialog.open(ConfirmDialog, { data, maxWidth: '95vw' }).afterClosed());
    if (!ok) return;
    try {
      await firstValueFrom(this.api.deleteSeries(s.id));
      this.snack.open(`Usunięto serię „${s.name}”.`, 'OK', { duration: 4000 });
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}
