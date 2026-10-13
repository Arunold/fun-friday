/** An RGB colour with channels 0–255. */
export interface Rgb {
  r: number;
  g: number;
  b: number;
}

/**
 * Colours of the arena, resolved from the platform's design tokens so the canvas matches the
 * dark Material theme (the floor is game art and gets its own two tones derived from it).
 */
export interface ArenaTheme {
  floor: string;
  floorAlt: string;
  hazard: Rgb;
  hazardStripe: Rgb;
  text: string;
  shadow: string;
  font: string;
}

const FALLBACK: ArenaTheme = {
  floor: '#15131b',
  floorAlt: '#1a1822',
  hazard: { r: 255, g: 180, b: 171 },
  hazardStripe: { r: 255, g: 207, b: 92 },
  text: '#e8e0f0',
  shadow: 'rgba(8, 6, 14, 0.85)',
  font: 'Inter, system-ui, sans-serif',
};

/** Reads the theme from CSS variables (via a probe element, so `light-dark()` etc. resolve). */
export function resolveTheme(host: HTMLElement): ArenaTheme {
  const probe = document.createElement('span');
  probe.style.display = 'none';
  host.appendChild(probe);
  const read = (token: string): Rgb | null => {
    probe.style.color = '';
    probe.style.color = `var(${token})`;
    return parseColor(getComputedStyle(probe).color);
  };
  try {
    const surface = read('--mat-sys-surface-container-lowest');
    const onSurface = read('--mat-sys-on-surface');
    const danger = read('--ff-danger');
    const warning = read('--ff-warning');
    if (!surface || !onSurface) {
      return FALLBACK;
    }
    const floor = mix(surface, { r: 30, g: 26, b: 44 }, 0.35);
    return {
      floor: css(floor),
      floorAlt: css(mix(floor, onSurface, 0.035)),
      hazard: danger ?? FALLBACK.hazard,
      hazardStripe: warning ?? FALLBACK.hazardStripe,
      text: css(onSurface),
      shadow: FALLBACK.shadow,
      font: getComputedStyle(host).fontFamily || FALLBACK.font,
    };
  } finally {
    probe.remove();
  }
}

/** Parses `rgb()`/`rgba()`, `color(srgb …)` and `#rrggbb`. */
export function parseColor(value: string): Rgb | null {
  const text = value.trim();
  const hex = /^#([0-9a-f]{6})$/i.exec(text);
  if (hex) {
    const n = Number.parseInt(hex[1], 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }
  const rgb = /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)/i.exec(text);
  if (rgb) {
    return { r: Number(rgb[1]), g: Number(rgb[2]), b: Number(rgb[3]) };
  }
  const srgb = /^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)/i.exec(text);
  if (srgb) {
    return { r: Number(srgb[1]) * 255, g: Number(srgb[2]) * 255, b: Number(srgb[3]) * 255 };
  }
  return null;
}

/** `a` blended towards `b` by `amount` (0–1). */
export function mix(a: Rgb, b: Rgb, amount: number): Rgb {
  return { r: a.r + (b.r - a.r) * amount, g: a.g + (b.g - a.g) * amount, b: a.b + (b.b - a.b) * amount };
}

/** A CSS colour string, optionally translucent. */
export function css(color: Rgb, alpha = 1): string {
  const r = Math.round(color.r);
  const g = Math.round(color.g);
  const b = Math.round(color.b);
  return alpha >= 1 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(3)})`;
}

const WHITE: Rgb = { r: 255, g: 255, b: 255 };
const BLACK: Rgb = { r: 0, g: 0, b: 0 };

/** The shades a snake is drawn with, derived from the player's colour. */
export interface SnakePalette {
  base: Rgb;
  head: string;
  body: string;
  /** From the neck (index 0) to the tip of the tail, slightly darker each step. */
  shades: string[];
  /** A small highlight on every segment. */
  sheen: string;
  outline: string;
  /** The bright rim of the viewer's own snake … */
  ring: string;
  /** … and the soft glow around it. */
  glow: string;
}

/** A palette for a player colour (`#rrggbb`); grey for unknown colours. */
export function paletteOf(color: string): SnakePalette {
  const base = parseColor(color) ?? { r: 150, g: 150, b: 160 };
  return {
    base,
    head: css(mix(base, WHITE, 0.14)),
    body: css(base),
    shades: Array.from({ length: 6 }, (_, k) => css(mix(base, BLACK, 0.06 * k))),
    sheen: css(WHITE, 0.24),
    outline: css(mix(base, BLACK, 0.5)),
    ring: css(mix(base, WHITE, 0.6)),
    glow: css(mix(base, WHITE, 0.25), 0.9),
  };
}
