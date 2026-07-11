import { renderHook, act } from '@testing-library/react';
import { expect, test } from 'vitest';
import { useEditorHistory } from '../../hooks/useEditorHistory';

test('manages undo and redo stacks correctly for timeline updates', () => {
  const { result } = renderHook(() => useEditorHistory('v1-timeline'));
  expect(result.current.state).toBe('v1-timeline');
  expect(result.current.canUndo).toBe(false);

  act(() => {
    result.current.execute('v2-timeline');
  });
  expect(result.current.state).toBe('v2-timeline');
  expect(result.current.canUndo).toBe(true);

  act(() => {
    result.current.undo();
  });
  expect(result.current.state).toBe('v1-timeline');
  expect(result.current.canRedo).toBe(true);

  act(() => {
    result.current.redo();
  });
  expect(result.current.state).toBe('v2-timeline');
});
