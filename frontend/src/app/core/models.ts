export interface Series {
  id: number;
  name: string;
  minValue: number;
  maxValue: number;
  color: string;
  icon: string | null;
  unit: string | null;
}

export type SeriesInput = Omit<Series, 'id'>;

export interface Measurement {
  id: number;
  seriesId: number;
  sensorId?: number;
  value: number;
  timestamp: string;
}

export interface Sensor {
  id: number;
  name: string;
  seriesId: number;
  createdAt: string;
  lastMeasurementAt: string | null;
}

export interface SensorCreated extends Sensor {
  apiKey: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface MeasurementQuery {
  series: number[];
  from?: string;
  to?: string;
  sort?: 'timestamp' | '-timestamp';
  limit?: number;
}
