# Navigation and Fast Scrolling Fix for Live TV

The user reported that when scrolling fast in the Live TV channel list using a remote (holding Up/Down buttons), the app unexpectedly switches to another category. This is likely due to focus jumping between panels (from channels to categories) or UI jank caused by rapid mini-player initialization during scrolling.

## Proposed Changes

### [Live TV Component]

#### [MODIFY] [LiveTvActivity.java](file:///C:/Users/by-kerimoff/AndroidStudioProjects/aurex-player/app/src/main/java/com/bykerimoff/player/LiveTvActivity.java)

1.  **Debounce Mini Player Playback**: Add a `Handler` and `Runnable` to delay `playMiniStream` by 500ms when a channel is focused. This prevents the app from trying to start playback for every item passed during fast scrolling, reducing UI lag.
2.  **Robust Focus Management in `onKeyDown`**:
    *   Increase `KEY_DELAY` to `30ms` to better handle rapid D-pad events.
    *   Improve `getRvPosition` using `findContainingItemView` to ensure the focused view's position is always correctly identified even if it's a sub-view.
    *   Ensure focus doesn't jump from `rvChannels` to `rvCategories` when navigating vertically (Up/Down).
3.  **Refined Wrapping Logic**:
    *   Improve `scrollToAndFocus` to use `scrollToPositionWithOffset` where possible for better visibility of the target item.
    *   Slightly reduce the delay in `scrollToAndFocus` for a snappier feel.

## Verification Plan

### Manual Verification
1.  Open Live TV.
2.  Select a category to view the channel list.
3.  Hold the Down button on the remote to scroll fast through the channels.
4.  Verify that focus stays within the channel list and doesn't jump to the category sidebar.
5.  Verify that the mini-player only starts playing when scrolling stops for a moment (500ms).
6.  Test wrapping from the first item (Up) and last item (Down) to ensure it still works correctly.
