import { Dir } from './snake-arena.types';

/** A rectangle in CSS pixels. */
interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Gap between neighbouring labels (CSS px). */
const LABEL_MARGIN = 2;

/**
 * Where the names next to the snakes' heads go in one picture: beside the head — above (below) a
 * snake moving sideways, right (left) of one moving up or down — so a label never covers the way
 * ahead; on a spot no earlier label takes and, where possible, off the snakes. A label with no free
 * spot is left out rather than overprinted. Create one per picture and place the most important
 * label first.
 */
export class LabelLayout {
  private readonly placed: Box[] = [];

  /**
   * @param cell the cell size (CSS px)
   * @param width the board width (CSS px)
   * @param height the board height (CSS px)
   * @param cols the grid's columns
   * @param solid the cells taken by snakes
   */
  constructor(
    private readonly cell: number,
    private readonly width: number,
    private readonly height: number,
    private readonly cols: number,
    private readonly solid: ReadonlySet<number>,
  ) {}

  /**
   * The centre for a `width` × `height` label next to a head at (`x`, `y`) px heading `dir`: the
   * first of its two spots that is clear of labels and snakes, else the first clear of labels, else
   * `null` — or the preferred spot anyway if the label is `required`.
   */
  place(
    x: number,
    y: number,
    dir: Dir,
    width: number,
    height: number,
    required: boolean,
  ): { cx: number; cy: number } | null {
    const spots = this.spots(x, y, dir, width).map((center) => ({
      ...center,
      box: { x: center.cx - width / 2, y: center.cy - height / 2, width, height },
    }));
    const free = (box: Box) => !this.placed.some((other) => overlaps(other, box));
    const spot =
      spots.find(({ box }) => free(box) && !this.onSnake(box)) ??
      spots.find(({ box }) => free(box)) ??
      (required ? spots[0] : undefined);
    if (!spot) {
      return null;
    }
    this.placed.push(spot.box);
    return { cx: spot.cx, cy: spot.cy };
  }

  /** The two candidate centres, the preferred one first, kept on the board. */
  private spots(x: number, y: number, dir: Dir, width: number): { cx: number; cy: number }[] {
    const gap = 0.62 * this.cell + 4;
    let spots: { cx: number; cy: number }[];
    if (dir === 'UP' || dir === 'DOWN') {
      const right = { cx: x + gap + width / 2, cy: y };
      const left = { cx: x - gap - width / 2, cy: y };
      spots = right.cx + width / 2 <= this.width ? [right, left] : [left, right];
    } else {
      const above = { cx: x, cy: y - gap - 6 };
      const below = { cx: x, cy: y + gap + 6 };
      spots = above.cy >= 10 ? [above, below] : [below, above];
    }
    return spots.map(({ cx, cy }) => ({
      cx: Math.min(this.width - width / 2 - 2, Math.max(width / 2 + 2, cx)),
      cy: Math.min(this.height - 8, Math.max(8, cy)),
    }));
  }

  /** Whether a box lies on any cell of a snake. */
  private onSnake(box: Box): boolean {
    const x0 = Math.max(0, Math.floor(box.x / this.cell));
    const x1 = Math.min(this.cols - 1, Math.floor((box.x + box.width) / this.cell));
    const y0 = Math.max(0, Math.floor(box.y / this.cell));
    const y1 = Math.floor((box.y + box.height) / this.cell);
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        if (this.solid.has(y * this.cols + x)) {
          return true;
        }
      }
    }
    return false;
  }
}

/** Whether two boxes overlap or nearly touch. */
function overlaps(a: Box, b: Box): boolean {
  return (
    a.x < b.x + b.width + LABEL_MARGIN &&
    b.x < a.x + a.width + LABEL_MARGIN &&
    a.y < b.y + b.height + LABEL_MARGIN &&
    b.y < a.y + a.height + LABEL_MARGIN
  );
}
