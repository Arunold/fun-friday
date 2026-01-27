import { Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {

  private stompClient: Client;
  private subscriptions = new Map<string, StompSubscription>();
  private pendingSubscriptions: { topic: string; callback: (message: any) => void }[] = [];

  constructor() {
    this.stompClient = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => this.processPendingSubscriptions()
    });
  }

  connect(): void {
    if (!this.stompClient.active) {
      this.stompClient.activate();
    }
  }

  private processPendingSubscriptions(): void {
    const pending = [...this.pendingSubscriptions];
    this.pendingSubscriptions = [];
    pending.forEach(sub => this.subscribe(sub.topic, sub.callback));
  }

  subscribe(topic: string, callback: (message: any) => void): void {
    if (!this.stompClient.active) {
      this.pendingSubscriptions.push({ topic, callback });
      return;
    }

    if (this.subscriptions.has(topic)) {
      return;
    }

    const subscription = this.stompClient.subscribe(topic, (message) => {
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

  sendMessage(destination: string, message: any): void {
    if (this.stompClient.active && this.stompClient.connected) {
      try {
        this.stompClient.publish({ destination, body: JSON.stringify(message) });
      } catch {
        // Connection lost during send - ignore
      }
    }
  }

  isConnected(): boolean {
    return this.stompClient.active && this.stompClient.connected;
  }
}
