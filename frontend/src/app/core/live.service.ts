import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Measurement } from './models';

export type LiveStatus = 'connecting' | 'open' | 'error';

// Nowe wyniki przesyłane przez serwer (Server-Sent Events)
@Injectable({ providedIn: 'root' })
export class LiveService {
  stream(onStatus: (s: LiveStatus) => void): Observable<Measurement> {
    return new Observable<Measurement>((sub) => {
      if (typeof EventSource === 'undefined') {
        onStatus('error');
        return;
      }
      onStatus('connecting');
      const es = new EventSource('/api/measurements/stream');
      es.onopen = () => onStatus('open');
      es.onerror = () => onStatus(es.readyState === EventSource.CLOSED ? 'error' : 'connecting');
      es.addEventListener('measurement', (e) => {
        try {
          sub.next(JSON.parse((e as MessageEvent<string>).data) as Measurement);
        } catch {
          /* pomiń uszkodzone zdarzenie */
        }
      });
      return () => es.close();
    });
  }
}
