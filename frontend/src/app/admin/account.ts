import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl, FormControl, FormGroup, FormGroupDirective, ReactiveFormsModule, ValidationErrors, Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/errors';

function passwordsMatch(g: AbstractControl): ValidationErrors | null {
  return g.get('newPassword')?.value === g.get('repeat')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-account',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  template: `
    <section class="card narrow" aria-labelledby="pwd-heading">
      <h2 id="pwd-heading">Zmiana hasła</h2>
      <form [formGroup]="form" (ngSubmit)="submit(fd)" #fd="ngForm" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Bieżące hasło</mat-label>
          <input matInput type="password" formControlName="currentPassword" autocomplete="current-password" required />
          @if (form.controls.currentPassword.hasError('required')) { <mat-error>Podaj bieżące hasło</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Nowe hasło</mat-label>
          <input matInput type="password" formControlName="newPassword" autocomplete="new-password" required aria-describedby="pwd-hint" />
          <mat-hint id="pwd-hint">Co najmniej 8 znaków</mat-hint>
          @if (form.controls.newPassword.hasError('required')) { <mat-error>Podaj nowe hasło</mat-error> }
          @if (form.controls.newPassword.hasError('minlength')) { <mat-error>Hasło musi mieć co najmniej 8 znaków</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Powtórz nowe hasło</mat-label>
          <input matInput type="password" formControlName="repeat" autocomplete="new-password" required />
        </mat-form-field>
        @if (form.hasError('mismatch') && form.controls.repeat.touched) {
          <p class="error-box" role="alert">Hasła nie są identyczne.</p>
        }
        @if (error(); as err) { <p class="error-box" role="alert">{{ err }}</p> }
        @if (busy()) { <mat-progress-bar mode="indeterminate" aria-label="Zapisywanie" /> }
        <button mat-flat-button type="submit" [disabled]="busy()">Zmień hasło</button>
      </form>
    </section>
  `,
  styles: `
    .narrow { max-width: 480px; }
    form { display: flex; flex-direction: column; gap: .25rem; }
    button { align-self: flex-start; margin-top: .5rem; }
  `,
})
export class Account {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup(
    {
      currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      newPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8), Validators.maxLength(72)] }),
      repeat: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: passwordsMatch },
  );

  async submit(fd: FormGroupDirective): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.changePassword(currentPassword, newPassword));
      fd.resetForm();
      this.snack.open('Hasło zostało zmienione.', 'OK', { duration: 4000 });
    } catch (e) {
      this.error.set(e instanceof HttpErrorResponse && e.status === 403 ? 'Bieżące hasło jest niepoprawne.' : errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
