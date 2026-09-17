import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { IncidentService } from '../core/incident.service';
import { AnalysisResponse, ChatMessage, Incident, Timeline } from '../core/models';

@Component({
  selector: 'app-incident-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, DatePipe, DecimalPipe],
  templateUrl: './incident-detail.component.html',
  styleUrl: './incident-detail.component.css',
})
export class IncidentDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(IncidentService);
  readonly auth = inject(AuthService);

  readonly incidentId = this.route.snapshot.paramMap.get('id')!;
  readonly incident = signal<Incident | null>(null);
  readonly timeline = signal<Timeline | null>(null);
  readonly analysis = signal<AnalysisResponse | null>(null);
  readonly chat = signal<ChatMessage[]>([]);

  readonly analysing = signal(false);
  readonly asking = signal(false);
  readonly error = signal<string | null>(null);
  question = '';

  /** Percentage for the confidence bar. */
  readonly confidencePercent = computed(() =>
    Math.round((this.analysis()?.analysis.confidence ?? 0) * 100),
  );

  constructor() {
    this.service.getIncident(this.incidentId).subscribe({
      next: (incident) => this.incident.set(incident),
      error: () => this.error.set('Could not load this incident.'),
    });
    this.service.getTimeline(this.incidentId).subscribe({
      next: (timeline) => this.timeline.set(timeline),
      error: () => undefined,
    });
    // A 404 here simply means no analysis has been produced yet.
    this.service.getAnalysis(this.incidentId).subscribe({
      next: (analysis) => this.analysis.set(analysis),
      error: () => undefined,
    });
    this.service.chatHistory(this.incidentId).subscribe({
      next: (messages) => this.chat.set(messages),
      error: () => undefined,
    });
  }

  analyse(refresh = false): void {
    this.analysing.set(true);
    this.error.set(null);
    this.service.analyse(this.incidentId, refresh).subscribe({
      next: (analysis) => {
        this.analysis.set(analysis);
        this.analysing.set(false);
      },
      error: (err) => {
        this.analysing.set(false);
        this.error.set(this.describe(err));
      },
    });
  }

  ask(): void {
    const message = this.question.trim();
    if (!message || this.asking()) {
      return;
    }
    this.asking.set(true);
    this.error.set(null);
    this.question = '';
    this.service.ask(this.incidentId, message).subscribe({
      next: () => {
        this.service.chatHistory(this.incidentId).subscribe({
          next: (messages) => {
            this.chat.set(messages);
            this.asking.set(false);
          },
        });
      },
      error: (err) => {
        this.asking.set(false);
        this.error.set(this.describe(err));
      },
    });
  }

  /** Turns a refusal into something an engineer can act on, rather than a bare status code. */
  private describe(err: { status?: number; error?: { message?: string } }): string {
    if (err.status === 429) {
      return err.error?.message ?? 'The model request limit has been reached. Try again later.';
    }
    if (err.status === 403) {
      return 'Your role cannot run an analysis. Sign in as sre or admin.';
    }
    if (err.status === 503) {
      return err.error?.message ?? 'The analysis service is unavailable.';
    }
    if (err.status === 502) {
      return err.error?.message ?? 'The model could not produce a valid analysis.';
    }
    return 'Something went wrong running the analysis.';
  }
}
