import { HttpErrorResponse } from '@angular/common/http';

interface Problem {
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}

export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Brak połączenia z serwerem. Sprawdź połączenie z internetem i spróbuj ponownie.';
    }
    const p = (typeof err.error === 'object' && err.error) ? (err.error as Problem) : null;
    if (p?.detail) return p.detail;
    if (p?.title) return p.title;
    if (err.status >= 500) return 'Błąd serwera. Spróbuj ponownie za chwilę.';
    return `Żądanie nie powiodło się (kod ${err.status}).`;
  }
  return 'Wystąpił nieoczekiwany błąd.';
}
