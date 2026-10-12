import { ChangeDetectionStrategy, Component } from '@angular/core';

const COLORS = ['#b69df8', '#ff8a65', '#6fdc8c', '#ffcf5c', '#4fc3f7', '#f48fb1'];

interface Piece {
  left: number;
  delay: number;
  duration: number;
  color: string;
  rotate: number;
  size: number;
  drift: number;
}

/** A one-off CSS confetti burst for the podium (skipped automatically with reduced motion). */
@Component({
  selector: 'ff-confetti',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (piece of pieces; track $index) {
      <span
        class="piece"
        [style.left.%]="piece.left"
        [style.width.px]="piece.size"
        [style.height.px]="piece.size * 0.45"
        [style.background]="piece.color"
        [style.animation-delay.ms]="piece.delay"
        [style.animation-duration.ms]="piece.duration"
        [style.--rotate.deg]="piece.rotate"
        [style.--drift.px]="piece.drift"
      ></span>
    }
  `,
  host: { 'aria-hidden': 'true' },
  styles: `
    :host {
      position: fixed;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
      z-index: 5;
    }
    .piece {
      position: absolute;
      top: -20px;
      border-radius: 2px;
      opacity: 0;
      animation-name: fall;
      animation-timing-function: cubic-bezier(0.25, 0.6, 0.5, 1);
      animation-fill-mode: forwards;
    }
    @keyframes fall {
      0% {
        opacity: 1;
        transform: translate(0, 0) rotate(0deg);
      }
      85% {
        opacity: 1;
      }
      100% {
        opacity: 0;
        transform: translate(var(--drift), 105vh) rotate(var(--rotate));
      }
    }
    @media (prefers-reduced-motion: reduce) {
      :host {
        display: none;
      }
    }
  `,
})
export class Confetti {
  protected readonly pieces: Piece[] = Array.from({ length: 70 }, () => ({
    left: Math.random() * 100,
    delay: Math.random() * 900,
    duration: 2400 + Math.random() * 1800,
    color: COLORS[Math.floor(Math.random() * COLORS.length)],
    rotate: 360 + Math.random() * 720,
    size: 8 + Math.random() * 8,
    drift: (Math.random() - 0.5) * 240,
  }));
}
