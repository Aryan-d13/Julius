import { renderHook, act } from '@testing-library/react';
import { expect, test } from 'vitest';
import { useEditorHistory } from '../../hooks/useEditorHistory';

interface WordItem {
  text: string;
  start: number;
  end: number;
}

interface TimelineState {
  durationSeconds: number;
  tracks: Array<{
    id: string;
    type: string;
    name: string;
    segments: Array<{
      id: string;
      assetId: string;
      sourceStart: number;
      timelineStart: number;
      duration: number;
      words: WordItem[];
    }>;
  }>;
}

const initialTimeline: TimelineState = {
  durationSeconds: 30,
  tracks: [
    {
      id: 'track-1',
      type: 'SUBTITLE',
      name: 'Subtitles',
      segments: [
        {
          id: 'seg-1',
          assetId: 'asset-1',
          sourceStart: 0.0,
          timelineStart: 0.0,
          duration: 30.0,
          words: [
            { text: 'Julius', start: 0.5, end: 1.5 },
            { text: 'editor', start: 1.6, end: 2.5 }
          ]
        }
      ]
    }
  ]
};

test('timeline operations: trimming, editing words, undoing and styling updates', () => {
  const { result } = renderHook(() => useEditorHistory<TimelineState>(initialTimeline));

  // 1. Verify initial layout state
  expect(result.current.state.durationSeconds).toBe(30);
  expect(result.current.state.tracks[0].segments[0].duration).toBe(30.0);

  // 2. Perform segment trimming (trim start to 5.0, duration to 20.0)
  const trimmedSegment = {
    ...result.current.state.tracks[0].segments[0],
    timelineStart: 5.0,
    duration: 20.0
  };
  const trimmedState: TimelineState = {
    ...result.current.state,
    tracks: [
      {
        ...result.current.state.tracks[0],
        segments: [trimmedSegment]
      }
    ]
  };

  act(() => {
    result.current.execute(trimmedState);
  });
  expect(result.current.state.tracks[0].segments[0].timelineStart).toBe(5.0);
  expect(result.current.state.tracks[0].segments[0].duration).toBe(20.0);
  expect(result.current.canUndo).toBe(true);

  // 3. Edit Transcript text (word at index 0 text "Julius" -> "Julius AI")
  const editedWords = [...result.current.state.tracks[0].segments[0].words];
  editedWords[0] = { ...editedWords[0], text: 'Julius AI' };

  const editedState: TimelineState = {
    ...result.current.state,
    tracks: [
      {
        ...result.current.state.tracks[0],
        segments: [
          {
            ...result.current.state.tracks[0].segments[0],
            words: editedWords
          }
        ]
      }
    ]
  };

  act(() => {
    result.current.execute(editedState);
  });
  expect(result.current.state.tracks[0].segments[0].words[0].text).toBe('Julius AI');

  // 4. Test Undo operation (should revert to trimmed state)
  act(() => {
    result.current.undo();
  });
  expect(result.current.state.tracks[0].segments[0].words[0].text).toBe('Julius');
  expect(result.current.state.tracks[0].segments[0].timelineStart).toBe(5.0);

  // 5. Test Redo operation (should restore the text edit)
  act(() => {
    result.current.redo();
  });
  expect(result.current.state.tracks[0].segments[0].words[0].text).toBe('Julius AI');
});
