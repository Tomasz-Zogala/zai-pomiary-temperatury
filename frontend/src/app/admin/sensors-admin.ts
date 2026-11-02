import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../core/api.service';
import { Sensor, Series } from '../core/models';
import { errorMessage } from '../core/errors';
import { ConfirmData, ConfirmDialog } from '../shared/confirm-dialog';
import { ApiKeyDialog } from './api-key-dialog';

@Component({
  selector: 'app-sensors-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule, MatSelectModule,
  ],
  template: `
    <section class="card" aria-labelledby="register-heading">
      <h2 id="register-heading">Rejestracja czujnika</h2>
      <form [formGroup]="form" (ngSubmit)="register(fd)" #fd="ngForm" novalidate>
        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>Nazwa czujnika</mat-label>
            <input matInput formControlName="name" maxlength="100" required />
            @if (form.controls.name.hasError('required')) { <mat-error>Podaj nazwę</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Seria</mat-label>
            <mat-select formControlName="seriesId" required>
              @for (s of series(); track s.id) { <mat-option [value]="s.id">{{ s.name }}</mat-option> }
            </mat-select>
            @if (form.controls.seriesId.hasError('required')) { <mat-error>Wybierz serię</mat-error> }
          </mat-form-field>
        </div>
        @if (formError(); as err) { <p class="error-box" role="alert">{{ err }}</p> }
        <button mat-flat-button type="submit" [disabled]="busy()">Zarejestruj czujnik</button>
      </form>
    </section>

    <section class="card" aria-labelledby="sensors-heading">
      <h2 id="sensors-heading">Zarejestrowane czujniki</h2>
      @if (loading()) { <mat-progress-bar mode="indeterminate" aria-label="Wczytywanie czujników" /> }
      @if (error(); as err) {
        <div class="error-box" role="alert">
          <p>{{ err }}</p>
          <button mat-stroked-button type="button" (click)="load()">Spróbuj ponownie</button>
        </div>
      }
      @if (!loading() && sensors().length === 0 && !error()) { <p>Brak zarejestrowanych czujników.</p> }
      @if (sensors().length) {
        <div class="table-scroll">
          <table class="data-table">
            <caption class="visually-hidden">Lista czujników</caption>
            <thead>
              <tr>
                <th scope="col">Nazwa</th>
                <th scope="col">Seria</th>
                <th scope="col">Zarejestrowany</th>
                <th scope="col">Ostatni wynik</th>
                <th scope="col">Akcje</th>
              </tr>
            </thead>
            <tbody>
              @for (s of sensors(); track s.id) {
                <tr>
                  <td>{{ s.name }}</td>
                  <td>{{ seriesName(s.seriesId) }}</td>
                  <td>{{ s.createdAt | date: 'd.MM.yyyy HH:mm' }}</td>
                  <td>{{ s.lastMeasurementAt ? (s.lastMeasurementAt | date: 'd.MM.yyyy HH:mm') : '–' }}</td>
                  <td>
                    <button mat-button type="button" class="danger" (click)="unregister(s)"
                            [attr.aria-label]="'Wyrejestruj czujnik ' + s.name">Wyrejestruj</button>
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
    :host { display: flex; flex-direction: column; gap: 1rem; }
    form { display: flex; flex-direction: column; align-items: flex-start; }
    form .form-row { width: 100%; }
    .danger { color: #b3261e; }
  `,
})
export class SensorsAdmin implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly sensors = signal<Sensor[]>([]);
  readonly series = signal<Series[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly formError = signal<string | null>(null);

  private readonly names = computed(() => new Map(this.series().map((s) => [s.id, s.name])));

  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    seriesId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  ngOnInit(): void {
    void this.load();
  }

  seriesName(id: number): string {
    return this.names().get(id) ?? `#${id}`;
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [sensors, series] = await Promise.all([
        firstValueFrom(this.api.listSensors()),
        firstValueFrom(this.api.listSeries()),
      ]);
      this.sensors.set(sensors);
      this.series.set(series);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  async register(fd: FormGroupDirective): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { name, seriesId } = this.form.getRawValue();
    this.busy.set(true);
    this.formError.set(null);
    try {
      const created = await firstValueFrom(this.api.registerSensor(name.trim(), seriesId!));
      fd.resetForm();
      this.dialog.open(ApiKeyDialog, { data: created, width: '560px', maxWidth: '95vw', disableClose: true });
      await this.load();
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  async unregister(s: Sensor): Promise<void> {
    const data: ConfirmData = {
      title: 'Wyrejestrować czujnik?',
      message: `Klucz czujnika „${s.name}” natychmiast przestanie działać. Zebrane wyniki pozostaną w serii.`,
      confirmLabel: 'Wyrejestruj',
    };
    const ok = await firstValueFrom(this.dialog.open(ConfirmDialog, { data, maxWidth: '95vw' }).afterClosed());
    if (!ok) return;
    try {
      await firstValueFrom(this.api.unregisterSensor(s.id));
      this.snack.open(`Wyrejestrowano czujnik „${s.name}”.`, 'OK', { duration: 4000 });
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}
