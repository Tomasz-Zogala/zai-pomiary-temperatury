import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/errors';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  template: `
    <div class="page narrow">
      <section class="card" aria-labelledby="login-heading">
        <h1 id="login-heading">Logowanie administratora</h1>
        @if (expired()) {
          <p class="info-box" role="status">Sesja wygasła. Zaloguj się ponownie.</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Login</mat-label>
            <input matInput formControlName="username" autocomplete="username" required />
            @if (form.controls.username.hasError('required')) { <mat-error>Podaj login</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Hasło</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="current-password" required />
            @if (form.controls.password.hasError('required')) { <mat-error>Podaj hasło</mat-error> }
          </mat-form-field>
          @if (error(); as err) {
            <p class="error-box" role="alert">{{ err }}</p>
          }
          @if (busy()) {
            <mat-progress-bar mode="indeterminate" aria-label="Logowanie" />
          }
          <button mat-flat-button type="submit" [disabled]="busy()">Zaloguj</button>
        </form>
      </section>
    </div>
  `,
  styles: `
    .narrow { max-width: 440px; }
    form { display: flex; flex-direction: column; gap: .25rem; }
    button { align-self: flex-start; margin-top: .5rem; }
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();
  readonly expired = input<string>();

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { username, password } = this.form.getRawValue();
      await this.auth.login(username.trim(), password);
      const target = this.returnUrl();
      await this.router.navigateByUrl(target && target.startsWith('/') && !target.startsWith('//') ? target : '/admin');
    } catch (e) {
      this.error.set(e instanceof HttpErrorResponse && e.status === 401 ? 'Niepoprawny login lub hasło.' : errorMessage(e));
      this.form.controls.password.reset();
    } finally {
      this.busy.set(false);
    }
  }
}
