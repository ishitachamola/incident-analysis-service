#!/usr/bin/env bash
# Scores the platform's analyses against known expected answers.
#
# Each simulated scenario has a documented root cause and a historical incident that is the correct
# precedent, so an analysis can be checked rather than admired. Two things are measured per incident:
#
#   root cause   - do the expected terms appear in the stated root cause (a keyword proxy, not a
#                  claim of semantic equivalence; the wording is reviewed by hand in the report)
#   precedent    - did it cite the correct historical incident
#
# Grounding statistics come from the platform's own validation: how many claims cited evidence that
# was actually provided, and how many invented citations were removed.
#
# By default it reads analyses that already exist and makes NO model calls. Pass --analyse to
# produce missing ones, which costs one model call per incident.
set -uo pipefail

INCIDENT_API="${INCIDENT_API:-http://localhost:8081}"
ANALYSIS_API="${ANALYSIS_API:-http://localhost:8083}"
USERNAME="${EVAL_USER:-sre}"
PASSWORD="${EVAL_PASSWORD:-sre123}"
ANALYSE=false
[[ "${1:-}" == "--analyse" ]] && ANALYSE=true

# service | expected root-cause terms (all must appear) | expected precedent
EXPECTATIONS=(
  "payment-service|pool|INC-003"
  "order-service|consumer|INC-002"
  "user-service|deployment|INC-004"
  "inventory-service|quer|INC-005"
)

token=$(curl -s -X POST "$INCIDENT_API/api/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
if [[ -z "$token" || ${#token} -lt 20 ]]; then
  echo "Could not sign in to $INCIDENT_API. Is the incident service running?" >&2
  exit 1
fi

incidents=$(curl -s -H "Authorization: Bearer $token" "$INCIDENT_API/api/incidents")

printf '%-20s %-9s %-10s %-9s %-8s\n' SERVICE ROOT-CAUSE PRECEDENT VERIFIED INVENTED
printf '%s\n' "---------------------------------------------------------------"

total=0; root_ok=0; precedent_ok=0; claims_total=0; claims_verified=0; invented=0

for expectation in "${EXPECTATIONS[@]}"; do
  IFS='|' read -r service terms precedent <<< "$expectation"

  id=$(printf '%s' "$incidents" | tr '{' '\n' | grep "\"serviceName\":\"$service\"" \
        | sed -E 's/.*"id":"([^"]+)".*/\1/' | head -1)
  [[ -z "$id" ]] && { printf '%-20s %s\n' "$service" "no incident found"; continue; }

  if [[ "$ANALYSE" == true ]]; then
    curl -s -X POST -H "Authorization: Bearer $token" \
      "$ANALYSIS_API/api/incidents/$id/analyze" --max-time 180 > /dev/null
    sleep 13   # stays under the model's per-minute limit
  fi

  analysis=$(curl -s -H "Authorization: Bearer $token" "$ANALYSIS_API/api/incidents/$id/analysis")
  if ! printf '%s' "$analysis" | grep -q '"rootCause"'; then
    printf '%-20s %s\n' "$service" "no analysis yet (re-run with --analyse)"
    continue
  fi

  total=$((total + 1))
  root_cause=$(printf '%s' "$analysis" | sed -E 's/.*"rootCause":"([^"]*)".*/\1/' | tr '[:upper:]' '[:lower:]')

  root_match=MISS
  if printf '%s' "$root_cause" | grep -qi "$terms"; then root_match=HIT; root_ok=$((root_ok + 1)); fi

  precedent_match=MISS
  if printf '%s' "$analysis" | grep -q "$precedent"; then
    precedent_match=HIT; precedent_ok=$((precedent_ok + 1))
  fi

  verified=$(printf '%s' "$analysis" | sed -E 's/.*"claimsVerified":([0-9]+).*/\1/')
  claims=$(printf '%s' "$analysis" | sed -E 's/.*"claimsTotal":([0-9]+).*/\1/')
  removed=$(printf '%s' "$analysis" | sed -E 's/.*"invalidCitationsRemoved":([0-9]+).*/\1/')
  claims_total=$((claims_total + claims)); claims_verified=$((claims_verified + verified))
  invented=$((invented + removed))

  printf '%-20s %-9s %-10s %-9s %-8s\n' "$service" "$root_match" "$precedent_match" \
    "$verified/$claims" "$removed"
done

echo
echo "Scenarios scored:        $total"
echo "Root cause correct:      $root_ok/$total"
echo "Correct precedent cited: $precedent_ok/$total"
echo "Claims citing real evidence: $claims_verified/$claims_total"
echo "Invented citations caught and removed: $invented"
