import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from './api.config';
import {
  AnalysisResponse,
  ChatMessage,
  ChatReply,
  Incident,
  Timeline,
  UsageSnapshot,
} from './models';

@Injectable({ providedIn: 'root' })
export class IncidentService {
  private readonly http = inject(HttpClient);

  listIncidents(): Observable<Incident[]> {
    return this.http.get<Incident[]>(`${API.incidents}/api/incidents`);
  }

  getIncident(id: string): Observable<Incident> {
    return this.http.get<Incident>(`${API.incidents}/api/incidents/${id}`);
  }

  getTimeline(id: string): Observable<Timeline> {
    return this.http.get<Timeline>(`${API.incidents}/api/incidents/${id}/timeline`);
  }

  /** Returns the stored analysis; 404 means none has been produced yet. */
  getAnalysis(id: string): Observable<AnalysisResponse> {
    return this.http.get<AnalysisResponse>(`${API.analysis}/api/incidents/${id}/analysis`);
  }

  analyse(id: string, refresh = false): Observable<AnalysisResponse> {
    return this.http.post<AnalysisResponse>(
      `${API.analysis}/api/incidents/${id}/analyze?refresh=${refresh}`,
      {},
    );
  }

  chatHistory(id: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${API.analysis}/api/incidents/${id}/chat`);
  }

  ask(id: string, message: string): Observable<ChatReply> {
    return this.http.post<ChatReply>(`${API.analysis}/api/incidents/${id}/chat`, { message });
  }

  usage(): Observable<UsageSnapshot> {
    return this.http.get<UsageSnapshot>(`${API.analysis}/api/ai/usage`);
  }
}
