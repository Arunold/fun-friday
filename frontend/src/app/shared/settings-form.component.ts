import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { SettingDef, SettingValues } from '../core/protocol';

/** Human-readable value of a setting, e.g. "30 s" or "On". */
export function settingLabel(def: SettingDef, value: unknown): string {
  switch (def.type) {
    case 'SELECT':
      return def.options.find((option) => option.value === value)?.label ?? String(value ?? '–');
    case 'TOGGLE':
      return value ? 'On' : 'Off';
    default:
      return String(value ?? '–');
  }
}

/**
 * Renders a game's settings form from the schema the server publishes (`GET /api/games`), so the
 * host page, the lobby and the "change game" dialog always offer exactly the same options.
 * Two-way bind the values with `[(values)]`; `readonly` shows a compact summary instead.
 */
@Component({
  selector: 'ff-settings-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonToggleModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule],
  template: `
    @if (readonly()) {
      <dl class="summary">
        @for (def of defs(); track def.key) {
          <div class="item">
            <dt>{{ def.label }}</dt>
            <dd>{{ label(def) }}</dd>
          </div>
        }
      </dl>
    } @else {
      @for (def of defs(); track def.key) {
        <div class="setting" [attr.data-setting]="def.key">
          <span class="label" [id]="labelId(def)">{{ def.label }}</span>
          @switch (def.type) {
            @case ('SELECT') {
              @if (useToggles(def)) {
                <mat-button-toggle-group
                  hideSingleSelectionIndicator
                  [attr.aria-labelledby]="labelId(def)"
                  [value]="values()[def.key]"
                  [disabled]="disabled()"
                  (change)="set(def.key, $event.value)"
                >
                  @for (option of def.options; track option.value) {
                    <mat-button-toggle [value]="option.value">{{ option.label }}</mat-button-toggle>
                  }
                </mat-button-toggle-group>
              } @else {
                <mat-form-field class="select">
                  <mat-select
                    [attr.aria-labelledby]="labelId(def)"
                    [value]="values()[def.key]"
                    [disabled]="disabled()"
                    (selectionChange)="set(def.key, $event.value)"
                  >
                    @for (option of def.options; track option.value) {
                      <mat-option [value]="option.value">{{ option.label }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              }
            }
            @case ('NUMBER') {
              <mat-form-field class="number">
                <input
                  matInput
                  type="number"
                  [attr.aria-labelledby]="labelId(def)"
                  [min]="def.min ?? null"
                  [max]="def.max ?? null"
                  [step]="def.step ?? 1"
                  [value]="values()[def.key]"
                  [disabled]="disabled()"
                  (change)="setNumber(def, $event)"
                />
              </mat-form-field>
            }
            @case ('TOGGLE') {
              <mat-slide-toggle
                [attr.aria-labelledby]="labelId(def)"
                [checked]="!!values()[def.key]"
                [disabled]="disabled()"
                (change)="set(def.key, $event.checked)"
              />
            }
          }
          @if (def.help) {
            <p class="help">{{ def.help }}</p>
          }
        </div>
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 14px;
    }
    .setting {
      display: grid;
      grid-template-columns: minmax(110px, 160px) 1fr;
      align-items: center;
      gap: 4px 16px;
    }
    .label {
      font-weight: 600;
    }
    .help {
      grid-column: 2;
      font-size: 12px;
      color: var(--ff-text-muted);
    }
    .select,
    .number {
      max-width: 220px;
    }
    mat-button-toggle-group {
      width: fit-content;
    }
    .summary {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
      gap: 10px;
      margin: 0;
    }
    .item {
      padding: 10px 14px;
      border-radius: var(--ff-radius-sm);
      background: var(--mat-sys-surface-container-high);
    }
    dt {
      font-size: 12px;
      color: var(--ff-text-muted);
    }
    dd {
      margin: 2px 0 0;
      font-weight: 700;
    }
  `,
})
export class SettingsForm {
  readonly defs = input.required<readonly SettingDef[]>();
  readonly values = model<SettingValues>({});
  readonly readonly = input(false);
  readonly disabled = input(false);

  /** Few short choices render as a segmented control, everything else as a drop-down. */
  protected useToggles(def: SettingDef): boolean {
    const labels = def.options.map((option) => option.label).join('');
    return def.options.length <= 6 && labels.length <= 32;
  }

  protected label(def: SettingDef): string {
    return settingLabel(def, this.values()[def.key]);
  }

  protected labelId(def: SettingDef): string {
    return `ff-setting-${def.key}`;
  }

  protected set(key: string, value: unknown): void {
    this.values.update((values) => ({ ...values, [key]: value }));
  }

  protected setNumber(def: SettingDef, event: Event): void {
    const input = event.target as HTMLInputElement;
    const step = def.step ?? 1;
    let value = Number.parseInt(input.value, 10);
    if (Number.isNaN(value)) {
      value = Number(def.defaultValue);
    }
    value = Math.min(def.max ?? value, Math.max(def.min ?? value, value));
    const base = def.min ?? 0;
    value = base + Math.round((value - base) / step) * step;
    input.value = String(value);
    this.set(def.key, value);
  }
}
