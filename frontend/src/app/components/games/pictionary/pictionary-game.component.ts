import { Component, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { WebsocketService, GameService } from '../../../services';
import { DrawingStroke, PictionaryRoundInfo } from '../../../models';
import { GameLayoutComponent } from '../../game-layout/game-layout.component';
import { BaseGameComponent } from '../base-game.component';
import { getCurrentTurn, getTotalTurns, isPictionaryRoundInfo } from '../../../utils/game-config.utils';

@Component({
  selector: 'app-pictionary-game',
  templateUrl: './pictionary-game.component.html',
  styleUrl: './pictionary-game.component.css',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatInputModule,
    MatIconModule,
    MatSliderModule,
    MatTooltipModule,
    GameLayoutComponent
  ]
})
export class PictionaryGameComponent extends BaseGameComponent implements AfterViewInit {
  @ViewChild('drawingCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('colorPickerInput') colorPickerRef!: ElementRef<HTMLInputElement>;
  
  guessValue = '';
  playerGuesses: string[] = [];
  readonly gameIcon = 'brush';
  
  // Drawing properties
  private ctx: CanvasRenderingContext2D | null = null;
  private isDrawing = false;
  private currentStroke: DrawingStroke | null = null;
  brushColor = '#FFFFFF';
  brushSize = 4;
  
  // Undo/Redo stacks
  strokeHistory: DrawingStroke[] = [];
  redoStack: DrawingStroke[] = [];
  
  // Extended color palette
  readonly COLORS = [
    '#FFFFFF', '#000000', '#808080',  // White, Black, Gray
    '#FF0000', '#FF5252', '#FF9800',  // Reds, Orange
    '#FFEB3B', '#CDDC39', '#4CAF50',  // Yellow, Lime, Green
    '#00BCD4', '#2196F3', '#3F51B5',  // Cyan, Blue, Indigo
    '#9C27B0', '#E91E63', '#795548'   // Purple, Pink, Brown
  ];

  // Getters for turn info from gameConfig
  get currentTurn(): number {
    return getCurrentTurn(this.game);
  }

  get totalTurns(): number {
    return getTotalTurns(this.game);
  }

  // Typed getter for round info
  get roundInfo(): PictionaryRoundInfo | null {
    const info = this.game?.currentRoundInfo;
    return isPictionaryRoundInfo(info) ? info : null;
  }

  constructor(
    route: ActivatedRoute,
    router: Router,
    websocketService: WebsocketService,
    gameService: GameService
  ) {
    super(route, router, websocketService, gameService);
  }

  ngAfterViewInit(): void {
    this.initCanvas();
  }

  private initCanvas(): void {
    if (this.canvasRef?.nativeElement) {
      const canvas = this.canvasRef.nativeElement;
      this.ctx = canvas.getContext('2d');
      if (this.ctx) {
        this.ctx.lineCap = 'round';
        this.ctx.lineJoin = 'round';
        this.clearCanvas();
      }
    }
  }

  protected override initializeGame(): void {
    super.initializeGame();
    
    // Subscribe to drawing updates
    if (this.gameId) {
      this.websocketService.subscribe('/topic/game/' + this.gameId + '/drawing', (message: any) => {
        if (message.type === 'DRAWING_STROKE' && message.stroke) {
          this.drawReceivedStroke(message.stroke);
          // Store received stroke for other players' undo sync
          if (!this.isDrawer()) {
            this.strokeHistory.push(message.stroke);
          }
        } else if (message.type === 'CLEAR_CANVAS') {
          this.clearCanvas();
          this.strokeHistory = [];
        } else if (message.type === 'UNDO_STROKE' && !this.isDrawer()) {
          // Handle undo from drawer
          if (this.strokeHistory.length > 0) {
            this.strokeHistory.pop();
            this.redrawAllStrokes();
          }
        } else if (message.type === 'REDO_STROKE' && message.stroke && !this.isDrawer()) {
          // Handle redo from drawer
          this.strokeHistory.push(message.stroke);
          this.drawReceivedStroke(message.stroke);
        }
      });

      // Subscribe to guess updates
      this.websocketService.subscribe('/topic/game/' + this.gameId + '/guess', (message: any) => {
        if (message.type === 'PICTIONARY_GUESS') {
          // Handle real-time guess display if needed
        }
      });
    }
  }

  protected onNewRound(): void {
    this.guessValue = '';
    this.playerGuesses = [];
    this.strokeHistory = [];
    this.redoStack = [];
    setTimeout(() => {
      this.initCanvas();
      this.clearCanvas();
    }, 100);
  }

  protected checkIfPlayerAnswered(): void {
    const guesses = this.roundInfo?.playerPictionaryGuesses?.[this.playerName];
    if (guesses && guesses.length > 0) {
      this.playerGuesses = guesses;
    }
    
    // Check if this player guessed correctly
    if (this.roundInfo?.correctGuesser === this.playerName) {
      this.hasGuessed = true;
    }
  }

  override ngOnDestroy(): void {
    super.ngOnDestroy();
    if (this.gameId) {
      this.websocketService.unsubscribe('/topic/game/' + this.gameId + '/drawing');
      this.websocketService.unsubscribe('/topic/game/' + this.gameId + '/guess');
    }
  }

  isDrawer(): boolean {
    return this.roundInfo?.drawerName === this.playerName;
  }

  // Drawing methods
  startDrawing(event: MouseEvent | TouchEvent): void {
    if (!this.isDrawer() || !this.ctx) return;
    
    this.isDrawing = true;
    const pos = this.getPosition(event);
    
    this.currentStroke = {
      points: [pos],
      color: this.brushColor,
      size: this.brushSize
    };
    
    this.ctx.strokeStyle = this.brushColor;
    this.ctx.lineWidth = this.brushSize;
    this.ctx.beginPath();
    this.ctx.moveTo(pos.x, pos.y);
  }

  draw(event: MouseEvent | TouchEvent): void {
    if (!this.isDrawing || !this.ctx || !this.currentStroke) return;
    
    event.preventDefault();
    const pos = this.getPosition(event);
    
    this.currentStroke.points.push(pos);
    this.ctx.lineTo(pos.x, pos.y);
    this.ctx.stroke();
  }

  stopDrawing(): void {
    if (!this.isDrawing || !this.currentStroke) return;
    
    this.isDrawing = false;
    
    // Save stroke to history for undo and clear redo stack
    if (this.currentStroke.points.length > 0) {
      this.strokeHistory.push({ ...this.currentStroke });
      this.redoStack = [];
    }
    
    // Send the stroke to other players (only if still the drawer and connected)
    if (this.gameId && this.currentStroke.points.length > 0 && this.isDrawer() && this.websocketService.isConnected()) {
      this.websocketService.sendMessage('/app/draw', {
        gameId: this.gameId,
        playerName: this.playerName,
        stroke: this.currentStroke
      });
    }
    
    this.currentStroke = null;
  }

  private getPosition(event: MouseEvent | TouchEvent): { x: number; y: number } {
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    
    // Calculate scale factor for CSS-scaled canvas
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    
    // Round to integers to reduce message size
    if (event instanceof MouseEvent) {
      return {
        x: Math.round((event.clientX - rect.left) * scaleX),
        y: Math.round((event.clientY - rect.top) * scaleY)
      };
    } else {
      const touch = event.touches[0];
      return {
        x: Math.round((touch.clientX - rect.left) * scaleX),
        y: Math.round((touch.clientY - rect.top) * scaleY)
      };
    }
  }

  clearCanvasAction(): void {
    if (!this.isDrawer()) return;
    
    this.clearCanvas();
    this.strokeHistory = [];
    this.redoStack = [];
    if (this.gameId && this.websocketService.isConnected()) {
      this.websocketService.sendMessage('/app/clearCanvas', {
        gameId: this.gameId,
        playerName: this.playerName
      });
    }
  }

  private clearCanvas(): void {
    if (this.ctx && this.canvasRef?.nativeElement) {
      const canvas = this.canvasRef.nativeElement;
      this.ctx.fillStyle = '#282828';
      this.ctx.fillRect(0, 0, canvas.width, canvas.height);
    }
  }

  undo(): void {
    if (!this.isDrawer() || this.strokeHistory.length === 0) return;
    
    const lastStroke = this.strokeHistory.pop()!;
    this.redoStack.push(lastStroke);
    this.redrawAllStrokes();
    
    // Sync with other players
    if (this.gameId) {
      this.websocketService.sendMessage('/app/undoStroke', {
        gameId: this.gameId,
        playerName: this.playerName
      });
    }
  }

  redo(): void {
    if (!this.isDrawer() || this.redoStack.length === 0) return;
    
    const stroke = this.redoStack.pop()!;
    this.strokeHistory.push(stroke);
    this.drawReceivedStroke(stroke);
    
    // Sync with other players
    if (this.gameId) {
      this.websocketService.sendMessage('/app/redoStroke', {
        gameId: this.gameId,
        playerName: this.playerName,
        stroke: stroke
      });
    }
  }

  private redrawAllStrokes(): void {
    this.clearCanvas();
    for (const stroke of this.strokeHistory) {
      this.drawReceivedStroke(stroke);
    }
  }

  openColorPicker(): void {
    this.colorPickerRef?.nativeElement?.click();
  }

  onCustomColorChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.brushColor = input.value;
  }

