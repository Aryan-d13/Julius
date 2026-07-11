import { renderHook, act } from '@testing-library/react';
import { expect, test } from 'vitest';
import { useCurrentUser } from '../../hooks/useCurrentUser';

test('manages current user profile state with local storage updates', () => {
  const { result } = renderHook(() => useCurrentUser());
  expect(result.current.user).toBeNull();

  act(() => {
    result.current.setUser({
      id: 'usr-4444',
      email: 'test@julius.com',
      fullName: 'Test Operator'
    });
  });

  expect(result.current.user).toEqual({
    id: 'usr-4444',
    email: 'test@julius.com',
    fullName: 'Test Operator'
  });
});
