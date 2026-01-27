import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'orderBy',
  standalone: true,
  pure: true
})
export class OrderByPipe implements PipeTransform {

  transform(array: any[], field: string, direction: string = 'asc'): any[] {
    if (!array) {
      return [];
    }

    const sorted = [...array];
    sorted.sort((a, b) => {
      if (a[field] < b[field]) {
        return direction === 'asc' ? -1 : 1;
      } else if (a[field] > b[field]) {
        return direction === 'asc' ? 1 : -1;
      }
      return 0;
    });
    return sorted;
  }
}