  private drawReceivedStroke(stroke: DrawingStroke): void {
    if (!this.ctx || !stroke.points.length) return;
    
    this.ctx.strokeStyle = stroke.color;
    this.ctx.lineWidth = stroke.size;
    this.ctx.beginPath();
    this.ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
    
    for (let i = 1; i < stroke.points.length; i++) {
      this.ctx.lineTo(stroke.points[i].x, stroke.points[i].y);
    }
    
    this.ctx.stroke();
  }

  // Guessing methods
  submitGuess(): void {
    if (!this.gameId || !this.guessValue.trim() || this.isDrawer()) return;

    this.websocketService.sendMessage('/app/pictionaryGuess', {
      gameId: this.gameId,
      playerName: this.playerName,
      guess: this.guessValue.trim()
    });
    
    this.playerGuesses.push(this.guessValue.trim());
    this.guessValue = '';
  }

  hasPlayerGuessedCorrectly(playerName: string): boolean {
    return this.roundInfo?.correctGuesser === playerName;
  }

  getPlayerGuesses(playerName: string): string[] {
    return this.roundInfo?.playerPictionaryGuesses?.[playerName] || [];
  }

  hasPlayerAnswered(playerName: string): boolean {
    const guesses = this.getPlayerGuesses(playerName);
    return guesses.length > 0 || this.hasPlayerGuessedCorrectly(playerName);
  }

  getWordHint(): string {
    const word = this.roundInfo?.wordToDraw || '';
    return word.split('').map((c: string) => c === ' ' ? ' ' : '_').join(' ');
  }
}
