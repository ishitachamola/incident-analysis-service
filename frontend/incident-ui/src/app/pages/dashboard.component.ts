import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IncidentService } from '../core/incident.service';
import { Incident, UsageSnapshot } from '../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <header class="page-head">
      <div>
        <h1>Active incidents</h1>
        <p class="muted">Detected automatically from the ingested log stream.</p>
      </div>
      @if (usage(); as u) {
        <div class="usage" title="Model requests used today against the configured daily limit">
          <span class="usage-label">Model budget today</span>
          @for (model of u.models; track model.model) {
            @if (model.model === u.activeModel) {
              <span class="usage-value">{{ model.requestsToday }} / {{ model.requestsPerDayLimit }}</span>
              <span class="usage-model">{{ model.model }}</span>
            }
          }
        </div>
      }
    </header>

    @if (loading()) {
      <p class="muted">Loading incidents…</p>
    } @else if (error()) {
      <p class="error">{{ error() }}</p>
    } @else if (incidents().length === 0) {
      <div class="card empty">
        <p>No incidents yet.</p>
        <p class="muted">Run a scenario from the event simulator, then wait for the detection cycle.</p>
      </div>
    } @else {
      <div class="card table-wrap">
        <table>
          <thead>
            <tr><th>Service</th><th>Title</th><th>Severity</th><th>Status</th><th>Detected</th></tr>
          </thead>
          <tbody>
            @for (incident of incidents(); track incident.id) {
              <tr [routerLink]="['/incidents', incident.id]" class="row-link">
                <td class="mono">{{ incident.serviceName }}</td>
                <td>{{ incident.title }}</td>
                <td><span class="chip sev-{{ incident.severity.toLowerCase() }}">{{ incident.severity }}</span></td>
                <td><span class="chip status">{{ incident.status }}</span></td>
                <td class="mono muted">{{ incident.detectedAt | date: 'dd MMM HH:mm' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [
    `
      .page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem;
                   flex-wrap: wrap; margin-bottom: 1.2rem; }
      h1 { margin: 0 0 0.25rem; font-size: 1.5rem; }
      .usage { background: var(--surface); border: 1px solid var(--line); border-radius: 8px;
               padding: 0.6rem 0.9rem; display: grid; gap: 0.1rem; }
      .usage-label { font-size: 0.68rem; letter-spacing: 0.08em; text-transform: uppercase;
                     color: var(--ink-muted); }
      .usage-value { font-family: var(--mono); font-size: 1.05rem; }
      .usage-model { font-family: var(--mono); font-size: 0.7rem; color: var(--ink-muted); }
      .table-wrap { padding: 0; overflow-x: auto; }
      .row-link { cursor: pointer; }
      .row-link:hover td { background: var(--surface-alt); }
      .empty { text-align: center; padding: 3rem 1rem; }
      .empty p { margin: 0.2rem 0; }
    `,
  ],
})
export class DashboardComponent {
  private readonly service = inject(IncidentService);

  readonly incidents = signal<Incident[]>([]);
  readonly usage = signal<UsageSnapshot | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.service.listIncidents().subscribe({
      next: (incidents) => {
        this.incidents.set(incidents);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load incidents. Is the incident service running on port 8081?');
        this.loading.set(false);
      },
    });

    // Only operators may read the usage endpoint, so a viewer simply sees no budget panel.
    this.service.usage().subscribe({ next: (usage) => this.usage.set(usage), error: () => undefined });
  }
}
