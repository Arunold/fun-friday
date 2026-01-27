import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { GameSetupComponent } from './components/game-setup/game-setup.component';
import { ErrorPageComponent } from './components/error-page/error-page.component';
import { ResultsComponent } from './components/results/results.component';
import {
    NumberGuessGameComponent,
    WordScrambleGameComponent,
    PictionaryGameComponent,
    SpeedTypingGameComponent,
    ReactionShowdownGameComponent
} from './components/games';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'setup/host/:gameTypeId', component: GameSetupComponent },
  { path: 'setup/join/:gameCode', component: GameSetupComponent },
  { path: 'error/:type', component: ErrorPageComponent },
  { path: 'game/number-guess/:id', component: NumberGuessGameComponent },
  { path: 'game/word-scramble/:id', component: WordScrambleGameComponent },
  { path: 'game/pictionary/:id', component: PictionaryGameComponent },
  { path: 'game/speed-typing/:id', component: SpeedTypingGameComponent },
  { path: 'game/reaction-showdown/:id', component: ReactionShowdownGameComponent },
  { path: 'results/:id', component: ResultsComponent },
  { path: '**', redirectTo: '' }
];
