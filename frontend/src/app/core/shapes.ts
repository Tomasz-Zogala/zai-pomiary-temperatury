export type PointShape = 'circle' | 'rect' | 'triangle' | 'rectRot' | 'star' | 'crossRot';

export const SHAPES: { value: PointShape; label: string }[] = [
  { value: 'circle', label: 'Koło' },
  { value: 'rect', label: 'Kwadrat' },
  { value: 'triangle', label: 'Trójkąt' },
  { value: 'rectRot', label: 'Romb' },
  { value: 'star', label: 'Gwiazda' },
  { value: 'crossRot', label: 'Krzyżyk' },
];

const ALIASES: Record<string, PointShape> = { square: 'rect', diamond: 'rectRot', cross: 'crossRot' };

const DASHES: number[][] = [[], [6, 4], [2, 3], [10, 3, 2, 3], [14, 4]];

// Kształt punktu i styl linii pozwalają odróżnić serie bez polegania na kolorze (WCAG)
export function shapeOf(icon: string | null | undefined, index: number): PointShape {
  const v = (icon ?? '').trim();
  if (SHAPES.some((s) => s.value === v)) return v as PointShape;
  if (ALIASES[v]) return ALIASES[v];
  return SHAPES[index % SHAPES.length].value;
}

export function dashOf(index: number): number[] {
  return DASHES[index % DASHES.length];
}

export function shapeLabel(shape: PointShape): string {
  return SHAPES.find((s) => s.value === shape)?.label ?? shape;
}
