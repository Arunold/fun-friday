import { Injectable } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class WebsocketService {
  private stompClient: Client;
  private connectionState = new BehaviorSubject<boolean>(false);
  private subscriptions = new Map<string, StompSubscription>();
  private pendingSubscriptions: { topic: string; callback: (message: unknown) => void }[] = [];

  constructor() {
    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('WebSocket connected');
        this.connectionState.next(true);
        this.processPendingSubscriptions();
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        this.connectionState.next(false);
      },
    });
  }

  connect() {
    if (!this.stompClient.active) {
      this.stompClient.activate();
    }
  }

  private processPendingSubscriptions() {
    const pending = [...this.pendingSubscriptions];
    this.pendingSubscriptions = [];
    pending.forEach(sub => this.subscribe(sub.topic, sub.callback));
  }

  subscribe(topic: string, callback: (message: unknown) => void): void {
    if (!this.stompClient.active) {
      this.pendingSubscriptions.push({ topic, callback });
      return;
    }

    // Avoid duplicate subscriptions
    if (this.subscriptions.has(topic)) {
      return;
    }

    const subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
      callback(JSON.parse(message.body));
    });
    this.subscriptions.set(topic, subscription);
  }

  unsubscribe(topic: string): void {
    const subscription = this.subscriptions.get(topic);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(topic);
    }
  }

  unsubscribeAll(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions.clear();
  }

  sendMessage(destination: string, message: unknown): void {
    if (this.stompClient.active) {
      this.stompClient.publish({ destination, body: JSON.stringify(message) });
    } else {
      console.error('STOMP client is not connected.');
    }
  }

  isConnected(): boolean {
    return this.stompClient.active;
  }
}
