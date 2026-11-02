import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SensorCreated } from '../core/models';

@Component({
  selector: 'app-api-key-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Czujnik „{{ data.name }}” zarejestrowany</h2>
    <mat-dialog-content>
      <p class="info-box"><strong>Klucz API jest wyświetlany tylko raz.</strong> Skopiuj go teraz i podaj w generatorze danych (parametr <code>--key</code> lub zmienna <code>ZAI_API_KEY</code>).</p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Klucz API czujnika</mat-label>
        <input matInput [value]="data.apiKey" readonly #key (focus)="key.select()" />
      </mat-form-field>
      <p aria-live="polite" class="copied">{{ copied() }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" (click)="copy()">Kopiuj klucz</button>
      <button mat-flat-button type="button" mat-dialog-close>Zamknij</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; }
    input { font-family: ui-monospace, Menlo, Consolas, monospace; }
    .copied { min-height: 1.5em; margin: 0; }
  `,
})
export class ApiKeyDialog {
  readonly data = inject<SensorCreated>(MAT_DIALOG_DATA);
  readonly copied = signal('');

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.data.apiKey);
      this.copied.set('Skopiowano klucz do schowka.');
    } catch {
      this.copied.set('Nie udało się skopiować – zaznacz klucz i skopiuj ręcznie.');
    }
  }
}
