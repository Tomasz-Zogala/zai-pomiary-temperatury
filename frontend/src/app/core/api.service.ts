import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Measurement, MeasurementQuery, Sensor, SensorCreated, Series, SeriesInput } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  listSeries(): Observable<Series[]> {
    return this.http.get<Series[]>('/api/series');
  }

  createSeries(input: SeriesInput): Observable<Series> {
    return this.http.post<Series>('/api/series', input);
  }

  updateSeries(id: number, input: SeriesInput): Observable<Series> {
    return this.http.put<Series>(`/api/series/${id}`, input);
  }

  deleteSeries(id: number): Observable<void> {
    return this.http.delete<void>(`/api/series/${id}`);
  }

  measurements(q: MeasurementQuery): Observable<Measurement[]> {
    let params = new HttpParams().set('series', q.series.join(','));
    if (q.from) params = params.set('from', q.from);
    if (q.to) params = params.set('to', q.to);
    if (q.sort) params = params.set('sort', q.sort);
    if (q.limit) params = params.set('limit', q.limit);
    return this.http.get<Measurement[]>('/api/measurements', { params });
  }

  listSensors(): Observable<Sensor[]> {
    return this.http.get<Sensor[]>('/api/sensors');
  }

  registerSensor(name: string, seriesId: number): Observable<SensorCreated> {
    return this.http.post<SensorCreated>('/api/sensors', { name, seriesId });
  }

  unregisterSensor(id: number): Observable<void> {
    return this.http.delete<void>(`/api/sensors/${id}`);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>('/api/auth/password', { currentPassword, newPassword });
  }
}
