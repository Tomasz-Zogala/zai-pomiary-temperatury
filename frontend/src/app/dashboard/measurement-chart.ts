import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, computed, effect, inject, input, viewChild,
} from '@angular/core';
import {
  Chart, ChartDataset, Legend, LineController, LineElement, LinearScale, PointElement, ScriptableContext, TimeScale, Tooltip,
} from 'chart.js';
import 'chartjs-adapter-date-fns';
import { pl } from 'date-fns/locale';
import { Measurement, Series } from '../core/models';
import { dashOf, shapeOf } from '../core/shapes';

Chart.register(LineController, LineElement, PointElement, LinearScale, TimeScale, Tooltip, Legend);

export interface Highlight {
  ts: number;
  seriesId?: number;
}

interface Point {
  x: number;
  y: number;
  id: number;
}

@Component({
  selector: 'app-measurement-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chart-box">
      <canvas #canvas role="img" [attr.aria-label]="ariaLabel()">
        Wykres wyników pomiarów. Te same dane są dostępne w tabeli poniżej.
      </canvas>
    </div>
  `,
  styles: `
    .chart-box { position: relative; height: 380px; width: 100%; }
    @media (max-width: 600px) { .chart-box { height: 300px; } }
    @media print { .chart-box { height: 330px; width: 100%; } }
  `,
})
export class MeasurementChart {
  readonly series = input.required<Series[]>();
  readonly measurements = input.required<Measurement[]>();
  readonly highlight = input<Highlight | null>(null);

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart<'line', Point[]>;

  readonly ariaLabel = computed(() => {
    const names = this.series().map((s) => s.name).join(', ');
    return `Wykres temperatury w funkcji czasu dla serii: ${names || 'brak'}. Szczegóły w tabeli wyników.`;
  });

  private readonly datasets = computed<ChartDataset<'line', Point[]>[]>(() => {
    const bySeries = new Map<number, Point[]>();
    for (const m of this.measurements()) {
      let arr = bySeries.get(m.seriesId);
      if (!arr) bySeries.set(m.seriesId, (arr = []));
      arr.push({ x: Date.parse(m.timestamp), y: m.value, id: m.id });
    }
    return this.series().map((s, i) => {
      const data = (bySeries.get(s.id) ?? []).sort((a, b) => a.x - b.x);
      const hlRadius = (ctx: ScriptableContext<'line'>, normal: number, big: number) =>
        this.isHighlighted(s.id, (ctx.raw as Point | undefined)?.x) ? big : normal;
      return {
        label: s.unit ? `${s.name} (${s.unit})` : s.name,
        data,
        borderColor: s.color,
        backgroundColor: s.color,
        borderDash: dashOf(i),
        borderWidth: 2,
        pointStyle: shapeOf(s.icon, i),
        pointRadius: (ctx: ScriptableContext<'line'>) => hlRadius(ctx, ctx.chart.width < 500 ? 2 : 4, 10),
        pointHoverRadius: 8,
        pointBorderColor: (ctx: ScriptableContext<'line'>) =>
          this.isHighlighted(s.id, (ctx.raw as Point | undefined)?.x) ? '#000' : s.color,
        pointBorderWidth: (ctx: ScriptableContext<'line'>) => hlRadius(ctx, 1, 3),
        tension: 0.2,
        spanGaps: true,
      };
    });
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    afterNextRender(() => {
      this.chart = new Chart<'line', Point[]>(this.canvas().nativeElement, {
        type: 'line',
        data: { datasets: this.datasets() },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: false,
          parsing: false,
          normalized: true,
          interaction: { mode: 'nearest', intersect: false },
          scales: {
            x: {
              type: 'time',
              adapters: { date: { locale: pl } },
              time: { tooltipFormat: 'd MMM yyyy, HH:mm', displayFormats: { hour: 'HH:mm', day: 'd MMM', minute: 'HH:mm' } },
              ticks: {
                major: { enabled: true },
                font: (ctx) => (ctx.tick?.major ? { weight: 'bold' } : {}),
              },
              title: { display: true, text: 'Czas' },
            },
            y: { title: { display: true, text: 'Temperatura' } },
          },
          plugins: {
            legend: { position: 'bottom', labels: { usePointStyle: true, boxHeight: 8, padding: 16 } },
            tooltip: {
              usePointStyle: true,
              callbacks: {
                label: (item) => `${item.dataset.label}: ${(item.parsed.y as number).toLocaleString('pl-PL', { maximumFractionDigits: 2 })}`,
              },
            },
          },
        },
      });
      this.applyHighlight();
    });

    effect(() => {
      const ds = this.datasets();
      if (!this.chart) return;
      this.chart.data.datasets = ds;
      this.chart.update('none');
      this.applyHighlight();
    });

    effect(() => {
      this.highlight();
      if (!this.chart) return;
      this.chart.update('none');
      this.applyHighlight();
    });

    const resize = () => this.chart?.resize();
    window.addEventListener('beforeprint', resize);
    window.addEventListener('afterprint', resize);
    destroyRef.onDestroy(() => {
      window.removeEventListener('beforeprint', resize);
      window.removeEventListener('afterprint', resize);
      this.chart?.destroy();
    });
  }

  private isHighlighted(seriesId: number, x: number | undefined): boolean {
    const h = this.highlight();
    return !!h && x === h.ts && (h.seriesId === undefined || h.seriesId === seriesId);
  }

  // Wyróżniony punkt: większy znacznik z obwódką i otwarty tooltip
  private applyHighlight(): void {
    const chart = this.chart;
    const h = this.highlight();
    if (!chart) return;
    const active: { datasetIndex: number; index: number }[] = [];
    if (h) {
      chart.data.datasets.forEach((ds, datasetIndex) => {
        const sid = this.series()[datasetIndex]?.id;
        if (h.seriesId !== undefined && h.seriesId !== sid) return;
        const index = ds.data.findIndex((p) => p.x === h.ts);
        if (index >= 0) active.push({ datasetIndex, index });
      });
    }
    chart.setActiveElements(active);
    chart.tooltip?.setActiveElements(active, { x: 0, y: 0 });
    chart.update('none');
  }
}
