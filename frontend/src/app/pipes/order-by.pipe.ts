import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'orderBy',
  standalone: true,
  pure: true
})
export class OrderByPipe implements PipeTransform {

  transform<T extends object>(array: T[], field: keyof T, direction = 'asc'): T[] {
    if (!array) {
      return [];
    }

    const sorted = [...array];
    sorted.sort((a, b) => {
      const aVal = a[field];
      const bVal = b[field];
      if (aVal < bVal) {
        return direction === 'asc' ? -1 : 1;
      } else if (aVal > bVal) {
        return direction === 'asc' ? 1 : -1;
      }
      return 0;
    });
    return sorted;
  }
}
