# 16 Android Wellness Dashboard

This document summarizes the requirements and architecture for the Wellness Dashboard feature implemented on the Android client. The dashboard replaces the raw "Records" list with a unified glanceable view of the user's daily metrics, weekly trends, AI recommendations, and an in-memory historical record filter.

---

## What It Does

### 1. Today's Snapshot
- Shows today's **Sleep** hours, **Activity** minutes, and **Mood** score in three horizontal tiles at the top.
- If today has no logged entry yet, it automatically falls back to displaying the most recent day's values with a "No entry today" note and date label.

### 2. Metric Cards & Sparklines
Three cards represent Sleep, Activity, and Mood, each featuring:
- **Trend Charts**: Drawn using a custom Canvas view (`SparklineView.kt`) without adding external dependencies:
  - **Sleep**: Blue line chart with a dot at the end.
  - **Activity**: Green bar chart (skips zero-exercise days).
  - **Mood**: Amber line chart with dots on all logged points.
- **Weekly Stats**: Text summary (e.g., "Avg 7.2 h · 6 days logged") of the past 7 calendar days.
- **Status Badges**: Derived from weekly averages:
  - **Excellent**: Green (`#2E7D32`)
  - **Good**: Teal (`#00695C`)
  - **Fair**: Amber (`#E65100`)
  - **Below Target**: Red (error color)
  - **No Data**: Grey (secondary text color)

Threshold limits (comparisons are rounded to 1 decimal place to prevent floating-point issues):

| Metric | Excellent | Good | Fair | Below Target |
|---|---|---|---|---|
| Sleep Average | ≥ 8.0h | 7.0 - 7.9h | 6.0 - 6.9h | < 6.0h |
| Active Days | ≥ 5 days | 3 - 4 days | 1 - 2 days | 0 days |
| Mood Average | ≥ 4.0 | 3.0 - 3.9 | 2.0 - 2.9 | < 2.0 |

### 3. AI Insight Teaser
- Displays a preview card showing the newest generated recommendation (title and truncated excerpt, capped at 120 characters).
- Tapping the teaser redirects the user directly to the Recommendations tab. If no recommendations exist, it shows a placeholder prompt to generate one.

### 4. Historical Records & Date Filter
- Renders the scrollable record cards under the cards section.
- Added a `📅 Filter` button that opens a start-date and end-date `DatePickerDialog` sequence.
- Filters the list in-memory client-side (no redundant API calls are triggered). When active, a dismissible filter chip appears to clear the filter.

### 5. Multi-Log Aggregation & Safety
- **Deduplication**: If a user logs multiple records on the same day, they are consolidated for calculations: sums exercise minutes, averages sleep hours, and averages mood score.
- **Crash Prevention**: Malformed or unparseable record dates are silently skipped during calculations so the app doesn't crash on bad database rows.

---

## How It's Built

The implementation is kept lightweight and decoupled:
1. **`DashboardDataHelper.kt`**: A pure-Kotlin companion helper that handles all aggregations, date grouping, badge threshold calculations, and date filters. Because it has no Android SDK dependencies, it can be tested quickly on local JVMs.
2. **`SparklineView.kt`**: A lightweight custom `View` that overrides `onDraw` to render lines, round-rect bars, and axis labels directly to the native `Canvas`.
3. **`HomeActivity.kt`**: Integrated as the tab landing view. Orchestrates concurrent loading of wellness records and AI recommendations, and builds the UI programmatically using standard Android layout views.

---

## How We Test It

1. **JVM Unit Tests (`DashboardDataHelperTest.kt`)**:
   - Deduplication correctness (averaging vs. summing).
   - Defensive parsing (skipping malformed dates).
   - Rounding margins (e.g. average sleep of `6.99` rounds to `7.0` and correctly earns a `GOOD` status badge).
   - Date filter inclusivity on range boundaries.
2. **Manual Checks**:
   - Fast load time under 2 seconds.
   - Graceful empty states when there is zero data or the backend is offline.
   - Multi-tab navigation safety.
