import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PointShape } from '../core/shapes';

@Component({
  selector: 'app-series-glyph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
      @switch (shape()) {
        @case ('rect') { <rect x="3" y="3" width="12" height="12" [attr.fill]="color()" stroke="#222" stroke-width="1" /> }
        @case ('triangle') { <polygon points="9,2 16,15 2,15" [attr.fill]="color()" stroke="#222" stroke-width="1" /> }
        @case ('rectRot') { <polygon points="9,1 17,9 9,17 1,9" [attr.fill]="color()" stroke="#222" stroke-width="1" /> }
        @case ('star') { <polygon [attr.points]="star" [attr.fill]="color()" stroke="#222" stroke-width="1" /> }
        @case ('crossRot') { <path d="M3 3 L15 15 M15 3 L3 15" [attr.stroke]="color()" stroke-width="3" /> }
        @default { <circle cx="9" cy="9" r="6.5" [attr.fill]="color()" stroke="#222" stroke-width="1" /> }
      }
    </svg>
  `,
  styles: `:host { display: inline-flex; vertical-align: middle; }`,
})
export class SeriesGlyph {
  readonly color = input.required<string>();
  readonly shape = input.required<PointShape>();

  readonly star = computed(() => {
    const pts: string[] = [];
    for (let i = 0; i < 10; i++) {
      const r = i % 2 === 0 ? 8 : 3.5;
      const a = (Math.PI / 5) * i - Math.PI / 2;
      pts.push(`${(9 + r * Math.cos(a)).toFixed(1)},${(9.5 + r * Math.sin(a)).toFixed(1)}`);
    }
    return pts.join(' ');
  })();
}
