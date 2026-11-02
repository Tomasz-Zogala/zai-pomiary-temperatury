import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators,
} from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../core/api.service';
import { Series, SeriesInput } from '../core/models';
import { errorMessage } from '../core/errors';
import { PointShape, SHAPES, shapeOf } from '../core/shapes';
import { SeriesGlyph } from '../shared/series-glyph';

function rangeValidator(g: AbstractControl): ValidationErrors | null {
  const min = g.get('minValue')?.value as number | null;
  const max = g.get('maxValue')?.value as number | null;
  return min !== null && max !== null && min >= max ? { range: true } : null;
}

const HEX = /^#[0-9A-Fa-f]{6}$/;

@Component({
  selector: 'app-series-form-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatProgressBarModule, SeriesGlyph,
  ],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Edycja serii' : 'Nowa seria' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Nazwa</mat-label>
          <input matInput formControlName="name" maxlength="100" required />
          @if (form.controls.name.hasError('required')) { <mat-error>Podaj nazwę</mat-error> }
          @if (form.controls.name.hasError('maxlength')) { <mat-error>Najwyżej 100 znaków</mat-error> }
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>Minimum</mat-label>
            <input matInput type="number" step="any" formControlName="minValue" required />
            @if (form.controls.minValue.hasError('required')) { <mat-error>Podaj liczbę</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Maksimum</mat-label>
            <input matInput type="number" step="any" formControlName="maxValue" required />
            @if (form.controls.maxValue.hasError('required')) { <mat-error>Podaj liczbę</mat-error> }
          </mat-form-field>
        </div>
        @if (form.hasError('range') && (form.controls.minValue.touched || form.controls.maxValue.touched)) {
          <p class="error-box" role="alert">Minimum musi być mniejsze niż maksimum.</p>
        }

        <div class="form-row">
          <mat-form-field appearance="outline">
            <mat-label>Jednostka</mat-label>
            <input matInput formControlName="unit" maxlength="20" />
            @if (form.controls.unit.hasError('maxlength')) { <mat-error>Najwyżej 20 znaków</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Znacznik na wykresie</mat-label>
            <mat-select formControlName="icon">
              @for (s of shapes; track s.value) {
                <mat-option [value]="s.value">{{ s.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <div class="color-row">
          <label for="series-color">Kolor</label>
          <input id="series-color" type="color" [value]="form.controls.color.value" (input)="pickColor($event)" />
          <mat-form-field appearance="outline">
            <mat-label>Kolor (#RRGGBB)</mat-label>
            <input matInput formControlName="color" maxlength="7" required />
            @if (form.controls.color.invalid) { <mat-error>Podaj kolor w formacie #RRGGBB</mat-error> }
          </mat-form-field>
          <span class="preview">
            Podgląd: <app-series-glyph [color]="previewColor()" [shape]="previewShape()" />
          </span>
        </div>

        @if (error(); as err) {
          <p class="error-box" role="alert">{{ err }}</p>
        }
        @if (busy()) { <mat-progress-bar mode="indeterminate" aria-label="Zapisywanie" /> }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Anuluj</button>
        <button mat-flat-button type="submit" [disabled]="busy()">Zapisz</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .full { width: 100%; }
    .color-row { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; }
    .color-row input[type=color] { width: 48px; height: 40px; border: 1px solid #79747e; border-radius: 6px; padding: 2px; background: none; }
    .preview { display: inline-flex; align-items: center; gap: .4rem; }
  `,
})
export class SeriesFormDialog {
  private readonly api = inject(ApiService);
  private readonly ref = inject(MatDialogRef<SeriesFormDialog, Series>);
  readonly data = inject<Series | null>(MAT_DIALOG_DATA);

  readonly shapes = SHAPES;
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly previewColor = signal(this.data?.color ?? '#1f77b4');
  readonly previewShape = signal<PointShape>(shapeOf(this.data?.icon, 0));

  readonly form = new FormGroup(
    {
      name: new FormControl(this.data?.name ?? '', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
      minValue: new FormControl<number | null>(this.data?.minValue ?? -40, { validators: [Validators.required] }),
      maxValue: new FormControl<number | null>(this.data?.maxValue ?? 45, { validators: [Validators.required] }),
      unit: new FormControl(this.data?.unit ?? '°C', { nonNullable: true, validators: [Validators.maxLength(20)] }),
      icon: new FormControl<PointShape>(shapeOf(this.data?.icon, 0), { nonNullable: true }),
      color: new FormControl(this.data?.color ?? '#1f77b4', { nonNullable: true, validators: [Validators.required, Validators.pattern(HEX)] }),
    },
    { validators: rangeValidator },
  );

  constructor() {
    this.form.controls.color.valueChanges.subscribe((c) => { if (HEX.test(c)) this.previewColor.set(c); });
    this.form.controls.icon.valueChanges.subscribe((i) => this.previewShape.set(i));
  }

  pickColor(ev: Event): void {
    const v = (ev.target as HTMLInputElement).value;
    this.form.controls.color.setValue(v);
    this.form.controls.color.markAsTouched();
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const input: SeriesInput = {
      name: v.name.trim(),
      minValue: Number(v.minValue),
      maxValue: Number(v.maxValue),
      color: v.color,
      icon: v.icon,
      unit: v.unit.trim() || null,
    };
    this.busy.set(true);
    this.error.set(null);
    try {
      const saved = this.data
        ? await firstValueFrom(this.api.updateSeries(this.data.id, input))
        : await firstValueFrom(this.api.createSeries(input));
      this.ref.close(saved);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
