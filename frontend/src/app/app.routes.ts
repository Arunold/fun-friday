import { Routes } from '@angular/router';

/** Every page is lazy-loaded; game UIs are separate chunks loaded by the room page. */
export const routes: Routes = [
  {
    path: '',
    title: 'Fun Friday',
    loadComponent: () => import('./pages/home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'host/:gameId',
    title: 'Host a game · Fun Friday',
    loadComponent: () => import('./pages/host/host.page').then((m) => m.HostPage),
  },
  {
    path: 'join',
    title: 'Join a room · Fun Friday',
    loadComponent: () => import('./pages/join/join.page').then((m) => m.JoinPage),
  },
  {
    path: 'join/:code',
    title: 'Join a room · Fun Friday',
    loadComponent: () => import('./pages/join/join.page').then((m) => m.JoinPage),
  },
  {
    path: 'room/:code',
    title: 'Fun Friday',
    loadChildren: () => import('./pages/room/room.routes'),
  },
  {
    path: 'history',
    title: 'Hall of fame · Fun Friday',
    loadComponent: () => import('./pages/history/history.page').then((m) => m.HistoryPage),
  },
  {
    path: 'oops/:reason',
    title: 'Oops · Fun Friday',
    loadComponent: () => import('./pages/oops/oops.page').then((m) => m.OopsPage),
  },
  { path: '**', redirectTo: '' },
];
