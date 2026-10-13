import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';

import { LevelView } from './backseat-drivers.types';
import { BusFeed } from './bus-feed';
import { fontOf, prefersReducedMotion, runCanvas } from './canvas-loop';
import { MapRenderer } from './map-renderer';

/**
 * The whole map: the navigator's view while driving (with what's coming up highlighted and the
 * drivers' circle of sight), and everybody's view once the level is over (with the bus's trail).
 */
@Component({
  selector: 'ff-bd-map-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <canvas #canvas></canvas>
    @if (driving()) {
      <ul class="legend" aria-label="Map legend">
        <li><span class="sign">P</span> Office parking</li>
        <li><span class="dot ceo"></span> CEO's car</li>
        <li><span class="dot cone"></span> Cone −5 s</li>
        <li><span class="dot coffee"></span> Coffee +5 s</li>
        <li>🦢 Geese</li>
        <li><span class="swatch rain"></span> Rain</li>
        <li><span class="swatch tunnel"></span> Tunnel</li>
        <li><span class="ring"></span> Drivers see this far</li>
      </ul>
    }
  `,
  styleUrl: './map-view.component.scss',
  host: {
    role: 'img',
    '[attr.aria-label]': 'label()',
    '[attr.data-status]': 'level().status',
  },
})
export class MapView {
  readonly level = input.required<LevelView>();

  private readonly feed = inject(BusFeed);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  protected readonly driving = computed(() => this.level().status === 'DRIVING');
  protected readonly label = computed(() =>
    this.driving()
      ? `Map of ${this.level().name}: guide the bus to the office parking lot.`
      : `Map of ${this.level().name} with the route the bus took.`,
  );

  constructor() {
    afterNextRender(() => {
      const canvas = this.canvas().nativeElement;
      const renderer = new MapRenderer(canvas, fontOf(this.host.nativeElement));
      const reducedMotion = prefersReducedMotion();
      runCanvas(canvas, this.host.nativeElement, this.destroyRef, ({ now, dpr }) => {
        const level = this.level();
        if (!level.map) {
          return;
        }
        renderer.render({
          now,
          map: level.map,
          pose: this.feed.pose(now),
          frame: this.feed.latest,
          trail: level.trail ?? null,
          status: level.status,
          reducedMotion,
          dpr,
        });
      });
    });
  }
}
