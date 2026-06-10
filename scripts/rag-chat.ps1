<#
.SYNOPSIS
  Interactive RAG Q&A against the QuantLens chat endpoint, from the terminal.

.DESCRIPTION
  Logs in as a demo persona (session cookie), then loops: type a question and the
  grounded answer prints with its 10-K citations.

  In DEMO mode (no key) every question returns the same seeded, grounded answer +
  citations — it proves the RAG response shape with zero setup. Pass -ApiKey to set
  a session-scoped LLM key and get genuine retrieval + a live model answer.

  Requires the stack to be running:  docker compose up -d

.EXAMPLE
  .\scripts\rag-chat.ps1
  # demo mode as alice

.EXAMPLE
  .\scripts\rag-chat.ps1 -User bob -ApiKey sk-ant-xxxxx -Provider anthropic
  # live mode against Anthropic, scoped to this session only

.NOTES
  curl equivalent (demo):
    curl -s -c jar.txt -X POST http://localhost:8080/api/auth/login \
      -d "username=alice&password=demo1234"
    curl -s -b jar.txt -X POST http://localhost:8080/api/ai/chat \
      -H "Content-Type: application/json" -d '{"message":"What are AAPL'\''s key risks?"}'
#>
param(
  [string]$User = "alice",
  [string]$Password = "demo1234",
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ApiKey = "",
  [ValidateSet("anthropic", "openai")][string]$Provider = "anthropic"
)

$ErrorActionPreference = "Stop"
function Say($text, $color) { Write-Host $text -ForegroundColor $color }

# 1) Log in — captures the session cookie into $session
try {
  Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
    -Body @{ username = $User; password = $Password } -SessionVariable session | Out-Null
}
catch {
  Say "Login failed for '$User' at $BaseUrl. Is the stack up?  docker compose up -d" Red
  exit 1
}

# 2) Optional: switch this session to live by setting an LLM key (never persisted server-side)
if ($ApiKey) {
  try {
    $keyBody = @{ provider = $Provider; apiKey = $ApiKey } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/ai/key" -Method Post -ContentType "application/json" `
      -Body $keyBody -WebSession $session | Out-Null
  }
  catch {
    Say "Could not set the API key (continuing in demo mode): $($_.Exception.Message)" Yellow
  }
}

# 3) Report mode
$status = Invoke-RestMethod -Uri "$BaseUrl/api/ai/status" -Method Get -WebSession $session
$mode = ([string]$status.mode).ToUpper()

Say "`n  QuantLens RAG Q&A   user: $User   mode: $mode" Cyan
if ($mode -eq "DEMO") {
  Say "  demo mode: every question returns the seeded grounded answer + citations (pass -ApiKey for live)" DarkGray
}
Say "  Ask about the portfolio's filings. Type 'exit' to quit." DarkGray
Say "  e.g.  What are AAPL's key risks?   |   How is NVIDIA exposed to AI?   |   What rate risk does JPM face?`n" DarkGray

# 4) Chat loop
while ($true) {
  $q = Read-Host "you"
  if ([string]::IsNullOrWhiteSpace($q)) { continue }
  if ($q -eq "exit" -or $q -eq "quit") { break }

  try {
    $body = @{ message = $q } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/ai/chat" -Method Post `
      -ContentType "application/json" -Body $body -WebSession $session

    Say "`nquantlens:" Green
    Write-Host $resp.answer
    if ($resp.citations -and $resp.citations.Count -gt 0) {
      Say "`nsources:" DarkGray
      foreach ($c in $resp.citations) {
        Say ("  - [{0} / {1}] {2}" -f $c.ticker, $c.section, $c.source) DarkGray
      }
    }
    Write-Host ""
  }
  catch {
    Say "request failed: $($_.Exception.Message)" Red
  }
}

Say "bye." DarkGray
