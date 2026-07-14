import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import CreateJobPage from '../../app/dashboard/jobs/page';
import { QueryProvider } from '../../providers/QueryProvider';
import { WorkspaceProvider } from '../../providers/WorkspaceProvider';

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

test('simulation flow for job submission input state changes', async () => {
  render(
    <QueryProvider>
      <WorkspaceProvider>
        <CreateJobPage />
      </WorkspaceProvider>
    </QueryProvider>
  );

  const input = screen.getByPlaceholderText('https://www.youtube.com/watch?v=...');
  fireEvent.change(input, { target: { value: 'https://youtube.com/watch?v=abcdef' } });
  
  expect(input).toHaveValue('https://youtube.com/watch?v=abcdef');
});
