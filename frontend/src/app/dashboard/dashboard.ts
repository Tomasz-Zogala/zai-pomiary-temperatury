import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { firstValueFrom, Subscription } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ApiService } from '../core/api.service';
import { LiveService, LiveStatus } from '../core/live.service';
import { Measurement, Series } from '../core/models';
import { errorMessage } from '../core/errors';
import { PointShape, shapeLabel, shapeOf } from '../core/shapes';
import { SeriesGlyph } from '../shared/series-glyph';
import { Highlight, MeasurementChart } from './measurement-chart';

const LIMIT = 10000;

interface Row {
  ts: number;
  cells: Map<number, Measurement>;
}

function rangeValidator(group: AbstractControl): ValidationErrors | null {
  const from = group.get('from')?.value as string;
  const to = group.get('to')?.value as string;
  return from && to && new Date(from) > new Date(to) ? { range: true } : null;
}

function toLocalInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule, DatePipe, DecimalPipe, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, MatSlideToggleModule, SeriesGlyph, MeasurementChart,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(ApiService);
  private readonly live = inject(LiveService);
  private readonly destroyRef = inject(DestroyRef);

  readonly series = signal<Series[]>([]);
  readonly selected = signal<ReadonlySet<number>>(new Set());
  readonly measurements = signal<Measurement[]>([]);
  readonly loadingSeries = signal(true);
  readonly loadingData = signal(false);
  readonly error = signal<string | null>(null);
  readonly highlight = signal<Highlight | null>(null);
  readonly liveEnabled = signal(true);
  readonly liveStatus = signal<LiveStatus | null>(null);
  readonly truncated = signal(false);
  readonly applied = signal<{ from: string; to: string }>({ from: '', to: '' });

  readonly filters = new FormGroup(
    {
      from: new FormControl<string>('', { nonNullable: true }),
      to: new FormControl<string>('', { nonNullable: true }),
    },
    { validators: rangeValidator },
  );

  readonly shapes = computed(() => {
    const m = new Map<number, PointShape>();
    this.series().forEach((s, i) => m.set(s.id, shapeOf(s.icon, i)));
    return m;
  });

  readonly visibleSeries = computed(() => this.series().filter((s) => this.selected().has(s.id)));
  readonly visibleNames = computed(() => this.visibleSeries().map((s) => s.name).join(', ') || 'brak');

  readonly rows = computed<Row[]>(() => {
    const map = new Map<number, Row>();
    for (const m of this.measurements()) {
      const ts = Date.parse(m.timestamp);
      let row = map.get(ts);
      if (!row) map.set(ts, (row = { ts, cells: new Map() }));
      row.cells.set(m.seriesId, m);
    }
    return [...map.values()].sort((a, b) => b.ts - a.ts);
  });

  readonly isLiveRange = computed(() => !this.applied().to);

  private liveSub?: Subscription;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.liveSub?.unsubscribe());
    void this.loadSeries();
  }

  async loadSeries(): Promise<void> {
    this.loadingSeries.set(true);
    this.error.set(null);
    try {
      const list = await firstValueFrom(this.api.listSeries());
      this.series.set(list);
      this.selected.set(new Set(list.map((s) => s.id)));
      await this.apply();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loadingSeries.set(false);
    }
  }

  async apply(): Promise<void> {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    const { from, to } = this.filters.getRawValue();
    this.applied.set({ from, to });
    await this.loadMeasurements();
    this.updateLive();
  }

  async loadMeasurements(): Promise<void> {
    const ids = [...this.selected()];
    this.highlight.set(null);
    if (ids.length === 0) {
      this.measurements.set([]);
      return;
    }
    const { from, to } = this.applied();
    this.loadingData.set(true);
    this.error.set(null);
    try {
      const data = await firstValueFrom(this.api.measurements({
        series: ids,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
        sort: '-timestamp',
        limit: LIMIT,
      }));
      this.truncated.set(data.length >= LIMIT);
      this.measurements.set(data.reverse());
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loadingData.set(false);
    }
  }

  quickRange(hours: number | null): void {
    this.filters.setValue({
      from: hours === null ? '' : toLocalInput(new Date(Date.now() - hours * 3600_000)),
      to: '',
    });
    void this.apply();
  }

  toggleSeries(id: number, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) next.add(id);
    else next.delete(id);
    this.selected.set(next);
    void this.loadMeasurements();
  }

  selectAll(all: boolean): void {
    this.selected.set(new Set(all ? this.series().map((s) => s.id) : []));
    void this.loadMeasurements();
  }

  setLive(on: boolean): void {
    this.liveEnabled.set(on);
    this.updateLive();
  }

  select(ts: number, seriesId?: number): void {
    const h = this.highlight();
    this.highlight.set(h && h.ts === ts && h.seriesId === seriesId ? null : { ts, seriesId });
  }

  isSelected(ts: number, seriesId?: number): boolean {
    const h = this.highlight();
    return !!h && h.ts === ts && (seriesId === undefined ? h.seriesId === undefined : h.seriesId === seriesId);
  }

  shapeName(id: number): string {
    return shapeLabel(this.shapes().get(id) ?? 'circle');
  }

  print(): void {
    window.print();
  }

  private updateLive(): void {
    const want = this.liveEnabled() && this.isLiveRange();
    if (!want) {
      this.liveSub?.unsubscribe();
      this.liveSub = undefined;
      this.liveStatus.set(null);
      return;
    }
    if (this.liveSub) return;
    this.liveSub = this.live.stream((s) => this.liveStatus.set(s)).subscribe((m) => this.onLive(m));
  }

  private onLive(m: Measurement): void {
    if (!this.selected().has(m.seriesId)) return;
    const from = this.applied().from;
    if (from && Date.parse(m.timestamp) < new Date(from).getTime()) return;
    if (this.measurements().some((x) => x.id === m.id)) return;
    this.measurements.update((list) => [...list, m]);
  }
}
