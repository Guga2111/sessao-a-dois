#!/bin/bash
# Ralph Wiggum - Long-running AI agent loop
# Usage: ./ralph.sh [--tool amp|claude] [max_iterations]

set -e

# Parse arguments
TOOL="amp"  # Default to amp for backwards compatibility
MAX_ITERATIONS=10

while [[ $# -gt 0 ]]; do
  case $1 in
    --tool)
      TOOL="$2"
      shift 2
      ;;
    --tool=*)
      TOOL="${1#*=}"
      shift
      ;;
    *)
      # Assume it's max_iterations if it's a number
      if [[ "$1" =~ ^[0-9]+$ ]]; then
        MAX_ITERATIONS="$1"
      fi
      shift
      ;;
  esac
done

# Validate tool choice
if [[ "$TOOL" != "amp" && "$TOOL" != "claude" ]]; then
  echo "Error: Invalid tool '$TOOL'. Must be 'amp' or 'claude'."
  exit 1
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRD_FILE="$SCRIPT_DIR/prd.json"
PROGRESS_FILE="$SCRIPT_DIR/progress.txt"
ARCHIVE_DIR="$SCRIPT_DIR/archive"
LAST_BRANCH_FILE="$SCRIPT_DIR/.last-branch"

# Archive previous run if branch changed
if [ -f "$PRD_FILE" ] && [ -f "$LAST_BRANCH_FILE" ]; then
  CURRENT_BRANCH=$(jq -r '.branchName // empty' "$PRD_FILE" 2>/dev/null || echo "")
  LAST_BRANCH=$(cat "$LAST_BRANCH_FILE" 2>/dev/null || echo "")
  
  if [ -n "$CURRENT_BRANCH" ] && [ -n "$LAST_BRANCH" ] && [ "$CURRENT_BRANCH" != "$LAST_BRANCH" ]; then
    # Archive the previous run
    DATE=$(date +%Y-%m-%d)
    # Strip "ralph/" prefix from branch name for folder
    FOLDER_NAME=$(echo "$LAST_BRANCH" | sed 's|^ralph/||')
    ARCHIVE_FOLDER="$ARCHIVE_DIR/$DATE-$FOLDER_NAME"
    
    echo "Archiving previous run: $LAST_BRANCH"
    mkdir -p "$ARCHIVE_FOLDER"
    [ -f "$PRD_FILE" ] && cp "$PRD_FILE" "$ARCHIVE_FOLDER/"
    [ -f "$PROGRESS_FILE" ] && cp "$PROGRESS_FILE" "$ARCHIVE_FOLDER/"
    echo "   Archived to: $ARCHIVE_FOLDER"
    
    # Reset progress file for new run
    echo "# Ralph Progress Log" > "$PROGRESS_FILE"
    echo "Started: $(date)" >> "$PROGRESS_FILE"
    echo "---" >> "$PROGRESS_FILE"
  fi
fi

# Track current branch
if [ -f "$PRD_FILE" ]; then
  CURRENT_BRANCH=$(jq -r '.branchName // empty' "$PRD_FILE" 2>/dev/null || echo "")
  if [ -n "$CURRENT_BRANCH" ]; then
    echo "$CURRENT_BRANCH" > "$LAST_BRANCH_FILE"
  fi
fi

# Initialize progress file if it doesn't exist
if [ ! -f "$PROGRESS_FILE" ]; then
  echo "# Ralph Progress Log" > "$PROGRESS_FILE"
  echo "Started: $(date)" >> "$PROGRESS_FILE"
  echo "---" >> "$PROGRESS_FILE"
fi

# Use the branch that is currently checked out — never create/switch branches.
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
if [ -z "$GIT_BRANCH" ] || [ "$GIT_BRANCH" = "HEAD" ]; then
  echo "Error: not on a git branch (detached HEAD or not a repo). Checkout a branch first."
  exit 1
fi

# Instruction appended to every prompt so the agent stays on the current branch.
BRANCH_NOTE=$(printf '\n\n## Current Git Branch (DO NOT CHANGE)\nYou are on branch `%s`. Implement, commit, and stay on THIS branch.\nDo NOT create or switch branches (no `git checkout -b`, `git switch -c`, `git checkout <other>`, or `git branch`).\nIgnore `branchName` in prd.json for switching purposes.\n' "$GIT_BRANCH")

echo "Starting Ralph - Tool: $TOOL - Max iterations: $MAX_ITERATIONS"
echo "Using current git branch: $GIT_BRANCH (auto branch creation disabled)"

for i in $(seq 1 $MAX_ITERATIONS); do
  echo ""
  echo "==============================================================="
  echo "  Ralph Iteration $i of $MAX_ITERATIONS ($TOOL)"
  echo "==============================================================="

  # Run the selected tool with the ralph prompt (+ current-branch instruction)
  if [[ "$TOOL" == "amp" ]]; then
    OUTPUT=$( { cat "$SCRIPT_DIR/prompt.md"; printf '%s' "$BRANCH_NOTE"; } | amp --dangerously-allow-all 2>&1 | tee /dev/stderr) || true
  else
    # Claude Code: use --dangerously-skip-permissions for autonomous operation, --print for output
    OUTPUT=$( { cat "$SCRIPT_DIR/CLAUDE.md"; printf '%s' "$BRANCH_NOTE"; } | claude --dangerously-skip-permissions --print 2>&1 | tee /dev/stderr) || true
  fi
  
  # Check for completion. The model's own "<promise>COMPLETE</promise>" text is
  # NOT authoritative: it can appear in free-text output even when the agent is
  # explaining/declining the stop condition (e.g. quoting the instruction while
  # saying it does NOT apply yet), which previously caused false-positive early
  # exits. prd.json (via jq) is the source of truth for whether all stories pass.
  REMAINING=$(jq '[.userStories[] | select(.passes != true)] | length' "$PRD_FILE" 2>/dev/null || echo "")
  if [ "$REMAINING" = "0" ]; then
    echo ""
    echo "Ralph completed all tasks! (verified via prd.json: all stories passes=true)"
    echo "Completed at iteration $i of $MAX_ITERATIONS"
    exit 0
  fi
  if echo "$OUTPUT" | grep -q "<promise>COMPLETE</promise>" && [ -n "$REMAINING" ]; then
    echo ""
    echo "Note: model emitted the COMPLETE signal, but $REMAINING stor$([ "$REMAINING" = "1" ] && echo y || echo ies) in prd.json still have passes=false — ignoring the signal and continuing."
  fi
  
  echo "Iteration $i complete. Continuing..."
  sleep 2
done

echo ""
echo "Ralph reached max iterations ($MAX_ITERATIONS) without completing all tasks."
echo "Check $PROGRESS_FILE for status."
exit 1
